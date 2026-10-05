package com.aimoodchecker.controller;

import com.aimoodchecker.repository.EntryRepository;
import com.aimoodchecker.repository.EntryRepository.TrendPoint;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GraphController implements RoutedController {
    private AppController app;
    private final EntryRepository repo = EntryRepository.getInstance();
    @FXML private VBox chartContainer;
    @FXML private ComboBox<String> rangeCombo;
    @FXML private Label statusLabel;

    @Override public void setApp(AppController app) { this.app = app; }
    @FXML private void initialize() {
        rangeCombo.getItems().addAll("Last 7 days", "Last 30 days", "Last 90 days");
        rangeCombo.getSelectionModel().select(1);
        rangeCombo.valueProperty().addListener((observable, previous, next) -> rebuildChart());
        rebuildChart();
    }
    @FXML private void onRefresh() {
        rebuildChart();
        if (app != null) app.setStatus(statusLabel.getText());
    }
    private int selectedDays() {
        return switch (rangeCombo.getSelectionModel().getSelectedIndex()) { case 0 -> 7; case 2 -> 90; default -> 30; };
    }
    private void rebuildChart() {
        int days = selectedDays();
        try {
            List<TrendPoint> points = repo.findDailyAverages(days);
            if (points.isEmpty()) {
                showMessage("Your patterns will appear here.\nAdd a daily check-in, or choose a wider date range.");
                statusLabel.setText("No check-ins in this date range");
                return;
            }
            LineChart<Number, Number> chart = createChart(days);
            XYChart.Series<Number, Number> mood = new XYChart.Series<>();
            XYChart.Series<Number, Number> ai = new XYChart.Series<>();
            mood.setName("Your mood · solid line");
            ai.setName("Text tone · dashed line");
            for (TrendPoint point : points) {
                if (point.avgMood() != null) addPoint(mood, point.date(), (point.avgMood() - 1) / 4, "Your mood");
                if (point.avgAi() != null) addPoint(ai, point.date(), point.avgAi(), "Text tone");
            }
            chart.getData().add(mood);
            if (!ai.getData().isEmpty()) chart.getData().add(ai);
            chart.setAccessibleText("Mood trends over " + days + " days. " + points.size() + " days recorded. Daily numeric details follow below the chart.");
            chartContainer.getChildren().setAll(chart);
            // A keyboard-accessible textual equivalent for the chart, available without hover.
            javafx.scene.control.TextArea details = new javafx.scene.control.TextArea(points.stream()
                .map(point -> point.date() + "   Mood: " + (point.avgMood() == null ? "unavailable" : String.format("%.2f", (point.avgMood() - 1) / 4))
                    + "   Text tone: " + (point.avgAi() == null ? "unavailable" : String.format("%.2f", point.avgAi())))
                .collect(java.util.stream.Collectors.joining("\n")));
            details.setEditable(false);
            details.setPrefRowCount(4);
            details.setAccessibleText("Daily trend values");
            details.getStyleClass().add("journal-input");
            javafx.scene.control.TitledPane values = new javafx.scene.control.TitledPane("View daily values", details);
            values.setExpanded(false);
            values.setAnimated(false);
            chartContainer.getChildren().add(values);
            statusLabel.setText(points.size() == 1 ? "1 day recorded · keep checking in to see a pattern" : points.size() + " days recorded in the last " + days + " days");
        } catch (SQLException error) {
            showMessage("Your trends couldn't load.\nPlease try Refresh.");
            statusLabel.setText("Trends unavailable");
        }
    }
    private LineChart<Number, Number> createChart(int days) {
        LocalDate end = LocalDate.now();
        NumberAxis x = new NumberAxis(end.minusDays(days - 1).toEpochDay(), end.toEpochDay(), Math.max(1, (days - 1) / 6));
        NumberAxis y = new NumberAxis(0, 1, 0.25);
        x.setMinorTickVisible(false);
        y.setMinorTickVisible(false);
        x.setForceZeroInRange(false);
        x.setTickLabelFormatter(new NumberAxis.DefaultFormatter(x) {
            @Override public String toString(Number value) {
                return LocalDate.ofEpochDay(value.longValue()).format(DateTimeFormatter.ofPattern("MMM d"));
            }
        });
        y.setLabel("Daily average");
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.getStyleClass().add("trend-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        chart.setMinHeight(220);
        chart.setPrefHeight(340);
        chart.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return chart;
    }
    private void addPoint(XYChart.Series<Number, Number> series, LocalDate date, double value, String label) {
        XYChart.Data<Number, Number> point = new XYChart.Data<>(date.toEpochDay(), value);
        point.nodeProperty().addListener((observable, previous, node) -> {
            if (node != null) {
                String description = date + " · " + label + ": " + String.format("%.2f", value);
                Tooltip.install(node, new Tooltip(description));
            }
        });
        series.getData().add(point);
    }
    private void showMessage(String text) {
        Label message = new Label(text);
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.getStyleClass().add("empty-state");
        chartContainer.getChildren().setAll(message);
    }
}
