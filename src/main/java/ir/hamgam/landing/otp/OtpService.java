package ir.hamgam.landing.otp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ir.hamgam.landing.AppProperties;
import ir.hamgam.landing.sms.SmsException;
import ir.hamgam.landing.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Issues and checks SMS one-time codes, and turns a correct code into a verified signup.
 * Rate limits are counted from the {@code otp_request} table, so they survive restarts and
 * hold across several app instances.
 */
@Service
public class OtpService {

    public enum LimitReason { PHONE_COOLDOWN, PHONE_DAILY, IP_HOURLY }

    public sealed interface StartResult permits Sent, Limited, SendFailed {
    }

    public record Sent(UUID requestId) implements StartResult {
    }

    public record Limited(LimitReason reason) implements StartResult {
    }

    public record SendFailed() implements StartResult {
    }

    public enum VerifyResult { OK, WRONG, EXPIRED, NOT_FOUND, ALREADY_DONE }

    public record OtpRequest(UUID id, String phone, UUID visitId, String platforms, String priceAnswer,
                             Instant createdAt, Instant expiresAt, int attempts, Instant consumedAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SmsProvider sms;
    private final AppProperties.Otp config;
    private final byte[] hmacKey;
    private final SecureRandom random = new SecureRandom();

    public OtpService(JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock,
                      SmsProvider sms, AppProperties properties) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.sms = sms;
        this.config = properties.otp();
        if (StringUtils.hasText(config.hmacSecret())) {
            this.hmacKey = config.hmacSecret().getBytes(StandardCharsets.UTF_8);
        } else {
            log.warn("OTP_HMAC_SECRET is not set: using a random key, so pending codes die on restart");
            this.hmacKey = new byte[32];
            random.nextBytes(this.hmacKey);
        }
    }

    /** Checks the rate limits, stores a new code and sends it. */
    public StartResult start(String phone, String ip, UUID visitId, String platforms, String priceAnswer) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        // Locale.ROOT: a fa/ar default locale would otherwise format the code with non-ASCII digits.
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));

        LimitReason blocked = tx.execute(status -> {
            // Serialize concurrent requests for one phone so two tabs cannot both pass the checks.
            jdbc.sql("select pg_advisory_xact_lock(hashtext(:phone))").param("phone", phone)
                    .query((rs, n) -> 0).list();
            if (count("phone", phone, now.minus(config.phoneCooldown())) > 0) {
                return LimitReason.PHONE_COOLDOWN;
            }
            if (count("phone", phone, now.minus(Duration.ofDays(1))) >= config.phoneDailyLimit()) {
                return LimitReason.PHONE_DAILY;
            }
            if (count("ip", ip, now.minus(Duration.ofHours(1))) >= config.ipHourlyLimit()) {
                return LimitReason.IP_HOURLY;
            }
            jdbc.sql("""
                            insert into otp_request (id, phone, ip, code_hash, created_at, expires_at,
                                                     visit_id, platforms, price_answer)
                            values (:id, :phone, :ip, :hash, :now, :expires,
                                    cast(:visit as uuid), :platforms, cast(:price as text))
                            """)
                    .param("id", id)
                    .param("phone", phone)
                    .param("ip", ip)
                    .param("hash", hash(id, code))
                    .param("now", ts(now))
                    .param("expires", ts(now.plus(config.ttl())))
                    .param("visit", visitId, Types.OTHER)
                    .param("platforms", platforms == null ? "" : platforms)
                    .param("price", priceAnswer, Types.VARCHAR)
                    .update();
            return null;
        });
        if (blocked != null) {
            return new Limited(blocked);
        }

        try {
            sms.sendOtp(phone, code);
        } catch (SmsException | RuntimeException e) {
            // Nothing was delivered, so this attempt must not count against the visitor.
            jdbc.sql("delete from otp_request where id = :id").param("id", id).update();
            log.warn("SMS send failed: {}", e.getMessage());
            return new SendFailed();
        }
        return new Sent(id);
    }

    /**
     * When the phone is cooling down but this same browser already holds a live code, hand that
     * request back (with the latest answers) instead of showing an error.
     */
    public Optional<UUID> reuseActive(String phone, UUID visitId, String platforms, String priceAnswer) {
        if (visitId == null) {
            return Optional.empty();
        }
        Optional<UUID> active = jdbc.sql("""
                        select id from otp_request
                        where phone = :phone and visit_id = :visit and consumed_at is null and expires_at > :now
                        order by created_at desc limit 1
                        """)
                .param("phone", phone)
                .param("visit", visitId)
                .param("now", ts(clock.instant()))
                .query(UUID.class)
                .optional();
        active.ifPresent(id -> jdbc.sql("""
                        update otp_request set platforms = :platforms, price_answer = cast(:price as text)
                        where id = :id
                        """)
                .param("platforms", platforms == null ? "" : platforms)
                .param("price", priceAnswer, Types.VARCHAR)
                .param("id", id)
                .update());
        return active;
    }

    public Optional<OtpRequest> find(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        select id, phone, visit_id, platforms, price_answer, created_at, expires_at,
                               attempts, consumed_at
                        from otp_request where id = :id
                        """)
                .param("id", id)
                .query(OtpService::mapRequest)
                .optional();
    }

    /** Checks a code. A correct, unexpired code creates (or updates) the signup for that phone. */
    public VerifyResult verify(UUID id, String code) {
        Instant now = clock.instant();
        return tx.execute(status -> {
            Optional<Row> found = jdbc.sql("""
                            select id, phone, visit_id, platforms, price_answer, created_at, expires_at,
                                   attempts, consumed_at, code_hash
                            from otp_request where id = :id for update
                            """)
                    .param("id", id)
                    .query((rs, n) -> new Row(mapRequest(rs, n), rs.getString("code_hash")))
                    .optional();
            if (found.isEmpty()) {
                return VerifyResult.NOT_FOUND;
            }
            OtpRequest request = found.get().request();
            if (request.consumedAt() != null) {
                return VerifyResult.ALREADY_DONE;
            }
            if (!now.isBefore(request.expiresAt()) || request.attempts() >= config.maxAttempts()) {
                return VerifyResult.EXPIRED;
            }
            jdbc.sql("update otp_request set attempts = attempts + 1 where id = :id").param("id", id).update();

            boolean matches = MessageDigest.isEqual(
                    hash(id, code == null ? "" : code).getBytes(StandardCharsets.US_ASCII),
                    found.get().codeHash().getBytes(StandardCharsets.US_ASCII));
            if (!matches) {
                return VerifyResult.WRONG;
            }
            jdbc.sql("update otp_request set consumed_at = :now where id = :id")
                    .param("now", ts(now)).param("id", id).update();
            saveSignup(request, now);
            return VerifyResult.OK;
        });
    }

    /** One signup per phone: a repeat verification only refreshes the answers. */
    private void saveSignup(OtpRequest request, Instant now) {
        jdbc.sql("""
                        insert into signup (phone, platforms, price_answer, visit_id, utm_source, utm_medium,
                                            utm_campaign, referrer, first_seen_at, otp_requested_at,
                                            verified_at, updated_at)
                        select :phone, :platforms, cast(:price as text), v.id, coalesce(v.utm_source, '(none)'),
                               v.utm_medium, v.utm_campaign, v.referrer, v.first_seen_at, :requested, :now, :now
                        from (select 1) as one
                        left join visit v on v.id = cast(:visit as uuid)
                        on conflict (phone) do update set
                            platforms = case when excluded.platforms <> '' then excluded.platforms
                                             else signup.platforms end,
                            price_answer = coalesce(excluded.price_answer, signup.price_answer),
                            updated_at = excluded.updated_at
                        """)
                .param("phone", request.phone())
                .param("platforms", request.platforms())
                .param("price", request.priceAnswer(), Types.VARCHAR)
                .param("visit", request.visitId(), Types.OTHER)
                .param("requested", ts(request.createdAt()))
                .param("now", ts(now))
                .update();
    }

    private int count(String column, String value, Instant since) {
        // "column" is one of two literals from this class, never user input.
        return jdbc.sql("select count(*) from otp_request where " + column + " = :value and created_at > :since")
                .param("value", value)
                .param("since", ts(since))
                .query(Integer.class)
                .single();
    }

    private String hash(UUID id, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OtpRequest mapRequest(ResultSet rs, int rowNum) throws SQLException {
        return new OtpRequest(
                rs.getObject("id", UUID.class),
                rs.getString("phone"),
                rs.getObject("visit_id", UUID.class),
                rs.getString("platforms"),
                rs.getString("price_answer"),
                instant(rs, "created_at"),
                instant(rs, "expires_at"),
                rs.getInt("attempts"),
                instant(rs, "consumed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Row(OtpRequest request, String codeHash) {
    }
}
