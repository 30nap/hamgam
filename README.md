# Hamgam (همگام) pre-registration page

A single landing page that measures demand for Hamgam **before the product exists**.
Visitors leave a phone number, say where they sell and whether the price works for them,
and confirm the number with an SMS code. An admin page shows the funnel per ad source.

The page says plainly that the product is not built yet. It has no invented features,
testimonials or user counts. Its text lives in [copy-fa.md](copy-fa.md).

- Java 25, Spring Boot 4, PostgreSQL, server-rendered HTML, about 160 lines of plain JavaScript
- **Zero requests to other hosts**: self-hosted fonts (Vazirmatn, Lalezar), no CDN, no analytics script.
  A strict Content-Security-Policy makes the browser enforce this, and a test checks it.
- Tracking is first-party and server-side.

## Contents

1. [Run it locally](#1-run-it-locally)
2. [Configuration](#2-configuration)
3. [Ad links and the admin page](#3-ad-links-and-the-admin-page)
4. [Deploy on an Iranian VPS](#4-deploy-on-an-iranian-vps)
5. [Operating it](#5-operating-it)
6. [How it works](#6-how-it-works)

---

## 1. Run it locally

You need **JDK 25** and Maven 3.9+. Docker is not needed for development or tests.

If `mvn -v` reports an older Java, point `JAVA_HOME` at JDK 25 for the current shell first:

```bash
export JAVA_HOME="/path/to/jdk-25"      # PowerShell: $env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
```

Start the site with a throw-away PostgreSQL and console SMS:

```bash
mvn spring-boot:test-run -Dspring-boot.run.main-class=ir.hamgam.landing.LocalDevApplication
```

- Site: <http://localhost:8090>
- The SMS code is printed in the log: `[console sms] code for 0912... is 123456`
- Admin: <http://localhost:8090/admin>, user `admin`, password `admin`
- All data is discarded when you stop it.

Run the tests:

```bash
mvn verify
```

The integration tests start a real PostgreSQL in-process (downloaded once by Maven), so
they need no Docker. They cover phone normalization, OTP expiry and rate limits, UTM
capture, the admin numbers, the verbatim copy and the no-external-requests rule.

## 2. Configuration

Everything is set with environment variables. For Docker Compose, copy `.env.example`
to `.env` and fill it in. `.env` is git-ignored.

| Variable | Required | Meaning |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | yes | Database name and credentials |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | yes | Basic-auth login for `/admin`. The app refuses to start without them |
| `SMS_PROVIDER` | yes | `kavenegar` in production, `console` for local development only |
| `KAVENEGAR_API_KEY` | with kavenegar | API key from the Kavenegar panel |
| `KAVENEGAR_OTP_TEMPLATE` | with kavenegar | Name of the verify template (its text must contain `%token`) |
| `OTP_HMAC_SECRET` | yes | Long random string used to hash codes before storing them |
| `OTP_PHONE_DAILY_LIMIT` | no (5) | Codes per phone number per 24 hours |
| `OTP_IP_HOURLY_LIMIT` | no (30) | Codes per IP address per hour |
| `OTP_PHONE_COOLDOWN` | no (60s) | Minimum gap between two codes to one phone |
| `OTP_TTL` | no (2m) | How long a code is valid |
| `OTP_MAX_ATTEMPTS` | no (5) | Wrong guesses allowed per code |
| `HTTP_PORT`, `HTTPS_PORT` | no (80, 443) | Host ports nginx is published on. Change only for local testing |
| `APP_VERSION` | no (latest) | Tag of the app image |
| `REGISTRY` | no (docker.io) | Registry host for base images (set to a mirror if Docker Hub is blocked) |
| `MAVEN_MIRROR_URL` | no | Maven repository mirror, used only while building the image |

The per-IP limit is generous on purpose. Iranian mobile carriers put many subscribers
behind one public address, so a strict limit would block real visitors arriving from the
same ad. The per-phone limits are the ones that protect your SMS budget.

Running without Docker, the app reads `DB_URL`, `DB_USER` and `DB_PASSWORD` instead of the
`POSTGRES_*` variables.

## 3. Ad links and the admin page

Tag every ad with UTM parameters. One `utm_source` per channel:

```text
https://YOUR-DOMAIN/?utm_source=yektanet&utm_medium=cpc&utm_campaign=repricing-a
https://YOUR-DOMAIN/?utm_source=bale&utm_medium=channel-post&utm_campaign=repricing-a
```

`utm_source` and `utm_medium` are stored in lower case. Visits without a tag are grouped
under `(none)`.

The thank-you page asks people to pass the link on to another seller. That link is tagged
`utm_source=share`, so word of mouth shows up as its own row.

Open `https://YOUR-DOMAIN/admin`. For each `utm_source` it shows:

| Column | Definition |
|---|---|
| Views | Loads of the landing page. Bots, link previews and prefetches are not counted |
| Visitors | Distinct browsers (first-party cookie `hv`) |
| Form starts | Visitors who touched the form at least once |
| Codes sent | Distinct phone numbers that were sent an SMS code |
| Verified signups | Phone numbers confirmed with a correct code. One per phone |
| Price reservations | Verified signups who answered "آره، حتماً" to the price question |
| Start rate | Form starts / visitors |
| Signup rate | Verified signups / visitors |
| Completion | Verified signups / form starts |
| Reservation rate | Price reservations / verified signups |

Two more tables show the price answers (yes / maybe / no / not answered) and the selling
platforms, again per source. Both CSV exports are linked at the top of the page:
`/admin/stats.csv` (the funnel) and `/admin/signups.csv` (every verified signup with its
UTM tags, referrer and timestamps).

A visitor is attributed to the most recent `utm_source` they arrived with. If they later
return without a tag, they keep that source.

Decide what counts as "build it", "iterate" and "kill it" **before** you spend the ad
budget, and write the numbers down. Otherwise any result can be read as encouraging.

## 4. Deploy on an Iranian VPS

Assumptions: a fresh Ubuntu 22.04/24.04 server inside Iran, a domain, and your own
machine with Docker and working access to Docker Hub and Maven Central.

From inside Iran, Docker Hub, Maven Central and Let's Encrypt may be unreachable or may
refuse service. This guide therefore never depends on the server reaching any of them.

### 4.1 Harden the server first

Do this before anything else. Keep your current SSH session open until step 4 succeeds.

**1. Create a non-root user with your SSH key**

```bash
# on the server, as root
adduser deploy
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
```

```bash
# on your machine: send your public key (create one with `ssh-keygen -t ed25519` if needed)
ssh-copy-id -i ~/.ssh/id_ed25519.pub deploy@SERVER_IP
```

On Windows without `ssh-copy-id`:

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh deploy@SERVER_IP "cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

**2. Allow keys only, no passwords, no root login**

```bash
sudo tee /etc/ssh/sshd_config.d/00-hardening.conf >/dev/null <<'EOF'
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
sudo sshd -t && sudo systemctl reload ssh
```

The file name starts with `00-` because sshd uses the first value it reads, and cloud
images often ship a `50-cloud-init.conf` that turns passwords back on.

**3. Check the effective settings**

```bash
sudo sshd -T | grep -Ei 'passwordauthentication|kbdinteractiveauthentication|permitrootlogin'
```

All three must print `no`.

**4. Test from a new terminal** (do not close the old one yet)

```bash
ssh deploy@SERVER_IP                                   # must log in with the key
ssh -o PubkeyAuthentication=no deploy@SERVER_IP        # must be refused
```

**5. Firewall: only 22, 80 and 443**

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Two things to know about this firewall:

- **Docker bypasses ufw for published ports.** That is safe here because
  `docker-compose.yml` publishes only 80 and 443. PostgreSQL and the app have no
  published port. Never add a `ports:` entry for them.
- If your provider has its own firewall panel, set the same three ports there too.

**6. Keep it patched**

```bash
sudo apt update && sudo apt install -y unattended-upgrades fail2ban
sudo dpkg-reconfigure -plow unattended-upgrades
```

### 4.2 Install Docker on the server

Docker's own download site may be blocked. Ubuntu's packages come from Ubuntu mirrors:

```bash
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker deploy      # log out and in again afterwards
docker compose version
```

### 4.3 Get the images onto the server

The stack needs three images: the app, `postgres:17-alpine` and `nginx:stable-alpine`.
Choose one of the two options.

#### Option A (recommended): build locally, transfer with `docker save` / `docker load`

The server never talks to Docker Hub or Maven Central.

On your machine, in this folder:

```bash
docker build --platform linux/amd64 -t hamgam-landing:1 .
docker pull --platform linux/amd64 postgres:17-alpine
docker pull --platform linux/amd64 nginx:stable-alpine
docker save -o hamgam-images.tar hamgam-landing:1 postgres:17-alpine nginx:stable-alpine
```

Use `linux/arm64` instead if the server has an ARM processor (`uname -m` prints `aarch64`).

Copy the images and the deployment files:

```bash
ssh deploy@SERVER_IP "sudo install -d -o deploy -g deploy /opt/hamgam"
scp hamgam-images.tar docker-compose.yml .env.example deploy@SERVER_IP:/opt/hamgam/
scp -r deploy deploy@SERVER_IP:/opt/hamgam/
```

On the server:

```bash
cd /opt/hamgam
docker load -i hamgam-images.tar
docker images        # hamgam-landing:1, postgres:17-alpine and nginx:stable-alpine must be listed
```

In `.env` (next step) set `APP_VERSION=1` and leave `REGISTRY=docker.io`. Start with
`--no-build --pull never` so Compose uses only the loaded images.

To ship an update, build `hamgam-landing:2`, save and load only that image, set
`APP_VERSION=2`, then:

```bash
docker compose up -d --no-build --pull never
docker compose restart nginx      # nginx looks up the app's address only when it starts
```

The database lives in the `pgdata` volume and is kept across updates.

#### Option B: build on the server through an Iranian registry mirror

Use this if you would rather build on the server. You need the host name of a Docker
registry mirror that is reachable from the server; several Iranian cloud providers run
one. Take the current host name from the provider's documentation.

Either make the mirror transparent for the whole Docker daemon:

```bash
sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{ "registry-mirrors": ["https://MIRROR_HOST"] }
EOF
sudo systemctl restart docker
```

or name it in `.env`, which this project then uses for every base image:

```bash
REGISTRY=MIRROR_HOST
```

The image build also downloads Java libraries. If Maven Central is blocked from the
server, set a Maven mirror in `.env` as well:

```bash
MAVEN_MIRROR_URL=https://MAVEN_MIRROR_HOST/path
```

Then copy the whole project to `/opt/hamgam` (or `git clone` it there) and start it with
`docker compose up -d --build`.

With a mirror, check that the image tags you get are current. Mirrors sometimes lag.

### 4.4 Configure and start

```bash
cd /opt/hamgam
cp .env.example .env
chmod 600 .env
nano .env
```

Set at least `POSTGRES_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `OTP_HMAC_SECRET`,
`KAVENEGAR_API_KEY` and `KAVENEGAR_OTP_TEMPLATE`. Generate secrets with
`openssl rand -hex 32`.

```bash
docker compose up -d --no-build --pull never     # Option A
docker compose up -d --build                     # Option B
```

Check it:

```bash
docker compose ps
curl -s http://localhost/healthz        # prints: ok
docker compose logs -f app
```

### 4.5 HTTPS without Let's Encrypt

Let's Encrypt may not be usable from Iran: its servers have been reported unreachable from
Iranian networks, and its subscriber agreement excludes sanctioned countries. Do not plan
around `certbot`. Use the certificate your CDN provider issues instead. The steps below use
ArvanCloud as the example; menu names in the panel may differ.

1. Add the domain to the CDN and point the domain's nameservers to the CDN.
2. Create an `A` record for the domain with the VPS address and turn the CDN (proxy) on
   for that record.
3. In the HTTPS/SSL settings, issue the provider's certificate for the domain and enable
   "redirect HTTP to HTTPS".
4. Choose how the CDN talks to your server:
   - **HTTPS to the origin (preferred).** Create an origin certificate in the panel, save
     it as `deploy/nginx/certs/fullchain.pem` and `privkey.pem`, then
     `cp deploy/nginx/conf.d/server-tls.conf.example deploy/nginx/conf.d/server-tls.conf`
     and run `docker compose restart nginx`.
   - **HTTP to the origin.** Works with no extra setup, but phone numbers and codes travel
     unencrypted between the CDN and your server. Acceptable only as a short stopgap.
5. Restore real visitor addresses. Behind a CDN every request arrives from the CDN's own
   addresses, which would make the per-IP limit useless:
   `cp deploy/nginx/conf.d/http-realip.conf.example deploy/nginx/conf.d/http-realip.conf`,
   replace the placeholder with the CDN's published edge ranges, and restart nginx.
6. Optional but worthwhile: in your provider's firewall, allow ports 80 and 443 only from
   the CDN's ranges, so nobody can reach the server around the CDN.

Leave the CDN's page caching off for HTML. The app already marks pages as non-cacheable
(each response carries a visitor cookie and a form token) and marks fonts and scripts as
cacheable for a year.

No CDN? Buy a certificate from a reseller that serves Iran, put the two files in
`deploy/nginx/certs/`, and enable `server-tls.conf` the same way.

### 4.6 Kavenegar

1. In the Kavenegar panel, create a **verify (lookup) template**, for example named
   `hamgam-otp`, with a text that contains `%token`, such as: `کد تأیید همگام: %token`
2. Wait until the template is approved.
3. Put the API key and the template name in `.env`, then `docker compose up -d`.
4. Register on the live site with your own phone and confirm the SMS arrives.

Check the request format against Kavenegar's current documentation before launch; the
provider is in `KavenegarSmsProvider.java` and is small. To use another SMS company,
implement the `SmsProvider` interface (one method) and select it with `SMS_PROVIDER`.

## 5. Operating it

```bash
docker compose logs -f app                       # application log
docker compose logs -f nginx                     # access log
docker compose exec postgres pg_dump -U hamgam hamgam | gzip > backup-$(date +%F).sql.gz
docker compose down                              # stop (data stays in the pgdata volume)
```

Back the database up before each update and at the end of each ad test. The phone numbers
are the result of the experiment.

Before you spend money on ads, check on a real phone over mobile data:

- [ ] The page opens over HTTPS, the headline is in Lalezar and the text in Vazirmatn
- [ ] The browser's network panel shows requests to your domain only
- [ ] An SMS code arrives and confirms; `/admin` shows the signup under the right source
- [ ] `/admin` asks for a password; the password is not the one from `.env.example`

## 6. How it works

```text
visitor ── CDN ── nginx ── app (Spring Boot) ── PostgreSQL
                              └── SmsProvider ── Kavenegar
```

**Form flow.** `GET /` logs the view and renders the form. `POST /signup` validates the
phone, checks the limits, stores a hashed code and sends the SMS, then redirects to
`/verify`. `POST /verify` checks the code and creates the signup. `POST /resend` issues a
new code under the same limits. Signing up works without JavaScript. The script reports the
first touch of the form, prevents double taps, makes the price demo draggable, keeps the
sign-up button on screen on phones, and adds the share button on the thank-you page.

**Page design.** One stylesheet, inlined in `fragments.html`; its opening comment describes
the look. On a phone the page is one column that ends in the form, with the button pinned
to the bottom edge while neither the first button nor the form is visible. From 960px up
the form sits beside the text and stays in view. The price demo under the headline uses
made-up numbers and says so (see [copy-fa.md](copy-fa.md)).

**Phone numbers.** `۰۹۱۲…`, `٠٩١٢…`, `+98 912…`, `0098912…` and `912…` all become
`09xxxxxxxxx`. Anything else is rejected.

**Codes.** Six digits, valid for two minutes, five wrong guesses at most. Only an HMAC of
the code is stored. A phone that registers twice still gets one signup; the second time
only updates the answers. The page never reveals whether a number is already registered.

**Limits.** One code per phone per minute and five per day; thirty per IP per hour. They
are counted in the database, so they survive restarts. If the SMS provider fails, the
attempt does not count against the visitor.

**Tracking.** A first-party cookie identifies the browser. UTM tags, the referrer (without
its query string) and timestamps are stored with the visit and copied onto the signup.
Nothing is sent to any third party.

**Privacy.** The database holds phone numbers. Use them only for what the footer promises:
telling people when Hamgam is ready. Protect `/admin`, `.env` and the backups accordingly.

### Project layout

```text
copy-fa.md                              page text (source of truth) + list of added UI strings
src/main/resources/templates/           landing, verify, done, admin pages
src/main/resources/static/              app.js, Vazirmatn and Lalezar fonts + OFL licences
src/main/resources/db/migration/        database schema (Flyway)
src/main/java/ir/hamgam/landing/
  phone/PhoneNumbers.java               normalization
  otp/OtpService.java                   codes, limits, signup
  sms/                                  SmsProvider, Kavenegar, Console
  tracking/Tracking.java                visits, page views, UTM
  web/LandingController.java            public pages
  admin/                                funnel queries, admin page, CSV
  SecurityConfig.java                   basic auth, CSRF, Content-Security-Policy
deploy/nginx/                           nginx.conf and optional CDN / TLS snippets
Dockerfile, docker-compose.yml, .env.example
```

### Font licence

Vazirmatn is copyright The Vazirmatn Project Authors and Lalezar is copyright The Lalezar
Project Authors. Both are used under the SIL Open Font License 1.1. The licence texts ship
with the fonts in `src/main/resources/static/fonts/` (`OFL.txt` and `OFL-Lalezar.txt`).
