package com.pawly.service;

import java.util.regex.Pattern;

/**
 * Rejects empty text, oversized text, and contact details before they are stored
 * or forwarded. The rejected value is never copied into the exception message.
 */
public final class AdoptionTexts {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");

    private AdoptionTexts() {
    }

    public static boolean hasContact(String value) {
        return value != null && (EMAIL.matcher(value).find() || LONG_DIGITS.matcher(value).find());
    }

    public static String requireClean(String value, int max) {
        String text = clean(value, max);
        if (text == null) {
            throw new IllegalArgumentException("Text is empty");
        }
        return text;
    }

    public static String optional(String value, int max) {
        return clean(value, max);
    }

    private static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new IllegalArgumentException("Text is too long");
        }
        if (hasContact(text)) {
            throw new IllegalArgumentException("Contact details are not accepted");
        }
        return text;
    }
}
