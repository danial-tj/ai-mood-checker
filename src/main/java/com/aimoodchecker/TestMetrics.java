package com.aimoodchecker;

import com.aimoodchecker.dao.DBConnection;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.repository.EntryRepository.MoodStatistics;
import com.aimoodchecker.repository.EntryRepository.TrendPoint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Utility to generate measurable performance numbers for the resume.
 *
 * It seeds the SQLite database with synthetic mood entries and then
 * times key operations (history load, 90‑day trends, statistics).
 *
 * Run after compiling the project, e.g.:
 *   java -cp target/classes com.aimoodchecker.TestMetrics
 */
public class TestMetrics {

    public static void main(String[] args) {
        int totalEntries = 5_000;   // adjust if you want more / fewer rows
        int daySpan = 180;          // spread data across ~6 months

        System.out.println("=== AI Mood Checker Metrics Test ===");

        try {
            // Ensure schema exists
            DBConnection.initDatabase();

            // Seed synthetic data (idempotent in the sense that it just adds more rows)
            seedTestData(totalEntries, daySpan);

            EntryRepository repo = EntryRepository.getInstance();

            // Measure loading all entries
            long t1 = System.nanoTime();
            List<com.aimoodchecker.model.MoodEntry> allEntries = repo.getAllMoodEntries();
            long t2 = System.nanoTime();
            long loadAllMs = (t2 - t1) / 1_000_000;

            // Measure 90‑day trend computation
            int days = 90;
            long t3 = System.nanoTime();
            List<TrendPoint> trendPoints = repo.findDailyAverages(days);
            long t4 = System.nanoTime();
            long trendsMs = (t4 - t3) / 1_000_000;

            // Measure 90‑day statistics computation
            long t5 = System.nanoTime();
            MoodStatistics stats = repo.getMoodStatistics(days);
            long t6 = System.nanoTime();
            long statsMs = (t6 - t5) / 1_000_000;

            System.out.println();
            System.out.println("Total entries in DB: " + allEntries.size());
            System.out.println("Load all history: " + loadAllMs + " ms");
            System.out.println("90‑day trend points: " + trendPoints.size() +
                    " (computed in " + trendsMs + " ms)");
            System.out.println("90‑day statistics computed in: " + statsMs + " ms");
            System.out.println("90‑day avg mood score: " + stats.avgMoodScore());
            System.out.println("90‑day avg sentiment score: " + stats.avgSentimentScore());
            System.out.println();
            System.out.println("Use these numbers directly in your resume bullets.");

        } catch (Exception e) {
            System.err.println("Metrics test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Quickly seeds the database with synthetic mood entries spread across a range of days.
     */
    private static void seedTestData(int totalEntries, int daySpan) throws SQLException {
        System.out.println("Seeding " + totalEntries + " synthetic mood entries over " +
                daySpan + " days...");

        String[] moods = {"Happy", "Neutral", "Sad"};
        Random random = new Random();

        String sql = "INSERT INTO mood_entries (date, mood_type, description, sentiment_score) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (int i = 0; i < totalEntries; i++) {
                LocalDate date = LocalDate.now().minusDays(random.nextInt(daySpan));
                String mood = moods[random.nextInt(moods.length)];
                String description = "Test entry " + (i + 1) + " feeling " + mood.toLowerCase();
                double sentiment = switch (mood) {
                    case "Happy" -> 0.8;
                    case "Sad" -> 0.2;
                    default -> 0.5;
                };

                pstmt.setString(1, date.toString());
                pstmt.setString(2, mood);
                pstmt.setString(3, description);
                pstmt.setDouble(4, sentiment);
                pstmt.addBatch();

                if ((i + 1) % 500 == 0) {
                    pstmt.executeBatch();
                }
            }

            pstmt.executeBatch();
            conn.commit();
        }

        System.out.println("Done seeding synthetic entries.");
    }
}


