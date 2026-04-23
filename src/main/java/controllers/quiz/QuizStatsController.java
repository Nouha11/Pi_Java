package controllers.quiz;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.QuestionService;
import services.quiz.QuizService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuizStatsController {

    // ── Summary cards ────────────────────────────────────────────
    @FXML private Label lblTotalQuizzes;
    @FXML private Label lblTotalQuestions;
    @FXML private Label lblTotalXP;
    @FXML private Label lblAvgQuestionsPerQuiz;

    // ── Charts ───────────────────────────────────────────────────
    @FXML private PieChart  difficultyPie;
    @FXML private BarChart<String, Number> xpPerDifficultyBar;

    // ── Top quizzes by question count ────────────────────────────
    @FXML private VBox topQuizzesBox;

    // ── Questions per quiz bar chart (custom) ────────────────────
    @FXML private VBox questionsPerQuizChart;

    private final QuizService     quizService     = new QuizService();
    private final QuestionService questionService = new QuestionService();

    @FXML
    public void initialize() {
        loadStats();
    }

    @FXML
    private void handleRefresh() {
        loadStats();
    }

    private void loadStats() {
        try {
            List<Quiz>     quizzes   = quizService.getAllQuizzes();
            List<Question> allQuestions = quizzes.stream()
                    .flatMap(q -> questionService.getQuestionsByQuizId(q.getId()).stream())
                    .collect(Collectors.toList());

            // ── Summary ──────────────────────────────────────────────────
            int totalQuizzes    = quizzes.size();
            int totalQuestions  = allQuestions.size();
            int totalXP         = allQuestions.stream().mapToInt(Question::getXpValue).sum();
            double avgQuestions = totalQuizzes > 0 ? (double) totalQuestions / totalQuizzes : 0;

            lblTotalQuizzes.setText(String.valueOf(totalQuizzes));
            lblTotalQuestions.setText(String.valueOf(totalQuestions));
            lblTotalXP.setText(totalXP + " XP");
            lblAvgQuestionsPerQuiz.setText(String.format("%.1f", avgQuestions));

            // ── Difficulty pie chart ─────────────────────────────────────
            difficultyPie.getData().clear();
            Map<String, Long> byDifficulty = allQuestions.stream()
                    .filter(q -> q.getDifficulty() != null)
                    .collect(Collectors.groupingBy(Question::getDifficulty, Collectors.counting()));

            byDifficulty.forEach((diff, count) ->
                    difficultyPie.getData().add(
                            new PieChart.Data(diff + " (" + count + ")", count)));
            styleChartLabels(difficultyPie);

            // ── XP per difficulty bar chart ──────────────────────────────
            xpPerDifficultyBar.getData().clear();
            XYChart.Series<String, Number> xpSeries = new XYChart.Series<>();
            xpSeries.setName("Total XP");
            Map<String, Integer> xpByDiff = allQuestions.stream()
                    .filter(q -> q.getDifficulty() != null)
                    .collect(Collectors.groupingBy(
                            Question::getDifficulty,
                            Collectors.summingInt(Question::getXpValue)));

            List.of("EASY", "MEDIUM", "HARD").forEach(d ->
                    xpSeries.getData().add(
                            new XYChart.Data<>(d, xpByDiff.getOrDefault(d, 0))));
            xpPerDifficultyBar.getData().add(xpSeries);

            // ── Top quizzes by question count ────────────────────────────
            topQuizzesBox.getChildren().clear();
            quizzes.stream()
                    .map(q -> {
                        int qCount = questionService.getQuestionsByQuizId(q.getId()).size();
                        return new Object[]{q, qCount};
                    })
                    .sorted((a, b) -> Integer.compare((int) b[1], (int) a[1]))
                    .limit(5)
                    .forEach(entry -> {
                        Quiz q      = (Quiz) entry[0];
                        int  qCount = (int)  entry[1];

                        HBox row = new HBox(10);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 10 14; " +
                                     "-fx-border-radius: 8; -fx-background-radius: 8; " +
                                     "-fx-border-color: #e4e8f0; -fx-border-width: 1;");

                        Label icon = new Label("📝");
                        icon.setStyle("-fx-font-size: 16px;");

                        Label name = new Label(q.getTitle());
                        name.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 13px; -fx-font-weight: bold;");
                        HBox.setHgrow(name, Priority.ALWAYS);

                        Label badge = new Label(qCount + " questions");
                        badge.setStyle("-fx-text-fill: #6366f1; -fx-font-weight: bold; -fx-font-size: 12px; " +
                                       "-fx-background-color: #eef2ff; -fx-background-radius: 6; -fx-padding: 3 8;");

                        row.getChildren().addAll(icon, name, badge);
                        topQuizzesBox.getChildren().add(row);
                    });

            // ── Questions per quiz horizontal bar chart ──────────────────
            buildQuestionsPerQuizChart(quizzes);

        } catch (Exception e) {
            System.err.println("QuizStats error: " + e.getMessage());
        }
    }

    private void buildQuestionsPerQuizChart(List<Quiz> quizzes) {
        if (questionsPerQuizChart == null) return;
        questionsPerQuizChart.getChildren().clear();

        List<Object[]> data = quizzes.stream()
                .map(q -> new Object[]{q.getTitle(), questionService.getQuestionsByQuizId(q.getId()).size()})
                .sorted(Comparator.comparingInt(a -> -(int) a[1]))
                .limit(8)
                .collect(Collectors.toList());

        if (data.isEmpty()) {
            questionsPerQuizChart.getChildren().add(new Label("No quizzes yet."));
            return;
        }

        int max = data.stream().mapToInt(r -> (int) r[1]).max().orElse(1);
        for (Object[] row : data) {
            String title = (String) row[0];
            int    count = (int)   row[1];
            double pct   = (double) count / max;

            HBox barRow = new HBox(8);
            barRow.setAlignment(Pos.CENTER_LEFT);

            Label lbl = new Label(title.length() > 22 ? title.substring(0, 20) + "…" : title);
            lbl.setMinWidth(160);
            lbl.setMaxWidth(160);
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

            Rectangle bar = new Rectangle(Math.max(pct * 220, 6), 18, Color.web("#6366f1"));
            bar.setArcWidth(4);
            bar.setArcHeight(4);

            Label valLbl = new Label(count + "q");
            valLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

            barRow.getChildren().addAll(lbl, bar, valLbl);
            questionsPerQuizChart.getChildren().add(barRow);
        }
    }

    private void styleChartLabels(PieChart chart) {
        chart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) applyLabelStyle(chart);
        });
        javafx.application.Platform.runLater(() -> applyLabelStyle(chart));
    }

    private void applyLabelStyle(PieChart chart) {
        chart.lookupAll(".chart-pie-label").forEach(node -> {
            if (node instanceof Text t) {
                t.setFill(Color.web("#1e293b"));
                t.setStyle("-fx-font-size: 12px;");
            }
        });
        chart.lookupAll(".chart-legend-item").forEach(node -> {
            if (node instanceof Label l) {
                l.setTextFill(Color.web("#475569"));
                l.setStyle("-fx-font-size: 12px;");
            }
        });
    }
}
