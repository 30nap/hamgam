package ir.hamgam.landing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import ir.hamgam.landing.IntegrationTest;
import ir.hamgam.landing.admin.StatsRepository;
import ir.hamgam.landing.admin.StatsRepository.SourceStats;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class SignupFlowTest extends IntegrationTest {

    @Autowired
    StatsRepository stats;

    @Test
    void utmTagsReferrerAndTimestampsAreStoredWithTheSignup() throws Exception {
        Cookie visit = visitLanding("/?utm_source=Yektanet&utm_medium=CPC&utm_campaign=launch-1",
                "https://news.example.ir/tech/article-12?utm=x");
        String verifyUrl = submitForm(visit, "۰۹۱۲ ۳۴۵ ۶۷۸۹", "yes", "woocommerce", "bale");

        assertThat(sms.sent()).hasSize(1);
        assertThat(sms.sent().getFirst().phone()).isEqualTo("09123456789");

        mvc.perform(post("/verify").with(csrf()).cookie(visit)
                        .param("r", verifyUrl.substring(verifyUrl.indexOf('=') + 1))
                        .param("code", sms.lastCode()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/done"));

        Map<String, Object> signup = jdbc.sql("select * from signup").query().singleRow();
        assertThat(signup)
                .containsEntry("phone", "09123456789")
                .containsEntry("platforms", "woocommerce,bale")
                .containsEntry("price_answer", "yes")
                .containsEntry("utm_source", "yektanet")
                .containsEntry("utm_medium", "cpc")
                .containsEntry("utm_campaign", "launch-1")
                .containsEntry("referrer", "https://news.example.ir/tech/article-12");
        assertThat(signup.get("first_seen_at")).isNotNull();
        assertThat(signup.get("otp_requested_at")).isNotNull();
        assertThat(signup.get("verified_at")).isNotNull();
        assertThat(signup.get("visit_id")).hasToString(visit.getValue());
    }

    @Test
    void pageViewsAreLoggedPerSourceAndAReturningVisitorKeepsTheirSource() throws Exception {
        Cookie fromAd = visitLanding("/?utm_source=bale&utm_medium=channel&utm_campaign=c7", null);
        // Same browser comes back later without any tag.
        mvc.perform(get("/").header("User-Agent", BROWSER).cookie(fromAd)).andExpect(status().isOk());
        // A different browser arrives with no tag at all.
        visitLanding("/", null);

        List<Map<String, Object>> views = jdbc.sql(
                "select utm_source, count(*) as n from page_view group by utm_source order by utm_source")
                .query().listOfRows();
        assertThat(views).hasSize(2);
        assertThat(views.get(0)).containsEntry("utm_source", "(none)").containsEntry("n", 1L);
        assertThat(views.get(1)).containsEntry("utm_source", "bale").containsEntry("n", 2L);
        assertThat(count("visit")).isEqualTo(2);
        assertThat(jdbc.sql("select utm_campaign from page_view where utm_source = 'bale' limit 1")
                .query(String.class).single()).isEqualTo("c7");
    }

    @Test
    void botsPrefetchesAndTagLessClientsAreNotCountedAsViews() throws Exception {
        mvc.perform(get("/?utm_source=yektanet").header("User-Agent", "Googlebot/2.1")).andExpect(status().isOk());
        mvc.perform(get("/?utm_source=yektanet")).andExpect(status().isOk());
        mvc.perform(get("/?utm_source=yektanet").header("User-Agent", BROWSER).header("Sec-Purpose", "prefetch"))
                .andExpect(status().isOk());

        assertThat(count("page_view")).isZero();
        assertThat(count("visit")).isZero();
    }

    @Test
    void adminFunnelCountsViewsStartsSignupsAndPriceAnswersPerSource() throws Exception {
        // yektanet: two visitors, one finishes and reserves at the price.
        Cookie a = visitLanding("/?utm_source=yektanet", null);
        visitLanding("/?utm_source=yektanet", null);
        completeSignup(a, "09120000001", "yes", "digikala");

        // bale: one visitor starts the form (beacon) but never sends it; one finishes and says "no".
        Cookie c = visitLanding("/?utm_source=bale", null);
        mvc.perform(post("/e/start").cookie(c)).andExpect(status().isNoContent());
        Cookie d = visitLanding("/?utm_source=bale", null);
        completeSignup(d, "09120000002", "no", "eitaa", "bale");

        Map<String, SourceStats> bySource = new java.util.HashMap<>();
        stats.bySource().forEach(row -> bySource.put(row.source(), row));

        SourceStats yektanet = bySource.get("yektanet");
        assertThat(yektanet.views()).isEqualTo(2);
        assertThat(yektanet.visitors()).isEqualTo(2);
        assertThat(yektanet.formStarts()).isEqualTo(1);
        assertThat(yektanet.codesSent()).isEqualTo(1);
        assertThat(yektanet.verified()).isEqualTo(1);
        assertThat(yektanet.priceYes()).isEqualTo(1);
        assertThat(yektanet.platform("digikala")).isEqualTo(1);
        assertThat(yektanet.signupRate()).isEqualTo("50.0%");
        assertThat(yektanet.reservationRate()).isEqualTo("100.0%");

        SourceStats bale = bySource.get("bale");
        assertThat(bale.formStarts()).isEqualTo(2);
        assertThat(bale.verified()).isEqualTo(1);
        assertThat(bale.priceYes()).isZero();
        assertThat(bale.priceNo()).isEqualTo(1);
        assertThat(bale.platform("eitaa")).isEqualTo(1);
        assertThat(bale.platform("bale")).isEqualTo(1);
        assertThat(bale.completionRate()).isEqualTo("50.0%");
    }

    @Test
    void adminPageAndExportsRequireTheConfiguredCredentials() throws Exception {
        Cookie visit = visitLanding("/?utm_source=yektanet", null);
        completeSignup(visit, "09120000003", "maybe", "basalam");

        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/signups.csv")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin").with(httpBasic("admin", "wrong"))).andExpect(status().isUnauthorized());

        mvc.perform(get("/admin").with(httpBasic("admin", "test-pass")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("yektanet")));

        String signups = mvc.perform(get("/admin/signups.csv").with(httpBasic("admin", "test-pass")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(signups).contains("\"09120000003\",\"basalam\",\"maybe\",\"yektanet\"");

        String funnel = mvc.perform(get("/admin/stats.csv").with(httpBasic("admin", "test-pass")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(funnel).contains("\"utm_source\",\"views\",\"visitors\",\"form_starts\"")
                .contains("\"yektanet\",1,1,1,1,1,0,1,0,0");
    }

    @Test
    void invalidPhoneShowsTheFormAgainAndSendsNothing() throws Exception {
        Cookie visit = visitLanding("/", null);

        mvc.perform(post("/signup").with(csrf()).cookie(visit).param("phone", "12345").param("price", "yes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("شماره موبایل درست نیست")));

        assertThat(sms.sent()).isEmpty();
        assertThat(count("otp_request")).isZero();
    }

    @Test
    void formPostsWithoutACsrfTokenAreRefused() throws Exception {
        Cookie visit = visitLanding("/", null);

        mvc.perform(post("/signup").cookie(visit).param("phone", "09123456789"))
                .andExpect(status().is3xxRedirection());

        assertThat(sms.sent()).isEmpty();
    }

    @Test
    void doubleSubmitFromTheSameBrowserReturnsToTheSameCodePage() throws Exception {
        Cookie visit = visitLanding("/", null);
        String first = submitForm(visit, "09123456789", null);
        String second = submitForm(visit, "09123456789", "yes");

        assertThat(second).isEqualTo(first);
        assertThat(sms.sent()).hasSize(1);
    }

    private Cookie visitLanding(String url, String referrer) throws Exception {
        var request = get(url).header("User-Agent", BROWSER);
        if (referrer != null) {
            request.header("Referer", referrer);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        Cookie visit = result.getResponse().getCookie(LandingController.VISIT_COOKIE);
        assertThat(visit).isNotNull();
        return visit;
    }

    /** Posts the form and returns the redirect target ({@code /verify?r=...}). */
    private String submitForm(Cookie visit, String phone, String price, String... platforms) throws Exception {
        var request = post("/signup").with(csrf()).cookie(visit).param("phone", phone);
        if (price != null) {
            request.param("price", price);
        }
        for (String platform : platforms) {
            request.param("platforms", platform);
        }
        String location = mvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/verify?r=");
        return location;
    }

    private void completeSignup(Cookie visit, String phone, String price, String... platforms) throws Exception {
        String verifyUrl = submitForm(visit, phone, price, platforms);
        mvc.perform(post("/verify").with(csrf()).cookie(visit)
                        .param("r", verifyUrl.substring(verifyUrl.indexOf('=') + 1))
                        .param("code", sms.lastCode()))
                .andExpect(redirectedUrl("/done"));
    }
}
