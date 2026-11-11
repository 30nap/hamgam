package ir.hamgam.landing.phone;

import java.util.Optional;

/** Normalizes what people actually type into a phone field to the canonical {@code 09xxxxxxxxx}. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Accepts Persian (۰-۹) and Arabic-Indic (٠-٩) digits, spaces and dashes, and the
     * {@code +98}, {@code 0098}, {@code 98} and bare {@code 9xxxxxxxxx} forms.
     *
     * @return the number as {@code 09xxxxxxxxx}, or empty if it is not an Iranian mobile number
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder(raw.length());
        boolean plus = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            int digit = toAsciiDigit(c);
            if (digit >= 0) {
                digits.append((char) ('0' + digit));
            } else if (c == '+' && digits.isEmpty() && !plus) {
                plus = true;
            } else if (!isSeparator(c)) {
                return Optional.empty();
            }
        }
        String d = digits.toString();
        if (plus) {
            if (!d.startsWith("98")) {
                return Optional.empty();
            }
            d = d.substring(2);
        } else if (d.startsWith("0098")) {
            d = d.substring(4);
        } else if (d.startsWith("98") && d.length() >= 12) {
            d = d.substring(2);
        }
        if (d.length() == 10 && d.charAt(0) == '9') {
            d = "0" + d;
        }
        boolean valid = d.length() == 11 && d.startsWith("09");
        return valid ? Optional.of(d) : Optional.empty();
    }

    /** Keeps only the digits of {@code raw}, converted to ASCII. Used for the OTP field. */
    public static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            int digit = toAsciiDigit(raw.charAt(i));
            if (digit >= 0) {
                digits.append((char) ('0' + digit));
            }
        }
        return digits.toString();
    }

    private static int toAsciiDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= '۰' && c <= '۹') { // Persian
            return c - '۰';
        }
        if (c >= '٠' && c <= '٩') { // Arabic-Indic
            return c - '٠';
        }
        return -1;
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c)
                || c == '-' || c == '(' || c == ')' || c == '.'
                || c == '‌' || c == '‍' || c == '‎' || c == '‏' // ZWNJ, ZWJ, LRM, RLM
                || c == '‪' || c == '‫' || c == '‬' || c == '⁦' || c == '⁧' || c == '⁩';
    }
}
