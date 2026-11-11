package ir.hamgam.landing;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HamgamApplication {

    public static void main(String[] args) {
        SpringApplication.run(HamgamApplication.class, args);
    }

    /** All time-dependent logic (OTP expiry, rate limits) reads this clock so tests can move time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
