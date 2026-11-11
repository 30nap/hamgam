package ir.hamgam.landing.admin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ir.hamgam.landing.admin.StatsRepository.SignupRow;
import ir.hamgam.landing.admin.StatsRepository.SourceStats;
import ir.hamgam.landing.web.LandingController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Everything under /admin requires HTTP basic auth (see SecurityConfig). */
@Controller
public class AdminController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final StatsRepository stats;

    public AdminController(StatsRepository stats) {
        this.stats = stats;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        List<SourceStats> rows = stats.bySource();
        model.addAttribute("rows", rows);
        model.addAttribute("total", SourceStats.total(rows));
        model.addAttribute("platformKeys", LandingController.PLATFORMS);
        return "admin";
    }

    @GetMapping("/admin/stats.csv")
    public ResponseEntity<byte[]> statsCsv() {
        List<List<Object>> lines = new ArrayList<>();
        List<Object> header = new ArrayList<>(List.of("utm_source", "views", "visitors", "form_starts",
                "codes_sent", "verified_signups", "price_yes_reservations", "price_maybe", "price_no",
                "price_unanswered", "start_rate", "signup_rate", "completion_rate", "reservation_rate"));
        LandingController.PLATFORMS.forEach(key -> header.add("platform_" + key));
        lines.add(header);
        for (SourceStats row : stats.bySource()) {
            List<Object> line = new ArrayList<>(List.of(row.source(), row.views(), row.visitors(),
                    row.formStarts(), row.codesSent(), row.verified(), row.priceYes(), row.priceMaybe(),
                    row.priceNo(), row.priceUnanswered(), row.startRate(), row.signupRate(),
                    row.completionRate(), row.reservationRate()));
            LandingController.PLATFORMS.forEach(key -> line.add(row.platform(key)));
            lines.add(line);
        }
        return csv("hamgam-stats.csv", lines);
    }

    @GetMapping("/admin/signups.csv")
    public ResponseEntity<byte[]> signupsCsv() {
        List<List<Object>> lines = new ArrayList<>();
        lines.add(List.of("phone", "platforms", "price_answer", "utm_source", "utm_medium", "utm_campaign",
                "referrer", "first_seen_at", "otp_requested_at", "verified_at"));
        for (SignupRow row : stats.signups()) {
            List<Object> line = new ArrayList<>();
            line.add(row.phone());
            line.add(row.platforms());
            line.add(row.priceAnswer());
            line.add(row.utmSource());
            line.add(row.utmMedium());
            line.add(row.utmCampaign());
            line.add(row.referrer());
            line.add(row.firstSeenAt());
            line.add(row.otpRequestedAt());
            line.add(row.verifiedAt());
            lines.add(line);
        }
        return csv("hamgam-signups.csv", lines);
    }

    private static ResponseEntity<byte[]> csv(String filename, List<List<Object>> lines) {
        StringBuilder out = new StringBuilder("﻿"); // BOM so Excel reads UTF-8
        for (List<Object> line : lines) {
            for (int i = 0; i < line.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(cell(line.get(i)));
            }
            out.append("\r\n");
        }
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Quotes every text cell and defuses spreadsheet formulas (utm values come from the URL). */
    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
