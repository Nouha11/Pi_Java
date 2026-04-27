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
import services.studysession.CourseService;
import services.studysession.EnrollmentService;
import utils.EmojiUtil;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

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
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private Map<Integer, String> enrollmentStatusMap = new HashMap<>();

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
        
        // Batch load enrollment statuses before rendering cards
        loadEnrollmentStatuses(courses);
        
        for (Course c : courses) courseCardsPane.getChildren().add(buildCard(c));
    }

    /**
     * Loads enrollment statuses for all courses in a single batch query to avoid N+1 queries.
     */
    private void loadEnrollmentStatuses(List<Course> courses) {
        enrollmentStatusMap.clear();
        
        if (courses.isEmpty()) {
            return;
        }
        
        try {
            int currentUserId = UserSession.getInstance().getUserId();
            List<Integer> courseIds = courses.stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
            
            enrollmentStatusMap = enrollmentService.getBatchEnrollmentStatuses(courseIds, currentUserId);
        } catch (SQLException e) {
            setStatus("Failed to load enrollment statuses: " + e.getMessage(), true);
        }
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

        // Title + published badge (matching CourseController pattern)
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

        // Provider label
        String providerText = "Provided by Nova";
        if (course.getCreatorRole() != null && course.getCreatorRole().equals("ROLE_TUTOR")) {
            providerText = "Provided by " + (course.getCreatorName() != null ? course.getCreatorName() : "Unknown");
        }
        Label providerLabel = new Label(providerText);
        providerLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        providerLabel.setMaxWidth(268);
        providerLabel.setWrapText(true);

        // Badges
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

        body.getChildren().addAll(titleRow, providerLabel, badgeRow, metaRow, progressBg, progressLbl);

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
        detailBtn.setOnAction(e -> openCourseDetailsPage(course));

        // Enrollment button logic
        String enrollmentStatus = enrollmentStatusMap.get(course.getId());
        boolean isAdminOwned = "ROLE_ADMIN".equals(course.getCreatorRole());
        
        if (isAdminOwned) {
            // Admin-owned courses: show "Details" + "Start" buttons
            Button detailsBtn = new Button("🔍 Details");
            detailsBtn.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5;" +
                               "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            detailsBtn.setOnAction(e -> openCourseDetailsPage(course));
            Button startBtn = new Button("▶ Start");
            startBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;" +
                             "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            startBtn.setOnAction(e -> openCourseContentPage(course));
            footer.getChildren().addAll(detailsBtn, startBtn);
        } else if (enrollmentStatus == null) {
            // No enrollment: show "Enroll Request" button
            Button enrollBtn = new Button("Enroll Request");
            enrollBtn.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;" +
                              "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            enrollBtn.setOnAction(e -> handleEnrollRequest(course));
            footer.getChildren().addAll(detailBtn, enrollBtn);
        } else if ("PENDING".equals(enrollmentStatus)) {
            // Pending enrollment: show "Pending" badge
            Label pendingBadge = new Label("⏳ Pending");
            pendingBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706;" +
                                 "-fx-padding: 5 12; -fx-background-radius: 8; -fx-font-size: 11px; -fx-font-weight: bold;");
            footer.getChildren().addAll(detailBtn, pendingBadge);
        } else if ("ACCEPTED".equals(enrollmentStatus)) {
            // Accepted enrollment: show "Details" + "Start" buttons
            Button detailsBtn = new Button("🔍 Details");
            detailsBtn.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4f46e5;" +
                               "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            detailsBtn.setOnAction(e -> openCourseDetailsPage(course));
            Button startBtn = new Button("▶ Start");
            startBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;" +
                             "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            startBtn.setOnAction(e -> openCourseContentPage(course));
            footer.getChildren().addAll(detailsBtn, startBtn);
        } else if ("REJECTED".equals(enrollmentStatus)) {
            // Rejected enrollment: show "Rejected" badge and "Enroll Request" button
            Label rejectedBadge = new Label("❌ Rejected");
            rejectedBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;" +
                                  "-fx-padding: 5 12; -fx-background-radius: 8; -fx-font-size: 11px; -fx-font-weight: bold;");
            Button enrollBtn = new Button("Enroll Request");
            enrollBtn.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;" +
                              "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            enrollBtn.setOnAction(e -> handleEnrollRequest(course));
            footer.getChildren().addAll(detailBtn, rejectedBadge, enrollBtn);
        } else {
            // Fallback: show detail button only
            footer.getChildren().add(detailBtn);
        }

        // Double-click also opens details page
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openCourseDetailsPage(course); });
        card.getChildren().addAll(strip, body, footer);
        return card;
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    /**
     * Handles the "Enroll Request" button action.
     * Opens a custom dialog with inline validation to collect an optional message from the student.
     * Requirements: 12.3, 12.4
     */
    private void handleEnrollRequest(Course course) {
        // ── Build custom dialog ──────────────────────────────────────────────
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Enroll in Course");
        dialog.setHeaderText("Request enrollment in: " + course.getCourseName());

        // TextArea for optional message
        TextArea messageArea = new TextArea();
        messageArea.setPromptText("Optional message (leave blank if none)...");
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(4);
        messageArea.setPrefWidth(380);
        messageArea.setStyle("-fx-font-size: 12px;");

        // Inline error label — hidden by default (Requirements 12.3, 12.4)
        Label errMessage = new Label();
        errMessage.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
        errMessage.setVisible(false);
        errMessage.setManaged(false);

        // Field label
        Label fieldLabel = new Label("Message (optional, max 500 characters):");
        fieldLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

        VBox content = new VBox(6, fieldLabel, messageArea, errMessage);
        content.setStyle("-fx-padding: 10 0 0 0;");
        dialog.getDialogPane().setContent(content);

        // Buttons
        ButtonType submitButtonType = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButtonType, ButtonType.CANCEL);

        // Get the actual Submit button node so we can intercept its action
        Button submitButton = (Button) dialog.getDialogPane().lookupButton(submitButtonType);

        // Override the submit button to perform inline validation before closing
        submitButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String msg = messageArea.getText();

            // Validate: message must not exceed 500 characters
            if (msg != null && msg.length() > 500) {
                // Show inline error — do NOT close the dialog (Requirements 12.3, 12.4)
                errMessage.setText("Message must not exceed 500 characters (currently " + msg.length() + ").");
                errMessage.setVisible(true);
                errMessage.setManaged(true);
                messageArea.setStyle("-fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 3; -fx-font-size: 12px;");
                event.consume(); // prevent dialog from closing
                return;
            }

            // Clear any previous error
            errMessage.setVisible(false);
            errMessage.setManaged(false);
            messageArea.setStyle("-fx-font-size: 12px;");
        });

        // Clear error styling when user edits the field
        messageArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() <= 500) {
                errMessage.setVisible(false);
                errMessage.setManaged(false);
                messageArea.setStyle("-fx-font-size: 12px;");
            }
        });

        // Result converter: return the message text when Submit is clicked
        dialog.setResultConverter(buttonType -> {
            if (buttonType == submitButtonType) {
                return messageArea.getText();
            }
            return null;
        });

        // Show dialog and process result
        dialog.showAndWait().ifPresent(message -> {
            try {
                int currentUserId = UserSession.getInstance().getUserId();
                enrollmentService.createRequest(course.getId(), currentUserId, message);

                // Refresh the card to show "Pending" badge
                applyFilters();
                setStatus("Enrollment request submitted successfully!", false);
            } catch (IllegalStateException e) {
                setStatus("Error: " + e.getMessage(), true);
            } catch (SQLException e) {
                setStatus("Failed to submit enrollment request: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Opens the Course Details Page (planning sessions, analytics, progress overview).
     * Loads CourseDetail.fxml with CourseDetailController.
     * Requirement 10, 11.2
     */
    private void openCourseDetailsPage(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseDetail.fxml"));
            Parent root = loader.load();
            CourseDetailController ctrl = loader.getController();
            ctrl.initData(course);
            Stage stage = new Stage();
            stage.setTitle("Course Details: " + course.getCourseName());
            stage.setScene(new Scene(root, 900, 600));
            stage.show();
        } catch (IOException e) {
            setStatus("Cannot open course details: " + e.getMessage(), true);
        }
    }

    /**
     * Opens the Course Content Page (Pomodoro timer, videos, Wikipedia, PDF resources).
     * Loads CourseContentView.fxml with CourseContentController, maximized.
     * Requirement 1, 11.3
     */
    private void openCourseContentPage(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CourseContentView.fxml"));
            Parent root = loader.load();
            CourseContentController ctrl = loader.getController();
            Stage stage = new Stage();
            stage.setTitle(course.getCourseName());
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
            ctrl.initData(course);
        } catch (IOException e) {
            setStatus("Cannot open course content: " + e.getMessage(), true);
        }
    }

    /**
     * @deprecated Use {@link #openCourseContentPage(Course)} directly.
     * Kept for backward compatibility; now delegates to openCourseContentPage.
     */
    private void startCourse(Course course) {
        openCourseContentPage(course);
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
        if (!isError) {
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> statusLabel.setText(""));
            pause.play();
        }
    }
}
