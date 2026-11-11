package ir.hamgam.landing.tracking;

import java.net.URI;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-party, server-side tracking. A visit is one browser (cookie); its attribution is the
 * most recent visit that carried a utm_source. Nothing is sent to any third party.
 */
@Service
public class Tracking {

    public static final String NO_SOURCE = "(none)";

    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|curl|wget|python|java/|okhttp|go-http|headless|monitor|uptime|preview|scan|facebookexternalhit",
            Pattern.CASE_INSENSITIVE);

    private final JdbcClient jdbc;
    private final Clock clock;

    public Tracking(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Real page loads only: no bots, link previews, prefetches or HEAD probes. */
    public boolean shouldTrack(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        String purpose = request.getHeader("Sec-Purpose");
        if (purpose == null) {
            purpose = request.getHeader("Purpose");
        }
        if (purpose != null && purpose.toLowerCase(Locale.ROOT).contains("prefetch")) {
            return false;
        }
        String agent = request.getHeader("User-Agent");
        return agent != null && !agent.isBlank() && !BOT.matcher(agent).find();
    }

    /**
     * Logs one page view and returns the visit id to store in the cookie.
     *
     * @param cookieVisit visit id from the cookie, or null for a new browser
     * @param ownHost     this site's host, so internal navigation is not recorded as a referrer
     */
    @Transactional
    public UUID recordView(UUID cookieVisit, String utmSource, String utmMedium, String utmCampaign,
                           String referrerHeader, String ownHost) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        UUID id = cookieVisit != null ? cookieVisit : UUID.randomUUID();
        String source = lower(clean(utmSource, 100));
        boolean hasUtm = source != null;
        String referrer = externalReferrer(referrerHeader, ownHost);

        Attribution attribution = jdbc.sql("""
                        insert into visit (id, first_seen_at, last_seen_at, utm_source, utm_medium, utm_campaign, referrer)
                        values (:id, :now, :now, :source, cast(:medium as text), cast(:campaign as text),
                                cast(:referrer as text))
                        on conflict (id) do update set
                            last_seen_at = excluded.last_seen_at,
                            utm_source = case when cast(:hasUtm as boolean) then excluded.utm_source
                                              else visit.utm_source end,
                            utm_medium = case when cast(:hasUtm as boolean) then excluded.utm_medium
                                              else visit.utm_medium end,
                            utm_campaign = case when cast(:hasUtm as boolean) then excluded.utm_campaign
                                                else visit.utm_campaign end,
                            referrer = case when cast(:hasUtm as boolean)
                                            then coalesce(excluded.referrer, visit.referrer)
                                            else coalesce(visit.referrer, excluded.referrer) end
                        returning utm_source, utm_medium, utm_campaign, referrer
                        """)
                .param("id", id)
                .param("now", now)
                .param("source", hasUtm ? source : NO_SOURCE)
                .param("medium", hasUtm ? lower(clean(utmMedium, 100)) : null, Types.VARCHAR)
                .param("campaign", hasUtm ? clean(utmCampaign, 100) : null, Types.VARCHAR)
                .param("referrer", referrer, Types.VARCHAR)
                .param("hasUtm", hasUtm)
                .query((rs, n) -> new Attribution(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .single();

        jdbc.sql("""
                        insert into page_view (visit_id, viewed_at, utm_source, utm_medium, utm_campaign, referrer)
                        values (:visit, :now, :source, cast(:medium as text), cast(:campaign as text),
                                cast(:referrer as text))
                        """)
                .param("visit", id)
                .param("now", now)
                .param("source", attribution.source())
                .param("medium", attribution.medium(), Types.VARCHAR)
                .param("campaign", attribution.campaign(), Types.VARCHAR)
                .param("referrer", attribution.referrer(), Types.VARCHAR)
                .update();
        return id;
    }

    /** Returns the id only if that visit exists, so a forged id can never break a foreign key. */
    public Optional<UUID> existing(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.sql("select id from visit where id = :id").param("id", id).query(UUID.class).optional();
    }

    /** Records the first interaction with the form. Later calls for the same visit are ignored. */
    public void markFormStarted(UUID visitId) {
        if (visitId == null) {
            return;
        }
        jdbc.sql("update visit set form_started_at = :now where id = :id and form_started_at is null")
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("id", visitId)
                .update();
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Scheme, host and path of an off-site referrer. The query string is dropped on purpose. */
    static String externalReferrer(String header, String ownHost) {
        String value = clean(header, 2000);
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.equalsIgnoreCase(ownHost)) {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return clean(uri.getScheme() + "://" + host.toLowerCase(Locale.ROOT) + path, 300);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Attribution(String source, String medium, String campaign, String referrer) {
    }
}
