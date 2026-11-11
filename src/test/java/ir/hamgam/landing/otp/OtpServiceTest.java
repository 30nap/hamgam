package ir.hamgam.landing.otp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import ir.hamgam.landing.IntegrationTest;
import ir.hamgam.landing.otp.OtpService.Limited;
import ir.hamgam.landing.otp.OtpService.LimitReason;
import ir.hamgam.landing.otp.OtpService.SendFailed;
import ir.hamgam.landing.otp.OtpService.Sent;
import ir.hamgam.landing.otp.OtpService.StartResult;
import ir.hamgam.landing.otp.OtpService.VerifyResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OtpServiceTest extends IntegrationTest {

    private static final String PHONE = "09123456789";
    private static final String IP = "5.120.10.20";

    @Autowired
    OtpService otp;

    @Test
    void correctCodeWithinTwoMinutesCreatesTheSignup() {
        UUID id = send(PHONE, IP);
        clock.advance(Duration.ofSeconds(119));

        assertThat(otp.verify(id, sms.lastCode())).isEqualTo(VerifyResult.OK);
        assertThat(count("signup")).isEqualTo(1);
    }

    @Test
    void codeExpiresAfterTwoMinutes() {
        UUID id = send(PHONE, IP);
        clock.advance(Duration.ofMinutes(2));

        assertThat(otp.verify(id, sms.lastCode())).isEqualTo(VerifyResult.EXPIRED);
        assertThat(count("signup")).isZero();
    }

    @Test
    void wrongCodeIsRejectedAndTheCodeLocksAfterFiveAttempts() {
        UUID id = send(PHONE, IP);
        String right = sms.lastCode();
        String wrong = right.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(otp.verify(id, wrong)).as("attempt %d", attempt).isEqualTo(VerifyResult.WRONG);
        }
        // Even the right code is refused now: guessing must not be free.
        assertThat(otp.verify(id, right)).isEqualTo(VerifyResult.EXPIRED);
        assertThat(count("signup")).isZero();
    }

    @Test
    void usedCodeCannotBeUsedAgainAndUnknownRequestIsNotFound() {
        UUID id = send(PHONE, IP);
        assertThat(otp.verify(id, sms.lastCode())).isEqualTo(VerifyResult.OK);

        assertThat(otp.verify(id, sms.lastCode())).isEqualTo(VerifyResult.ALREADY_DONE);
        assertThat(otp.verify(UUID.randomUUID(), "123456")).isEqualTo(VerifyResult.NOT_FOUND);
    }

    @Test
    void samePhoneMustWaitSixtySecondsBetweenCodes() {
        send(PHONE, IP);

        assertThat(otp.start(PHONE, "5.120.99.99", null, "", null))
                .isEqualTo(new Limited(LimitReason.PHONE_COOLDOWN));

        clock.advance(Duration.ofSeconds(59));
        assertThat(otp.start(PHONE, IP, null, "", null)).isEqualTo(new Limited(LimitReason.PHONE_COOLDOWN));

        clock.advance(Duration.ofSeconds(1));
        assertThat(otp.start(PHONE, IP, null, "", null)).isInstanceOf(Sent.class);
        assertThat(sms.sent()).hasSize(2);
    }

    @Test
    void samePhoneGetsAtMostFiveCodesADay() {
        for (int i = 0; i < 5; i++) {
            send(PHONE, "5.120.0." + i);
            clock.advance(Duration.ofMinutes(2));
        }

        assertThat(otp.start(PHONE, IP, null, "", null)).isEqualTo(new Limited(LimitReason.PHONE_DAILY));
        assertThat(sms.sent()).hasSize(5);

        clock.advance(Duration.ofDays(1));
        assertThat(otp.start(PHONE, IP, null, "", null)).isInstanceOf(Sent.class);
    }

    @Test
    void oneIpGetsAtMostThirtyCodesAnHour() {
        for (int i = 0; i < 30; i++) {
            send("0912000" + (1000 + i), IP);
        }

        assertThat(otp.start("09350000000", IP, null, "", null)).isEqualTo(new Limited(LimitReason.IP_HOURLY));
        // Another address is unaffected.
        assertThat(otp.start("09350000000", "5.120.77.77", null, "", null)).isInstanceOf(Sent.class);

        clock.advance(Duration.ofHours(1));
        assertThat(otp.start("09360000000", IP, null, "", null)).isInstanceOf(Sent.class);
    }

    @Test
    void failedSmsIsReportedAndDoesNotCountAgainstTheVisitor() {
        sms.failNext();

        assertThat(otp.start(PHONE, IP, null, "", null)).isEqualTo(new SendFailed());
        assertThat(count("otp_request")).isZero();
        // No cooldown, because nothing was delivered.
        assertThat(otp.start(PHONE, IP, null, "", null)).isInstanceOf(Sent.class);
    }

    @Test
    void onePhoneMeansOneSignupAndASecondVerificationOnlyUpdatesTheAnswers() {
        StartResult first = otp.start(PHONE, IP, null, "woocommerce", "maybe");
        assertThat(otp.verify(((Sent) first).requestId(), sms.lastCode())).isEqualTo(VerifyResult.OK);

        clock.advance(Duration.ofMinutes(5));
        StartResult second = otp.start(PHONE, IP, null, "basalam,bale", "yes");
        assertThat(otp.verify(((Sent) second).requestId(), sms.lastCode())).isEqualTo(VerifyResult.OK);

        assertThat(count("signup")).isEqualTo(1);
        assertThat(jdbc.sql("select platforms || '|' || price_answer from signup").query(String.class).single())
                .isEqualTo("basalam,bale|yes");
    }

    @Test
    void theCodeIsNeverStoredInTheDatabase() {
        send(PHONE, IP);

        String stored = jdbc.sql("select code_hash from otp_request").query(String.class).single();
        assertThat(sms.lastCode()).matches("\\d{6}");
        assertThat(stored).hasSize(64).doesNotContain(sms.lastCode());
    }

    private UUID send(String phone, String ip) {
        StartResult result = otp.start(phone, ip, null, "", null);
        assertThat(result).isInstanceOf(Sent.class);
        return ((Sent) result).requestId();
    }
}
