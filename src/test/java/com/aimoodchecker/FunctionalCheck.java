package com.aimoodchecker;

import com.aimoodchecker.dao.DBConnection;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.service.CoachingChecks;
import com.aimoodchecker.service.SentimentService;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.*;

/** Runs only against a new, isolated synthetic journal. No live HTTP transport is used. */
public final class FunctionalCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(".").toAbsolutePath().normalize();
        for (String name : new String[]{"mood.db", "config.properties", ".env"})
            if (Files.exists(directory.resolve(name))) throw new IllegalStateException("Use a fresh directory without journal or credentials.");
        System.setProperty("aimoodchecker.dataDir", directory.toString());
        scoring();
        checks += CoachingChecks.run();
        DBConnection.initDatabase();
        EntryRepository repo = EntryRepository.getInstance();
        check(repo.getAllMoodEntries().isEmpty(), "New journal is empty");
        check(repo.findDailyAverages(7).isEmpty(), "New journal has no fabricated trends");
        String words = "Synthetic reflection \"quoted\"; DROP TABLE mood_entries;\nUnicode 🌿 and slash \\.";
        repo.saveMoodEntry("Happy", words);
        int firstId = repo.getAllMoodEntries().getFirst().getId();
        repo.saveMoodEntry("Sad", "Synthetic sad day");
        int latestId = Math.max(firstId + 1, repo.getAllMoodEntries().stream().mapToInt(entry -> entry.getId()).max().orElseThrow());
        try (Connection connection = DBConnection.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE mood_entries SET created_at='2026-01-01 00:00:00'");
        }
        check(repo.getAllMoodEntries().getFirst().getId() == latestId, "Same-second entries are newest-first by ID");
        check(repo.getMoodEntryById(firstId).orElseThrow().getDescription().equals(words), "Quoted/unicode reflection round-trips through SQLite");
        repo.updateMoodEntry(firstId, "Neutral", "A great synthetic afternoon");
        check(repo.getMoodEntryById(firstId).orElseThrow().getSentimentScore() == .8, "Updating an entry recalculates local tone");
        try (Connection a = DBConnection.getConnection(); Connection b = DBConnection.getConnection()) {
            a.close();
            check(!b.isClosed() && b.isValid(1), "One operation closing its connection cannot close another operation");
        }
        insert(LocalDate.now().minusDays(1), "Neutral", null);
        insert(LocalDate.now().minusDays(6), "Happy", .8);
        insert(LocalDate.now().minusDays(7), "Sad", .2);
        var unknown = repo.getMoodEntriesForDateRange(LocalDate.now().minusDays(1), LocalDate.now().minusDays(1)).getFirst();
        check(unknown.getSentimentScore() == null && unknown.getSentimentCategory().equals("Unknown"), "SQL NULL tone stays unknown in journal");
        var trends = repo.findDailyAverages(7);
        check(trends.size() == 3 && trends.getFirst().date().equals(LocalDate.now().minusDays(6)), "Seven-day range is inclusive and sorted chronologically");
        check(trends.get(1).avgAi() == null, "Missing tone remains unavailable in trends");
        check(Math.abs(trends.getLast().avgMood() - 2.0) < .001 && Math.abs(trends.getLast().avgAi() - .5) < .001, "Daily averages combine multiple saved entries correctly");
        repo.deleteMoodEntry(firstId);
        check(repo.getMoodEntryById(firstId).isEmpty(), "Deletion persists");
        boolean missingDelete = false;
        try { repo.deleteMoodEntry(firstId); } catch (SQLException expected) { missingDelete = true; }
        check(missingDelete, "Deleting a missing entry reports failure");
        int beforeParallel = repo.getAllMoodEntries().size();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int index = 0; index < 16; index++) futures.add(pool.submit(() -> { repo.saveMoodEntry("Neutral", "Synthetic concurrent entry"); return null; }));
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        }
        check(repo.getAllMoodEntries().size() == beforeParallel + 16, "Concurrent saves retain every entry without shared-connection failures");
        check(repo.getMoodPatterns().happyCount() + repo.getMoodPatterns().neutralCount() + repo.getMoodPatterns().sadCount() == repo.getAllMoodEntries().size(), "Coaching counts agree with saved journal");
        check(DBConnection.isConnectionValid(), "Packaged SQLite driver can reopen persisted journal");
        System.out.println("PASS: " + checks + " functional checks, synthetic data only, no network calls.");
    }
    private static void scoring() {
        var service = new SentimentService();
        check(service.analyzeSentiment("I am HAPPY") == .8, "Positive keyword matching is case-independent");
        check(service.analyzeSentiment("I am unhappy") == .2, "Unhappy is not matched as happy");
        check(service.analyzeSentiment("A badge and goodbye") == .5, "Substrings are not sentiment words");
        check(service.analyzeSentiment("I do not feel good") == .2, "Short negation of positive tone is handled");
        check(service.analyzeSentiment("Not bad") == .8, "Short negation of negative tone is handled");
        check(service.analyzeSentiment("Good and sad") == .5, "Mixed keyword tone is neutral");
        check(service.analyzeSentiment(null) == .5 && service.analyzeSentiment("") == .5, "Missing legacy reflection text is neutral");
        Locale previous = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); check(service.analyzeSentiment("FINE") == .5, "Scoring does not depend on system locale"); }
        finally { Locale.setDefault(previous); }
    }
    private static void insert(LocalDate date, String mood, Double tone) throws SQLException {
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO mood_entries(date,mood_type,description,sentiment_score) VALUES(?,?,?,?)")) {
            statement.setString(1, date.toString()); statement.setString(2, mood); statement.setString(3, "Synthetic legacy entry");
            if (tone == null) statement.setNull(4, Types.REAL); else statement.setDouble(4, tone);
            statement.executeUpdate();
        }
    }
    private static void check(boolean result, String label) { if (!result) throw new AssertionError(label); checks++; System.out.println("PASS: " + label); }
}
