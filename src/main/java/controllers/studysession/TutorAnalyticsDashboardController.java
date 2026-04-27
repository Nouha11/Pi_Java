package controllers.studysession;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.util.Duration;
import services.studysession.AnalyticsService;
import utils.MyConnection;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Controller for the Tutor Analytics Dashboard.
 *
 * All data is scoped strictly to the logged-in tutor's courses (via {@code tutorId}).
 * Data is loaded asynchronously on a background thread; the UI is updated via
 * {@code Platform.runLater()}. Spinners are shown during loading and hidden with a
 * 300 ms {@code FadeTransition} once data is ready.
 *
 * The parent controller ({@link TutorDashboardController}) must call
 * {@link #setTutorId(int)} after loading this FXML to scope all queries.
 *
 * Requirements: 8.3, 8.4, 8.5, 8.6, 8.7, 15.3, 15.5
 */
public class TutorAnalyticsDashboardController implements Initializable {

    // ── Difficulty color constants ────────────────────────────────────────────
    private static final String COLOR_BEGINNER     = "#22c55e";
    private static final String COLOR_INTERMEDIATE = "#f59e0b";
    private static final String COLOR_ADVANCED     = "#ef4444";

    // ── Stat card labels ──────────────────────────────────────────────────────
    @FXML private Label lblMyStudents;
    @FXML private Label lblMyCourses;
    @FXML private Label lblMyCompletionRate;
    @FXML private Label lblMySessions;

    // ── Charts ────────────────────────────────────────────────────────────────
    @FXML private LineChart<String, Number> chartMySessionsWeekly;
    @FXML private BarChart<String, Number>  chartMyTimeByCourse;
    @FXML private PieChart                  chartMyDifficulty;

    // ── Spinners ──────────────────────────────────────────────────────────────
    @FXML private ProgressIndicator spinnerSessionsWeekly;
    @FXML private ProgressIndicator spinnerTimeByCourse;
    @FXML private ProgressIndicator spinnerDifficulty;

    // ── No-data labels ────────────────────────────────────────────────────────
    @FXML private Label lblNoDataSessionsWeekly;
    @FXML private Label lblNoDataTimeByCourse;
    @FXML private Label lblNoDataDifficulty;

    // ── Service ───────────────────────────────────────────────────────────────
    private final AnalyticsService analyticsService = new AnalyticsService();

    // ── Tutor identity ────────────────────────────────────────────────────────
    /** Set by the parent controller after FXML load. -1 means not yet set. */
    private int tutorId = -1;

    // ─────────────────────────────────────────────────────────────────────────
    //  Initializable
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Show all spinners immediately while waiting for tutorId / data
        showAllSpinners();
        // Data will be loaded once setTutorId() is called by the parent controller.
        // If tutorId is already set (unlikely on first init), load now.
        if (tutorId >= 0) {
            loadAllDataAsync();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API — called by parent controller
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets the tutor ID and triggers asynchronous data loading.
     * Must be called by the parent controller after loading this FXML.
     *
     * @param tutorId the {@code userId} of the logged-in tutor
     * Requirements: 8.3, 8.7
     */
    public void setTutorId(int tutorId) {
        this.tutorId = tutorId;
        loadAllDataAsync();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Spinner helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showAllSpinners() {
        for (ProgressIndicator pi : new ProgressIndicator[]{
                spinnerSessionsWeekly, spinnerTimeByCourse, spinnerDifficulty}) {
            pi.setVisible(true);
            pi.setManaged(true);
            pi.setOpacity(1.0);
        }
    }

    /**
     * Fades out a spinner over 300 ms, then hides it.
     * Requirements: 15.5
     */
    private void hideSpinner(ProgressIndicator spinner) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), spinner);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            spinner.setVisible(false);
            spinner.setManaged(false);
        });
        ft.play();
    }

    /**
     * Fades in a chart/content node over 300 ms (opacity 0 → 1).
     * Requirements: 15.5
     */
    private void fadeInNode(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Async data loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads all dashboard data on a background thread and updates the UI via
     * {@code Platform.runLater()}.
     * Requirements: 8.3, 8.4, 8.5, 8.6, 15.3, 15.5
     */
    private void loadAllDataAsync() {
        if (tutorId < 0) {
            // tutorId not yet set — spinners remain visible until setTutorId() is called
            return;
        }

        final int tid = tutorId;

        Task<Void> task = new Task<>() {
            // Fetched data holders
            private long myStudents;
            private long myCourses;
            private double myCompletionRate;
            private long mySessions;

            private List<Object[]> sessionsByWeek;
            private List<Object[]> timeByCourse;
            private Map<String, Integer> difficultyData;

            @Override
            protected Void call() throws Exception {
                // ── Stat card values ──────────────────────────────────────────
                // cardMyStudents: distinct students with ACCEPTED enrollment in tutor's courses
                myStudents = queryCount(
                    "SELECT COUNT(DISTINCT er.student_id) " +
                    "FROM enrollment_requests er " +
                    "INNER JOIN course c ON er.course_id = c.id " +
                    "WHERE c.created_by_id = ? AND er.status = 'ACCEPTED'",
                    tid);

                // cardMyCourses: active/published courses created by this tutor
                myCourses = queryCount(
                    "SELECT COUNT(*) FROM course WHERE created_by_id = ? AND is_published = 1",
                    tid);

                // cardMyCompletionRate: avg progress_percentage for tutor's courses
                myCompletionRate = queryAvg(
                    "SELECT AVG(scp.progress_percentage) " +
                    "FROM student_course_progress scp " +
                    "INNER JOIN course c ON scp.course_id = c.id " +
                    "WHERE c.created_by_id = ?",
                    tid);

                // cardMySessions: total study sessions in tutor's courses
                mySessions = queryCount(
                    "SELECT COUNT(ss.id) " +
                    "FROM study_session ss " +
                    "INNER JOIN planning p ON ss.planning_id = p.id " +
                    "INNER JOIN course   c ON p.course_id    = c.id " +
                    "WHERE c.created_by_id = ?",
                    tid);

                // ── Chart data ────────────────────────────────────────────────
                sessionsByWeek = analyticsService.getSessionCountByWeek(8, tid);
                timeByCourse   = analyticsService.getTimeSpentByCourse(null, null, tid);
                difficultyData = analyticsService.getSessionCountByDifficulty(tid);

                Platform.runLater(() -> {
                    // Stat cards
                    lblMyStudents.setText(String.valueOf(myStudents));
                    lblMyCourses.setText(String.valueOf(myCourses));
                    lblMyCompletionRate.setText(String.format("%.1f%%", myCompletionRate));
                    lblMySessions.setText(String.valueOf(mySessions));

                    // Charts
                    populateSessionsWeeklyChart(sessionsByWeek);
                    populateTimeByCourseChart(timeByCourse);
                    populateDifficultyChart(difficultyData);

                    // Hide all spinners with fade
                    hideSpinner(spinnerSessionsWeekly);
                    hideSpinner(spinnerTimeByCourse);
                    hideSpinner(spinnerDifficulty);
                });
                return null;
            }
        };

        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = task.getException();
            System.err.println("[TutorAnalyticsDashboard] Data load failed: " +
                    (ex != null ? ex.getMessage() : "unknown error"));
            // Hide spinners even on failure so the UI is not stuck
            hideSpinner(spinnerSessionsWeekly);
            hideSpinner(spinnerTimeByCourse);
            hideSpinner(spinnerDifficulty);
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chart population helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Populates the Sessions Per Week line chart.
     * Requirements: 8.4
     */
    private void populateSessionsWeeklyChart(List<Object[]> rows) {
        chartMySessionsWeekly.getData().clear();
        if (rows == null || rows.isEmpty()) {
            showNoData(lblNoDataSessionsWeekly, chartMySessionsWeekly);
            return;
        }
        hideNoData(lblNoDataSessionsWeekly, chartMySessionsWeekly);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sessions");
        for (Object[] row : rows) {
            series.getData().add(new XYChart.Data<>((String) row[0], (Integer) row[1]));
        }
        chartMySessionsWeekly.getData().add(series);
        installTooltipsOnSeries(series);
        fadeInNode(chartMySessionsWeekly);
    }

    /**
     * Populates the Time Spent by Course bar chart.
     * Requirements: 8.4
     */
    private void populateTimeByCourseChart(List<Object[]> rows) {
        chartMyTimeByCourse.getData().clear();
        if (rows == null || rows.isEmpty()) {
            showNoData(lblNoDataTimeByCourse, chartMyTimeByCourse);
            return;
        }
        hideNoData(lblNoDataTimeByCourse, chartMyTimeByCourse);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Minutes");
        for (Object[] row : rows) {
            String courseName = (String) row[0];
            if (courseName == null) courseName = "(Unknown)";
            // Truncate to 20 chars per requirement 4.4 pattern
            if (courseName.length() > 20) {
                courseName = courseName.substring(0, 20);
            }
            series.getData().add(new XYChart.Data<>(courseName, (Integer) row[1]));
        }
        chartMyTimeByCourse.getData().add(series);
        installTooltipsOnSeries(series);
        fadeInNode(chartMyTimeByCourse);
    }

    /**
     * Populates the Session Distribution by Difficulty pie chart with DifficultyColor fills.
     * Requirements: 8.4
     */
    private void populateDifficultyChart(Map<String, Integer> data) {
        chartMyDifficulty.getData().clear();
        if (data == null || data.isEmpty()) {
            showNoData(lblNoDataDifficulty, chartMyDifficulty);
            return;
        }
        hideNoData(lblNoDataDifficulty, chartMyDifficulty);

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        chartMyDifficulty.setData(pieData);
        fadeInNode(chartMyDifficulty);

        // Apply DifficultyColor fills after the chart has been laid out
        Platform.runLater(() -> {
            for (PieChart.Data slice : chartMyDifficulty.getData()) {
                String difficulty = slice.getName().split(" ")[0].toUpperCase();
                String color = difficultyColor(difficulty);
                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + color + ";");
                    // Install tooltip on each pie slice
                    Tooltip tooltip = new Tooltip(slice.getName() + ": " + (int) slice.getPieValue());
                    Tooltip.install(slice.getNode(), tooltip);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the DifficultyColor hex string for a given difficulty label. */
    private String difficultyColor(String difficulty) {
        if (difficulty == null) return "#94a3b8";
        return switch (difficulty.toUpperCase()) {
            case "BEGINNER"     -> COLOR_BEGINNER;
            case "INTERMEDIATE" -> COLOR_INTERMEDIATE;
            case "ADVANCED"     -> COLOR_ADVANCED;
            default             -> "#94a3b8";
        };
    }

    /** Shows the no-data label and hides the chart node. */
    private void showNoData(Label noDataLabel, Node chart) {
        noDataLabel.setVisible(true);
        noDataLabel.setManaged(true);
        chart.setVisible(false);
        chart.setManaged(false);
    }

    /** Hides the no-data label and shows the chart node. */
    private void hideNoData(Label noDataLabel, Node chart) {
        noDataLabel.setVisible(false);
        noDataLabel.setManaged(false);
        chart.setVisible(true);
        chart.setManaged(true);
    }

    /** Installs tooltips on all data points in an XYChart series. */
    private <X, Y> void installTooltipsOnSeries(XYChart.Series<X, Y> series) {
        for (XYChart.Data<X, Y> data : series.getData()) {
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), new Tooltip(data.getXValue() + ": " + data.getYValue()));
            }
        }
        // Also install after chart renders (nodes may not exist yet at this point)
        Platform.runLater(() -> {
            for (XYChart.Data<X, Y> data : series.getData()) {
                if (data.getNode() != null) {
                    Tooltip.install(data.getNode(), new Tooltip(data.getXValue() + ": " + data.getYValue()));
                }
            }
        });
    }

    /**
     * Executes a COUNT/scalar query with a single integer parameter and returns the long result.
     *
     * @param sql   parameterized SQL with exactly one {@code ?} placeholder
     * @param param the integer value to bind
     * @return the first column of the first row as a long, or 0 on error
     */
    private long queryCount(String sql, int param) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[TutorAnalyticsDashboard] Count query failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Executes an AVG query with a single integer parameter and returns the double result.
     *
     * @param sql   parameterized SQL with exactly one {@code ?} placeholder
     * @param param the integer value to bind
     * @return the first column of the first row as a double, or 0.0 on error / null result
     */
    private double queryAvg(String sql, int param) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double val = rs.getDouble(1);
                        return rs.wasNull() ? 0.0 : val;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[TutorAnalyticsDashboard] Avg query failed: " + e.getMessage());
        }
        return 0.0;
    }
}
