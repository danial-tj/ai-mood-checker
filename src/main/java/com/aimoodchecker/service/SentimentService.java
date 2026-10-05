package com.aimoodchecker.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** A small local keyword estimate, not an AI or clinical assessment. */
public class SentimentService {
    private static final Set<String> POSITIVE = Set.of("happy", "great", "good", "excellent");
    private static final Set<String> NEGATIVE = Set.of("sad", "bad", "terrible", "worried", "unhappy");
    private static final Set<String> NEGATIONS = Set.of("not", "never", "no", "don't", "didn't", "isn't", "wasn't", "aren't", "can't");
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+(?:'[\\p{L}]+)?");

    public double analyzeSentiment(String text) {
        if (text == null || text.isBlank()) return 0.5;
        var words = WORD.matcher(text.toLowerCase(Locale.ROOT).replace('\u2019', '\''));
        int total = 0;
        boolean negateNext = false;
        int negationWindow = 0;
        while (words.find()) {
            String word = words.group();
            if (NEGATIONS.contains(word)) { negateNext = true; negationWindow = 3; continue; }
            int value = POSITIVE.contains(word) ? 1 : NEGATIVE.contains(word) ? -1 : 0;
            if (value != 0) {
                total += negateNext ? -value : value;
                negateNext = false;
            }
            if (--negationWindow <= 0) negateNext = false;
        }
        return total > 0 ? 0.8 : total < 0 ? 0.2 : 0.5;
    }

    public String getSentimentCategory(double score) {
        if (score >= 0.7) return "Positive";
        if (score <= 0.3) return "Negative";
        return "Neutral";
    }
}
