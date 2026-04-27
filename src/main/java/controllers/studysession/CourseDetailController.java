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
import models.studysession.Planning;
import models.studysession.StudentProgress;
import services.studysession.EnrollmentService;
import services.studysession.PlanningService;
import services.studysession.StudentProgressService;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class CourseDetailController implements Initializable {

    // ── Existing fields (kept for backward compatibility) ──────────────────
    @FXML private Label lblCourseName, lblCategory, lblDifficulty, lblStatus;
    @FXML private Label lblDuration, lblProgress, lblPublished, lblPlanCount;
    @FXML private Label lblProvider;
    @FXML private TextArea txtDescription;

    // ── New fields added for Course Details Page (task 10) ─────────────────
    @FXML private ProgressBar progressBar;
    @FXML private VBox vboxUpcoming;
    @FXML private VBox vboxCompleted;
    @FXML private Label lblTotalSessions;
    @FXML private Label lblCompletedSessions;
    @FXML private Label lblMissedSessions;
    @FXML private Label lblTotalTime;

    private final PlanningService planningService = new PlanningService();
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final StudentProgressService progressService = new StudentProgressService();
    private Course course;

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void initData(Course c) {
        this.course = c;

        // CourseAccessGuard — check access for students
        String currentRole = UserSession.getInstance().getRole();
        if ("ROLE_STUDENT".equals(currentRole)) {
            boolean isAdminOwned = "ROLE_ADMIN".equals(c.getCreatorRole()) || c.getCreatedById() == null;
            if (!isAdminOwned) {
                try {
                    int currentUserId = UserSession.getInstance().getUserId();
                    String enrollmentStatus = enrollmentService.getEnrollmentStatus(c.getId(), currentUserId);
                    if (!"ACCEPTED".equals(enrollmentStatus)) {
                        showAccessDenied();
                        return;
                    }
                } catch (SQLException e) {
                    System.err.println("[ACCESS DENIED] Enrollment check failed: " + e.getMessage());
                    showAccessDenied();
                    return;
                }
            }
        }

        // Populate course info card
        lblCourseName.setText(c.getCourseName());
        lblCategory.setText(c.getCategory());
        lblDifficulty.setText(c.getDifficulty());
        lblStatus.setText(c.getStatus());
        lblDuration.setText("⏱ " + c.getEstimatedDuration() + " min");
        lblPublished.setText(c.isPublished() ? "✅ Published" : "❌ Draft");
        txtDescription.setText(c.getDescription() != null ? c.getDescription() : "No description.");

        String providerText = "Provided by Nova";
        if (c.getCreatorRole() != null && c.getCreatorRole().equals("ROLE_TUTOR")) {
            providerText = "Provided by " + (c.getCreatorName() != null ? c.getCreatorName() : "Unknown");
        }
        lblProvider.setText(providerText);

        // Progress bar — load live record from StudentProgressService, fall back to course.getProgress()
        int pct = c.getProgress();
        try {
            int currentUserId = UserSession.getInstance().getUserId();
            StudentProgress sp = progressService.getProgress(currentUserId, c.getId());
            if (sp != null) {
                pct = sp.getProgressPercentage();
            }
        } catch (SQLException e) {
            System.err.println("[CourseDetailController] Could not load student progress: " + e.getMessage());
        }
        if (progressBar != null) {
            progressBar.setProgress(pct / 100.0);
            applyProgressColor(progressBar, pct);
        }
        if (lblProgress != null) {
            lblProgress.setText(pct + "%");
        }

        loadPlannings();
    }

    // ── Data loading ────────────────────────────────────────────────────────

    private void loadPlannings() {
        try {
            List<Planning> plannings = planningService.findByCourse(course.getId());
            renderPlanningGroups(plannings);
            computeAnalytics(plannings);
            lblPlanCount.setText(plannings.size() + " session(s)");
        } catch (SQLException e) {
            lblPlanCount.setText("Error loading sessions");
        }
    }

    // ── Planning groups (Upcoming / Completed) ──────────────────────────────

    private void renderPlanningGroups(List<Planning> plannings) {
        LocalDate today = LocalDate.now();

        List<Planning> upcoming = plannings.stream()
                .filter(p -> "SCHEDULED".equalsIgnoreCase(p.getStatus())
                        && p.getScheduledDate() != null
                        && !p.getScheduledDate().isBefore(today))
                .collect(Collectors.toList());

        List<Planning> completed = plannings.stream()
                .filter(p -> "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        // Render Upcoming
        if (vboxUpcoming != null) {
            vboxUpcoming.getChildren().clear();
            if (upcoming.isEmpty()) {
                vboxUpcoming.getChildren().add(createEmptyLabel("No upcoming sessions."));
            } else {
                for (Planning p : upcoming) vboxUpcoming.getChildren().add(buildCard(p));
            }
        }

        // Render Completed
        if (vboxCompleted != null) {
            vboxCompleted.getChildren().clear();
            if (completed.isEmpty()) {
                vboxCompleted.getChildren().add(createEmptyLabel("No completed sessions yet."));
            } else {
                for (Planning p : completed) vboxCompleted.getChildren().add(buildCard(p));
            }
        }
    }

    // ── Quick analytics ─────────────────────────────────────────────────────

    private void computeAnalytics(List<Planning> plannings) {
        int total     = plannings.size();
        int completed = (int) plannings.stream()
                .filter(p -> "COMPLETED".equalsIgnoreCase(p.getStatus())).count();
        int missed    = (int) plannings.stream()
                .filter(p -> "MISSED".equalsIgnoreCase(p.getStatus())).count();
        int totalTime = plannings.stream()
                .mapToInt(Planning::getPlannedDuration).sum();

        if (lblTotalSessions    != null) lblTotalSessions.setText(String.valueOf(total));
        if (lblCompletedSessions != null) lblCompletedSessions.setText(String.valueOf(completed));
        if (lblMissedSessions   != null) lblMissedSessions.setText(String.valueOf(missed));
        if (lblTotalTime        != null) lblTotalTime.setText(totalTime + " min");
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    @FXML
    private void handleStartStudying() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/CourseContentView.fxml"));
            Parent root = loader.load();
            CourseContentController ctrl = loader.getController();

            Stage currentStage = (Stage) lblCourseName.getScene().getWindow();
            currentStage.close();

            Stage stage = new Stage();
            stage.setTitle(course.getCourseName());
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            ctrl.initData(course);
            stage.show();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open course content: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handleAddPlanning() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/PlanningForm.fxml"));
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

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void showAccessDenied() {
        // Defer until the scene is attached to a window (initData may be called before stage.show())
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Access Denied");
            alert.setHeaderText("Course Access Restricted");
            alert.setContentText("You must have an accepted enrollment to access this course.");
            alert.showAndWait();
            if (lblCourseName.getScene() != null && lblCourseName.getScene().getWindow() != null) {
                ((Stage) lblCourseName.getScene().getWindow()).close();
            }
        });
    }

    private void applyProgressColor(ProgressBar bar, int pct) {
        String color = pct >= 80 ? "#22c55e" : pct >= 40 ? "#f59e0b" : "#ef4444";
        bar.setStyle("-fx-accent: " + color + ";");
    }

    private HBox buildCard(Planning p) {
        String statusVal = p.getStatus() != null ? p.getStatus().toUpperCase() : "";
        String[] badge = switch (statusVal) {
            case "COMPLETED" -> new String[]{"Completed", "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"};
            case "SCHEDULED" -> new String[]{"Scheduled", "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;"};
            case "MISSED"    -> new String[]{"Missed",    "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;"};
            case "CANCELLED" -> new String[]{"Cancelled", "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"};
            default          -> new String[]{statusVal,   "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"};
        };
        String accent = switch (statusVal) {
            case "COMPLETED" -> "#10b981"; case "SCHEDULED" -> "#3b82f6";
            case "MISSED"    -> "#ef4444"; case "CANCELLED" -> "#94a3b8"; default -> "#f59e0b";
        };

        String dateStr = p.getScheduledDate() != null ? p.getScheduledDate().toString() : "—";
        String timeStr = p.getScheduledTime() != null ? p.getScheduledTime().toString() : "—";
        String subtitle = dateStr + "  " + timeStr + "  ·  ⏱ " + p.getPlannedDuration() + " min"
                + (p.isReminder() ? "  ·  🔔" : "");

        return createCard(accent, "📅", "#eff6ff",
                p.getTitle() != null ? p.getTitle() : "—", subtitle, badge[0], badge[1]);
    }

    private HBox createCard(String accentColor, String iconText, String iconBg,
                            String title, String subtitle, String badgeText, String badgeStyle) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        String base = "-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);";
        String hover = "-fx-background-color: #f8fafc; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 3);";
        card.setStyle(base);
        card.setOnMouseEntered(e -> card.setStyle(hover));
        card.setOnMouseExited(e  -> card.setStyle(base));

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(38, 38); iconCircle.setMaxSize(38, 38);
        iconCircle.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 50;");
        Label iconLbl = new Label(iconText); iconLbl.setStyle("-fx-font-size: 16px;");
        iconCircle.getChildren().add(iconLbl);

        VBox text = new VBox(3); HBox.setHgrow(text, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        titleLbl.setWrapText(true);
        text.getChildren().add(titleLbl);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            text.getChildren().add(subLbl);
        }
        card.getChildren().addAll(iconCircle, text);
        if (badgeText != null && !badgeText.isBlank()) {
            Label badge = new Label(badgeText);
            badge.setStyle(badgeStyle + " -fx-font-weight: bold; -fx-font-size: 11px; " +
                    "-fx-padding: 4 10; -fx-background-radius: 20;");
            card.getChildren().add(badge);
        }
        return card;
    }

    private Label createEmptyLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-padding: 12 0; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
        return lbl;
    }
}
