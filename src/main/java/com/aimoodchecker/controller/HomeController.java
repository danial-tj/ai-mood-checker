package com.aimoodchecker.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import com.aimoodchecker.model.MoodEntry;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.service.ChatGPTService;
import com.aimoodchecker.service.SentimentService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.SQLException;
import java.util.List;

public class HomeController implements RoutedController, NeedsDeps {
    private AppController app;
    @FXML private Label dateLabel, weekCount, lastMood, lastDate, totalCount;
    @FXML private VBox recentEntries;
    @FXML private void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")));
    }
    @Override public void setApp(AppController app) { this.app = app; }
    @Override public void init(EntryRepository repo, SentimentService sentiment, ChatGPTService chatGPT) {
        try {
            List<MoodEntry> entries = repo.getAllMoodEntries();
            LocalDate today = LocalDate.now();
            long week = entries.stream().filter(e -> e.getDate() != null && !e.getDate().isBefore(today.minusDays(6)) && !e.getDate().isAfter(today)).count();
            weekCount.setText(Long.toString(week));
            totalCount.setText(Integer.toString(entries.size()));
            if (entries.isEmpty()) {
                Label empty = new Label("Your journal starts here.\nTake a moment to write about what's on your mind.");
                empty.getStyleClass().add("empty-state");
                empty.setWrapText(true);
                empty.setMaxWidth(Double.MAX_VALUE);
                recentEntries.getChildren().setAll(empty);
            } else {
                MoodEntry latest = entries.getFirst();
                lastMood.setText(latest.getMoodType());
                lastDate.setText(latest.getDate() == null ? "Date unavailable" : latest.getDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
                for (MoodEntry entry : entries.stream().limit(2).toList()) {
                    Label meta = new Label(entry.getMoodType() + "   ·   " + entry.getFormattedDate());
                    meta.getStyleClass().add("reflection-meta");
                    String description = entry.getDescription() == null ? "" : entry.getDescription();
                    Label words = new Label(description.length() > 180 ? description.substring(0, 177) + "…" : description);
                    words.setWrapText(true);
                    words.setMaxWidth(Double.MAX_VALUE);
                    words.getStyleClass().add("reflection-text");
                    VBox row = new VBox(8, meta, words);
                    row.getStyleClass().add("reflection-row");
                    recentEntries.getChildren().add(row);
                }
            }
        } catch (SQLException error) {
            weekCount.setText("—");
            totalCount.setText("—");
            Label message = new Label("Your journal couldn't load. Open the journal and try Refresh.");
            message.setWrapText(true);
            message.getStyleClass().add("field-error");
            recentEntries.getChildren().setAll(message);
        }
    }
    @FXML private void handleLogMood() { app.goCompose(); }
    @FXML private void handleViewHistory() { app.goHistory(); }
}