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
import java.util.ResourceBundle;

/**
 * User-facing planning controller.
 * Allowed: create, mark complete/incomplete, start session.
 * Not allowed: edit, delete, cancel.
 */
public class UserPlanningController implements Initializable {

    @FXML private TableView<Planning> planningTable;
    @FXML private TableColumn<Planning, String>  colCourse;
    @FXML private TableColumn<Planning, String>  colTitle;
    @FXML private TableColumn<Planning, String>  colDate;
    @FXML private TableColumn<Planning, String>  colTime;
    @FXML private TableColumn<Planning, Integer> colDuration;
    @FXML private TableColumn<Planning, String>  colStatus;
    @FXML private TableColumn<Planning, String>  colReminder;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterStatus;
    @FXML private ComboBox<Course> filterCourse;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final PlanningService planningService = new PlanningService();
    private final CourseService   courseService   = new CourseService();
    private final ObservableList<Planning> data   = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        autoMarkMissed();
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
                    case "SCHEDULED" -> "-fx-text-fill: #4f46e5; -fx-font-weight: bold;";
                    case "COMPLETED" -> "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
                    case "MISSED"    -> "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
                    case "CANCELLED" -> "-fx-text-fill: #94a3b8; -fx-font-weight: bold;";
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
            List<Course> courses = courseService.findAll();
            filterCourse.getItems().add(null);
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
        } catch (SQLException e) {
            System.err.println("UserPlanningController: failed to load courses — " + e.getMessage());
        }

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterStatus.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterCourse.valueProperty().addListener((obs, o, n) -> applyFilters());
        dateFrom.valueProperty().addListener((obs, o, n) -> applyFilters());
        dateTo.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void autoMarkMissed() {
        try { planningService.autoMarkMissed(); }
        catch (SQLException e) { System.err.println("Auto-mark missed: " + e.getMessage()); }
    }

    private void loadData() { applyFilters(); }

    @FXML
    private void applyFilters() {
        try {
            String status = filterStatus.getValue();
            String search = searchField.getText();
            LocalDate from = dateFrom.getValue();
            LocalDate to   = dateTo.getValue();
            Course course  = filterCourse.getValue();

            List<Planning> plannings = planningService.findByFilters(
                    (status == null || status.isEmpty()) ? null : status,
                    from, to,
                    course != null ? course.getId() : null,
                    (search == null || search.isEmpty()) ? null : search
            );

            data.setAll(plannings);
            statsLabel.setText("Showing " + plannings.size() + " planning(s)");
        } catch (SQLException e) {
            setStatus("⚠ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleNew() {
        Course course = filterCourse.getValue();
        openPlanningForm(null, course != null ? course.getId() : 0,
                course != null ? course.getCourseName() : "");
    }

    @FXML
    private void handleMarkComplete() {
        Planning sel = planningTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Please select a planning session."); return; }
        if (Planning.STATUS_COMPLETED.equals(sel.getStatus())) {
            showInfo("This session is already completed."); return;
        }
        try {
            planningService.updateStatus(sel.getId(), Planning.STATUS_COMPLETED);
            setStatus("Marked as COMPLETED.", false);
            loadData();
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    @FXML
    private void handleMarkIncomplete() {
        Planning sel = planningTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Please select a planning session."); return; }
        try {
            planningService.updateStatus(sel.getId(), Planning.STATUS_SCHEDULED);
            setStatus("Marked as SCHEDULED.", false);
            loadData();
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    @FXML
    private void handleStartSession() {
        Planning sel = planningTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Please select a planning session."); return; }
        if (!Planning.STATUS_SCHEDULED.equals(sel.getStatus())) {
            showInfo("Only SCHEDULED sessions can be started."); return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
            Parent root = loader.load();
            StudySessionFormController ctrl = loader.getController();
            ctrl.initForPlanning(sel, this::loadData);
            Stage stage = new Stage();
            stage.setTitle("Complete Session — " + sel.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) { showError("Cannot open session form: " + e.getMessage()); }
    }

    @FXML
    private void handleRefresh() {
        autoMarkMissed();
        loadData();
        setStatus("Refreshed.", false);
    }

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
        } catch (IOException e) { showError("Cannot open form: " + e.getMessage()); }
    }

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #16a34a;");
    }

    private void showError(String msg) {
        setStatus("⚠ " + msg, true);
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
