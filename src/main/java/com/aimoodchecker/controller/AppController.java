package com.aimoodchecker.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import com.aimoodchecker.service.SentimentService;
import com.aimoodchecker.service.ChatGPTService;
import com.aimoodchecker.repository.EntryRepository;
import java.io.IOException;
import java.util.List;

public class AppController {
    @FXML private StackPane content;
    @FXML private Label statusLabel;
    @FXML private ToggleButton homeNav, composeNav, historyNav, graphNav;
    private final EntryRepository repo = EntryRepository.getInstance();
    private final SentimentService sentiment = new SentimentService();
    private final ChatGPTService chatGPT;
    private Object currentController;
    private ToggleButton currentNav;
    private boolean busy;

    public AppController() { this(new ChatGPTService()); }

    /** Allows isolated UI verification without sending journal entries to a service. */
    public AppController(ChatGPTService chatGPT) { this.chatGPT = chatGPT; }

    @FXML private void initialize() { goHome(); }

    private void setContent(String fxml, ToggleButton nav) {
        if (nav == currentNav || busy) { updateNavigation(); return; }
        if (!canLeave()) { updateNavigation(); return; }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent page = loader.load();
            Object controller = loader.getController();
            if (controller instanceof NeedsDeps deps) deps.init(repo, sentiment, chatGPT);
            if (controller instanceof RoutedController routed) routed.setApp(this);
            content.getChildren().setAll(page);
            currentController = controller;
            currentNav = nav;
            updateNavigation();
        } catch (IOException error) {
            setStatus("This page couldn't open. Please try again.");
            error.printStackTrace();
            updateNavigation();
        }
    }

    public boolean canLeave() {
        if (busy) return false;
        return !(currentController instanceof ComposeController compose) || compose.confirmLeave();
    }

    private void updateNavigation() {
        for (ToggleButton nav : List.of(homeNav, composeNav, historyNav, graphNav)) nav.setSelected(nav == currentNav);
    }

    public void setBusy(boolean value) {
        busy = value;
        for (ToggleButton nav : List.of(homeNav, composeNav, historyNav, graphNav)) nav.setDisable(value);
    }

    public void setStatus(String message) { statusLabel.setText(message); }
    public Window getWindow() { return content.getScene() == null ? null : content.getScene().getWindow(); }

    public void styleDialog(Dialog<?> dialog) {
        var css = getClass().getResource("/styles.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        if (getWindow() != null) dialog.initOwner(getWindow());
        dialog.setResizable(true);
    }

    @FXML public void goHome() { setContent("/HomeView.fxml", homeNav); }
    @FXML public void goCompose() { setContent("/ComposeView.fxml", composeNav); }
    @FXML public void goHistory() { setContent("/HistoryView.fxml", historyNav); }
    @FXML public void goGraph() { setContent("/GraphView.fxml", graphNav); }
}
