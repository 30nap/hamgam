package ir.hamgam.landing.phone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    private static final String CANONICAL = "09123456789";

    @ParameterizedTest
    @ValueSource(strings = {
            "09123456789",
            "9123456789",
            "+989123456789",
            "00989123456789",
            "989123456789",
            "+98 912 345 6789",
            "0912-345-6789",
            "(0912) 345 6789",
            "  09123456789  ",
            "۰۹۱۲۳۴۵۶۷۸۹",          // Persian digits
            "٠٩١٢٣٤٥٦٧٨٩",          // Arabic-Indic digits
            "۰۹۱۲ ۳۴۵ ۶۷۸۹",
            "+۹۸۹۱۲۳۴۵۶۷۸۹",
            "۰۹۱۲٣٤٥6789",          // mixed keyboards
            "‏09123456789‎", // direction marks pasted along with the number
            "+9809123456789"
    })
    void acceptsEveryCommonWayOfTypingTheSameNumber(String input) {
        assertThat(PhoneNumbers.normalize(input)).contains(CANONICAL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "0912345678",       // too short
            "091234567890",     // too long
            "08123456789",      // not a mobile prefix
            "02112345678",      // landline
            "+19123456789",     // not Iran
            "0912345678a",
            "09123456789;drop",
            "abc",
            "98912345678",      // country code with one digit missing
            "+98+9123456789"
    })
    void rejectsEverythingElse(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEmpty();
    }

    @Test
    void otpDigitsAreConvertedAndStripped() {
        assertThat(PhoneNumbers.digitsOnly("۱۲۳ ۴۵۶")).isEqualTo("123456");
        assertThat(PhoneNumbers.digitsOnly("٠٠١-٢٣٤")).isEqualTo("001234");
        assertThat(PhoneNumbers.digitsOnly(null)).isEmpty();
    }

    @Test
    void maskedNumberHidesTheMiddleDigits() {
        assertThat(PhoneNumbers.masked(CANONICAL)).isEqualTo("۰۹۱۲ ••• ۶۷۸۹");
        assertThat(PhoneNumbers.masked("123")).isEmpty();
        assertThat(PhoneNumbers.masked(null)).isEmpty();
    }
}
