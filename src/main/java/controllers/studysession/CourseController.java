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
import services.studysession.CourseService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class CourseController implements Initializable {

    @FXML private TableView<Course> courseTable;
    @FXML private TableColumn<Course, Integer> colId;
    @FXML private TableColumn<Course, String> colName;
    @FXML private TableColumn<Course, String> colCategory;
    @FXML private TableColumn<Course, String> colDifficulty;
    @FXML private TableColumn<Course, String> colStatus;
    @FXML private TableColumn<Course, Integer> colDuration;
    @FXML private TableColumn<Course, Integer> colProgress;
    @FXML private TableColumn<Course, String> colPublished;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterDifficulty;
    @FXML private ComboBox<String> filterCategory;
    @FXML private ComboBox<String> filterStatus;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final CourseService courseService = new CourseService();
    private final ObservableList<Course> courseData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colDifficulty.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("estimatedDuration"));
        colProgress.setCellValueFactory(new PropertyValueFactory<>("progress"));
        colPublished.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().isPublished() ? "✅ Yes" : "❌ No"));

        // Color-code difficulty
        colDifficulty.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "BEGINNER"     -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "INTERMEDIATE" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "ADVANCED"     -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        // Progress bar cell
        colProgress.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item + "%");
                if (item >= 80) setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                else if (item >= 40) setStyle("-fx-text-fill: #f39c12;");
                else setStyle("-fx-text-fill: #e74c3c;");
            }
        });

        courseTable.setItems(courseData);

        // Double-click to open detail
        courseTable.setRowFactory(tv -> {
            TableRow<Course> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openCourseDetail(row.getItem());
            });
            return row;
        });
    }

    private void setupFilters() {
        filterDifficulty.getItems().addAll("", "BEGINNER", "INTERMEDIATE", "ADVANCED");
        filterDifficulty.setValue("");
        filterStatus.getItems().addAll("", "NOT_STARTED", "IN_PROGRESS", "COMPLETED");
        filterStatus.setValue("");

        // Auto-search on typing
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterDifficulty.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterCategory.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterStatus.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void loadData() {
        try {
            // Load categories for filter
            List<String> cats = courseService.findAllCategories();
            filterCategory.getItems().clear();
            filterCategory.getItems().add("");
            filterCategory.getItems().addAll(cats);
            filterCategory.setValue("");

            applyFilters();
        } catch (SQLException e) {
            showError("Failed to load courses: " + e.getMessage());
        }
    }

    @FXML
    private void applyFilters() {
        try {
            String search = searchField.getText();
            String diff = filterDifficulty.getValue();
            String cat = filterCategory.getValue();
            // Status filter applied client-side after query for simplicity
            String status = filterStatus.getValue();

            List<Course> courses = courseService.findByFilters(
                    (diff == null || diff.isEmpty()) ? null : diff,
                    (cat == null || cat.isEmpty()) ? null : cat,
                    null,
                    (search == null || search.isEmpty()) ? null : search
            );

            // Client-side status filter
            if (status != null && !status.isEmpty()) {
                courses = courses.stream()
                        .filter(c -> status.equals(c.getStatus()))
                        .toList();
            }

            courseData.setAll(courses);
            statsLabel.setText("Showing " + courses.size() + " course(s)");
        } catch (SQLException e) {
            showError("Filter error: " + e.getMessage());
        }
    }

    @FXML
    private void handleNew() {
        openCourseForm(null);
    }

    @FXML
    private void handleEdit() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a course to edit."); return; }
        openCourseForm(selected);
    }

    @FXML
    private void handleDelete() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a course to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete course \"" + selected.getCourseName() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                courseService.delete(selected.getId());
                setStatus("Course deleted successfully.", false);
                loadData();
            } catch (SQLException e) {
                showError(e.getMessage());
            }
        }
    }

    @FXML
    private void handleTogglePublish() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a course."); return; }
        try {
            courseService.togglePublish(selected);
            setStatus("Course " + (selected.isPublished() ? "published" : "unpublished") + ".", false);
            applyFilters();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleViewPlannings() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a course."); return; }
        openPlanningView(selected);
    }

    @FXML
    private void handleStats() {
        openStatsView();
    }

    @FXML
    private void handleRefresh() {
        loadData();
        setStatus("Refreshed.", false);
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    private void openCourseForm(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseForm.fxml"));
            Parent root = loader.load();
            CourseFormController ctrl = loader.getController();
            ctrl.initData(course, this::loadData);

            Stage stage = new Stage();
            stage.setTitle(course == null ? "New Course" : "Edit Course");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open form: " + e.getMessage());
        }
    }

    private void openCourseDetail(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseDetail.fxml"));
            Parent root = loader.load();
            CourseDetailController ctrl = loader.getController();
            ctrl.initData(course);

            Stage stage = new Stage();
            stage.setTitle("Course: " + course.getCourseName());
            stage.setScene(new Scene(root, 900, 600));
            stage.show();
        } catch (IOException e) {
            showError("Cannot open detail: " + e.getMessage());
        }
    }

    private void openPlanningView(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningView.fxml"));
            Parent root = loader.load();
            PlanningController ctrl = loader.getController();
            ctrl.initWithCourse(course);

            Stage stage = new Stage();
            stage.setTitle("Plannings — " + course.getCourseName());
            stage.setScene(new Scene(root, 1000, 650));
            stage.show();
        } catch (IOException e) {
            showError("Cannot open planning view: " + e.getMessage());
        }
    }

    private void openStatsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StatsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Study Session Statistics");
            stage.setScene(new Scene(root, 900, 700));
            stage.show();
        } catch (IOException e) {
            showError("Cannot open stats: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
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