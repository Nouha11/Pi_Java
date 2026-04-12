package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.studysession.Course;
import models.studysession.Planning;
import services.studysession.PlanningService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class CourseDetailController implements Initializable {

    @FXML private Label lblCourseName;
    @FXML private Label lblCategory;
    @FXML private Label lblDifficulty;
    @FXML private Label lblStatus;
    @FXML private Label lblDuration;
    @FXML private Label lblProgress;
    @FXML private Label lblPublished;
    @FXML private TextArea txtDescription;
    @FXML private TableView<Planning> planningTable;
    @FXML private TableColumn<Planning, String> colTitle;
    @FXML private TableColumn<Planning, String> colDate;
    @FXML private TableColumn<Planning, String> colTime;
    @FXML private TableColumn<Planning, Integer> colDuration;
    @FXML private TableColumn<Planning, String> colPlanStatus;
    @FXML private Label lblPlanCount;

    private final PlanningService planningService = new PlanningService();
    private Course course;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDate.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getScheduledDate() != null
                                ? d.getValue().getScheduledDate().toString() : ""));
        colTime.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getScheduledTime() != null
                                ? d.getValue().getScheduledTime().toString() : ""));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("plannedDuration"));
        colPlanStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colPlanStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "SCHEDULED"  -> "-fx-text-fill: #3498db; -fx-font-weight: bold;";
                    case "COMPLETED"  -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "MISSED"     -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "CANCELLED"  -> "-fx-text-fill: #95a5a6; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });
    }

    public void initData(Course c) {
        this.course = c;
        lblCourseName.setText(c.getCourseName());
        lblCategory.setText(c.getCategory());
        lblDifficulty.setText(c.getDifficulty());
        lblStatus.setText(c.getStatus());
        lblDuration.setText(c.getEstimatedDuration() + " min");
        lblProgress.setText(c.getProgress() + "%");
        lblPublished.setText(c.isPublished() ? "✅ Published" : "❌ Draft");
        txtDescription.setText(c.getDescription() != null ? c.getDescription() : "No description.");
        loadPlannings();
    }

    private void loadPlannings() {
        try {
            List<Planning> plannings = planningService.findByCourse(course.getId());
            planningTable.setItems(FXCollections.observableArrayList(plannings));
            lblPlanCount.setText(plannings.size() + " planning session(s)");
        } catch (SQLException e) {
            lblPlanCount.setText("Error loading plannings.");
        }
    }

    @FXML
    private void handleAddPlanning() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningForm.fxml"));
            Parent root = loader.load();
            PlanningFormController ctrl = loader.getController();
            ctrl.initData(null, course.getId(), course.getCourseName(), this::loadPlannings);

            Stage stage = new Stage();
            stage.setTitle("New Planning Session");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open form: " + e.getMessage()).showAndWait();
        }
    }
}