package com.aimoodchecker.service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local configuration. Values and credentials are never written to logs. */
public final class APIConfig {
    private static final Properties CONFIG = new Properties();
    private static final Properties DOT_ENV = new Properties();
    static {
        Path directory = Path.of(System.getProperty("aimoodchecker.dataDir", "."));
        Path properties = directory.resolve("config.properties");
        if (Files.isRegularFile(properties)) {
            try (Reader reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) { CONFIG.load(reader); }
            catch (IOException ignored) { /* Environment variables remain available. */ }
        }
        Path env = directory.resolve(".env");
        if (Files.isRegularFile(env)) {
            try (var lines = Files.lines(env, StandardCharsets.UTF_8)) {
                lines.map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(line -> {
                    int equals = line.indexOf('=');
                    if (equals > 0) {
                        String key = line.substring(0, equals).trim();
                        String value = line.substring(equals + 1).trim();
                        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))))
                            value = value.substring(1, value.length() - 1);
                        DOT_ENV.setProperty(key, value);
                    }
                });
            } catch (IOException ignored) { /* Environment variables remain available. */ }
        }
    }
    private APIConfig() {}
    public static String getOpenAIKey() {
        for (String value : new String[]{CONFIG.getProperty("openai.api.key"), System.getProperty("OPENAI_API_KEY"), DOT_ENV.getProperty("OPENAI_API_KEY"), System.getenv("OPENAI_API_KEY")})
            if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
    public static String getChatGPTModel() { return CONFIG.getProperty("openai.model", "gpt-3.5-turbo"); }
    public static int getMaxTokens() {
        try { int value = Integer.parseInt(CONFIG.getProperty("openai.max.tokens", "1200")); return value > 0 ? value : 1200; }
        catch (NumberFormatException error) { return 1200; }
    }
    public static double getTemperature() {
        try { double value = Double.parseDouble(CONFIG.getProperty("openai.temperature", "0.8")); return Double.isFinite(value) && value >= 0 && value <= 2 ? value : 0.8; }
        catch (NumberFormatException error) { return 0.8; }
    }
    public static boolean isConfigured() { return getOpenAIKey() != null; }
}
