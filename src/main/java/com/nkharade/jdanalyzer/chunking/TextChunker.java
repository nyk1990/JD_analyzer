package com.nkharade.jdanalyzer.chunking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits raw resume / JD text into sentence-sized pieces worth embedding.
 *
 * Why chunk at all: one embedding for a whole document blurs everything together.
 * Embedding each JD requirement and each resume bullet separately lets us say
 * *which* requirement is covered by *which* bullet.
 */
public class TextChunker {

    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?;])\\s+");
    /** Leading bullets, dashes, arrows and "1." / "1)" style numbering. */
    private static final Pattern BULLET_PREFIX =
            Pattern.compile("^\\s*(?:[-*•▪●◦·–—>+]+|\\(?\\d{1,2}[.)])\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EMAIL_OR_PHONE =
            Pattern.compile("\\S+@\\S+|\\+?\\d[\\d\\s().-]{8,}\\d");

    /** JD lines that are about the company or legal boilerplate, not the role. */
    private static final List<String> JD_BOILERPLATE = List.of(
            "equal opportunity", "equal employment", "without regard to", "reasonable accommodation",
            "benefits", "401(k)", "401k", "paid time off", "salary range", "base pay",
            "compensation", "e-verify", "background check", "privacy notice", "click apply", "apply now");

    private static final int LONG_LINE = 300;

    private final int minLength;

    public TextChunker(int minLength) {
        this.minLength = minLength;
    }

    /** Resume bullets / sentences, in document order, de-duplicated. Contact lines are dropped. */
    public List<String> resumeChunks(String resume) {
        return split(resume).stream()
                .filter(s -> !EMAIL_OR_PHONE.matcher(s).find())
                .toList();
    }

    /** Candidate requirements from a job description, capped at {@code max}. */
    public List<String> jobRequirements(String jobDescription, int max) {
        return split(jobDescription).stream()
                .filter(s -> !isBoilerplate(s))
                .limit(max)
                .toList();
    }

    private List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();

        Set<String> out = new LinkedHashSet<>();
        for (String rawLine : LINE_BREAK.split(text)) {
            String line = clean(rawLine);
            if (line.isEmpty() || isHeading(line)) continue;

            List<String> pieces = line.length() > LONG_LINE
                    ? List.of(SENTENCE_BREAK.split(line))
                    : List.of(line);

            for (String piece : pieces) {
                String p = clean(piece);
                if (p.length() >= minLength) out.add(p);
            }
        }
        return new ArrayList<>(out);
    }

    private static String clean(String s) {
        String noBullet = BULLET_PREFIX.matcher(s).replaceFirst("");
        return WHITESPACE.matcher(noBullet).replaceAll(" ").trim();
    }

    /** "Requirements:", "EXPERIENCE", "What you'll do:" and similar section titles. */
    private static boolean isHeading(String line) {
        if (line.length() > 60) return false;
        if (line.endsWith(":")) return true;
        boolean hasLetters = line.chars().anyMatch(Character::isLetter);
        return hasLetters && line.equals(line.toUpperCase(Locale.ROOT));
    }

    private static boolean isBoilerplate(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return JD_BOILERPLATE.stream().anyMatch(lower::contains);
    }
}
