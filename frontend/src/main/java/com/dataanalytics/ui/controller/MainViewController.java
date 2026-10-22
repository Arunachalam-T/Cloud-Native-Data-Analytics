package com.dataanalytics.ui.controller;

import com.dataanalytics.ui.service.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class MainViewController implements Initializable {

    // ── Sidebar ──────────────────────────────────────
    @FXML private VBox sidebar;
    @FXML private Button btnDashboard;
    @FXML private Button btnUpload;
    @FXML private Button btnDatasets;
    @FXML private Button btnSettings;

    // ── Content Area ─────────────────────────────────
    @FXML private StackPane contentArea;
    @FXML private Label lblPageTitle;
    @FXML private VBox viewDatasets;
    @FXML private ScrollPane viewSettings;

    // ── Settings Fields ───────────────────────────────
    @FXML private TextField txtBackendUrl;
    @FXML private TextField txtApiKey;
    @FXML private Label lblConnectionResult;

    // ── Dataset Table ─────────────────────────────────
    @FXML private TableView<JsonNode> tblDatasets;
    @FXML private TableColumn<JsonNode, String> colName;
    @FXML private TableColumn<JsonNode, String> colStatus;
    @FXML private TableColumn<JsonNode, String> colRows;
    @FXML private TableColumn<JsonNode, String> colCols;
    @FXML private TableColumn<JsonNode, String> colFormat;
    @FXML private TableColumn<JsonNode, String> colCreated;

    // ── Status Bar ────────────────────────────────────
    @FXML private Label lblStatus;
    @FXML private ProgressIndicator progressIndicator;

    private final ObservableList<JsonNode> datasetList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTable();
        loadDashboard();
        refreshDatasets();
    }

    private void configureTable() {
        colName.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("name").asText("-")));
        colStatus.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("status").asText("-")));
        colRows.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("rowCount").asText("...")));
        colCols.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("columnCount").asText("...")));
        colFormat.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("fileFormat").asText("-")));
        colCreated.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().path("createdAt").asText("-")));

        // Color-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(switch (item) {
                        case "READY"      -> "-fx-text-fill: #4ade80;";
                        case "PROCESSING" -> "-fx-text-fill: #facc15;";
                        case "ERROR"      -> "-fx-text-fill: #f87171;";
                        default           -> "-fx-text-fill: #94a3b8;";
                    });
                }
            }
        });

        tblDatasets.setItems(datasetList);

        // Double-click to open detail view
        tblDatasets.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                JsonNode selected = tblDatasets.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openDatasetDetail(selected.path("id").asLong());
                }
            }
        });
    }

    @FXML
    private void handleUploadAction() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Dataset File");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.xlsx", "*.xls", "*.tsv"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = chooser.showOpenDialog(contentArea.getScene().getWindow());
        if (file != null) {
            uploadFile(file);
        }
    }

    private void uploadFile(File file) {
        setStatus("Uploading " + file.getName() + "...", true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return ApiClient.getInstance().uploadFile(file, file.getName(), null);
            }
        };

        task.setOnSucceeded(e -> {
            setStatus("Upload complete! Dataset is being processed.", false);
            refreshDatasets();
        });

        task.setOnFailed(e -> {
            setStatus("Upload failed: " + task.getException().getMessage(), false);
            showAlert(Alert.AlertType.ERROR, "Upload Failed", task.getException().getMessage());
        });

        new Thread(task, "upload-thread").start();
    }

    @FXML
    public void refreshDatasets() {
        setStatus("Loading datasets...", true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return ApiClient.getInstance().getRaw("/api/v1/datasets?page=0&size=100");
            }
        };

        task.setOnSucceeded(e -> {
            try {
                JsonNode root = ApiClient.getInstance().getObjectMapper().readTree(task.getValue());
                datasetList.clear();
                root.path("content").forEach(datasetList::add);
                setStatus("Loaded " + datasetList.size() + " datasets.", false);
            } catch (Exception ex) {
                setStatus("Failed to parse datasets: " + ex.getMessage(), false);
            }
        });

        task.setOnFailed(e -> setStatus("Failed to load datasets: " + task.getException().getMessage(), false));

        new Thread(task, "refresh-thread").start();
    }

    private void openDatasetDetail(Long datasetId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/DatasetDetailView.fxml")
            );
            Parent root = loader.load();
            DatasetDetailController ctrl = loader.getController();
            ctrl.loadDataset(datasetId);

            Stage stage = new Stage();
            stage.setTitle("Dataset Details — ID " + datasetId);
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(
                getClass().getResource("/css/dark-theme.css").toExternalForm()
            );
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to open dataset detail: " + e.getMessage());
        }
    }

    private void loadDashboard() {
        lblPageTitle.setText("Dashboard");
    }

    @FXML
    public void handleShowDashboard() {
        setActiveNav(btnDashboard);
        lblPageTitle.setText("Dashboard");
        if (viewDatasets != null) {
            viewDatasets.setVisible(true);
            viewDatasets.setManaged(true);
        }
        if (viewSettings != null) {
            viewSettings.setVisible(false);
            viewSettings.setManaged(false);
        }
        refreshDatasets();
    }

    @FXML
    public void handleShowDatasets() {
        setActiveNav(btnDatasets);
        lblPageTitle.setText("All Datasets (" + datasetList.size() + ")");
        if (viewDatasets != null) {
            viewDatasets.setVisible(true);
            viewDatasets.setManaged(true);
        }
        if (viewSettings != null) {
            viewSettings.setVisible(false);
            viewSettings.setManaged(false);
        }
        refreshDatasets();
    }

    @FXML
    public void handleShowSettings() {
        setActiveNav(btnSettings);
        lblPageTitle.setText("Settings & System Configuration");
        if (viewDatasets != null) {
            viewDatasets.setVisible(false);
            viewDatasets.setManaged(false);
        }
        if (viewSettings != null) {
            viewSettings.setVisible(true);
            viewSettings.setManaged(true);
        }
    }

    private void setActiveNav(Button active) {
        for (Button btn : java.util.List.of(btnDashboard, btnDatasets, btnSettings)) {
            if (btn != null) {
                btn.getStyleClass().remove("nav-btn-active");
            }
        }
        if (active != null && !active.getStyleClass().contains("nav-btn-active")) {
            active.getStyleClass().add("nav-btn-active");
        }
    }

    @FXML
    private void handleTestConnection() {
        if (lblConnectionResult != null) {
            lblConnectionResult.setText("Testing connection...");
            lblConnectionResult.setStyle("-fx-text-fill: #facc15;");
        }

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String response = ApiClient.getInstance().getRaw("/actuator/health");
                return response != null && response.contains("UP");
            }
        };

        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                if (lblConnectionResult != null) {
                    lblConnectionResult.setText("✓ Connected successfully! Status: UP");
                    lblConnectionResult.setStyle("-fx-text-fill: #4ade80;");
                }
            } else {
                if (lblConnectionResult != null) {
                    lblConnectionResult.setText("⚠ Connected but status not UP");
                    lblConnectionResult.setStyle("-fx-text-fill: #facc15;");
                }
            }
        });

        task.setOnFailed(e -> {
            if (lblConnectionResult != null) {
                lblConnectionResult.setText("✗ Connection failed: " + task.getException().getMessage());
                lblConnectionResult.setStyle("-fx-text-fill: #f87171;");
            }
        });

        new Thread(task).start();
    }

    @FXML
    private void handleSaveSettings() {
        if (txtBackendUrl != null) {
            String newUrl = txtBackendUrl.getText().trim();
            if (!newUrl.isEmpty()) {
                System.setProperty("API_BASE_URL", newUrl);
            }
        }
        if (txtApiKey != null) {
            String newKey = txtApiKey.getText().trim();
            if (!newKey.isEmpty()) {
                System.setProperty("API_KEY", newKey);
            }
        }
        if (lblConnectionResult != null) {
            lblConnectionResult.setText("✓ Settings saved for current session.");
            lblConnectionResult.setStyle("-fx-text-fill: #4ade80;");
        }
    }

    @FXML
    private void handleDeleteSelected() {
        JsonNode selected = tblDatasets.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete dataset '" + selected.path("name").asText() + "'?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                long id = selected.path("id").asLong();
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        ApiClient.getInstance().delete("/api/v1/datasets/" + id);
                        return null;
                    }
                };
                task.setOnSucceeded(e -> refreshDatasets());
                task.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "Error",
                    "Delete failed: " + task.getException().getMessage()));
                new Thread(task).start();
            }
        });
    }

    private void setStatus(String message, boolean busy) {
        Platform.runLater(() -> {
            lblStatus.setText(message);
            progressIndicator.setVisible(busy);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setTitle(title);
            alert.showAndWait();
        });
    }
}
