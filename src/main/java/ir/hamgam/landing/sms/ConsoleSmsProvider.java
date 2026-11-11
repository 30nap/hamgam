package ir.hamgam.landing.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local development: prints the code to the log instead of sending an SMS. Never use in production. */
@Component
@ConditionalOnProperty(name = "hamgam.sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsProvider.class);

    public ConsoleSmsProvider() {
        log.warn("SMS_PROVIDER=console: one-time codes are written to this log and no SMS is sent");
    }

    @Override
    public void sendOtp(String phone, String code) {
        log.info("[console sms] code for {} is {}", phone, code);
    }
}
