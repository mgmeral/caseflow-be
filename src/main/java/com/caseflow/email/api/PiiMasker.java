package com.caseflow.email.api;

import java.util.regex.Pattern;

/**
 * Masks personal data in operator-facing e-mail metadata (sender, subject, failure text).
 *
 * <ul>
 *   <li>E-mail addresses keep their first character and domain: {@code ali.veli@acme.com → a***@acme.com}.
 *       The domain stays visible because operators route and triage by it.</li>
 *   <li>IBANs, long digit runs (6+) and separated numbers with 10+ digits (phones) become {@code ***}.</li>
 *   <li>Ticket references such as {@code TKT-0000123} and dates are left untouched.</li>
 * </ul>
 */
public final class PiiMasker {

    private static final Pattern EMAIL =
            Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern IBAN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}(?:\\s?[A-Z0-9]{1,3})?\\b");
    /** Phone-like: 10+ digits, optionally separated by space, dot, dash or brackets. */
    private static final Pattern SEPARATED_NUMBER =
            Pattern.compile("(?<![\\w-])\\+?\\(?\\d(?:[\\s.()-]?\\d){9,}(?!\\w)");
    /** Not preceded by a word char or dash, so {@code TKT-0000123} survives. */
    private static final Pattern DIGIT_RUN = Pattern.compile("(?<![\\w-])\\d{6,}(?!\\w)");

    private static final String HIDDEN = "***";

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String masked = EMAIL.matcher(text).replaceAll("$1***@$2");
        masked = IBAN.matcher(masked).replaceAll(HIDDEN);
        masked = SEPARATED_NUMBER.matcher(masked).replaceAll(HIDDEN);
        return DIGIT_RUN.matcher(masked).replaceAll(HIDDEN);
    }
}
