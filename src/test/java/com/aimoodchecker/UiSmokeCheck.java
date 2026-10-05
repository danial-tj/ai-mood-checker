package com.aimoodchecker;

import com.aimoodchecker.controller.*;
import com.aimoodchecker.dao.DBConnection;
import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.service.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.*;

/** Run only in a fresh temporary working directory: uses a synthetic mood.db, never user data. */
public final class UiSmokeCheck {
    private static final Path OUTPUT = Path.of("screenshots");
    private static int checks;
    private static boolean previewShown;
    private static final java.util.concurrent.atomic.AtomicReference<Throwable> fxError = new java.util.concurrent.atomic.AtomicReference<>();

    public static void main(String[] args) throws Exception {
        if (Files.exists(Path.of("mood.db")) || Files.exists(Path.of("config.properties")) || Files.exists(Path.of(".env")))
            throw new IllegalStateException("Use a fresh temporary directory without a database or credentials.");
        Files.createDirectories(OUTPUT);
        DBConnection.initDatabase();
        Platform.startup(() -> Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> fxError.compareAndSet(null, error)));
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                var fixture = load(1180, 820);
                snapshot(fixture.root, "overview-empty");
                fixture.app.goHistory();
                check(fixture.root.lookup("#noDataLabel").isVisible(), "Journal empty state");
                snapshot(fixture.root, "journal-empty");
                fixture.app.goGraph();
                snapshot(fixture.root, "trends-empty");
                fixture.app.goCompose();
                fixture.root.applyCss();
                fixture.root.layout();
                Button save = (Button) fixture.root.lookup("#saveBtn");
                save.fire();
                check(fixture.root.lookup("#errorLabel").isVisible(), "Missing mood validation");
                ((ToggleButton) fixture.root.lookup("#happyButton")).fire();
                check(((ToggleButton) fixture.root.lookup("#happyButton")).isSelected(), "Mood selected state");
                save.fire();
                check(((Label) fixture.root.lookup("#errorLabel")).getText().contains("words"), "Missing reflection validation");
                ((TextArea) fixture.root.lookup("#moodText")).setText("I took a walk today and made time for a quiet break. It felt good to slow down.");
                snapshot(fixture.root, "check-in");
                return null;
            });
            checkDraftExit();
            seed();
            for (int width : new int[]{1180, 920}) {
                fx(() -> {
                    var fixture = load(width, width == 920 ? 680 : 820);
                    check(((Label) fixture.root.lookup("#totalCount")).getText().equals("5"), "Real journal count at " + width);
                    snapshot(fixture.root, "overview-" + width);
                    fixture.app.goHistory();
                    check(((TableView<?>) fixture.root.lookup("#historyTable")).getItems().size() == 5, "Journal rows at " + width);
                    snapshot(fixture.root, "journal-" + width);
                    fixture.app.goGraph();
                    snapshot(fixture.root, "trends-" + width);
                    ComboBox<?> range = (ComboBox<?>) fixture.root.lookup("#rangeCombo");
                    range.getSelectionModel().select(0);
                    check(fixture.root.lookup(".trend-chart") != null, "7-day chart renders at " + width);
                    fixture.app.goCompose();
                    snapshot(fixture.root, "check-in-" + width);
                    return null;
                });
            }
            saveWithFakeCoaching();
            checkHistoryDeletion();
            checkSaveFailure();
            fx(() -> { snapshot(load(1180, 820).root, "overview-release"); return null; });
            check(fxError.get() == null, "No uncaught JavaFX errors: " + fxError.get());
            System.out.println("PASS: " + checks + " UI checks; snapshots in " + OUTPUT.toAbsolutePath());
            if (java.util.Arrays.asList(args).contains("--preview")) {
                fx(() -> {
                    var fixture = load(1180, 820);
                    javafx.stage.Stage stage = new javafx.stage.Stage();
                    stage.setTitle("AI Mood Checker — design preview (sample journal)");
                    stage.setScene(fixture.root.getScene());
                    stage.setMinWidth(920);
                    stage.setMinHeight(680);
                    stage.setOnCloseRequest(event -> { if (!fixture.app.canLeave()) event.consume(); });
                    stage.setOnHidden(event -> { DBConnection.closeConnection(); Platform.exit(); });
                    stage.show();
                    previewShown = true;
                    return null;
                });
            }
        } finally {
            if (!previewShown) {
                DBConnection.closeConnection();
                Platform.exit();
            }
        }
    }

    private static void checkDraftExit() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(UiSmokeCheck.class.getResource("/ComposeView.fxml"));
            Parent root = loader.load();
            new Scene(root, 900, 800);
            root.applyCss();
            root.layout();
            ComposeController controller = loader.getController();
            final boolean[] discard = {false};
            controller.setApp(new AppController() {
                @Override public void styleDialog(Dialog<?> dialog) {
                    dialog.setOnShown(event -> Platform.runLater(() -> {
                        int index = discard[0] ? 1 : 0;
                        ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(index))).fire();
                    }));
                }
            });
            ((TextArea) root.lookup("#moodText")).setText("Synthetic unsaved reflection");
            check(!controller.confirmLeave(), "Keep writing preserves an unsaved draft");
            discard[0] = true;
            check(controller.confirmLeave(), "Discard draft allows navigation after confirmation");
            return null;
        });
    }

    private static void checkHistoryDeletion() throws Exception {
        var repository = EntryRepository.getInstance();
        int initial = repository.getAllMoodEntries().size();
        var entry = repository.getAllMoodEntries().getFirst();
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(UiSmokeCheck.class.getResource("/HistoryView.fxml"));
            Parent root = loader.load();
            new Scene(root, 900, 800);
            root.applyCss();
            root.layout();
            HistoryController controller = loader.getController();
            controller.init(repository, new SentimentService(), new ChatGPTService());
            final boolean[] approve = {false};
            controller.setApp(new AppController() {
                @Override public void setStatus(String message) {}
                @Override public void styleDialog(Dialog<?> dialog) {
                    dialog.setOnShown(event -> Platform.runLater(() -> ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(approve[0] ? 1 : 0))).fire()));
                }
            });
            var deletion = HistoryController.class.getDeclaredMethod("deleteMoodEntry", com.aimoodchecker.model.MoodEntry.class);
            deletion.setAccessible(true);
            deletion.invoke(controller, entry);
            check(repository.getAllMoodEntries().size() == initial, "Keeping a journal entry cancels deletion");
            approve[0] = true;
            deletion.invoke(controller, entry);
            check(repository.getMoodEntryById(entry.getId()).isEmpty(), "Confirmed journal deletion persists");
            check(((TableView<?>) root.lookup("#historyTable")).getItems().size() == initial - 1, "Journal refreshes immediately after deletion");
            return null;
        });
    }

    private static void checkSaveFailure() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        int initial = EntryRepository.getInstance().getAllMoodEntries().size();
        try (Connection lock = DBConnection.getConnection(); Statement statement = lock.createStatement()) {
            statement.execute("BEGIN EXCLUSIVE");
            Parent form = fx(() -> {
                FXMLLoader loader = new FXMLLoader(UiSmokeCheck.class.getResource("/ComposeView.fxml"));
                Parent root = loader.load();
            new Scene(root, 900, 800);
            root.applyCss();
            root.layout();
                ComposeController controller = loader.getController();
                controller.setApp(new AppController() {
                    @Override public void setBusy(boolean busy) { if (!busy) finished.countDown(); }
                    @Override public void setStatus(String message) {}
                });
                controller.init(EntryRepository.getInstance(), new SentimentService(), new ChatGPTService() {
                    @Override public String getMoodCoaching(String mood, String description, EntryRepository repo) { throw new AssertionError("Failed save must not request coaching"); }
                });
                ((ToggleButton) root.lookup("#neutralButton")).fire();
                ((TextArea) root.lookup("#moodText")).setText("Synthetic draft retained after a failed save");
                ((Button) root.lookup("#saveBtn")).fire();
                return root;
            });
            check(finished.await(15, TimeUnit.SECONDS), "Locked database returns a bounded save failure");
            fx(() -> {
                check(form.lookup("#errorLabel").isVisible(), "Save failure displays inline error");
                check(!form.lookup("#saveBtn").isDisabled(), "Save failure enables retry");
                check(((TextArea) form.lookup("#moodText")).getText().equals("Synthetic draft retained after a failed save"), "Save failure preserves reflection text");
                return null;
            });
            statement.execute("ROLLBACK");
        }
        check(EntryRepository.getInstance().getAllMoodEntries().size() == initial, "Failed save adds no journal entry");
    }

    private static void saveWithFakeCoaching() throws Exception {
        CountDownLatch coachStarted = new CountDownLatch(1), releaseCoach = new CountDownLatch(1), returnedHome = new CountDownLatch(1);
        int initial = EntryRepository.getInstance().getAllMoodEntries().size();
        var form = fx(() -> {
            FXMLLoader loader = new FXMLLoader(UiSmokeCheck.class.getResource("/ComposeView.fxml"));
            Parent root = loader.load();
            new Scene(root, 900, 800).getStylesheets().add(UiSmokeCheck.class.getResource("/styles.css").toExternalForm());
            root.applyCss();
            root.layout();
            ComposeController controller = loader.getController();
            controller.setApp(new AppController() {
                @Override public void setBusy(boolean busy) {}
                @Override public void setStatus(String message) {}
                @Override public void styleDialog(Dialog<?> dialog) {
                    dialog.setOnShown(event -> Platform.runLater(() -> ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst())).fire()));
                }
                @Override public void goHome() { returnedHome.countDown(); }
            });
            controller.init(EntryRepository.getInstance(), new SentimentService(), new ChatGPTService() {
                @Override public String getMoodCoaching(String mood, String description, EntryRepository repo) {
                    coachStarted.countDown();
                    try { if (!releaseCoach.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                    catch (InterruptedException error) { throw new RuntimeException(error); }
                    return "Synthetic coaching for UI verification. No network request was made.";
                }
            });
            ((ToggleButton) root.lookup("#neutralButton")).fire();
            ((TextArea) root.lookup("#moodText")).setText("Synthetic test entry: a quiet, ordinary day.");
            ((Button) root.lookup("#saveBtn")).fire();
            check(root.lookup("#saveBtn").isDisabled(), "Save is disabled while saving");
            ((Button) root.lookup("#saveBtn")).fire();
            return root;
        });
        check(coachStarted.await(10, TimeUnit.SECONDS), "Coaching starts after save");
        fx(() -> { check(((Label) form.lookup("#saveStatus")).getText().startsWith("Saved"), "Visible saved/progress feedback"); return null; });
        check(EntryRepository.getInstance().getAllMoodEntries().size() == initial + 1, "Duplicate save prevented");
        releaseCoach.countDown();
        check(returnedHome.await(10, TimeUnit.SECONDS), "Save/reflection flow returns to overview");
    }

    private record Fixture(Parent root, AppController app) {}
    private static Fixture load(int width, int height) throws Exception {
        FXMLLoader loader = new FXMLLoader(UiSmokeCheck.class.getResource("/App.fxml"));
        loader.setControllerFactory(type -> {
            if (type == AppController.class) return new AppController(new ChatGPTService() {
                @Override public String getMoodCoaching(String mood, String description, EntryRepository repo) {
                    return "This is the isolated design preview. Your sample check-in was saved to its temporary journal. No AI service was called.\n\nTake a moment to notice what helped today, and what you might make space for tomorrow.";
                }
            });
            throw new IllegalArgumentException("Unexpected shell controller " + type);
        });
        Parent root = loader.load();
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(UiSmokeCheck.class.getResource("/styles.css").toExternalForm());
        root.resize(width, height);
        root.applyCss();
        root.layout();
        return new Fixture(root, loader.getController());
    }
    private static void snapshot(Parent root, String name) throws Exception {
        root.applyCss();
        root.layout();
        var pixels = root.snapshot(null, null);
        var output = new java.awt.image.BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
        ImageIO.write(output, "png", OUTPUT.resolve(name + ".png").toFile());
    }
    private static void seed() throws SQLException {
        try (Connection connection = DBConnection.getConnection(); PreparedStatement insert = connection.prepareStatement("INSERT INTO mood_entries(date, mood_type, description, sentiment_score) VALUES(?, ?, ?, ?)")) {
            String[] moods = {"Happy", "Neutral", "Sad", "Happy", "Neutral"};
            String[] notes = {"Made time for a long walk and caught up with a friend. A good reminder to slow down.", "An ordinary day. Finished a few things and enjoyed a quiet evening.", "A difficult morning, but writing about it helped me make a little room for it.", "Finally finished the project I have been working on. Feeling proud of the small steps.", "A longer reflection for layout verification. ".repeat(18)};
            for (int index = 0; index < moods.length; index++) {
                insert.setString(1, LocalDate.now().minusDays(index).toString());
                insert.setString(2, moods[index]);
                insert.setString(3, notes[index]);
                insert.setDouble(4, new double[]{0.8, 0.5, 0.2, 0.8, 0.5}[index]);
                insert.executeUpdate();
            }
        }
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
    private static void check(boolean passed, String name) {
        if (!passed) throw new AssertionError(name);
        checks++;
        System.out.println("PASS: " + name);
    }
}
