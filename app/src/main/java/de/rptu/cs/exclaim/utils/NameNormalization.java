package de.rptu.cs.exclaim.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class NameNormalization {
    public static String normalizeName(String name) {
        // Manual replacements (Umlauts, ...)
        for (Replacement r : replacements) {
            name = r.pattern.matcher(name).replaceAll(r.replacement);
        }

        // Remove all accent marks: first decomposing base character and marks, then remove all mark characters.
        // This would also remove umlaut dots, so handle the umlauts first!
        return markPattern.matcher(Normalizer.normalize(name, Normalizer.Form.NFKD)).replaceAll("");
    }

    private record Replacement(Pattern pattern, String replacement) {
    }

    private static final Replacement[] replacements = {
        new Replacement(Pattern.compile("[ÄÆ]"), "Ae"),
        new Replacement(Pattern.compile("Ö"), "Oe"),
        new Replacement(Pattern.compile("Ü"), "Ue"),
        new Replacement(Pattern.compile("ẞ"), "Ss"),
        new Replacement(Pattern.compile("Ø"), "O"),
        new Replacement(Pattern.compile("[äæ]"), "ae"),
        new Replacement(Pattern.compile("ö"), "oe"),
        new Replacement(Pattern.compile("ü"), "ue"),
        new Replacement(Pattern.compile("ß"), "ss"),
        new Replacement(Pattern.compile("ø"), "o"),
        new Replacement(Pattern.compile("['`()]"), "")
    };

    private static final Pattern markPattern = Pattern.compile("\\p{M}");
}
