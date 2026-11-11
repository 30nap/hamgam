-- One row per browser (first-party cookie). Attribution is last non-empty UTM touch.
create table visit (
    id              uuid primary key,
    first_seen_at   timestamptz not null,
    last_seen_at    timestamptz not null,
    utm_source      text        not null default '(none)',
    utm_medium      text,
    utm_campaign    text,
    referrer        text,
    form_started_at timestamptz
);
create index visit_utm_source_idx on visit (utm_source);

-- Raw page-view log.
create table page_view (
    id           bigserial primary key,
    visit_id     uuid        not null references visit (id),
    viewed_at    timestamptz not null,
    utm_source   text        not null,
    utm_medium   text,
    utm_campaign text,
    referrer     text
);
create index page_view_utm_source_idx on page_view (utm_source);

-- One row per SMS code sent. Also the source of truth for rate limits.
-- The code itself is never stored, only an HMAC of it.
create table otp_request (
    id           uuid primary key,
    phone        text        not null,
    ip           text        not null,
    code_hash    text        not null,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    attempts     int         not null default 0,
    consumed_at  timestamptz,
    visit_id     uuid references visit (id),
    platforms    text        not null default '',
    price_answer text
);
create index otp_request_phone_idx on otp_request (phone, created_at);
create index otp_request_ip_idx on otp_request (ip, created_at);

-- Verified pre-registrations. One per phone.
create table signup (
    id               bigserial primary key,
    phone            text        not null unique,
    platforms        text        not null default '',
    price_answer     text,
    visit_id         uuid references visit (id),
    utm_source       text        not null default '(none)',
    utm_medium       text,
    utm_campaign     text,
    referrer         text,
    first_seen_at    timestamptz,
    otp_requested_at timestamptz not null,
    verified_at      timestamptz not null,
    updated_at       timestamptz not null
);
create index signup_utm_source_idx on signup (utm_source);
