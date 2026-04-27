package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Pos;
import services.studysession.TutorAnalyticsService;
import utils.UserSession;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * TutorAnalyticsController provides analytics view for tutors to monitor their course performance.
 * Displays enrollment metrics, per-course breakdown, and highlights the most popular course.
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5
 */
public class TutorAnalyticsController implements Initializable {

    // Summary metrics labels (Subtask 11.1)
    @FXML private Label lblTotalEnrolled;
    @FXML private Label lblActiveStudents;
    @FXML private Label lblCompletionRate;
    @FXML private Label lblTotalCourses;

    // Per-course breakdown table (Subtask 11.3)
    @FXML private TableView<CourseBreakdownRow> courseBreakdownTable;
    @FXML private TableColumn<CourseBreakdownRow, String> colCourseName;
    @FXML private TableColumn<CourseBreakdownRow, Integer> colEnrolledStudents;
    @FXML private TableColumn<CourseBreakdownRow, Integer> colProgress;

    // Most popular course card (Subtask 11.4)
    @FXML private VBox popularCourseCard;
    @FXML private Label lblPopularCourseName;
    @FXML private Label lblPopularCourseEnrollment;

    // Empty state (Subtask 11.5)
    @FXML private VBox emptyState;
    @FXML private VBox analyticsContent;

    private final TutorAnalyticsService tutorAnalyticsService = new TutorAnalyticsService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadAnalytics();
    }

    /**
     * Subtask 11.3: Setup TableView columns for per-course breakdown
     */
    private void setupTable() {
        if (courseBreakdownTable != null) {
            colCourseName.setCellValueFactory(new PropertyValueFactory<>("courseName"));
            colEnrolledStudents.setCellValueFactory(new PropertyValueFactory<>("enrolledStudents"));
            colProgress.setCellValueFactory(new PropertyValueFactory<>("progress"));
        }
    }

    /**
     * Subtask 11.2: Load analytics data from TutorAnalyticsService
     * Gets current user ID from UserSession and populates all metrics
     * Requirements: 9.1, 9.3
     */
    private void loadAnalytics() {
        try {
            // Get current tutor's user ID
            int userId = UserSession.getInstance().getUserId();

            // Check if tutor has any courses (Subtask 11.5)
            int totalCourses = tutorAnalyticsService.getTotalCoursesOwned(userId);
            
            if (totalCourses == 0) {
                // Show empty state
                showEmptyState();
                return;
            }

            // Hide empty state and show analytics content
            hideEmptyState();

            // Load summary metrics (Subtask 11.2)
            int totalEnrolled = tutorAnalyticsService.getTotalEnrolledStudents(userId);
            int activeStudents = tutorAnalyticsService.getActiveStudents(userId);
            double completionRate = tutorAnalyticsService.getCompletionRate(userId);

            // Populate summary labels (Subtask 11.1)
            lblTotalEnrolled.setText(String.valueOf(totalEnrolled));
            lblActiveStudents.setText(String.valueOf(activeStudents));
            lblCompletionRate.setText(String.format("%.1f%%", completionRate));
            lblTotalCourses.setText(String.valueOf(totalCourses));

            // Load per-course breakdown (Subtask 11.3)
            loadPerCourseBreakdown(userId);

            // Load most popular course (Subtask 11.4)
            loadMostPopularCourse(userId);

        } catch (SQLException e) {
            System.err.println("Failed to load tutor analytics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Subtask 11.3: Load per-course breakdown into TableView
     * Calls tutorAnalyticsService.getPerCourseBreakdown and populates table
     * Requirements: 9.2
     */
    private void loadPerCourseBreakdown(int userId) throws SQLException {
        if (courseBreakdownTable == null) return;

        List<Map<String, Object>> breakdown = tutorAnalyticsService.getPerCourseBreakdown(userId);
        courseBreakdownTable.getItems().clear();

        for (Map<String, Object> courseData : breakdown) {
            String courseName = (String) courseData.get("courseName");
            int enrolledCount = (int) courseData.get("enrolledCount");
            int progress = (int) courseData.get("progress");

            courseBreakdownTable.getItems().add(
                new CourseBreakdownRow(courseName, enrolledCount, progress)
            );
        }
    }

    /**
     * Subtask 11.4: Load and display most popular course
     * Calls tutorAnalyticsService.getMostPopularCourse and displays in styled card
     * Requirements: 9.4
     */
    private void loadMostPopularCourse(int userId) throws SQLException {
        if (popularCourseCard == null) return;

        Map<String, Object> popularCourse = tutorAnalyticsService.getMostPopularCourse(userId);

        if (popularCourse != null) {
            String courseName = (String) popularCourse.get("courseName");
            int enrolledCount = (int) popularCourse.get("enrolledCount");

            lblPopularCourseName.setText(courseName);
            lblPopularCourseEnrollment.setText(enrolledCount + " student" + (enrolledCount != 1 ? "s" : "") + " enrolled");
            
            popularCourseCard.setVisible(true);
            popularCourseCard.setManaged(true);
        } else {
            popularCourseCard.setVisible(false);
            popularCourseCard.setManaged(false);
        }
    }

    /**
     * Subtask 11.5: Show empty state when tutor has no courses
     * Requirements: 9.5
     */
    private void showEmptyState() {
        if (emptyState != null) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
        }
        if (analyticsContent != null) {
            analyticsContent.setVisible(false);
            analyticsContent.setManaged(false);
        }
    }

    /**
     * Hide empty state and show analytics content
     */
    private void hideEmptyState() {
        if (emptyState != null) {
            emptyState.setVisible(false);
            emptyState.setManaged(false);
        }
        if (analyticsContent != null) {
            analyticsContent.setVisible(true);
            analyticsContent.setManaged(true);
        }
    }

    @FXML
    private void handleRefresh() {
        loadAnalytics();
    }

    /**
     * Inner class to represent a row in the per-course breakdown table
     * Used for TableView data binding
     */
    public static class CourseBreakdownRow {
        private final String courseName;
        private final int enrolledStudents;
        private final int progress;

        public CourseBreakdownRow(String courseName, int enrolledStudents, int progress) {
            this.courseName = courseName;
            this.enrolledStudents = enrolledStudents;
            this.progress = progress;
        }

        public String getCourseName() {
            return courseName;
        }

        public int getEnrolledStudents() {
            return enrolledStudents;
        }

        public int getProgress() {
            return progress;
        }
    }
}
