package com.aimoodchecker.repository;

import com.aimoodchecker.model.MoodEntry;
import com.aimoodchecker.dao.DBConnection;
import com.aimoodchecker.service.SentimentService;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

public class EntryRepository {
    private static final EntryRepository INSTANCE = new EntryRepository();
    private static final SentimentService SENTIMENT = new SentimentService();
    
    private EntryRepository() {
        // No ChatGPTService instantiation here to avoid circular dependency
    }
    
    public static EntryRepository getInstance() { return INSTANCE; }
    
    // ===== CREATE OPERATIONS =====
    
    /**
     * Save a new mood entry to the database
     */
    public void saveMoodEntry(String moodType, String description) throws SQLException {
        String sql = "INSERT INTO mood_entries (date, mood_type, description, sentiment_score) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, LocalDate.now().toString());
            pstmt.setString(2, moodType);
            pstmt.setString(3, description);
            pstmt.setDouble(4, SENTIMENT.analyzeSentiment(description));
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to insert mood entry");
            }
        }
    }
    
    // ===== READ OPERATIONS =====
    
    /**
     * Get all mood entries ordered by date (newest first)
     */
    public List<MoodEntry> getAllMoodEntries() throws SQLException {
        List<MoodEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM mood_entries ORDER BY date DESC, created_at DESC, id DESC";
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                entries.add(createMoodEntryFromResultSet(rs));
            }
        }
        return entries;
    }
    
    /**
     * Get mood entries for a specific date range
     */
    public List<MoodEntry> getMoodEntriesForDateRange(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<MoodEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM mood_entries WHERE date BETWEEN ? AND ? ORDER BY date DESC, created_at DESC, id DESC";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(createMoodEntryFromResultSet(rs));
                }
            }
        }
        return entries;
    }
    
    /**
     * Get mood entries for the last N days
     */
    public List<MoodEntry> getMoodEntriesForLastDays(int days) throws SQLException {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        
        if (days < 1) throw new IllegalArgumentException("days must be positive");
        return getMoodEntriesForDateRange(startDate, endDate);
    }
    
    /**
     * Get a single mood entry by ID
     */
    public Optional<MoodEntry> getMoodEntryById(int id) throws SQLException {
        String sql = "SELECT * FROM mood_entries WHERE id = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(createMoodEntryFromResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    // ===== UPDATE OPERATIONS =====
    
    /**
     * Update an existing mood entry
     */
    public void updateMoodEntry(int id, String moodType, String description) throws SQLException {
        String sql = "UPDATE mood_entries SET mood_type = ?, description = ?, sentiment_score = ? WHERE id = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, moodType);
            pstmt.setString(2, description);
            pstmt.setDouble(3, SENTIMENT.analyzeSentiment(description));
            pstmt.setInt(4, id);
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No mood entry found with ID: " + id);
            }
        }
    }
    
    // ===== DELETE OPERATIONS =====
    
    /**
     * Delete a mood entry by ID
     */
    public void deleteMoodEntry(int id) throws SQLException {
        String sql = "DELETE FROM mood_entries WHERE id = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No mood entry found with ID: " + id);
            }
        }
    }
    
    // ===== TREND ANALYSIS =====
    
    /**
     * Get daily averages for mood and AI sentiment over a specified period
     */
    public List<TrendPoint> findDailyAverages(int days) throws SQLException {
        Map<LocalDate, List<MoodEntry>> byDate = getMoodEntriesForLastDays(days).stream()
            .filter(entry -> entry.getDate() != null)
            .collect(Collectors.groupingBy(MoodEntry::getDate));
        List<TrendPoint> points = new ArrayList<>();
        for (var day : byDate.entrySet()) {
            double mood = day.getValue().stream().mapToDouble(entry -> moodTypeToScore(entry.getMoodType())).average().orElse(3.0);
            var tone = day.getValue().stream().map(MoodEntry::getSentimentScore).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average();
            points.add(new TrendPoint(day.getKey(), mood, tone.isPresent() ? tone.getAsDouble() : null));
        }
        points.sort(java.util.Comparator.comparing(TrendPoint::date));
        return points;
    }
    
    /**
     * Get mood statistics for the last N days
     */
    public MoodStatistics getMoodStatistics(int days) throws SQLException {
        List<MoodEntry> entries = getMoodEntriesForLastDays(days);
        
        if (entries.isEmpty()) {
            return new MoodStatistics(0, 0, 0, 0.0, 0.0);
        }
        
        long happyCount = entries.stream().filter(e -> "Happy".equals(e.getMoodType())).count();
        long neutralCount = entries.stream().filter(e -> "Neutral".equals(e.getMoodType())).count();
        long sadCount = entries.stream().filter(e -> "Sad".equals(e.getMoodType())).count();
        
        double avgMoodScore = entries.stream()
            .mapToDouble(e -> moodTypeToScore(e.getMoodType()))
            .average()
            .orElse(0.0);
        
        double avgSentimentScore = entries.stream()
            .map(MoodEntry::getSentimentScore).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        return new MoodStatistics(happyCount, neutralCount, sadCount, avgMoodScore, avgSentimentScore);
    }

    /**
     * Get mood patterns and insights for AI coaching
     * @return MoodPatterns object containing insights about user's mood patterns
     */
    public MoodPatterns getMoodPatterns() throws SQLException {
        List<MoodEntry> allEntries = getAllMoodEntries();
        
        if (allEntries.isEmpty()) {
            return new MoodPatterns(0, 0, 0, 0.0, 0.0, "No data available", List.of(), List.of());
        }
        
        // Basic counts
        long happyCount = allEntries.stream().filter(e -> "Happy".equals(e.getMoodType())).count();
        long neutralCount = allEntries.stream().filter(e -> "Neutral".equals(e.getMoodType())).count();
        long sadCount = allEntries.stream().filter(e -> "Sad".equals(e.getMoodType())).count();
        
        // Calculate averages
        double avgMoodScore = allEntries.stream()
            .mapToDouble(e -> moodTypeToScore(e.getMoodType()))
            .average()
            .orElse(0.0);
        
        double avgSentimentScore = allEntries.stream()
            .map(MoodEntry::getSentimentScore).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Find recent happy moments (last 10)
        List<String> recentHappyMoments = allEntries.stream()
            .filter(e -> "Happy".equals(e.getMoodType()))
            .limit(10)
            .map(MoodEntry::getDescription)
            .collect(Collectors.toList());
        
        // Find recent sad moments (last 5) to understand triggers
        List<String> recentSadMoments = allEntries.stream()
            .filter(e -> "Sad".equals(e.getMoodType()))
            .limit(5)
            .map(MoodEntry::getDescription)
            .collect(Collectors.toList());
        
        // Determine overall pattern
        String overallPattern = determineOverallPattern(happyCount, neutralCount, sadCount, avgMoodScore);
        
        return new MoodPatterns(happyCount, neutralCount, sadCount, avgMoodScore, avgSentimentScore, 
                              overallPattern, recentHappyMoments, recentSadMoments);
    }

    /**
     * Determines the overall mood pattern based on statistics
     */
    private String determineOverallPattern(long happyCount, long neutralCount, long sadCount, double avgMoodScore) {
        int total = (int) (happyCount + neutralCount + sadCount);
        if (total == 0) return "No mood data available";
        
        double happyPercentage = (double) happyCount / total * 100;
        double sadPercentage = (double) sadCount / total * 100;
        
        if (happyPercentage >= 60) return "Generally positive outlook";
        else if (sadPercentage >= 40) return "Tends toward negative moods";
        else if (avgMoodScore >= 3.5) return "Moderately positive pattern";
        else if (avgMoodScore <= 2.5) return "Moderately negative pattern";
        else return "Balanced mood pattern";
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Convert mood type string to numeric score (1-5)
     */
    private double moodTypeToScore(String moodType) {
        return switch (moodType) {
            case "Happy" -> 5.0;
            case "Neutral" -> 3.0;
            case "Sad" -> 1.0;
            default -> 3.0;
        };
    }
    
    /**
     * Create MoodEntry object from database ResultSet
     */
    private MoodEntry createMoodEntryFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String dateStr = rs.getString("date");
        String moodType = rs.getString("mood_type");
        String description = rs.getString("description");
        double rawSentiment = rs.getDouble("sentiment_score");
        Double sentimentScore = rs.wasNull() ? null : rawSentiment;
        String createdAt = rs.getString("created_at");
        
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            // Keep the journal row readable; unavailable dates are omitted from trends.
        }
        
        return new MoodEntry(id, date, moodType, description, sentimentScore, createdAt);
    }
    
    // ===== DATA MODELS =====
    
    /**
     * Record representing a trend data point for charts
     */
    public record TrendPoint(LocalDate date, Double avgMood, Double avgAi) {}
    
    /**
     * Record representing mood statistics
     */
    public record MoodStatistics(
        long happyCount, 
        long neutralCount, 
        long sadCount, 
        double avgMoodScore, 
        double avgSentimentScore
    ) {}

    /**
     * Record representing mood patterns and insights for AI coaching
     */
    public record MoodPatterns(
        long happyCount, 
        long neutralCount, 
        long sadCount, 
        double avgMoodScore, 
        double avgSentimentScore, 
        String overallPattern, 
        List<String> recentHappyMoments, 
        List<String> recentSadMoments
    ) {}
}


