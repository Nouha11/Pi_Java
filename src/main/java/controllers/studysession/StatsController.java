package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Pos;
import services.studysession.CourseService;
import services.studysession.PlanningService;
import services.studysession.StudySessionService;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class StatsController implements Initializable {

    // Session stats
    @FXML private Label lblTotalSessions;
    @FXML private Label lblTotalXP;
    @FXML private Label lblAvgDuration;
    @FXML private Label lblTotalMinutes;

    // Course stats
    @FXML private Label lblTotalCourses;
    @FXML private Label lblPublishedCourses;

    // Planning stats
    @FXML private Label lblTotalPlannings;
    @FXML private Label lblScheduled;
    @FXML private Label lblCompleted;
    @FXML private Label lblMissed;
    @FXML private Label lblCancelled;

    // Burnout distribution
    @FXML private Label lblBurnoutLow;
    @FXML private Label lblBurnoutModerate;
    @FXML private Label lblBurnoutHigh;

    // Chart containers
    @FXML private VBox xpPerCourseChart;
    @FXML private VBox difficultyChart;
    @FXML private HBox burnoutBars;

    private final StudySessionService sessionService = new StudySessionService();
    private final CourseService courseService = new CourseService();
    private final PlanningService planningService = new PlanningService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadStats();
    }

    @FXML
    private void handleRefresh() { loadStats(); }

    private void loadStats() {
        loadSessionStats();
        loadCourseStats();
        loadPlanningStats();
        loadBurnoutChart();
        loadXpPerCourseChart();
        loadDifficultyChart();
    }

    private void loadSessionStats() {
        try {
            Map<String, Object> stats = sessionService.getGlobalStats();
            lblTotalSessions.setText(String.valueOf(stats.getOrDefault("totalSessions", 0)));
            lblTotalXP.setText(String.valueOf(stats.getOrDefault("totalXP", 0)) + " XP");
            lblAvgDuration.setText(String.valueOf(stats.getOrDefault("avgDuration", 0)) + " min");
            lblTotalMinutes.setText(String.valueOf(stats.getOrDefault("totalMinutes", 0)) + " min");
        } catch (SQLException e) {
            System.err.println("Session stats error: " + e.getMessage());
        }
    }

    private void loadCourseStats() {
        try {
            List<?> all = courseService.findAll();
            long published = all.stream()
                    .filter(c -> ((models.studysession.Course) c).isPublished()).count();
            lblTotalCourses.setText(String.valueOf(all.size()));
            lblPublishedCourses.setText(published + " published");
        } catch (SQLException e) {
            System.err.println("Course stats error: " + e.getMessage());
        }
    }

    private void loadPlanningStats() {
        try {
            List<Object[]> stats = planningService.countByStatus();
            int total = 0, scheduled = 0, completed = 0, missed = 0, cancelled = 0;
            for (Object[] row : stats) {
                String status = (String) row[0];
                int cnt = (int) row[1];
                total += cnt;
                switch (status) {
                    case "SCHEDULED"  -> scheduled = cnt;
                    case "COMPLETED"  -> completed = cnt;
                    case "MISSED"     -> missed = cnt;
                    case "CANCELLED"  -> cancelled = cnt;
                }
            }
            lblTotalPlannings.setText(String.valueOf(total));
            lblScheduled.setText("📅 " + scheduled + " Scheduled");
            lblCompleted.setText("✅ " + completed + " Completed");
            lblMissed.setText("❌ " + missed + " Missed");
            lblCancelled.setText("🚫 " + cancelled + " Cancelled");
        } catch (SQLException e) {
            System.err.println("Planning stats error: " + e.getMessage());
        }
    }

    private void loadBurnoutChart() {
        try {
            Map<String, Integer> dist = sessionService.getBurnoutDistribution();
            int low = dist.getOrDefault("LOW", 0);
            int mod = dist.getOrDefault("MODERATE", 0);
            int high = dist.getOrDefault("HIGH", 0);
            int total = low + mod + high;

            lblBurnoutLow.setText("🟢 LOW: " + low);
            lblBurnoutModerate.setText("🟡 MODERATE: " + mod);
            lblBurnoutHigh.setText("🔴 HIGH: " + high);

            if (burnoutBars != null && total > 0) {
                burnoutBars.getChildren().clear();
                burnoutBars.getChildren().addAll(
                        makeBar("LOW", low, total, "#27ae60"),
                        makeBar("MOD", mod, total, "#f39c12"),
                        makeBar("HIGH", high, total, "#e74c3c")
                );
            }
        } catch (SQLException e) {
            System.err.println("Burnout chart error: " + e.getMessage());
        }
    }

    private void loadXpPerCourseChart() {
        if (xpPerCourseChart == null) return;
        try {
            List<Object[]> data = sessionService.getXpPerCourse();
            xpPerCourseChart.getChildren().clear();
            if (data.isEmpty()) {
                xpPerCourseChart.getChildren().add(new Label("No data yet."));
                return;
            }
            int maxXp = data.stream().mapToInt(r -> (int) r[1]).max().orElse(1);
            for (Object[] row : data) {
                String course = (String) row[0];
                int xp = (int) row[1];
                double pct = (double) xp / maxXp;

                HBox row2 = new HBox(8);
                row2.setAlignment(Pos.CENTER_LEFT);
                Label lbl = new Label(course != null ? course : "Unknown");
                lbl.setMinWidth(150); lbl.setMaxWidth(150);
                lbl.setStyle("-fx-font-size: 12px;");

                Rectangle bar = new Rectangle(pct * 250, 18, Color.web("#3498db"));
                bar.setArcWidth(4); bar.setArcHeight(4);
                Label valLbl = new Label(xp + " XP");
                valLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
                row2.getChildren().addAll(lbl, bar, valLbl);
                xpPerCourseChart.getChildren().add(row2);
            }
        } catch (SQLException e) {
            xpPerCourseChart.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private void loadDifficultyChart() {
        if (difficultyChart == null) return;
        try {
            List<Object[]> data = courseService.countByDifficulty();
            difficultyChart.getChildren().clear();
            if (data.isEmpty()) {
                difficultyChart.getChildren().add(new Label("No courses yet."));
                return;
            }
            int max = data.stream().mapToInt(r -> (int) r[1]).max().orElse(1);
            for (Object[] row : data) {
                String diff = (String) row[0];
                int cnt = (int) row[1];
                String color = switch (diff) {
                    case "BEGINNER" -> "#27ae60";
                    case "INTERMEDIATE" -> "#f39c12";
                    case "ADVANCED" -> "#e74c3c";
                    default -> "#95a5a6";
                };
                HBox r = new HBox(8);
                r.setAlignment(Pos.CENTER_LEFT);
                Label lbl = new Label(diff);
                lbl.setMinWidth(110); lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
                Rectangle bar = new Rectangle((double) cnt / max * 180, 18, Color.web(color));
                bar.setArcWidth(4); bar.setArcHeight(4);
                Label cl = new Label(cnt + " courses");
                cl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
                r.getChildren().addAll(lbl, bar, cl);
                difficultyChart.getChildren().add(r);
            }
        } catch (SQLException e) {
            difficultyChart.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private VBox makeBar(String label, int value, int total, String color) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.BOTTOM_CENTER);
        double height = total > 0 ? Math.max(((double) value / total) * 120, 4) : 4;
        Rectangle bar = new Rectangle(50, height, Color.web(color));
        bar.setArcWidth(6); bar.setArcHeight(6);
        Label lbl = new Label(label + "\n" + value);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        box.getChildren().addAll(bar, lbl);
        return box;
    }
}