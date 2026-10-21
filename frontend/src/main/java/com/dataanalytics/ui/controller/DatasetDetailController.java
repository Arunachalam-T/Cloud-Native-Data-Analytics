package com.dataanalytics.ui.controller;

import com.dataanalytics.ui.service.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.*;

public class DatasetDetailController implements Initializable {

    @FXML private Label lblDatasetName;
    @FXML private Label lblStatus;
    @FXML private Label lblRows;
    @FXML private Label lblCols;
    @FXML private Label lblFileSize;
    @FXML private Label lblFormat;

    // ── Tabs ──────────────────────────────────────────
    @FXML private TabPane tabPane;
    @FXML private Tab tabPreview;
    @FXML private Tab tabEda;
    @FXML private Tab tabQuality;
    @FXML private Tab tabCharts;

    // ── Preview Tab ───────────────────────────────────
    @FXML private TableView<List<String>> tblPreview;

    // ── EDA Tab ───────────────────────────────────────
    @FXML private TextArea txtEdaReport;

    // ── Quality Tab ───────────────────────────────────
    @FXML private TextArea txtQualityReport;
    @FXML private Label lblQualityScore;

    // ── Charts Tab ────────────────────────────────────
    @FXML private VBox chartsContainer;

    // ── Status ────────────────────────────────────────
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label lblStatusBar;

    private Long datasetId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, newTab) -> {
            if (newTab == tabEda) loadEda();
            else if (newTab == tabQuality) loadQuality();
            else if (newTab == tabCharts) loadCharts();
        });
    }

    public void loadDataset(Long id) {
        this.datasetId = id;
        setStatus("Loading dataset...", true);

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return ApiClient.getInstance().get(
                    "/api/v1/datasets/" + id, JsonNode.class
                );
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode data = task.getValue();
            lblDatasetName.setText(data.path("name").asText("Unknown"));
            lblStatus.setText(data.path("status").asText("-"));
            lblRows.setText(String.valueOf(data.path("rowCount").asLong(0)));
            lblCols.setText(String.valueOf(data.path("columnCount").asInt(0)));
            lblFormat.setText(data.path("fileFormat").asText("-"));
            long bytes = data.path("fileSizeBytes").asLong(0);
            lblFileSize.setText(formatBytes(bytes));
            setStatus("Ready", false);
            loadPreview();
        });

        task.setOnFailed(e -> setStatus("Error: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private void loadPreview() {
        setStatus("Loading preview...", true);
        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return ApiClient.getInstance().get(
                    "/api/v1/datasets/" + datasetId + "/preview?rows=50", JsonNode.class
                );
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode preview = task.getValue();
            tblPreview.getColumns().clear();

            List<String> headers = new ArrayList<>();
            preview.path("headers").forEach(h -> headers.add(h.asText()));

            for (int i = 0; i < headers.size(); i++) {
                final int colIndex = i;
                TableColumn<List<String>, String> col = new TableColumn<>(headers.get(i));
                col.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                        colIndex < data.getValue().size() ? data.getValue().get(colIndex) : ""
                    )
                );
                col.setMinWidth(100);
                tblPreview.getColumns().add(col);
            }

            List<List<String>> rows = new ArrayList<>();
            preview.path("rows").forEach(row -> {
                List<String> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(cell.asText("")));
                rows.add(cells);
            });

            tblPreview.setItems(FXCollections.observableArrayList(rows));
            setStatus("Preview loaded (" + rows.size() + " rows)", false);
        });

        task.setOnFailed(e -> setStatus("Preview failed: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private void loadEda() {
        if (txtEdaReport.getText() != null && !txtEdaReport.getText().isEmpty()) return;
        setStatus("Generating EDA report...", true);

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return ApiClient.getInstance().get(
                    "/api/v1/datasets/" + datasetId + "/eda", JsonNode.class
                );
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode eda = task.getValue();
            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════════════\n");
            sb.append("  EXPLORATORY DATA ANALYSIS REPORT\n");
            sb.append("═══════════════════════════════════════════════\n\n");
            sb.append("Dataset:          ").append(eda.path("datasetName").asText()).append("\n");
            sb.append("Total Rows:       ").append(eda.path("rowCount").asLong()).append("\n");
            sb.append("Total Columns:    ").append(eda.path("columnCount").asInt()).append("\n");
            sb.append("Duplicate Rows:   ").append(eda.path("duplicateRows").asLong()).append("\n");
            sb.append("Missing Values:   ").append(eda.path("totalMissingValues").asLong())
              .append(" (").append(eda.path("missingValuePercentage").asDouble()).append("%)\n\n");

            sb.append("─── Column Analysis ───────────────────────────\n\n");
            eda.path("columns").forEach(col -> {
                sb.append("▶ ").append(col.path("columnName").asText())
                  .append(" [").append(col.path("dataType").asText()).append("]\n");
                sb.append("  Nulls:      ").append(col.path("nullCount").asLong())
                  .append(" (").append(col.path("nullPercentage").asDouble()).append("%)\n");
                sb.append("  Unique:     ").append(col.path("uniqueCount").asLong())
                  .append(" (").append(col.path("uniquePercentage").asDouble()).append("%)\n");

                if (col.has("mean")) {
                    sb.append("  Min/Max:    ").append(col.path("min").asDouble())
                      .append(" / ").append(col.path("max").asDouble()).append("\n");
                    sb.append("  Mean:       ").append(col.path("mean").asDouble()).append("\n");
                    sb.append("  Median:     ").append(col.path("median").asDouble()).append("\n");
                    sb.append("  Std Dev:    ").append(col.path("stdDev").asDouble()).append("\n");
                    sb.append("  Q1/Q3:      ").append(col.path("q1").asDouble())
                      .append(" / ").append(col.path("q3").asDouble()).append("\n");
                    if (col.has("outliers") && col.path("outliers").size() > 0) {
                        sb.append("  Outliers:   ").append(col.path("outliers").size()).append(" detected\n");
                    }
                }
                if (col.has("mostFrequentValue")) {
                    sb.append("  Top Value:  '").append(col.path("mostFrequentValue").asText())
                      .append("' (").append(col.path("mostFrequentCount").asLong()).append("x)\n");
                }
                sb.append("\n");
            });

            txtEdaReport.setText(sb.toString());
            setStatus("EDA report ready.", false);
        });

        task.setOnFailed(e -> setStatus("EDA failed: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private void loadQuality() {
        if (txtQualityReport.getText() != null && !txtQualityReport.getText().isEmpty()) return;
        setStatus("Running data quality checks...", true);

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return ApiClient.getInstance().get(
                    "/api/v1/datasets/" + datasetId + "/quality", JsonNode.class
                );
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode quality = task.getValue();
            double score = quality.path("overallQualityScore").asDouble();
            lblQualityScore.setText(String.format("Overall Score: %.1f / 100", score));
            lblQualityScore.setStyle(score >= 80 ? "-fx-text-fill: #4ade80;" :
                                    score >= 50 ? "-fx-text-fill: #facc15;" :
                                                  "-fx-text-fill: #f87171;");

            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════════════\n");
            sb.append("  DATA QUALITY REPORT\n");
            sb.append("═══════════════════════════════════════════════\n\n");
            sb.append("Dataset:       ").append(quality.path("datasetName").asText()).append("\n");
            sb.append("Quality Score: ").append(String.format("%.1f / 100", score)).append("\n");
            sb.append("Duplicate Rows:").append(quality.path("duplicateRowCount").asLong()).append("\n\n");

            if (quality.path("warnings").size() > 0) {
                sb.append("⚠  WARNINGS:\n");
                quality.path("warnings").forEach(w -> sb.append("   • ").append(w.asText()).append("\n"));
                sb.append("\n");
            }
            if (quality.path("recommendations").size() > 0) {
                sb.append("💡 RECOMMENDATIONS:\n");
                quality.path("recommendations").forEach(r -> sb.append("   • ").append(r.asText()).append("\n"));
                sb.append("\n");
            }

            sb.append("─── Column Quality ────────────────────────────\n\n");
            quality.path("columnQuality").forEach(col -> {
                sb.append("▶ ").append(col.path("columnName").asText())
                  .append(" — Score: ").append(String.format("%.1f", col.path("qualityScore").asDouble())).append("/100\n");
                sb.append("  Nulls:    ").append(col.path("nullCount").asLong())
                  .append(" (").append(col.path("nullPercentage").asDouble()).append("%)\n");
                sb.append("  Unique:   ").append(col.path("uniqueCount").asLong()).append("\n");
                if (col.path("hasOutliers").asBoolean()) {
                    sb.append("  Outliers: ").append(col.path("outlierCount").asLong()).append(" detected\n");
                }
                col.path("issues").forEach(issue -> sb.append("  ⚠ ").append(issue.asText()).append("\n"));
                sb.append("\n");
            });

            txtQualityReport.setText(sb.toString());
            setStatus("Quality check complete.", false);
        });

        task.setOnFailed(e -> setStatus("Quality check failed: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private void loadCharts() {
        if (!chartsContainer.getChildren().isEmpty()) return;
        setStatus("Generating charts...", true);

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return ApiClient.getInstance().get(
                    "/api/v1/datasets/" + datasetId + "/visualizations", JsonNode.class
                );
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode viz = task.getValue();
            chartsContainer.getChildren().clear();

            viz.path("charts").forEach(chart -> {
                String type = chart.path("chartType").asText();
                Chart fxChart = switch (type) {
                    case "HISTOGRAM", "BAR" -> buildBarChart(chart);
                    case "SCATTER" -> buildScatterChart(chart);
                    default -> null;
                };
                if (fxChart != null) {
                    fxChart.setPrefHeight(300);
                    chartsContainer.getChildren().add(fxChart);
                }
            });
            setStatus("Charts rendered.", false);
        });

        task.setOnFailed(e -> setStatus("Chart load failed: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private BarChart<String, Number> buildBarChart(JsonNode chartData) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(chartData.path("xLabel").asText());
        yAxis.setLabel(chartData.path("yLabel").asText());

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(chartData.path("title").asText());
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        chartData.path("dataPoints").forEach(dp -> {
            String label = dp.has("label") ? dp.path("label").asText()
                : String.valueOf(dp.path("x").asDouble());
            series.getData().add(new XYChart.Data<>(label, dp.path("count").asLong()));
        });
        chart.getData().add(series);
        return chart;
    }

    private ScatterChart<Number, Number> buildScatterChart(JsonNode chartData) {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(chartData.path("xLabel").asText());
        yAxis.setLabel(chartData.path("yLabel").asText());

        ScatterChart<Number, Number> chart = new ScatterChart<>(xAxis, yAxis);
        chart.setTitle(chartData.path("title").asText());
        chart.setLegendVisible(false);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        chartData.path("dataPoints").forEach(dp ->
            series.getData().add(new XYChart.Data<>(dp.path("x").asDouble(), dp.path("y").asDouble()))
        );
        chart.getData().add(series);
        return chart;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private void setStatus(String msg, boolean busy) {
        Platform.runLater(() -> {
            lblStatusBar.setText(msg);
            progressIndicator.setVisible(busy);
        });
    }
}
