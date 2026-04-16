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
import models.studysession.Course;
import models.studysession.Planning;
import services.studysession.CourseService;
import services.studysession.PlanningService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class PlanningController implements Initializable {

    @FXML private TableView<Planning> planningTable;
    @FXML private TableColumn<Planning, String> colCourse;
    @FXML private TableColumn<Planning, String> colTitle;
    @FXML private TableColumn<Planning, String> colDate;
    @FXML private TableColumn<Planning, String> colTime;
    @FXML private TableColumn<Planning, Integer> colDuration;
    @FXML private TableColumn<Planning, String> colStatus;
    @FXML private TableColumn<Planning, String> colReminder;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterStatus;
    @FXML private ComboBox<Course> filterCourse;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;
    @FXML private Label pageTitleLabel;

    private final PlanningService planningService = new PlanningService();
    private final CourseService courseService = new CourseService();
    private final ObservableList<Planning> data = FXCollections.observableArrayList();
    private Course preselectedCourse = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        autoMarkMissed();
        loadData();
    }

    /** Called when opened from CourseDetail to pre-filter by course */
    public void initWithCourse(Course course) {
        this.preselectedCourse = course;
        if (pageTitleLabel != null)
            pageTitleLabel.setText("📅 Plannings — " + course.getCourseName());
        if (filterCourse != null) filterCourse.setValue(course);
        loadData();
    }

    private void setupColumns() {
        colCourse.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCourseNameCache()));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getScheduledDate() != null ? d.getValue().getScheduledDate().toString() : ""));
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getScheduledTime() != null ? d.getValue().getScheduledTime().toString() : ""));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("plannedDuration"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colReminder.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().isReminder() ? "🔔 Yes" : "—"));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "SCHEDULED" -> "-fx-text-fill: #3498db; -fx-font-weight: bold;";
                    case "COMPLETED" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "MISSED"    -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "CANCELLED" -> "-fx-text-fill: #95a5a6; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        planningTable.setItems(data);
    }

    private void setupFilters() {
        filterStatus.getItems().addAll("", "SCHEDULED", "COMPLETED", "MISSED", "CANCELLED");
        filterStatus.setValue("");

        try {
            if (filterCourse != null) {
                List<Course> courses = courseService.findAll();
                filterCourse.getItems().add(null); // "All courses"
                filterCourse.getItems().addAll(courses);
                filterCourse.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(Course c, boolean empty) {
                        super.updateItem(c, empty);
                        setText(empty || c == null ? "— All Courses —" : c.getCourseName());
                    }
                });
                filterCourse.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(Course c, boolean empty) {
                        super.updateItem(c, empty);
                        setText(empty || c == null ? "— All Courses —" : c.getCourseName());
                    }
                });
            }
        } catch (SQLException e) {
            System.err.println("Failed to load courses for filter: " + e.getMessage());
        }

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterStatus.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (filterCourse != null) filterCourse.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (dateFrom != null) dateFrom.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (dateTo != null) dateTo.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void autoMarkMissed() {
        try { planningService.autoMarkMissed(); }
        catch (SQLException e) { System.err.println("Auto-mark missed failed: " + e.getMessage()); }
    }

    private void loadData() {
        applyFilters();
    }

    @FXML
    private void applyFilters() {
        try {
            String status = filterStatus.getValue();
            String search = searchField != null ? searchField.getText() : null;
            LocalDate from = dateFrom != null ? dateFrom.getValue() : null;
            LocalDate to = dateTo != null ? dateTo.getValue() : null;
            Course course = filterCourse != null ? filterCourse.getValue() : preselectedCourse;

            List<Planning> plannings = planningService.findByFilters(
                    (status == null || status.isEmpty()) ? null : status,
                    from, to,
                    course != null ? course.getId() : null,
                    (search == null || search.isEmpty()) ? null : search
            );

            data.setAll(plannings);
            if (statsLabel != null)
                statsLabel.setText("Showing " + plannings.size() + " planning(s)");
        } catch (SQLException e) {
            if (statusLabel != null) {
                statusLabel.setText("⚠ " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            }
        }
    }

    @FXML
    private void handleNew() {
        Course course = (filterCourse != null && filterCourse.getValue() != null)
                ? filterCourse.getValue() : preselectedCourse;
        openPlanningForm(null, course != null ? course.getId() : 0,
                course != null ? course.getCourseName() : "");
    }

    @FXML
    private void handleEdit() {
        Planning selected = planningTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a planning session to edit."); return; }
        openPlanningForm(selected, selected.getCourseId(), selected.getCourseNameCache());
    }

    @FXML
    private void handleDelete() {
        Planning selected = planningTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a planning session to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete planning \"" + selected.getTitle() + "\"?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                planningService.delete(selected.getId());
                setStatus("Deleted successfully.", false);
                loadData();
            } catch (SQLException e) {
                showError(e.getMessage());
            }
        }
    }

    @FXML
    private void handleMarkComplete() {
        Planning selected = planningTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a planning session."); return; }
        if (Planning.STATUS_COMPLETED.equals(selected.getStatus())) {
            showInfo("This session is already completed.");
            return;
        }
        try {
            planningService.updateStatus(selected.getId(), Planning.STATUS_COMPLETED);
            setStatus("Marked as COMPLETED.", false);
            loadData();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleMarkCancelled() {
        Planning selected = planningTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a planning session."); return; }
        try {
            planningService.updateStatus(selected.getId(), Planning.STATUS_CANCELLED);
            setStatus("Marked as CANCELLED.", false);
            loadData();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleStartSession() {
        Planning selected = planningTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a planning session."); return; }
        if (!Planning.STATUS_SCHEDULED.equals(selected.getStatus())) {
            showInfo("Only SCHEDULED sessions can be started.");
            return;
        }
        openStudySessionForm(selected);
    }

    @FXML
    private void handleRefresh() {
        autoMarkMissed();
        loadData();
        setStatus("Refreshed.", false);
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    private void openPlanningForm(Planning p, int courseId, String courseName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningForm.fxml"));
            Parent root = loader.load();
            PlanningFormController ctrl = loader.getController();
            ctrl.initData(p, courseId, courseName, this::loadData);

            Stage stage = new Stage();
            stage.setTitle(p == null ? "New Planning" : "Edit Planning");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open form: " + e.getMessage());
        }
    }

    private void openStudySessionForm(Planning planning) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
            Parent root = loader.load();
            StudySessionFormController ctrl = loader.getController();
            ctrl.initForPlanning(planning, this::loadData);

            Stage stage = new Stage();
            stage.setTitle("Complete Session — " + planning.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open session form: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────

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