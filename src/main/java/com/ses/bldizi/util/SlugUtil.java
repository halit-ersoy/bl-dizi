package com.ses.bldizi.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SlugUtil {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    public static String toSlug(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Pre-clean problematic characters (Apostrophes, Quotes, etc.)
        String precleaned = input
                .replace("'", "")
                .replace("`", "")
                .replace("“", "")
                .replace("”", "")
                .replace("’", "")
                .replace("\"", "")
                .replace(":", "-")
                .replace(";", "-");

        String nowhitespace = WHITESPACE.matcher(precleaned).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");

        // Remove duplicate hyphens
        slug = slug.replaceAll("-{2,}", "-");

        // Remove leading and trailing hyphens
        slug = slug.replaceAll("^-|-$", "");

        return slug.toLowerCase(Locale.ENGLISH);
    }
}
