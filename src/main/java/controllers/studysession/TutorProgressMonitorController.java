package controllers.studysession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.studysession.Course;
import models.studysession.StudentProgress;
import services.studysession.CourseService;
import services.studysession.StudentProgressService;
import utils.UserSession;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * TutorProgressMonitorController — Task 14
 *
 * Displays all courses owned by the logged-in Tutor and, for each course,
 * shows a card with aggregate stats and a per-student progress list.
 * Supports real-time search filtering by student name.
 *
 * Feature: course-learning-experience
 * Validates: Requirements 9.1 – 9.8
 */
public class TutorProgressMonitorController implements Initializable {

    // ── 14.1 — FXML fields ────────────────────────────────────────────────────

    @FXML private VBox vboxCourseCards;
    @FXML private TextField txtSearch;
    @FXML private Label lblStatus;

    // ── Services ──────────────────────────────────────────────────────────────

    private final CourseService courseService = new CourseService();
    private final StudentProgressService progressService = new StudentProgressService();

    // ── State for search filtering ─────────────────────────────────────────────
    // Each entry: [HBox studentRow, String studentName (lower-case)]
    private final List<HBox> allStudentRows = new ArrayList<>();
    private final List<String> allStudentNames = new ArrayList<>();

    // ── 14.2 — initialize ─────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Platform.runLater(this::loadData);
    }

    // ── 14.3 — loadData ───────────────────────────────────────────────────────

    /**
     * Loads all courses created by the logged-in tutor, then for each course
     * fetches all student progress records and renders a course card.
     */
    private void loadData() {
        vboxCourseCards.getChildren().clear();
        allStudentRows.clear();
        allStudentNames.clear();

        int tutorId = UserSession.getInstance().getUserId();

        try {
            List<Course> courses = courseService.findByCreator(tutorId);

            if (courses.isEmpty()) {
                Label noCoursesLbl = new Label("You have not created any courses yet.");
                noCoursesLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 20 0;");
                vboxCourseCards.getChildren().add(noCoursesLbl);
                setStatus("No courses found.");
                return;
            }

            for (Course course : courses) {
                List<StudentProgress> progressList;
                try {
                    progressList = progressService.getProgressForCourse(course.getId());
                } catch (SQLException e) {
                    System.err.println("[TutorProgressMonitorController] Failed to load progress for course "
                            + course.getId() + ": " + e.getMessage());
                    progressList = new ArrayList<>();
                }
                renderCourseCard(course, progressList);
            }

            setStatus("Loaded " + courses.size() + " course(s).");

            // Wire search after all cards are rendered (14.6)
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> filterStudentRows(newVal));

        } catch (SQLException e) {
            System.err.println("[TutorProgressMonitorController] Failed to load courses: " + e.getMessage());
            setStatus("Error loading data: " + e.getMessage());
        }
    }

    // ── 14.4 — renderCourseCard ───────────────────────────────────────────────

    /**
     * Creates a styled card VBox for a course containing:
     *  - Course name header
     *  - Aggregate stats row (total enrolled, avg progress, completed, streak≥3)
     *  - Student rows (or empty state)
     */
    private void renderCourseCard(Course course, List<StudentProgress> progressList) {
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-radius: 12;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.07),8,0,0,2);" +
            "-fx-padding: 16;"
        );

        // ── Course name header ────────────────────────────────────────────────
        Label courseNameLbl = new Label(course.getCourseName());
        courseNameLbl.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;" +
            "-fx-padding: 0 0 10 0;"
        );
        courseNameLbl.setWrapText(true);

        // ── Aggregate stats row ───────────────────────────────────────────────
        int totalEnrolled = progressList.size();
        int avgProgress = 0;
        int completedCount = 0;
        int streakGe3Count = 0;

        if (totalEnrolled > 0) {
            int sumProgress = 0;
            for (StudentProgress sp : progressList) {
                sumProgress += sp.getProgressPercentage();
                if (sp.getProgressPercentage() == 100) completedCount++;
                if (sp.getStudyStreakDays() >= 3) streakGe3Count++;
            }
            avgProgress = Math.round((float) sumProgress / totalEnrolled);
        }

        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.setStyle("-fx-padding: 0 0 12 0;");
        statsRow.getChildren().addAll(
            buildStatLabel("👥 Enrolled", String.valueOf(totalEnrolled), "#4f46e5"),
            buildStatLabel("📈 Avg Progress", avgProgress + "%", "#0891b2"),
            buildStatLabel("✅ Completed", String.valueOf(completedCount), "#16a34a"),
            buildStatLabel("🔥 Streak ≥ 3", String.valueOf(streakGe3Count), "#d97706")
        );

        // ── Separator ─────────────────────────────────────────────────────────
        Separator sep = new Separator();
        sep.setStyle("-fx-padding: 0 0 8 0;");

        card.getChildren().addAll(courseNameLbl, statsRow, sep);

        // ── Student rows ──────────────────────────────────────────────────────
        if (progressList.isEmpty()) {
            Label emptyLbl = new Label("No enrolled students yet.");
            emptyLbl.setStyle(
                "-fx-font-size: 12px; -fx-text-fill: #94a3b8;" +
                "-fx-font-style: italic; -fx-padding: 10 0 4 0;"
            );
            card.getChildren().add(emptyLbl);
        } else {
            // Column header row
            HBox headerRow = buildColumnHeaderRow();
            card.getChildren().add(headerRow);

            // Student data rows
            for (StudentProgress sp : progressList) {
                HBox row = buildStudentRow(sp);
                card.getChildren().add(row);

                // Register for search filtering
                allStudentRows.add(row);
                String name = sp.getStudentName() != null ? sp.getStudentName().toLowerCase() : "";
                allStudentNames.add(name);
            }
        }

        vboxCourseCards.getChildren().add(card);
    }

    /**
     * Builds a small stat label block with a title and value.
     */
    private VBox buildStatLabel(String title, String value, String color) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle(
            "-fx-background-color: " + color + "11;" +
            "-fx-background-radius: 8;" +
            "-fx-border-radius: 8;" +
            "-fx-border-color: " + color + "33;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 6 12;"
        );
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        box.getChildren().addAll(titleLbl, valueLbl);
        return box;
    }

    /**
     * Builds the column header row for the student table.
     */
    private HBox buildColumnHeaderRow() {
        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 8;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 0 0 1 0;"
        );

        String headerStyle = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #64748b;";

        Label hName     = new Label("Student");       hName.setStyle(headerStyle);     hName.setPrefWidth(140);
        Label hProgress = new Label("Progress");      hProgress.setStyle(headerStyle); hProgress.setPrefWidth(160);
        Label hTime     = new Label("Total Time");    hTime.setStyle(headerStyle);     hTime.setPrefWidth(80);
        Label hPomodoro = new Label("Pomodoros");     hPomodoro.setStyle(headerStyle); hPomodoro.setPrefWidth(80);
        Label hStreak   = new Label("Streak");        hStreak.setStyle(headerStyle);   hStreak.setPrefWidth(80);
        Label hLast     = new Label("Last Activity"); hLast.setStyle(headerStyle);     hLast.setPrefWidth(120);
        Label hStatus   = new Label("Status");        hStatus.setStyle(headerStyle);   hStatus.setPrefWidth(120);
        Label hEst      = new Label("Est. Completion"); hEst.setStyle(headerStyle);    hEst.setPrefWidth(130);

        header.getChildren().addAll(hName, hProgress, hTime, hPomodoro, hStreak, hLast, hStatus, hEst);
        return header;
    }

    /**
     * Builds a single student data row HBox.
     */
    private HBox buildStudentRow(StudentProgress sp) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(
            "-fx-padding: 8 8;" +
            "-fx-border-color: #f1f5f9;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // Student Name
        Label nameLbl = new Label(sp.getStudentName() != null ? sp.getStudentName() : "—");
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-font-weight: bold;");
        nameLbl.setPrefWidth(140);
        nameLbl.setWrapText(true);

        // Progress bar + % label
        int pct = sp.getProgressPercentage();
        String progressColor = pct >= 80 ? "#22c55e" : pct >= 40 ? "#f59e0b" : "#ef4444";
        ProgressBar progressBar = new ProgressBar(pct / 100.0);
        progressBar.setPrefWidth(100);
        progressBar.setPrefHeight(10);
        progressBar.setStyle("-fx-accent: " + progressColor + ";");
        Label pctLbl = new Label(pct + "%");
        pctLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151; -fx-padding: 0 0 0 6;");
        HBox progressCell = new HBox(4, progressBar, pctLbl);
        progressCell.setAlignment(Pos.CENTER_LEFT);
        progressCell.setPrefWidth(160);

        // Total Time (Xh Ym)
        int totalMin = sp.getTotalMinutesStudied();
        String timeStr = (totalMin / 60) + "h " + (totalMin % 60) + "m";
        Label timeLbl = new Label(timeStr);
        timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
        timeLbl.setPrefWidth(80);

        // Pomodoro Cycles
        Label pomLbl = new Label(String.valueOf(sp.getPomodoroCyclesCompleted()));
        pomLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
        pomLbl.setPrefWidth(80);

        // Study Streak
        Label streakLbl = new Label(sp.getStudyStreakDays() + " days");
        streakLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
        streakLbl.setPrefWidth(80);

        // Last Activity
        String lastActivityStr;
        if (sp.getLastActivityAt() != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
            lastActivityStr = sp.getLastActivityAt().format(fmt);
        } else {
            lastActivityStr = "Never";
        }
        Label lastLbl = new Label(lastActivityStr);
        lastLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
        lastLbl.setPrefWidth(120);

        // Completion Status badge
        String badgeText;
        String badgeBg;
        String badgeFg;
        if (pct == 100) {
            badgeText = "✅ Complete";
            badgeBg = "#dcfce7";
            badgeFg = "#16a34a";
        } else if (pct > 0) {
            badgeText = "🔄 In Progress";
            badgeBg = "#fef3c7";
            badgeFg = "#d97706";
        } else {
            badgeText = "⏳ Not Started";
            badgeBg = "#f1f5f9";
            badgeFg = "#64748b";
        }
        Label statusBadge = new Label(badgeText);
        statusBadge.setStyle(
            "-fx-background-color: " + badgeBg + ";" +
            "-fx-text-fill: " + badgeFg + ";" +
            "-fx-padding: 2 8;" +
            "-fx-background-radius: 10;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;"
        );
        HBox statusCell = new HBox(statusBadge);
        statusCell.setAlignment(Pos.CENTER_LEFT);
        statusCell.setPrefWidth(120);

        // Estimated Completion
        String estCompletion = computeEstimatedCompletion(sp);
        Label estLbl = new Label(estCompletion);
        estLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
        estLbl.setPrefWidth(130);
        estLbl.setWrapText(true);

        row.getChildren().addAll(nameLbl, progressCell, timeLbl, pomLbl, streakLbl, lastLbl, statusCell, estLbl);
        return row;
    }

    // ── 14.5 — computeEstimatedCompletion ─────────────────────────────────────

    /**
     * Computes the estimated completion date for a student.
     *
     * If progress == 100 → "✅ Completed"
     * Otherwise:
     *   daysSinceFirst = days between firstActivityAt.toLocalDate() and today (min 1)
     *   avgDailyProgress = max(totalMinutesStudied / daysSinceFirst, 1)
     *   daysNeeded = ceil((100 - progressPercentage) / avgDailyProgress)
     *   return LocalDate.now().plusDays(daysNeeded) formatted as "MMM d, yyyy"
     */
    private String computeEstimatedCompletion(StudentProgress sp) {
        if (sp.getProgressPercentage() == 100) {
            return "✅ Completed";
        }

        LocalDateTime firstActivity = sp.getFirstActivityAt();
        long daysSinceFirst;
        if (firstActivity == null) {
            daysSinceFirst = 1;
        } else {
            daysSinceFirst = ChronoUnit.DAYS.between(firstActivity.toLocalDate(), LocalDate.now());
            if (daysSinceFirst < 1) daysSinceFirst = 1;
        }

        double avgDailyProgress = (double) sp.getTotalMinutesStudied() / daysSinceFirst;
        if (avgDailyProgress < 1.0) avgDailyProgress = 1.0;

        int remaining = 100 - sp.getProgressPercentage();
        long daysNeeded = (long) Math.ceil(remaining / avgDailyProgress);

        LocalDate estimatedDate = LocalDate.now().plusDays(daysNeeded);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        return estimatedDate.format(fmt);
    }

    // ── 14.6 — Real-time search ───────────────────────────────────────────────

    /**
     * Filters all student rows across all course cards by student name.
     * Case-insensitive contains match. Course cards remain visible even if
     * all their student rows are hidden.
     */
    private void filterStudentRows(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            // Show all rows
            for (HBox row : allStudentRows) {
                row.setVisible(true);
                row.setManaged(true);
            }
        } else {
            String lower = searchText.toLowerCase();
            for (int i = 0; i < allStudentRows.size(); i++) {
                boolean matches = allStudentNames.get(i).contains(lower);
                allStudentRows.get(i).setVisible(matches);
                allStudentRows.get(i).setManaged(matches);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        }
    }
}
