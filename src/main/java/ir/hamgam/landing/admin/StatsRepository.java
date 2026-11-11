package ir.hamgam.landing.admin;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ir.hamgam.landing.web.LandingController;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StatsRepository {

    /**
     * Funnel for one utm_source.
     *
     * @param views      page views (every load of the landing page)
     * @param visitors   distinct browsers
     * @param formStarts visitors who touched the form at least once
     * @param codesSent  distinct phone numbers that were sent a code
     * @param verified   signups that entered a correct code
     * @param priceYes   verified signups answering "yes" to the price question (price reservations)
     * @param platforms  verified signups per platform key
     */
    public record SourceStats(String source, long views, long visitors, long formStarts, long codesSent,
                              long verified, long priceYes, long priceMaybe, long priceNo,
                              long priceUnanswered, Map<String, Long> platforms) {

        public String startRate() {
            return percent(formStarts, visitors);
        }

        public String signupRate() {
            return percent(verified, visitors);
        }

        public String completionRate() {
            return percent(verified, formStarts);
        }

        public String reservationRate() {
            return percent(priceYes, verified);
        }

        public long platform(String key) {
            return platforms.getOrDefault(key, 0L);
        }

        static SourceStats total(List<SourceStats> rows) {
            Map<String, Long> platforms = new LinkedHashMap<>();
            for (String key : LandingController.PLATFORMS) {
                platforms.put(key, rows.stream().mapToLong(r -> r.platform(key)).sum());
            }
            return new SourceStats("Total",
                    rows.stream().mapToLong(SourceStats::views).sum(),
                    rows.stream().mapToLong(SourceStats::visitors).sum(),
                    rows.stream().mapToLong(SourceStats::formStarts).sum(),
                    rows.stream().mapToLong(SourceStats::codesSent).sum(),
                    rows.stream().mapToLong(SourceStats::verified).sum(),
                    rows.stream().mapToLong(SourceStats::priceYes).sum(),
                    rows.stream().mapToLong(SourceStats::priceMaybe).sum(),
                    rows.stream().mapToLong(SourceStats::priceNo).sum(),
                    rows.stream().mapToLong(SourceStats::priceUnanswered).sum(),
                    platforms);
        }

        private static String percent(long part, long whole) {
            return whole == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
        }
    }

    public record SignupRow(String phone, String platforms, String priceAnswer, String utmSource,
                            String utmMedium, String utmCampaign, String referrer,
                            OffsetDateTime firstSeenAt, OffsetDateTime otpRequestedAt,
                            OffsetDateTime verifiedAt) {
    }

    private final JdbcClient jdbc;

    public StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SourceStats> bySource() {
        StringBuilder platformColumns = new StringBuilder();
        for (String key : LandingController.PLATFORMS) {
            // Keys are constants from LandingController, not user input.
            platformColumns.append(", count(*) filter (where '").append(key)
                    .append("' = any(string_to_array(platforms, ','))) as p_").append(key);
        }
        String sql = """
                with sources as (
                    select utm_source from page_view
                    union select utm_source from visit
                    union select utm_source from signup
                ),
                pv as (select utm_source, count(*) as views from page_view group by utm_source),
                vi as (select utm_source, count(*) as visitors, count(form_started_at) as form_starts
                       from visit group by utm_source),
                ot as (select v.utm_source, count(distinct o.phone) as codes_sent
                       from otp_request o join visit v on v.id = o.visit_id group by v.utm_source),
                su as (select utm_source, count(*) as verified,
                              count(*) filter (where price_answer = 'yes') as price_yes,
                              count(*) filter (where price_answer = 'maybe') as price_maybe,
                              count(*) filter (where price_answer = 'no') as price_no,
                              count(*) filter (where price_answer is null) as price_unanswered
                              %s
                       from signup group by utm_source)
                select s.utm_source,
                       coalesce(pv.views, 0) as views,
                       coalesce(vi.visitors, 0) as visitors,
                       coalesce(vi.form_starts, 0) as form_starts,
                       coalesce(ot.codes_sent, 0) as codes_sent,
                       su.*
                from sources s
                left join pv on pv.utm_source = s.utm_source
                left join vi on vi.utm_source = s.utm_source
                left join ot on ot.utm_source = s.utm_source
                left join su on su.utm_source = s.utm_source
                order by coalesce(pv.views, 0) desc, s.utm_source
                """.formatted(platformColumns);
        return jdbc.sql(sql).query((rs, n) -> {
            Map<String, Long> platforms = new LinkedHashMap<>();
            for (String key : LandingController.PLATFORMS) {
                platforms.put(key, rs.getLong("p_" + key));
            }
            return new SourceStats(rs.getString(1), rs.getLong("views"), rs.getLong("visitors"),
                    rs.getLong("form_starts"), rs.getLong("codes_sent"), rs.getLong("verified"),
                    rs.getLong("price_yes"), rs.getLong("price_maybe"), rs.getLong("price_no"),
                    rs.getLong("price_unanswered"), platforms);
        }).list();
    }

    public List<SignupRow> signups() {
        return jdbc.sql("""
                        select phone, platforms, price_answer, utm_source, utm_medium, utm_campaign, referrer,
                               first_seen_at, otp_requested_at, verified_at
                        from signup order by verified_at
                        """)
                .query((rs, n) -> new SignupRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getObject(8, OffsetDateTime.class), rs.getObject(9, OffsetDateTime.class),
                        rs.getObject(10, OffsetDateTime.class)))
                .list();
    }
}
