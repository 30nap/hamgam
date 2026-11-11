package ir.hamgam.landing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hamgam")
public record AppProperties(Admin admin, Sms sms, Otp otp) {

    public record Admin(String username, String password) {
    }

    public record Sms(String provider, Kavenegar kavenegar) {

        public record Kavenegar(String apiKey, String template, String baseUrl) {
        }
    }

    public record Otp(Duration ttl, String hmacSecret, int maxAttempts, Duration phoneCooldown,
                      int phoneDailyLimit, int ipHourlyLimit) {
    }
}
