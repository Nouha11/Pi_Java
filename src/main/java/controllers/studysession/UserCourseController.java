package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.studysession.Course;
import services.studysession.CourseService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * User-facing course browser — read-only.
 * Users can view course details and schedule a planning session.
 * No create / edit / delete / publish actions.
 */
public class UserCourseController implements Initializable {

    @FXML private FlowPane courseCardsPane;
    @FXML private VBox emptyState;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterDifficulty;
    @FXML private ComboBox<String> filterCategory;
    @FXML private ComboBox<String> filterStatus;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final CourseService courseService = new CourseService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupFilters();
        loadData();
    }

    private void setupFilters() {
        filterDifficulty.getItems().addAll("", "BEGINNER", "INTERMEDIATE", "ADVANCED");
        filterDifficulty.setValue("");
        filterStatus.getItems().addAll("", "NOT_STARTED", "IN_PROGRESS", "COMPLETED");
        filterStatus.setValue("");

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterDifficulty.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterCategory.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterStatus.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void loadData() {
        try {
            List<String> cats = courseService.findAllCategories();
            filterCategory.getItems().clear();
            filterCategory.getItems().add("");
            filterCategory.getItems().addAll(cats);
            filterCategory.setValue("");
            applyFilters();
        } catch (SQLException e) {
            setStatus("Failed to load courses: " + e.getMessage(), true);
        }
    }

    @FXML
    private void applyFilters() {
        try {
            String search = searchField.getText();
            String diff   = filterDifficulty.getValue();
            String cat    = filterCategory.getValue();
            String status = filterStatus.getValue();

            List<Course> courses = courseService.findByFilters(
                    (diff   == null || diff.isEmpty())   ? null : diff,
                    (cat    == null || cat.isEmpty())    ? null : cat,
                    null,
                    (search == null || search.isEmpty()) ? null : search
            );

            // Only show published courses to users
            courses = courses.stream().filter(Course::isPublished).toList();

            if (status != null && !status.isEmpty()) {
                final String s = status;
                courses = courses.stream().filter(c -> s.equals(c.getStatus())).toList();
            }

            renderCards(courses);
            statsLabel.setText(courses.size() + " course(s)");
        } catch (SQLException e) {
            setStatus("Filter error: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleRefresh() {
        loadData();
        setStatus("Refreshed.", false);
    }

    // ── CARD BUILDER ──────────────────────────────────────────────────────────

    private void renderCards(List<Course> courses) {
        courseCardsPane.getChildren().clear();
        boolean empty = courses.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        for (Course c : courses) courseCardsPane.getChildren().add(buildCard(c));
    }

    private VBox buildCard(Course course) {
        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setMaxWidth(300);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-radius: 12;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.07),8,0,0,2);"
        );

        // Difficulty color strip
        String stripColor = switch (course.getDifficulty() != null ? course.getDifficulty() : "") {
            case "BEGINNER"     -> "#22c55e";
            case "INTERMEDIATE" -> "#f59e0b";
            case "ADVANCED"     -> "#ef4444";
            default             -> "#94a3b8";
        };
        HBox strip = new HBox();
        strip.setPrefHeight(5);
        strip.setStyle("-fx-background-color: " + stripColor + "; -fx-background-radius: 12 12 0 0;");

        // Body
        VBox body = new VBox(8);
        body.setStyle("-fx-padding: 14 16 12 16;");

        // Title
        Label nameLabel = new Label(course.getCourseName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-wrap-text: true;");
        nameLabel.setMaxWidth(268);
        nameLabel.setWrapText(true);

        // Badges
        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label catBadge = new Label("🏷 " + course.getCategory());
        catBadge.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5;" +
                          "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 11px;");
        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.setStyle("-fx-background-color: " + stripColor + "22; -fx-text-fill: " + stripColor + ";" +
                           "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
        badgeRow.getChildren().addAll(catBadge, diffBadge);

        // Status + duration
        String statusColor = switch (course.getStatus() != null ? course.getStatus() : "") {
            case "NOT_STARTED" -> "#94a3b8";
            case "IN_PROGRESS" -> "#f59e0b";
            case "COMPLETED"   -> "#22c55e";
            default            -> "#94a3b8";
        };
        Label statusLbl = new Label("● " + (course.getStatus() != null ? course.getStatus().replace("_", " ") : ""));
        statusLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + statusColor + ";");
        Label durationLbl = new Label("⏱ " + course.getEstimatedDuration() + " min");
        durationLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.getChildren().addAll(statusLbl, durationLbl);

        // Progress bar
        double progress = course.getProgress() / 100.0;
        String progressColor = progress >= 0.8 ? "#22c55e" : progress >= 0.4 ? "#f59e0b" : "#ef4444";
        StackPane progressBg = new StackPane();
        progressBg.setPrefHeight(6);
        progressBg.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 3;");
        HBox progressFill = new HBox();
        progressFill.setPrefHeight(6);
        progressFill.setPrefWidth(progress * 268);
        progressFill.setMaxWidth(progress * 268);
        progressFill.setStyle("-fx-background-color: " + progressColor + "; -fx-background-radius: 3;");
        progressBg.getChildren().add(progressFill);
        StackPane.setAlignment(progressFill, Pos.CENTER_LEFT);
        Label progressLbl = new Label(course.getProgress() + "% complete");
        progressLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");

        body.getChildren().addAll(nameLabel, badgeRow, metaRow, progressBg, progressLbl);

        // Footer — user actions only
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle(
            "-fx-padding: 8 16 10 16;" +
            "-fx-border-color: #f1f5f9; -fx-border-width: 1 0 0 0;" +
            "-fx-background-color: #f8fafc; -fx-background-radius: 0 0 12 12;"
        );

        Button detailBtn = new Button("🔍 View Details");
        detailBtn.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5;" +
                           "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        detailBtn.setOnAction(e -> openCourseDetail(course));

        Button planBtn = new Button("📅 Plan Session");
        planBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;" +
                         "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        planBtn.setOnAction(e -> openPlanningForm(course));

        footer.getChildren().addAll(detailBtn, planBtn);

        // Double-click also opens detail
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openCourseDetail(course); });
        card.getChildren().addAll(strip, body, footer);
        return card;
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

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
            setStatus("Cannot open detail: " + e.getMessage(), true);
        }
    }

    private void openPlanningForm(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningForm.fxml"));
            Parent root = loader.load();
            PlanningFormController ctrl = loader.getController();
            ctrl.initData(null, course.getId(), course.getCourseName(), this::applyFilters);
            Stage stage = new Stage();
            stage.setTitle("Schedule Session — " + course.getCourseName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            setStatus("Cannot open planning form: " + e.getMessage(), true);
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #22c55e;");
    }
}
