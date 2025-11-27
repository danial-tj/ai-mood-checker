package com.aimoodchecker;

import com.aimoodchecker.repository.EntryRepository.MoodPatterns;
import com.aimoodchecker.service.APIConfig;
import com.aimoodchecker.service.ChatGPTService;

/**
 * Simple harness to measure latency and success rate of the AI coaching HTTP calls.
 *
 * Run with a valid OPENAI_API_KEY configured (config.properties, .env, or env var), e.g.:
 *   java -cp target/classes com.aimoodchecker.TestHttpMetrics
 */
public class TestHttpMetrics {

    public static void main(String[] args) {
        System.out.println("=== AI Coaching HTTP Metrics Test ===");

        if (!APIConfig.isConfigured()) {
            System.out.println("OpenAI API key is not configured. Set OPENAI_API_KEY or config.properties before running.");
            return;
        }

        ChatGPTService chatGPT = new ChatGPTService();
        // Use synthetic mood patterns so this test does not depend on a working SQLite setup
        MoodPatterns syntheticPatterns = new MoodPatterns(
                100,   
                200,  
                50,    
                3.0,  
                0.5,   
                "Synthetic positive-ish pattern",
                java.util.List.of("Got good feedback on a project", "Had a productive study session"),
                java.util.List.of("Felt stressed about interviews")
        );

        int iterations = 5; //adjustable for more or less samples
        long totalMs = 0;
        long minMs = Long.MAX_VALUE;
        long maxMs = Long.MIN_VALUE;
        int successes = 0;

        for (int i = 1; i <= iterations; i++) {
            String mood = "Sad";
            String description = "I'm feeling really stressed about school and interviews and could use some practical ideas.";

            System.out.println("\n--- Request " + i + " ---");
            long start = System.nanoTime();
            String coaching = chatGPT.getMoodCoaching(mood, description, syntheticPatterns);
            long end = System.nanoTime();
            long elapsedMs = (end - start) / 1_000_000;

            totalMs += elapsedMs;
            minMs = Math.min(minMs, elapsedMs);
            maxMs = Math.max(maxMs, elapsedMs);

            boolean ok = coaching != null
                    && !coaching.startsWith("Unable to get coaching suggestions")
                    && !coaching.startsWith("I'm having trouble analyzing");
            if (ok) {
                successes++;
            }

            System.out.println("Elapsed: " + elapsedMs + " ms");
            System.out.println("Response (truncated):");
            if (coaching != null && coaching.length() > 200) {
                System.out.println(coaching.substring(0, 200) + "...");
            } else {
                System.out.println(coaching);
            }
        }

        double avgMs = iterations > 0 ? (double) totalMs / iterations : 0;

        System.out.println("\n=== Summary ===");
        System.out.println("Total requests: " + iterations);
        System.out.println("Successful responses: " + successes);
        System.out.println("Avg latency: " + String.format("%.1f", avgMs) + " ms");
        System.out.println("Min latency: " + minMs + " ms");
        System.out.println("Max latency: " + maxMs + " ms");
    }
}


