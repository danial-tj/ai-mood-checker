package com.aimoodchecker.service;

import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.repository.EntryRepository.MoodPatterns;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Optional remote coaching; scoring and journal persistence stay local. */
public class ChatGPTService {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String UNAVAILABLE = "Your check-in is saved. AI reflections are unavailable right now. You can return to your journal at any time.";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SentimentService SENTIMENT = new SentimentService();
    private final CoachingTransport transport;
    private final Supplier<String> apiKey;

    @FunctionalInterface
    interface CoachingTransport { HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException; }

    public ChatGPTService() {
        this(request -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            .send(request, HttpResponse.BodyHandlers.ofString()), APIConfig::getOpenAIKey);
    }

    // An injected transport permits deterministic tests with no network access.
    ChatGPTService(CoachingTransport transport, Supplier<String> apiKey) {
        this.transport = transport;
        this.apiKey = apiKey;
    }

    public String getMoodCoaching(String mood, String description, EntryRepository repository) {
        try { return getMoodCoaching(mood, description, repository.getMoodPatterns()); }
        catch (Exception error) { return UNAVAILABLE; }
    }

    public String getMoodCoaching(String mood, String description, MoodPatterns patterns) {
        if (Boolean.getBoolean("aimoodchecker.offline"))
            return "Your check-in is saved. Offline mode is on; no reflection was sent to an AI service.";
        String key = apiKey.get();
        if (key == null || key.isBlank())
            return "Your check-in is saved. AI reflections are not configured. Your journal and local tone estimate work without an API key.";
        try {
            String prompt = "Current mood: " + mood + "\nCurrent description: " + description
                + "\n\nMood history: " + (patterns.happyCount() + patterns.neutralCount() + patterns.sadCount())
                + " total entries (" + patterns.happyCount() + " happy, " + patterns.neutralCount() + " neutral, " + patterns.sadCount() + " sad)."
                + "\n\nGive me 4-6 brief, practical suggestions to improve my mood.";
            String body = JSON.writeValueAsString(Map.of(
                "model", APIConfig.getChatGPTModel(),
                "messages", List.of(
                    Map.of("role", "system", "content", "You are an empathetic AI mood coach. Provide 4-6 concise, practical suggestions. Be encouraging and brief. Do not diagnose or present a clinical assessment."),
                    Map.of("role", "user", "content", prompt)),
                "max_tokens", APIConfig.getMaxTokens(), "temperature", APIConfig.getTemperature()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key.trim())
                .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = transport.send(request);
            if (response.statusCode() != 200) return UNAVAILABLE;
            var content = JSON.readTree(response.body()).path("choices").path(0).path("message").path("content");
            return content.isTextual() && !content.asText().isBlank() ? content.asText().trim() : UNAVAILABLE;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return UNAVAILABLE;
        } catch (Exception error) {
            // Never log request bodies, reflection text, response bodies, or credentials.
            return UNAVAILABLE;
        }
    }

    public double getSentimentScore(String description) { return SENTIMENT.analyzeSentiment(description); }
}
