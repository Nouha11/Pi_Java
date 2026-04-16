package controllers.studysession;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import models.studysession.StudySession;
import services.studysession.StudySessionService;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

/**
 * User-facing study session history — read-only.
 * No edit or delete actions exposed.
 */
public class UserSessionController implements Initializable {

    @FXML private TableView<StudySession> sessionTable;
    @FXML private TableColumn<StudySession, String>  colCourse;
    @FXML private TableColumn<StudySession, String>  colPlanning;
    @FXML private TableColumn<StudySession, String>  colStarted;
    @FXML private TableColumn<StudySession, Integer> colDuration;
    @FXML private TableColumn<StudySession, Integer> colXP;
    @FXML private TableColumn<StudySession, String>  colBurnout;
    @FXML private TableColumn<StudySession, String>  colMood;
    @FXML private TableColumn<StudySession, String>  colEnergy;

    @FXML private ComboBox<String> filterBurnout;
    @FXML private ComboBox<String> filterMood;
    @FXML private ComboBox<String> filterEnergy;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final StudySessionService sessionService = new StudySessionService();
    private final ObservableList<StudySession> data  = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
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
                    case "LOW"      -> "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
                    case "MODERATE" -> "-fx-text-fill: #f59e0b; -fx-font-weight: bold;";
                    case "HIGH"     -> "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
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
        dateFrom.valueProperty().addListener((obs, o, n) -> applyFilters());
        dateTo.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void loadData() { applyFilters(); }

    @FXML
    private void applyFilters() {
        try {
            String burnout = filterBurnout.getValue();
            String mood    = filterMood.getValue();
            String energy  = filterEnergy.getValue();
            LocalDateTime from = dateFrom.getValue() != null ? dateFrom.getValue().atStartOfDay() : null;
            LocalDateTime to   = dateTo.getValue()   != null ? dateTo.getValue().atTime(23, 59, 59) : null;

            List<StudySession> sessions = sessionService.findByFilters(
                    null,
                    (burnout == null || burnout.isEmpty()) ? null : burnout,
                    (mood    == null || mood.isEmpty())    ? null : mood,
                    (energy  == null || energy.isEmpty())  ? null : energy,
                    from, to
            );
            data.setAll(sessions);
            statsLabel.setText("Showing " + sessions.size() + " session(s)");
        } catch (SQLException e) {
            setStatus("⚠ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleRefresh() {
        loadData();
        setStatus("Refreshed.", false);
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #16a34a;");
    }
}
