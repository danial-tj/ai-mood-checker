package com.aimoodchecker.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.repository.EntryRepository.MoodPatterns;

/**
 * Service class for making API calls to ChatGPT/OpenAI
 * Handles intelligent mood coaching
 */
public class ChatGPTService {
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    
    private final HttpClient httpClient;
    
    public ChatGPTService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Provides intelligent mood coaching based on user's mood history and current state.
     * This overload fetches mood patterns from the repository (normal app path).
     */
    public String getMoodCoaching(String currentMood, String currentDescription, EntryRepository entryRepository) {
        try {
            MoodPatterns moodPatterns = entryRepository.getMoodPatterns();
            return getMoodCoaching(currentMood, currentDescription, moodPatterns);
        } catch (Exception e) {
            System.err.println("Error getting mood coaching: " + e.getMessage());
            return "I'm having trouble analyzing your mood patterns right now. Please try again later.";
        }
    }

    /**
     * Provides intelligent mood coaching given pre-computed mood patterns.
     * This is useful for testing HTTP latency without requiring a live database.
     */
    public String getMoodCoaching(String currentMood, String currentDescription, MoodPatterns moodPatterns) {
        String apiKey = APIConfig.getOpenAIKey();
        
        try {
            // Create the coaching request body with enhanced data
            String requestBody = createEnhancedCoachingRequest(currentMood, currentDescription, moodPatterns);
            
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            long start = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long end = System.nanoTime();
            long durationMs = (end - start) / 1_000_000;
            System.out.println("AI coaching HTTP call completed in " + durationMs + " ms with status " + response.statusCode());
            
            if (response.statusCode() == 200) {
                return parseChatGPTResponse(response.body());
            } else {
                System.err.println("Coaching API call failed with status: " + response.statusCode());
                return "Unable to get coaching suggestions at the moment. Please try again later.";
            }
            
        } catch (Exception e) {
            System.err.println("Error getting mood coaching: " + e.getMessage());
            return "I'm having trouble analyzing your mood patterns right now. Please try again later.";
        }
    }


    private String createEnhancedCoachingRequest(String currentMood, String currentDescription, MoodPatterns moodPatterns) {
        return """
            {
                "model": "gpt-3.5-turbo",
                "messages": [
                    {
                        "role": "system",
                        "content": "You are an empathetic AI mood coach. Provide 4-6 concise, actionable suggestions to help improve mood. Focus on practical activities they can do immediately. Be encouraging but brief - no long explanations needed. Just the suggestions."
                    },
                    {
                        "role": "user",
                        "content": "Current Mood: %s\\nCurrent Description: %s\\n\\nMood History: %d total entries (%d happy, %d neutral, %d sad)\\n\\nGive me 4-6 quick, practical suggestions to improve my mood. Keep it brief and actionable."
                    }
                ],
                "max_tokens": 1200,
                "temperature": 0.8
            }
            """.formatted(
                currentMood,
                currentDescription,
                moodPatterns.happyCount() + moodPatterns.neutralCount() + moodPatterns.sadCount(),
                moodPatterns.happyCount(),
                moodPatterns.neutralCount(),
                moodPatterns.sadCount()
            );
    }
    
    /**
     * Parses the ChatGPT API response using simple string parsing
     */
    private String parseChatGPTResponse(String responseBody) {
        try {

            String content = extractContentFromResponse(responseBody);
            
            if (content != null && !content.trim().isEmpty()) {
                return content.trim();
            } else {
                return "No content in response";
            }
        } catch (Exception e) {
            return "Response parsing error: " + e.getMessage();
        }
    }
    
    /**
     * Extracts content from ChatGPT response using simple string parsing
     */
    private String extractContentFromResponse(String responseBody) {
        try {
            
            int contentIndex = responseBody.indexOf("\"content\":");
            if (contentIndex == -1) {
                return null;
            }
            
            // Find the start of the content value
            int startIndex = responseBody.indexOf("\"", contentIndex + 10);
            if (startIndex == -1) {
                return null;
            }
            
            // Find the end of the content value
            int endIndex = responseBody.indexOf("\"", startIndex + 1);
            if (endIndex == -1) {
                return null;
            }
            
            // Extract the content between quotes
            String content = responseBody.substring(startIndex + 1, endIndex);
            
            // Handle escaped quotes and newlines
            content = content.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\");
            
            return content;
            
        } catch (Exception e) {
            System.err.println("Error extracting content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets a sentiment score (0.0 to 1.0) from the analysis
     * @param moodDescription User's mood description
     * @return Sentiment score between 0.0 (very negative) and 1.0 (very positive)
     */
    public double getSentimentScore(String moodDescription) {
        // Simple sentiment scoring based on keywords
        String lowerDescription = moodDescription.toLowerCase();
        
        if (lowerDescription.contains("happy") || lowerDescription.contains("great") || 
            lowerDescription.contains("good") || lowerDescription.contains("excellent")) {
            return 0.8;
        } else if (lowerDescription.contains("okay") || lowerDescription.contains("fine") || 
                   lowerDescription.contains("alright")) {
            return 0.5;
        } else if (lowerDescription.contains("sad") || lowerDescription.contains("bad") || 
                   lowerDescription.contains("terrible") || lowerDescription.contains("worried")) {
            return 0.2;
        }
        
        return 0.5; // Default neutral score
    }
}
