package controllers.forum;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import services.forum.PostService;
import java.util.Map;

public class ForumStatsController {

    @FXML private Label totalPostsLabel;
    @FXML private BarChart<String, Number> spacesChart;

    private PostService postService = new PostService();

    @FXML
    public void initialize() {
        // 1. Set Total Count
        int total = postService.getTotalPostsCount();
        totalPostsLabel.setText(String.valueOf(total));

        // 2. Populate Chart
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<String, Integer> stats = postService.getPostsPerSpace();

        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        spacesChart.getData().add(series);
    }
}