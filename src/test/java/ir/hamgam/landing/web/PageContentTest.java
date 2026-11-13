package ir.hamgam.landing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ir.hamgam.landing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Guards the two promises of the page: the copy is verbatim, and nothing loads from another host. */
class PageContentTest extends IntegrationTest {

    /** Any attribute or CSS url() that would make the browser contact a host. */
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile(
            "(?:\\b(?:src|href|action|srcset|poster|data|formaction)\\s*=\\s*[\"']?\\s*|url\\(\\s*[\"']?\\s*|@import\\s+[\"']?\\s*)"
                    + "(?:https?:)?//[^\\s\"')>]+",
            Pattern.CASE_INSENSITIVE);

    @Test
    void everyLineOfTheCopyAppearsVerbatimOnTheSite() throws Exception {
        String site = String.join("\n", renderPages().values());

        List<String> missing = new ArrayList<>();
        for (String line : originalCopyLines()) {
            if (!site.contains(line)) {
                missing.add(line);
            }
        }
        assertThat(missing).as("copy-fa.md lines missing from the rendered pages").isEmpty();
    }

    @Test
    void pagesReferenceNoOtherHost() throws Exception {
        renderPages().forEach((name, html) -> {
            Matcher matcher = EXTERNAL_REFERENCE.matcher(html);
            boolean external = matcher.find();
            assertThat(external).as("external reference in %s: %s", name, external ? matcher.group() : "")
                    .isFalse();
        });
    }

    @Test
    void staticFilesReferenceNoOtherHost() throws IOException {
        Path root = Path.of("src/main/resources/static");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> textFiles = files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(js|css|html|svg)$"))
                    .toList();
            assertThat(textFiles).isNotEmpty();
            for (Path file : textFiles) {
                String text = Files.readString(file);
                assertThat(text).as(file.toString()).doesNotContainPattern("(?i)https?://");
            }
        }
    }

    @Test
    void contentSecurityPolicyForbidsEverythingButThisOrigin() throws Exception {
        String policy = mvc.perform(get("/").header("User-Agent", BROWSER))
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(policy).contains("default-src 'none'").contains("script-src 'self'")
                .contains("font-src 'self'").contains("connect-src 'self'").contains("form-action 'self'");
        assertThat(policy).doesNotContain("http:").doesNotContain("https:").doesNotContain("*");
    }

    @Test
    void landingPageIsRightToLeftPersianWithNoPreselectedPriceAnswer() throws Exception {
        String html = renderPages().get("landing");

        assertThat(html).contains("<html lang=\"fa\" dir=\"rtl\">");
        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html).doesNotContainPattern("<input[^>]*\\schecked");
    }

    @Test
    void codePageShowsTheNumberWithItsMiddleDigitsHidden() throws Exception {
        String html = renderPages().get("verify");

        assertThat(html).contains("۰۹۱۲ ••• ۶۷۸۹");
        assertThat(html).doesNotContain("09123456789").doesNotContain("۰۹۱۲۳۴۵۶۷۸۹");
    }

    /** The demo must not look like a live rate: it is labelled as made up, next to the numbers. */
    @Test
    void priceDemoSaysItsNumbersAreNotReal() throws Exception {
        String html = renderPages().get("landing");

        assertThat(html).contains("نرخ دلار (فرضی)").contains("این فقط یه نمونه‌ست و عددهاش واقعی نیستن.");
    }

    private Map<String, String> renderPages() throws Exception {
        Map<String, String> pages = new LinkedHashMap<>();
        MvcResult landing = mvc.perform(get("/").header("User-Agent", BROWSER)).andExpect(status().isOk()).andReturn();
        pages.put("landing", body(landing));

        String verifyUrl = mvc.perform(post("/signup").with(csrf())
                        .cookie(landing.getResponse().getCookie(LandingController.VISIT_COOKIE))
                        .param("phone", "09123456789"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        pages.put("verify", body(mvc.perform(get(verifyUrl)).andExpect(status().isOk()).andReturn()));
        pages.put("done", body(mvc.perform(get("/done")).andExpect(status().isOk()).andReturn()));
        return pages;
    }

    /** The Persian lines of copy-fa.md above the "added-ui-strings" marker, without list bullets. */
    private static List<String> originalCopyLines() throws IOException {
        List<String> lines = new ArrayList<>();
        for (String raw : Files.readAllLines(Path.of("copy-fa.md"), StandardCharsets.UTF_8)) {
            if (raw.contains("added-ui-strings")) {
                break;
            }
            String line = raw.strip();
            if (line.startsWith("* ")) {
                line = line.substring(2).strip();
            }
            if (line.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF)) {
                lines.add(line);
            }
        }
        assertThat(lines).hasSizeGreaterThan(20);
        return lines;
    }

    private static String body(MvcResult result) throws IOException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
