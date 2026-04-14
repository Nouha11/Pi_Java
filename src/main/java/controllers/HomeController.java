package controllers;

import javafx.animation.ScaleTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class HomeController {

    @FXML private VBox cardCourses;
    @FXML private VBox cardForum;
    @FXML private VBox cardLibrary;
    @FXML private VBox cardQuiz;
    @FXML private VBox cardGames;
    @FXML private VBox cardRewards;

    @FXML
    public void initialize() {
        setupHoverAnimation(cardCourses);
        setupHoverAnimation(cardForum);
        setupHoverAnimation(cardLibrary);
        setupHoverAnimation(cardQuiz);
        setupHoverAnimation(cardGames);
        setupHoverAnimation(cardRewards);
    }

    // 🔥 Cleaned up animation: No more flickering shadows!
    private void setupHoverAnimation(VBox card) {
        if (card == null) return;

        // Subtle 2% scale for a desktop feel
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), card);
        scaleUp.setToX(1.02);
        scaleUp.setToY(1.02);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), card);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        card.setOnMouseEntered(e -> {
            scaleDown.stop();
            scaleUp.play();
        });

        card.setOnMouseExited(e -> {
            scaleUp.stop();
            scaleDown.play();
        });
    }

    // --- 🔥 Banner Button Handlers 🔥 ---

    @FXML
    void handleStartLearning(ActionEvent event) {
        NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML
    void handleViewLeaderboard(ActionEvent event) {
        NovaDashboardController.loadPage("/views/gamification/user_games.fxml");
    }

    // --- Card Navigation Handlers ---

    @FXML void goToCourses(MouseEvent event) {
        NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML void goToForum(MouseEvent event) {
        NovaDashboardController.loadPage("/views/forum/forum_feed.fxml");
    }

    @FXML void goToLibrary(MouseEvent event) {
        NovaDashboardController.loadPage("/views/library/BookListView.fxml");
    }

    @FXML void goToQuiz(MouseEvent event) {
        NovaDashboardController.loadPage("/views/quiz/quiz_play_list.fxml");
    }

    @FXML void goToGames(MouseEvent event) {
        NovaDashboardController.loadPage("/views/gamification/user_games.fxml");
    }

    @FXML void goToRewards(MouseEvent event) {
        NovaDashboardController.loadPage("/views/gamification/user_rewards.fxml");
    }
}