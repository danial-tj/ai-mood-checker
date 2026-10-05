package com.aimoodchecker.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.collections.FXCollections;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.model.MoodEntry;
import com.aimoodchecker.service.SentimentService;
import com.aimoodchecker.service.ChatGPTService;
import java.sql.SQLException;

public class HistoryController implements RoutedController, NeedsDeps {
    private AppController app;
    private EntryRepository entryRepository;
    @FXML private TableView<MoodEntry> historyTable;
    @FXML private TableColumn<MoodEntry, String> dateColumn, moodColumn, descriptionColumn, sentimentColumn;
    @FXML private TableColumn<MoodEntry, Void> actionsColumn;
    @FXML private Label noDataLabel, entryCount;

    @FXML private void initialize() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("formattedDate"));
        moodColumn.setCellValueFactory(new PropertyValueFactory<>("moodType"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        sentimentColumn.setCellValueFactory(new PropertyValueFactory<>("sentimentCategory"));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("No reflections yet"));
        historyTable.setAccessibleText("Your mood journal. Select an entry and press Enter to read it.");
        historyTable.setRowFactory(table -> {
            TableRow<MoodEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) showEntry(row.getItem());
            });
            return row;
        });
        historyTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && historyTable.getSelectionModel().getSelectedItem() != null) {
                showEntry(historyTable.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button delete = new Button("Delete");
            {
                delete.getStyleClass().add("delete-button");
                delete.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) deleteMoodEntry(getTableRow().getItem());
                    event.consume();
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && getTableRow() != null && getTableRow().getItem() != null)
                    delete.setAccessibleText("Delete check-in from " + getTableRow().getItem().getFormattedDate());
                setGraphic(empty ? null : delete);
            }
        });
    }
    @Override public void setApp(AppController app) { this.app = app; loadMoodHistory(); }
    @Override public void init(EntryRepository repo, SentimentService sentiment, ChatGPTService chatGPT) { entryRepository = repo; }
    @FXML private void onRefresh() { loadMoodHistory(); }

    private void loadMoodHistory() {
        try {
            var entries = entryRepository.getAllMoodEntries();
            historyTable.setItems(FXCollections.observableArrayList(entries));
            noDataLabel.setText("Your story starts here.\nAdd a daily check-in to begin your journal.");
            noDataLabel.setVisible(entries.isEmpty());
            historyTable.setVisible(!entries.isEmpty());
            entryCount.setText(entries.size() + (entries.size() == 1 ? " reflection" : " reflections"));
            app.setStatus(entries.isEmpty() ? "Your journal is ready for your first check-in" : entries.size() + " check-ins in your journal");
        } catch (SQLException error) {
            noDataLabel.setText("Your journal couldn't load.\nTry Refresh to open it again.");
            noDataLabel.setVisible(true);
            historyTable.setVisible(false);
            app.setStatus("Journal unavailable. Please try Refresh.");
        }
    }

    private void showEntry(MoodEntry entry) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Your journal");
        dialog.setHeaderText(entry.getMoodType() + "  ·  " + entry.getFormattedDate());
        TextArea words = new TextArea(entry.getDescription());
        words.setEditable(false);
        words.setWrapText(true);
        words.setPrefRowCount(10);
        words.setPrefColumnCount(50);
        words.getStyleClass().add("journal-input");
        dialog.getDialogPane().setContent(words);
        app.styleDialog(dialog);
        dialog.showAndWait();
    }

    private void deleteMoodEntry(MoodEntry entry) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Delete check-in");
        dialog.setHeaderText("Delete this reflection?");
        dialog.setContentText(entry.getMoodType() + " · " + entry.getFormattedDate() + "\nThis will permanently remove this entry from your journal.");
        ButtonType cancel = new ButtonType("Keep entry", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType delete = new ButtonType("Delete entry", ButtonBar.ButtonData.OK_DONE);
        dialog.getButtonTypes().setAll(cancel, delete);
        app.styleDialog(dialog);
        if (dialog.showAndWait().orElse(cancel) != delete) return;
        try {
            entryRepository.deleteMoodEntry(entry.getId());
            loadMoodHistory();
            app.setStatus("Check-in deleted");
        } catch (SQLException error) {
            Alert failure = new Alert(Alert.AlertType.ERROR, "The entry couldn't be deleted. Please try again.");
            failure.setHeaderText("Your entry is still in your journal");
            app.styleDialog(failure);
            failure.showAndWait();
        }
    }
    @FXML private void onViewGraph() { app.goGraph(); }
}