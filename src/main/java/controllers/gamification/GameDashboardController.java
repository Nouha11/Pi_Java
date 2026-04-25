package controllers.gamification;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

public class GameDashboardController {

    @FXML private StackPane contentArea;

    @FXML
    public void initialize() {
        // Load Games screen by default on startup
        showGames();
    }

    @FXML
    public void showGames() {
        loadView("/views/gamification/game_list.fxml");
    }

    @FXML
    public void showPlayGames() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/gamification/game_launcher.fxml"));
            Parent view = loader.load();
            GameLauncherController ctrl = loader.getController();
            ctrl.setContentArea(contentArea);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            System.err.println("Navigation error: " + e.getMessage());
        }
    }

    @FXML
    public void showRewards() {
        loadView("/views/gamification/reward_list.fxml");
    }

    @FXML
    public void showStats() {
        loadView("/views/gamification/stats.fxml");
    }

    private void loadView(String fxmlPath) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            System.err.println("Navigation error: " + e.getMessage());
        }
    }
}
