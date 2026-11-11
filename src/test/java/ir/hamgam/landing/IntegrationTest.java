package ir.hamgam.landing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import ir.hamgam.landing.sms.SmsException;
import ir.hamgam.landing.sms.SmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for tests that need the whole application. Runs against a real PostgreSQL started
 * in-process (no Docker), with a clock the test can move and an SMS provider that records codes.
 */
@SpringBootTest(properties = {
        "hamgam.admin.username=admin",
        "hamgam.admin.password=test-pass",
        "hamgam.otp.hmac-secret=test-secret",
        "hamgam.sms.provider=console"
})
@Import(IntegrationTest.Beans.class)
public abstract class IntegrationTest {

    protected static final String BROWSER =
            "Mozilla/5.0 (Linux; Android 13; SM-A515F) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Mobile Safari/537.36";

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected RecordingSms sms;
    @Autowired
    private WebApplicationContext context;

    protected MockMvc mvc;

    @BeforeEach
    void resetState() {
        jdbc.sql("truncate signup, otp_request, page_view, visit restart identity cascade").update();
        clock.reset();
        sms.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    protected long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    @TestConfiguration
    static class Beans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        RecordingSms testSms() {
            return new RecordingSms();
        }
    }

    public static class MutableClock extends Clock {

        private static final Instant START = Instant.parse("2026-11-11T08:00:00Z");
        private Instant now = START;

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        void reset() {
            now = START;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    public static class RecordingSms implements SmsProvider {

        public record Message(String phone, String code) {
        }

        private final List<Message> sent = new ArrayList<>();
        private boolean failNext;

        @Override
        public void sendOtp(String phone, String code) throws SmsException {
            if (failNext) {
                failNext = false;
                throw new SmsException("simulated outage");
            }
            sent.add(new Message(phone, code));
        }

        public void failNext() {
            failNext = true;
        }

        public String lastCode() {
            return sent.getLast().code();
        }

        public List<Message> sent() {
            return sent;
        }

        void reset() {
            sent.clear();
            failNext = false;
        }
    }
}
