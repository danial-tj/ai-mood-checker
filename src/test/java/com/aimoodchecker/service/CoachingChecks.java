package com.aimoodchecker.service;

import com.aimoodchecker.repository.EntryRepository.MoodPatterns;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Flow;

public final class CoachingChecks {
    private static int checks;
    public static int run() throws Exception {
        MoodPatterns patterns = new MoodPatterns(2, 3, 1, 3.0, .5, "Synthetic", List.of(), List.of());
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        String answer = "Try saying \"one step at a time\".\nTake a short break. Path: C:\\sample. 🌿";
        String response = new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", answer)))));
        ChatGPTService service = new ChatGPTService(request -> { captured.set(request); return response(request, 200, response); }, () -> "synthetic-test-key");
        String description = "I said \"okay\".\nAnother line\tand a backslash \\ and 🌿.";
        check(service.getMoodCoaching("Neutral", description, patterns).equals(answer), "Quoted/newline/unicode coaching response survives JSON parsing");
        var json = new ObjectMapper().readTree(body(captured.get()));
        check(json.path("messages").path(1).path("content").asText().contains(description), "Quoted/newline/unicode reflection survives request JSON encoding");
        check(captured.get().timeout().orElseThrow().toSeconds() == 30, "Remote coaching has a bounded request timeout");
        for (String missing : new String[]{null, "", "  "}) {
            ChatGPTService absent = new ChatGPTService(request -> { throw new AssertionError("Missing key must not send a request"); }, () -> missing);
            check(absent.getMoodCoaching("Neutral", "synthetic", patterns).contains("not configured"), "Missing API key stays local");
        }
        System.setProperty("aimoodchecker.offline", "true");
        check(service.getMoodCoaching("Neutral", "synthetic", patterns).contains("Offline mode"), "Offline mode blocks transport");
        System.clearProperty("aimoodchecker.offline");
        for (int status : new int[]{401, 429, 500}) {
            ChatGPTService failed = new ChatGPTService(request -> response(request, status, "Synthetic failure"), () -> "synthetic-test-key");
            check(failed.getMoodCoaching("Neutral", "synthetic", patterns).contains("unavailable"), "HTTP " + status + " reports unavailable");
        }
        for (String malformed : List.of("not json", "{}", "{\"choices\":[{\"message\":{\"content\":null}}]}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}")) {
            ChatGPTService failed = new ChatGPTService(request -> response(request, 200, malformed), () -> "synthetic-test-key");
            check(failed.getMoodCoaching("Neutral", "synthetic", patterns).contains("unavailable"), "Malformed/empty response is not presented as coaching");
        }
        ChatGPTService io = new ChatGPTService(request -> { throw new IOException("Synthetic network failure"); }, () -> "synthetic-test-key");
        check(io.getMoodCoaching("Neutral", "synthetic", patterns).contains("unavailable"), "Transport failure preserves saved state");
        ChatGPTService interrupted = new ChatGPTService(request -> { throw new InterruptedException(); }, () -> "synthetic-test-key");
        check(interrupted.getMoodCoaching("Neutral", "synthetic", patterns).contains("unavailable") && Thread.currentThread().isInterrupted(), "Interruption flag restored");
        Thread.interrupted();
        return checks;
    }
    private static String body(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<String> value = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { while (buffer.hasRemaining()) bytes.write(buffer.get()); }
            public void onError(Throwable error) { value.completeExceptionally(error); }
            public void onComplete() { value.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return value.get(2, TimeUnit.SECONDS);
    }
    private static HttpResponse<String> response(HttpRequest request, int status, String value) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return request; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return value; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return request.uri(); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
    private static void check(boolean result, String label) { if (!result) throw new AssertionError(label); checks++; System.out.println("PASS: " + label); }
}
