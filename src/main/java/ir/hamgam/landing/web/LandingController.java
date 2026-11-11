package ir.hamgam.landing.web;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import ir.hamgam.landing.otp.OtpService;
import ir.hamgam.landing.otp.OtpService.Limited;
import ir.hamgam.landing.otp.OtpService.LimitReason;
import ir.hamgam.landing.otp.OtpService.OtpRequest;
import ir.hamgam.landing.otp.OtpService.SendFailed;
import ir.hamgam.landing.otp.OtpService.Sent;
import ir.hamgam.landing.otp.OtpService.StartResult;
import ir.hamgam.landing.otp.OtpService.VerifyResult;
import ir.hamgam.landing.phone.PhoneNumbers;
import ir.hamgam.landing.tracking.Tracking;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LandingController {

    static final String VISIT_COOKIE = "hv";

    /** Stored keys for the "where do you sell?" options, in display order. */
    public static final List<String> PLATFORMS =
            List.of("woocommerce", "basalam", "digikala", "bale", "eitaa", "other");

    /** Stored keys for the price question. "yes" is the price-reservation signal. */
    public static final Set<String> PRICE_ANSWERS = Set.of("yes", "maybe", "no");

    private final Tracking tracking;
    private final OtpService otp;
    private final JdbcClient jdbc;

    public LandingController(Tracking tracking, OtpService otp, JdbcClient jdbc) {
        this.tracking = tracking;
        this.otp = otp;
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public String landing(HttpServletRequest request, HttpServletResponse response, Model model,
                          @RequestParam(name = "utm_source", required = false) String utmSource,
                          @RequestParam(name = "utm_medium", required = false) String utmMedium,
                          @RequestParam(name = "utm_campaign", required = false) String utmCampaign) {
        UUID visitId = null;
        if (tracking.shouldTrack(request)) {
            visitId = tracking.recordView(visitFromCookie(request), utmSource, utmMedium, utmCampaign,
                    request.getHeader(HttpHeaders.REFERER), request.getServerName());
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(VISIT_COOKIE, visitId.toString())
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(90))
                    .build().toString());
        }
        return form(request, model, visitId, "", Set.of(), null, null);
    }

    @PostMapping("/signup")
    public String signup(HttpServletRequest request, Model model,
                         @RequestParam(name = "phone", required = false) String phone,
                         @RequestParam(name = "platforms", required = false) List<String> platforms,
                         @RequestParam(name = "price", required = false) String price,
                         @RequestParam(name = "v", required = false) String hiddenVisit) {
        UUID visitId = resolveVisit(request, hiddenVisit);
        tracking.markFormStarted(visitId); // covers browsers where the JS beacon did not run

        Set<String> chosen = new LinkedHashSet<>();
        for (String platform : PLATFORMS) {
            if (platforms != null && platforms.contains(platform)) {
                chosen.add(platform);
            }
        }
        String priceAnswer = price != null && PRICE_ANSWERS.contains(price) ? price : null;

        Optional<String> normalized = PhoneNumbers.normalize(phone);
        if (normalized.isEmpty()) {
            return form(request, model, visitId, phone, chosen, priceAnswer, "phone");
        }
        String joined = String.join(",", chosen);
        StartResult result = otp.start(normalized.get(), request.getRemoteAddr(), visitId, joined, priceAnswer);
        return switch (result) {
            case Sent sent -> "redirect:/verify?r=" + sent.requestId();
            case Limited limited when limited.reason() == LimitReason.PHONE_COOLDOWN ->
                    otp.reuseActive(normalized.get(), visitId, joined, priceAnswer)
                            .map(id -> "redirect:/verify?r=" + id)
                            .orElseGet(() -> form(request, model, visitId, phone, chosen, priceAnswer, "cooldown"));
            case Limited limited -> form(request, model, visitId, phone, chosen, priceAnswer, "limit");
            case SendFailed failed -> form(request, model, visitId, phone, chosen, priceAnswer, "sms");
        };
    }

    @GetMapping("/verify")
    public String verifyPage(HttpServletRequest request, Model model,
                             @RequestParam(name = "r", required = false) String requestId) {
        Optional<OtpRequest> pending = otp.find(Tracking.parseUuid(requestId));
        if (pending.isEmpty()) {
            return "redirect:/";
        }
        if (pending.get().consumedAt() != null) {
            return "redirect:/done";
        }
        return verifyView(request, model, pending.get().id(), null);
    }

    @PostMapping("/verify")
    public String verify(HttpServletRequest request, Model model,
                         @RequestParam(name = "r", required = false) String requestId,
                         @RequestParam(name = "code", required = false) String code) {
        UUID id = Tracking.parseUuid(requestId);
        if (id == null) {
            return "redirect:/";
        }
        VerifyResult result = otp.verify(id, PhoneNumbers.digitsOnly(code));
        return switch (result) {
            case OK, ALREADY_DONE -> "redirect:/done";
            case NOT_FOUND -> "redirect:/";
            case WRONG -> verifyView(request, model, id, "wrong");
            case EXPIRED -> verifyView(request, model, id, "expired");
        };
    }

    @PostMapping("/resend")
    public String resend(HttpServletRequest request, Model model,
                         @RequestParam(name = "r", required = false) String requestId) {
        Optional<OtpRequest> previous = otp.find(Tracking.parseUuid(requestId));
        if (previous.isEmpty()) {
            return "redirect:/";
        }
        OtpRequest old = previous.get();
        if (old.consumedAt() != null) {
            return "redirect:/done";
        }
        StartResult result = otp.start(old.phone(), request.getRemoteAddr(), old.visitId(), old.platforms(),
                old.priceAnswer());
        return switch (result) {
            case Sent sent -> "redirect:/verify?r=" + sent.requestId();
            case Limited limited when limited.reason() == LimitReason.PHONE_COOLDOWN ->
                    verifyView(request, model, old.id(), "cooldown");
            case Limited limited -> verifyView(request, model, old.id(), "limit");
            case SendFailed failed -> verifyView(request, model, old.id(), "sms");
        };
    }

    @GetMapping("/done")
    public String done() {
        return "done";
    }

    /** First interaction with the form, reported by the page's own script. */
    @PostMapping("/e/start")
    public ResponseEntity<Void> formStarted(HttpServletRequest request,
                                            @RequestParam(name = "v", required = false) String hiddenVisit) {
        tracking.markFormStarted(resolveVisit(request, hiddenVisit));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/healthz")
    @ResponseBody
    public String health() {
        jdbc.sql("select 1").query(Integer.class).single();
        return "ok";
    }

    private String form(HttpServletRequest request, Model model, UUID visitId, String phone,
                        Set<String> platforms, String price, String error) {
        loadCsrfToken(request);
        model.addAttribute("visitId", visitId == null ? "" : visitId.toString());
        model.addAttribute("phone", phone == null ? "" : phone);
        model.addAttribute("platforms", platforms);
        model.addAttribute("price", price == null ? "" : price);
        model.addAttribute("error", error);
        return "landing";
    }

    private String verifyView(HttpServletRequest request, Model model, UUID requestId, String error) {
        loadCsrfToken(request);
        model.addAttribute("requestId", requestId.toString());
        model.addAttribute("error", error);
        return "verify";
    }

    /**
     * Forces the CSRF cookie to be written now, before the template starts streaming.
     * (Spring Security creates the token lazily; once the response is committed it is too late.)
     */
    private static void loadCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }

    /** Cookie first; the hidden form field is the fallback for browsers that block cookies. */
    private UUID resolveVisit(HttpServletRequest request, String hiddenVisit) {
        UUID id = visitFromCookie(request);
        if (id == null) {
            id = Tracking.parseUuid(hiddenVisit);
        }
        return tracking.existing(id).orElse(null);
    }

    private static UUID visitFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (VISIT_COOKIE.equals(cookie.getName())) {
                return Tracking.parseUuid(cookie.getValue());
            }
        }
        return null;
    }
}
