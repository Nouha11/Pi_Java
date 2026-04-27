package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.studysession.Course;
import services.studysession.CourseService;
import utils.EmojiUtil;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class CourseController implements Initializable {

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
            showError("Failed to load courses: " + e.getMessage());
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

            if (status != null && !status.isEmpty()) {
                courses = courses.stream()
                        .filter(c -> status.equals(c.getStatus()))
                        .toList();
            }

            renderCards(courses);
            statsLabel.setText(courses.size() + " course(s)");
        } catch (SQLException e) {
            showError("Filter error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  CARD BUILDER
    // ─────────────────────────────────────────────

    private void renderCards(List<Course> courses) {
        courseCardsPane.getChildren().clear();

        boolean empty = courses.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);

        for (Course c : courses) {
            courseCardsPane.getChildren().add(buildCard(c));
        }
    }

    private VBox buildCard(Course course) {
        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setMaxWidth(300);
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-color: #e0e4ef;" +
                        "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.08),8,0,0,2);"
        );

        // ── TOP COLOR STRIP (difficulty color) ──
        String stripColor = switch (course.getDifficulty() != null ? course.getDifficulty() : "") {
            case "BEGINNER"     -> "#27ae60";
            case "INTERMEDIATE" -> "#f39c12";
            case "ADVANCED"     -> "#e74c3c";
            default             -> "#90a4ae";
        };
        HBox strip = new HBox();
        strip.setPrefHeight(5);
        strip.setStyle("-fx-background-color: " + stripColor + "; -fx-background-radius: 10 10 0 0;");

        // ── CARD BODY ──
        VBox body = new VBox(8);
        body.setStyle("-fx-padding: 14 16 12 16;");

        // Title + published badge
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(course.getCourseName());
        nameLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1a237e; -fx-wrap-text: true;");
        nameLabel.setMaxWidth(200);
        nameLabel.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label publishedBadge = new Label(course.isPublished() ? "✅" : "📝");
        publishedBadge.setStyle("-fx-font-size: 13;");
        Tooltip.install(publishedBadge, new Tooltip(course.isPublished() ? "Published" : "Draft"));
        titleRow.getChildren().addAll(nameLabel, spacer, publishedBadge);

        // Category + difficulty badges
        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        
        // Use EmojiUtil for consistent emoji display
        ImageView categoryIcon = EmojiUtil.getEmojiImage("🏷", 12);
        Label catBadge = new Label(" " + course.getCategory());
        if (categoryIcon != null) {
            catBadge.setGraphic(categoryIcon);
        } else {
            catBadge.setText("🏷 " + course.getCategory());
        }
        catBadge.setStyle(
                "-fx-background-color: #e8eaf6; -fx-text-fill: #3949ab;" +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 11;"
        );
        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.setStyle(
                "-fx-background-color: " + stripColor + "22; -fx-text-fill: " + stripColor + ";" +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 11; -fx-font-weight: bold;"
        );
        badgeRow.getChildren().addAll(catBadge, diffBadge);

        // Status
        String statusColor = switch (course.getStatus() != null ? course.getStatus() : "") {
            case "NOT_STARTED" -> "#90a4ae";
            case "IN_PROGRESS" -> "#f39c12";
            case "COMPLETED"   -> "#27ae60";
            default            -> "#90a4ae";
        };
        Label statusLabel = new Label("● " + (course.getStatus() != null ? course.getStatus().replace("_", " ") : ""));
        statusLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + statusColor + ";");

        // Duration
        Label durationLabel = new Label("⏱ " + course.getEstimatedDuration() + " min");
        durationLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #78909c;");

        // Progress bar
        double progress = course.getProgress() / 100.0;
        String progressColor = progress >= 0.8 ? "#27ae60" : progress >= 0.4 ? "#f39c12" : "#e74c3c";
        StackPane progressBg = new StackPane();
        progressBg.setPrefHeight(6);
        progressBg.setStyle("-fx-background-color: #eceff1; -fx-background-radius: 3;");
        HBox progressFill = new HBox();
        progressFill.setPrefHeight(6);
        progressFill.setPrefWidth(progress * 268);
        progressFill.setMaxWidth(progress * 268);
        progressFill.setStyle("-fx-background-color: " + progressColor + "; -fx-background-radius: 3;");
        progressBg.getChildren().add(progressFill);
        StackPane.setAlignment(progressFill, Pos.CENTER_LEFT);

        Label progressLabel = new Label(course.getProgress() + "% complete");
        progressLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #90a4ae;");

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.getChildren().addAll(statusLabel, durationLabel);

        body.getChildren().addAll(titleRow, badgeRow, metaRow, progressBg, progressLabel);

        // ── CARD FOOTER (action buttons) ──
        HBox footer = new HBox(6);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle(
                "-fx-padding: 8 16 10 16;" +
                        "-fx-border-color: #f0f0f0; -fx-border-width: 1 0 0 0;" +
                        "-fx-background-color: #fafafa; -fx-background-radius: 0 0 10 10;"
        );

        Button editBtn = new Button("✏");
        editBtn.setTooltip(new Tooltip("Edit"));
        editBtn.setStyle("-fx-background-color: #e8eaf6; -fx-text-fill: #3949ab; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 10;");
        editBtn.setOnAction(e -> openCourseForm(course));

        Button deleteBtn = new Button("🗑");
        deleteBtn.setTooltip(new Tooltip("Delete"));
        deleteBtn.setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 10;");
        deleteBtn.setOnAction(e -> handleDelete(course));

        Button planBtn = new Button("📅 Plannings");
        planBtn.setStyle("-fx-background-color: #e3f2fd; -fx-text-fill: #0277bd; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 10; -fx-font-size: 11;");
        planBtn.setOnAction(e -> openPlanningView(course));

        Button publishBtn = new Button(course.isPublished() ? "📢" : "📝");
        publishBtn.setTooltip(new Tooltip(course.isPublished() ? "Unpublish" : "Publish"));
        publishBtn.setStyle("-fx-background-color: #fff8e1; -fx-text-fill: #f57f17; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 10;");
        publishBtn.setOnAction(e -> {
            try {
                courseService.togglePublish(course);
                applyFilters();
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        footer.getChildren().addAll(editBtn, deleteBtn, planBtn, publishBtn);

        // double-click opens detail
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openCourseDetail(course);
        });

        card.getChildren().addAll(strip, body, footer);
        return card;
    }

    // ─────────────────────────────────────────────
    //  HANDLERS
    // ─────────────────────────────────────────────

    @FXML private void handleNew() { openCourseForm(null); }

    @FXML private void handleRefresh() { loadData(); setStatus("Refreshed.", false); }

    @FXML private void handleEdit() { showInfo("Double-click a card to edit, or use the ✏ button on each card."); }

    @FXML private void handleDelete() { showInfo("Use the 🗑 button on the card to delete."); }

    @FXML private void handleTogglePublish() { showInfo("Use the 📢 button on the card to toggle publish."); }

    @FXML private void handleViewPlannings() { showInfo("Use the 📅 button on the card to view plannings."); }

    @FXML private void handleStats() { openStatsView(); }

    private void handleDelete(Course course) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete course \"" + course.getCourseName() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                courseService.delete(course.getId());
                setStatus("Course deleted.", false);
                applyFilters();
            } catch (SQLException e) { showError(e.getMessage()); }
        }
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    private void openCourseForm(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseForm.fxml"));
            Parent root = loader.load();
            CourseFormController ctrl = loader.getController();
            ctrl.initData(course, this::applyFilters);
            Stage stage = new Stage();
            stage.setTitle(course == null ? "New Course" : "Edit Course");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) { showError("Cannot open form: " + e.getMessage()); }
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
        } catch (IOException e) { showError("Cannot open detail: " + e.getMessage()); }
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
        } catch (IOException e) { showError("Cannot open planning view: " + e.getMessage()); }
    }

    private void openStatsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StatsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Study Session Statistics");
            stage.setScene(new Scene(root, 900, 700));
            stage.show();
        } catch (IOException e) { showError("Cannot open stats: " + e.getMessage()); }
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