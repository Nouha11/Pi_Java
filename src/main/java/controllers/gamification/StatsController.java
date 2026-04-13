package controllers.gamification;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;
import services.gamification.RewardService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatsController {

    @FXML private Label totalGamesLabel;
    @FXML private Label activeGamesLabel;
    @FXML private Label totalRewardsLabel;
    @FXML private Label activeRewardsLabel;
    @FXML private Label totalXPLabel;
    @FXML private Label totalTokensLabel;
    @FXML private PieChart gameTypePie;
    @FXML private PieChart rewardTypePie;
    @FXML private BarChart<String, Number> difficultyBar;
    @FXML private VBox topRewardsBox;

    private final GameService   gameService   = new GameService();
    private final RewardService rewardService = new RewardService();

    @FXML
    public void initialize() {
        try {
            List<Game>   games   = gameService.getAllGames();
            List<Reward> rewards = rewardService.getAllRewards();

            // ── Summary cards ────────────────────────────────────────────
            totalGamesLabel.setText(String.valueOf(games.size()));
            activeGamesLabel.setText(String.valueOf(games.stream().filter(Game::isActive).count()));
            totalRewardsLabel.setText(String.valueOf(rewards.size()));
            activeRewardsLabel.setText(String.valueOf(rewards.stream().filter(Reward::isActive).count()));
            totalXPLabel.setText(String.valueOf(games.stream().mapToInt(Game::getRewardXP).sum()));
            totalTokensLabel.setText(String.valueOf(games.stream().mapToInt(Game::getRewardTokens).sum()));

            // ── Game type pie chart ───────────────────────────────────────
            Map<String, Long> byType = games.stream()
                    .collect(Collectors.groupingBy(Game::getType, Collectors.counting()));
            byType.forEach((type, count) ->
                    gameTypePie.getData().add(new PieChart.Data(type + " (" + count + ")", count)));
            styleChartLabels(gameTypePie);

            // ── Reward type pie chart ─────────────────────────────────────
            Map<String, Long> byRwType = rewards.stream()
                    .collect(Collectors.groupingBy(Reward::getType, Collectors.counting()));
            byRwType.forEach((type, count) ->
                    rewardTypePie.getData().add(new PieChart.Data(type + " (" + count + ")", count)));
            styleChartLabels(rewardTypePie);

            // ── Difficulty bar chart ──────────────────────────────────────
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Games");
            Map<String, Long> byDiff = games.stream()
                    .collect(Collectors.groupingBy(Game::getDifficulty, Collectors.counting()));
            List.of("EASY", "MEDIUM", "HARD").forEach(d ->
                    series.getData().add(new XYChart.Data<>(d, byDiff.getOrDefault(d, 0L))));
            difficultyBar.getData().add(series);

            // ── Top rewards by value ──────────────────────────────────────
            rewards.stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(r -> {
                        HBox row = new HBox(10);
                        row.setStyle("-fx-background-color: #0d2137; -fx-padding: 8 14; " +
                                     "-fx-border-radius: 4; -fx-background-radius: 4;");
                        Label name = new Label("🏆 " + r.getName());
                        name.setStyle("-fx-text-fill: #e0e0f0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                        name.setMaxWidth(Double.MAX_VALUE);
                        HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
                        Label type = new Label("[" + r.getType() + "]");
                        type.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");
                        Label val = new Label("+" + r.getValue() + " pts");
                        val.setStyle("-fx-text-fill: #4caf50; -fx-font-family: 'Consolas'; -fx-font-size: 13px; -fx-font-weight: bold;");
                        row.getChildren().addAll(name, type, val);
                        topRewardsBox.getChildren().add(row);
                    });

        } catch (Exception e) {
            System.err.println("Stats error: " + e.getMessage());
        }
    }

    /** Force pie chart label Text nodes to be visible on dark background. */
    private void styleChartLabels(PieChart chart) {
        // Labels are added after layout — use a listener on the scene
        chart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) applyLabelStyle(chart);
        });
        // Also apply immediately in case scene is already set
        javafx.application.Platform.runLater(() -> applyLabelStyle(chart));
    }

    private void applyLabelStyle(PieChart chart) {
        chart.lookupAll(".chart-pie-label").forEach(node -> {
            if (node instanceof Text t) {
                t.setFill(Color.web("#e0e0f0"));
                t.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            }
        });
        chart.lookupAll(".chart-legend-item").forEach(node -> {
            if (node instanceof Label l) {
                l.setTextFill(Color.web("#e0e0f0"));
                l.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            }
        });
    }
}
