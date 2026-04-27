package controllers.studysession;

import javafx.animation.PauseTransition;
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
import javafx.util.Duration;
import models.studysession.Course;
import models.studysession.EnrollmentRequest;
import services.studysession.CourseService;
import services.studysession.EnrollmentService;
import utils.EmojiUtil;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * TutorCourseController provides tutors with full CRUD capabilities for their own courses.
 * This controller mirrors the structure of CourseController but only shows courses owned by the logged-in tutor.
 */
public class TutorCourseController implements Initializable {

    @FXML private FlowPane courseCardsPane;
    @FXML private VBox emptyState;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterDifficulty;
    @FXML private ComboBox<String> filterCategory;
    @FXML private ComboBox<String> filterStatus;
    @FXML private Label statusLabel;
    @FXML private Label statsLabel;

    private final CourseService courseService = new CourseService();
    private final EnrollmentService enrollmentService = new EnrollmentService();

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

    /**
     * Subtask 9.2: Load only tutor's courses
     * Gets current user ID from UserSession and calls courseService.findByCreator()
     */
    private void loadData() {
        try {
            // Get current tutor's user ID
            int tutorId = UserSession.getInstance().getUserId();
            
            // Load all courses owned by this tutor
            List<Course> tutorCourses = courseService.findByCreator(tutorId);
            
            // Extract unique categories from tutor's courses for filter dropdown
            List<String> cats = tutorCourses.stream()
                    .map(Course::getCategory)
                    .distinct()
                    .sorted()
                    .toList();
            
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
            int tutorId = UserSession.getInstance().getUserId();
            String search = searchField.getText();
            String diff   = filterDifficulty.getValue();
            String cat    = filterCategory.getValue();
            String status = filterStatus.getValue();

            // Get tutor's courses
            List<Course> courses = courseService.findByCreator(tutorId);

            // Apply filters on the result set
            if (diff != null && !diff.isEmpty()) {
                courses = courses.stream()
                        .filter(c -> diff.equals(c.getDifficulty()))
                        .toList();
            }

            if (cat != null && !cat.isEmpty()) {
                courses = courses.stream()
                        .filter(c -> cat.equals(c.getCategory()))
                        .toList();
            }

            if (status != null && !status.isEmpty()) {
                courses = courses.stream()
                        .filter(c -> status.equals(c.getStatus()))
                        .toList();
            }

            if (search != null && !search.isEmpty()) {
                String searchLower = search.toLowerCase();
                courses = courses.stream()
                        .filter(c -> c.getCourseName().toLowerCase().contains(searchLower) ||
                                (c.getDescription() != null && c.getDescription().toLowerCase().contains(searchLower)))
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

    /**
     * Subtask 9.3: Build card with provider label and full CRUD buttons
     * Reuses card structure from CourseController with provider label and CRUD buttons
     */
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

        // ── TOP COLOR STRIP (difficulty color) ──
        String stripColor = switch (course.getDifficulty() != null ? course.getDifficulty() : "") {
            case "BEGINNER"     -> "#22c55e";
            case "INTERMEDIATE" -> "#f59e0b";
            case "ADVANCED"     -> "#ef4444";
            default             -> "#94a3b8";
        };
        HBox strip = new HBox();
        strip.setPrefHeight(5);
        strip.setStyle("-fx-background-color: " + stripColor + "; -fx-background-radius: 12 12 0 0;");

        // ── CARD BODY ──
        VBox body = new VBox(8);
        body.setStyle("-fx-padding: 14 16 12 16;");

        // Title + published badge
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(course.getCourseName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-wrap-text: true;");
        nameLabel.setMaxWidth(200);
        nameLabel.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label publishedBadge = new Label(course.isPublished() ? "✅" : "📝");
        publishedBadge.setStyle("-fx-font-size: 13px;");
        Tooltip.install(publishedBadge, new Tooltip(course.isPublished() ? "Published" : "Draft"));
        titleRow.getChildren().addAll(nameLabel, spacer, publishedBadge);

        // Provider label (Requirement 2.4, 7.7)
        Label providerLabel = new Label(getProviderText(course));
        providerLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        providerLabel.setMaxWidth(268);
        providerLabel.setWrapText(true);

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
                "-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5;" +
                        "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 11px;"
        );
        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.setStyle(
                "-fx-background-color: " + stripColor + "22; -fx-text-fill: " + stripColor + ";" +
                        "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;"
        );
        badgeRow.getChildren().addAll(catBadge, diffBadge);

        // Status
        String statusColor = switch (course.getStatus() != null ? course.getStatus() : "") {
            case "NOT_STARTED" -> "#94a3b8";
            case "IN_PROGRESS" -> "#f59e0b";
            case "COMPLETED"   -> "#22c55e";
            default            -> "#94a3b8";
        };
        Label statusLabel = new Label("● " + (course.getStatus() != null ? course.getStatus().replace("_", " ") : ""));
        statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + statusColor + ";");

        // Duration
        Label durationLabel = new Label("⏱ " + course.getEstimatedDuration() + " min");
        durationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

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

        Label progressLabel = new Label(course.getProgress() + "% complete");
        progressLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.getChildren().addAll(statusLabel, durationLabel);

        body.getChildren().addAll(titleRow, providerLabel, badgeRow, metaRow, progressBg, progressLabel);

        // ── CARD FOOTER (action buttons) ──
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle(
                "-fx-padding: 8 16 10 16;" +
                        "-fx-border-color: #f1f5f9; -fx-border-width: 1 0 0 0;" +
                        "-fx-background-color: #f8fafc; -fx-background-radius: 0 0 12 12;"
        );

        // Edit button (Subtask 9.5)
        Button editBtn = new Button("✏");
        editBtn.setTooltip(new Tooltip("Edit"));
        editBtn.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        editBtn.setOnAction(e -> handleEdit(course));

        // Delete button (Subtask 9.6)
        Button deleteBtn = new Button("🗑");
        deleteBtn.setTooltip(new Tooltip("Delete"));
        deleteBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #ef4444; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        deleteBtn.setOnAction(e -> handleDelete(course));

        // View Plannings button
        Button planBtn = new Button("📅 Plannings");
        planBtn.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        planBtn.setOnAction(e -> openPlanningView(course));

        // Publish/Unpublish button (Subtask 9.7)
        Button publishBtn = new Button(course.isPublished() ? "📢 Unpublish" : "📝 Publish");
        publishBtn.setTooltip(new Tooltip(course.isPublished() ? "Unpublish" : "Publish"));
        publishBtn.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        publishBtn.setOnAction(e -> handleTogglePublish(course));

        footer.getChildren().addAll(editBtn, deleteBtn, planBtn, publishBtn);

        // double-click opens detail
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openCourseDetail(course);
        });

        card.getChildren().addAll(strip, body, footer);
        return card;
    }

    /**
     * Helper method to get provider text for a course
     * Requirements: 2.2, 2.3
     */
    private String getProviderText(Course course) {
        if (course.getCreatorRole() != null && course.getCreatorRole().equals("ROLE_TUTOR")) {
            return "Provided by " + (course.getCreatorName() != null ? course.getCreatorName() : "Tutor");
        } else {
            return "Provided by Nova";
        }
    }

    // ─────────────────────────────────────────────
    //  HANDLERS
    // ─────────────────────────────────────────────

    /**
     * Subtask 9.4: Implement "New Course" button action
     * Opens CourseFormController with null course and passes current user ID as createdById
     */
    @FXML
    private void handleNew() {
        openCourseForm(null);
    }

    @FXML private void handleRefresh() { loadData(); setStatus("Refreshed.", false); }

    /**
     * Subtask 9.5: Implement "Edit Course" button action
     * Verifies course ownership before allowing edit
     */
    private void handleEdit(Course course) {
        int currentUserId = UserSession.getInstance().getUserId();
        
        // Verify ownership
        if (course.getCreatedById() == null || course.getCreatedById() != currentUserId) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Access Denied: You can only edit courses you created.",
                    ButtonType.OK);
            alert.setTitle("Access Denied");
            alert.showAndWait();
            System.err.println("[ACCESS DENIED] Role ROLE_TUTOR attempted to edit course not owned by them");
            return;
        }
        
        openCourseForm(course);
    }

    /**
     * Subtask 9.6: Implement "Delete Course" button action
     * Checks for accepted enrollments and shows confirmation dialog
     */
    private void handleDelete(Course course) {
        try {
            // Check if course has accepted enrollments
            List<EnrollmentRequest> enrollments = enrollmentService.findByCourse(course.getId());
            long acceptedCount = enrollments.stream()
                    .filter(e -> "ACCEPTED".equals(e.getStatus()))
                    .count();
            
            String message;
            if (acceptedCount > 0) {
                message = "Delete course \"" + course.getCourseName() + "\"?\n\n" +
                        "⚠ WARNING: This course has " + acceptedCount + " accepted enrollment(s).\n" +
                        "Enrolled students will lose access to this course.\n\n" +
                        "This action cannot be undone.";
            } else {
                message = "Delete course \"" + course.getCourseName() + "\"?\nThis cannot be undone.";
            }
            
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    message,
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirm Delete");
            Optional<ButtonType> result = confirm.showAndWait();
            
            if (result.isPresent() && result.get() == ButtonType.YES) {
                courseService.delete(course.getId());
                setStatus("Course deleted.", false);
                applyFilters();
            }
        } catch (SQLException e) {
            showError("Failed to delete course: " + e.getMessage());
        }
    }

    /**
     * Subtask 9.7: Implement "Publish/Unpublish" toggle action
     * Calls courseService.togglePublish and refreshes the card
     */
    private void handleTogglePublish(Course course) {
        try {
            courseService.togglePublish(course);
            setStatus("Course " + (course.isPublished() ? "published" : "unpublished") + ".", false);
            applyFilters();
        } catch (SQLException e) {
            showError("Failed to toggle publish status: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    /**
     * Opens CourseFormController for creating or editing a course
     * Subtask 9.4: Passes current user ID as createdById when creating new course
     * Subtask 9.8: Reuses existing CourseFormController validation
     */
    private void openCourseForm(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseForm.fxml"));
            Parent root = loader.load();
            CourseFormController ctrl = loader.getController();
            
            // If creating new course, set createdById to current tutor
            if (course == null) {
                Course newCourse = new Course();
                newCourse.setCreatedById(UserSession.getInstance().getUserId());
                ctrl.initData(newCourse, this::applyFilters);
            } else {
                ctrl.initData(course, this::applyFilters);
            }
            
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

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #22c55e;");
        if (!isError) {
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> statusLabel.setText(""));
            pause.play();
        }
    }

    private void showError(String msg) {
        setStatus("⚠ " + msg, true);
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
