package controllers.studysession;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.studysession.StudySession;
import services.studysession.StudySessionService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class StudySessionController implements Initializable {

    @FXML private TableView<StudySession> sessionTable;
    @FXML private TableColumn<StudySession, Integer> colId;
    @FXML private TableColumn<StudySession, String> colCourse;
    @FXML private TableColumn<StudySession, String> colPlanning;
    @FXML private TableColumn<StudySession, String> colStarted;
    @FXML private TableColumn<StudySession, Integer> colDuration;
    @FXML private TableColumn<StudySession, Integer> colXP;
    @FXML private TableColumn<StudySession, String> colBurnout;
    @FXML private TableColumn<StudySession, String> colMood;
    @FXML private TableColumn<StudySession, String> colEnergy;

    @FXML private ComboBox<String> filterBurnout;
    @FXML private ComboBox<String> filterMood;
    @FXML private ComboBox<String> filterEnergy;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final StudySessionService sessionService = new StudySessionService();
    private final ObservableList<StudySession> data = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCourse.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCourseNameCache()));
        colPlanning.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPlanningTitleCache()));
        colStarted.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStartedAt() != null
                        ? d.getValue().getStartedAt().toString().replace("T", " ") : ""));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colXP.setCellValueFactory(new PropertyValueFactory<>("xpEarned"));
        colBurnout.setCellValueFactory(new PropertyValueFactory<>("burnoutRisk"));
        colMood.setCellValueFactory(new PropertyValueFactory<>("mood"));
        colEnergy.setCellValueFactory(new PropertyValueFactory<>("energyLevel"));

        colBurnout.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "LOW"      -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "MODERATE" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "HIGH"     -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        sessionTable.setItems(data);
    }

    private void setupFilters() {
        filterBurnout.getItems().addAll("", "LOW", "MODERATE", "HIGH");
        filterBurnout.setValue("");
        filterMood.getItems().addAll("", "positive", "neutral", "negative");
        filterMood.setValue("");
        filterEnergy.getItems().addAll("", "low", "medium", "high");
        filterEnergy.setValue("");

        filterBurnout.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterMood.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterEnergy.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (dateFrom != null) dateFrom.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (dateTo != null) dateTo.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void loadData() { applyFilters(); }

    @FXML
    private void applyFilters() {
        try {
            String burnout = filterBurnout.getValue();
            String mood = filterMood.getValue();
            String energy = filterEnergy.getValue();
            LocalDateTime from = (dateFrom != null && dateFrom.getValue() != null)
                    ? dateFrom.getValue().atStartOfDay() : null;
            LocalDateTime to = (dateTo != null && dateTo.getValue() != null)
                    ? dateTo.getValue().atTime(23, 59, 59) : null;

            List<StudySession> sessions = sessionService.findByFilters(
                    null,
                    (burnout == null || burnout.isEmpty()) ? null : burnout,
                    (mood == null || mood.isEmpty()) ? null : mood,
                    (energy == null || energy.isEmpty()) ? null : energy,
                    from, to
            );
            data.setAll(sessions);
            if (statsLabel != null)
                statsLabel.setText("Showing " + sessions.size() + " session(s)");
        } catch (SQLException e) {
            setStatus("⚠ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleNew() { openSessionForm(null); }

    @FXML
    private void handleEdit() {
        StudySession sel = sessionTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Please select a session to edit."); return; }
        openSessionForm(sel);
    }

    @FXML
    private void handleDelete() {
        StudySession sel = sessionTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Please select a session to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this study session?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                sessionService.delete(sel.getId());
                setStatus("Deleted.", false);
                loadData();
            } catch (SQLException e) {
                showError(e.getMessage());
            }
        }
    }

    @FXML
    private void handleStats() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StatsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("📊 Study Session Statistics");
            stage.setScene(new Scene(root, 900, 700));
            stage.show();
        } catch (IOException e) {
            showError("Cannot open stats: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() { loadData(); setStatus("Refreshed.", false); }

    private void openSessionForm(StudySession s) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
            Parent root = loader.load();
            StudySessionFormController ctrl = loader.getController();
            ctrl.initData(s, this::loadData);

            Stage stage = new Stage();
            stage.setTitle(s == null ? "New Study Session" : "Edit Study Session");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open form: " + e.getMessage());
        }
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
    }

    private void showError(String msg) {
        setStatus("⚠ " + msg, true);
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
