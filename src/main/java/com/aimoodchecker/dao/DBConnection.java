package com.aimoodchecker.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Each caller owns its connection and closes it with try-with-resources. */
public final class DBConnection {
    private DBConnection() {}
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            Path directory = Path.of(System.getProperty("aimoodchecker.dataDir", ".")).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("mood.db"));
            try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=5000"); }
            catch (SQLException error) { connection.close(); throw error; }
            return connection;
        } catch (ClassNotFoundException | java.io.IOException error) {
            throw new SQLException("The local journal could not be opened.", error);
        }
    }
    /** Retained for callers from older versions; connections are now caller-owned. */
    public static void closeConnection() {}
    public static boolean isConnectionValid() {
        try (Connection connection = getConnection()) { return connection.isValid(5); }
        catch (SQLException error) { return false; }
    }
    public static void initDatabase() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS mood_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                mood_type TEXT NOT NULL,
                description TEXT,
                sentiment_score REAL,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );
            """;
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) { statement.execute(ddl); }
        catch (SQLException error) { throw new IllegalStateException("The local journal could not be initialized.", error); }
    }
}
