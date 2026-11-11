package ir.hamgam.landing;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/**
 * Runs the site on http://localhost:8090 with a throw-away PostgreSQL and console SMS, so you
 * can try the whole flow without Docker:
 *
 * <pre>mvn spring-boot:test-run</pre>
 *
 * The one-time code is printed in the log. Admin: http://localhost:8090/admin (admin / admin).
 * All data is discarded when the process stops. Never deploy this class.
 */
public class LocalDevApplication {

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
        setDefault("spring.datasource.url", "jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres");
        setDefault("spring.datasource.username", "postgres");
        setDefault("spring.datasource.password", "postgres");
        setDefault("hamgam.admin.username", "admin");
        setDefault("hamgam.admin.password", "admin");
        setDefault("hamgam.sms.provider", "console");
        setDefault("server.port", "8090");
        setDefault("spring.thymeleaf.cache", "false");
        SpringApplication.run(HamgamApplication.class, args);
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
