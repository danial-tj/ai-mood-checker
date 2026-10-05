package com.aimoodchecker.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import com.aimoodchecker.service.SentimentService;
import com.aimoodchecker.service.ChatGPTService;
import com.aimoodchecker.repository.EntryRepository;

public class ComposeController implements RoutedController, NeedsDeps {
    private AppController app;
    private EntryRepository entryRepository;
    private ChatGPTService chatGPT;
    private boolean saved;
    private boolean saving;
    @FXML private TextArea moodText;
    @FXML private Label selectedMoodLabel, errorLabel, saveStatus;
    @FXML private ToggleGroup moodGroup;
    @FXML private ToggleButton happyButton, neutralButton, sadButton;
    @FXML private Button saveBtn, cancelBtn;

    @FXML private void initialize() {
        moodText.textProperty().addListener((observable, oldText, newText) -> {
            if (!newText.isBlank()) clearError();
        });
    }

    @Override public void setApp(AppController app) { this.app = app; }
    @Override public void init(EntryRepository repo, SentimentService sentiment, ChatGPTService chatGPT) {
        this.entryRepository = repo;
        this.chatGPT = chatGPT;
    }

    @FXML private void onMoodSelected() {
        Toggle selection = moodGroup.getSelectedToggle();
        selectedMoodLabel.setText(selection == null ? "All feelings are welcome here." : "Selected: " + selection.getUserData());
        clearError();
    }

    @FXML private void onSave() {
        if (saving || saved) return;
        Toggle selection = moodGroup.getSelectedToggle();
        if (selection == null) {
            showError("Choose Happy, Neutral, or Sad before saving.");
            happyButton.requestFocus();
            return;
        }
        String description = moodText.getText().trim();
        if (description.isEmpty()) {
            showError("Add a few words about how you're feeling.");
            moodText.requestFocus();
            return;
        }
        clearError();
        String mood = selection.getUserData().toString();
        setSaving(true);
        saveBtn.setText("Saving…");
        saveStatus.setText("Saving your check-in…");
        app.setStatus("Saving your check-in…");

        // Keep persistence work off the UI thread and prevent duplicate submissions.
        Task<Void> save = new Task<>() {
            @Override protected Void call() throws Exception {
                entryRepository.saveMoodEntry(mood, description);
                return null;
            }
        };
        save.setOnSucceeded(event -> {
            saved = true;
            saveBtn.setText("Saved");
            saveStatus.setText("Saved. Preparing your reflection…");
            app.setStatus("Check-in saved. Preparing your AI reflection…");
            loadCoaching(mood, description);
        });
        save.setOnFailed(event -> {
            setSaving(false);
            saveBtn.setText("Save check-in");
            saveStatus.setText("");
            showError("Your check-in couldn't be saved. Your words are still here; please try again.");
            app.setStatus("Check-in not saved. Please try again.");
        });
        runInBackground(save, "mood-save");
    }

    private void loadCoaching(String mood, String description) {
        Task<String> coaching = new Task<>() {
            @Override protected String call() { return chatGPT.getMoodCoaching(mood, description, entryRepository); }
        };
        coaching.setOnSucceeded(event -> finishWithCoaching(coaching.getValue()));
        coaching.setOnFailed(event -> finishWithCoaching("Your check-in is saved. AI reflections are unavailable right now. You can return to your journal at any time."));
        runInBackground(coaching, "mood-reflection");
    }

    private void runInBackground(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void setSaving(boolean value) {
        saving = value;
        saveBtn.setDisable(value);
        cancelBtn.setDisable(value);
        moodText.setDisable(value);
        happyButton.setDisable(value);
        neutralButton.setDisable(value);
        sadButton.setDisable(value);
        app.setBusy(value);
    }

    private void finishWithCoaching(String coaching) {
        setSaving(false);
        saveBtn.setDisable(true);
        saveStatus.setText("Your check-in is saved.");
        app.setStatus("Check-in saved");
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Your reflection");
        dialog.setHeaderText("A moment to reflect");
        TextArea words = new TextArea(coaching);
        words.setEditable(false);
        words.setWrapText(true);
        words.setPrefRowCount(13);
        words.setPrefColumnCount(52);
        words.getStyleClass().add("journal-input");
        dialog.getDialogPane().setContent(words);
        dialog.getButtonTypes().setAll(new ButtonType("Back to overview", ButtonBar.ButtonData.OK_DONE));
        app.styleDialog(dialog);
        dialog.showAndWait();
        app.goHome();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public boolean confirmLeave() {
        if (saved || (moodText.getText().isBlank() && moodGroup.getSelectedToggle() == null)) return true;
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Leave this check-in?");
        dialog.setHeaderText("Keep writing, or leave your draft?");
        dialog.setContentText("This check-in hasn't been saved yet.");
        ButtonType keep = new ButtonType("Keep writing", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType leave = new ButtonType("Discard draft", ButtonBar.ButtonData.OK_DONE);
        dialog.getButtonTypes().setAll(keep, leave);
        app.styleDialog(dialog);
        return dialog.showAndWait().orElse(keep) == leave;
    }

    @FXML private void onCancel() { app.goHome(); }
}
