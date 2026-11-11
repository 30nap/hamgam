package ir.hamgam.landing.sms;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

import ir.hamgam.landing.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Kavenegar "verify lookup": sends a pre-approved template whose {@code %token} placeholder is
 * replaced with the code. The template must exist in the Kavenegar panel first.
 */
@Component
@ConditionalOnProperty(name = "hamgam.sms.provider", havingValue = "kavenegar")
public class KavenegarSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(KavenegarSmsProvider.class);
    private static final Pattern STATUS_OK = Pattern.compile("\"status\"\\s*:\\s*200\\b");

    private final RestClient http;
    private final String apiKey;
    private final String template;

    public KavenegarSmsProvider(AppProperties properties) {
        AppProperties.Sms.Kavenegar config = properties.sms().kavenegar();
        if (config == null || !StringUtils.hasText(config.apiKey()) || !StringUtils.hasText(config.template())) {
            throw new IllegalStateException(
                    "SMS_PROVIDER=kavenegar needs KAVENEGAR_API_KEY and KAVENEGAR_OTP_TEMPLATE");
        }
        this.apiKey = config.apiKey();
        this.template = config.template();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(config.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public void sendOtp(String phone, String code) throws SmsException {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("receptor", phone);
        form.add("token", code);
        form.add("template", template);
        String body;
        try {
            body = http.post()
                    .uri("/v1/{key}/verify/lookup.json", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // Never log the exception message or URL: the API key is part of the path.
            log.warn("Kavenegar rejected the message: HTTP {}", e.getStatusCode().value());
            throw new SmsException("Kavenegar returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Kavenegar could not be reached: {}", e.getClass().getSimpleName());
            throw new SmsException("Kavenegar could not be reached");
        }
        if (body == null || !STATUS_OK.matcher(body).find()) {
            log.warn("Kavenegar answered without status 200");
            throw new SmsException("Kavenegar did not confirm the message");
        }
    }
}
