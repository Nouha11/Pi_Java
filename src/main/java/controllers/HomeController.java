package controllers;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Random;

public class HomeController {

    @FXML private Pane animationPane;
    @FXML private VBox cardCourses;
    @FXML private VBox cardForum;
    @FXML private VBox cardLibrary;
    @FXML private VBox cardQuiz;
    @FXML private VBox cardGames;
    @FXML private VBox cardRewards;

    // 🔥 Added the ID references for the new SVG icons
    @FXML private StackPane iconCourses;
    @FXML private StackPane iconForum;
    @FXML private StackPane iconLibrary;
    @FXML private StackPane iconQuiz;
    @FXML private StackPane iconGames;
    @FXML private StackPane iconRewards;

    @FXML
    public void initialize() {
        setupHoverAnimation(cardCourses);
        setupHoverAnimation(cardForum);
        setupHoverAnimation(cardLibrary);
        setupHoverAnimation(cardQuiz);
        setupHoverAnimation(cardGames);
        setupHoverAnimation(cardRewards);

        // 🔥 Start the idle floating animation for the SVG icons
        setupIconIdleAnimation(iconCourses);
        setupIconIdleAnimation(iconForum);
        setupIconIdleAnimation(iconLibrary);
        setupIconIdleAnimation(iconQuiz);
        setupIconIdleAnimation(iconGames);
        setupIconIdleAnimation(iconRewards);

        if (animationPane != null) {
            // Prevent animation from bleeding outside the banner
            javafx.scene.shape.Rectangle clipBox = new javafx.scene.shape.Rectangle();
            clipBox.widthProperty().bind(animationPane.widthProperty());
            clipBox.heightProperty().bind(animationPane.heightProperty());
            clipBox.setArcWidth(16);
            clipBox.setArcHeight(16);
            animationPane.setClip(clipBox);

            startHUDDataStream();
        }
    }

    // 🔥 Makes the icons gently levitate inside their cards
    private void setupIconIdleAnimation(StackPane iconContainer) {
        if (iconContainer == null) return;

        TranslateTransition floatAnim = new TranslateTransition(Duration.millis(1500), iconContainer);
        floatAnim.setByY(-6); // Float up 6 pixels
        floatAnim.setCycleCount(Animation.INDEFINITE);
        floatAnim.setAutoReverse(true);
        // Add a slight random delay so they don't all float in exact sync
        floatAnim.setDelay(Duration.millis(Math.random() * 1000));
        floatAnim.play();
    }

    // Sleek "Gamer HUD" particle stream
    private void startHUDDataStream() {
        Random random = new Random();
        String[] colors = {"#4f46e5", "#818cf8", "#c7d2fe", "#3b82f6"};

        for (int i = 0; i < 30; i++) {
            Shape particle;
            int type = random.nextInt(3);
            double size = random.nextDouble() * 8 + 4;

            if (type == 0) {
                Rectangle rect = new Rectangle(size, size);
                rect.setFill(Color.TRANSPARENT);
                rect.setStroke(Color.web(colors[random.nextInt(colors.length)]));
                rect.setStrokeWidth(1.5);
                particle = rect;
            } else if (type == 1) {
                Text cross = new Text("+");
                cross.setFont(Font.font("Consolas", size * 1.5));
                cross.setFill(Color.web(colors[random.nextInt(colors.length)]));
                particle = cross;
            } else {
                Circle dot = new Circle(size / 2);
                dot.setFill(Color.web(colors[random.nextInt(colors.length)]));
                dot.setEffect(new GaussianBlur(4));
                particle = dot;
            }

            particle.setOpacity(random.nextDouble() * 0.4 + 0.1);
            particle.setTranslateX(random.nextDouble() * 1200);
            particle.setTranslateY(random.nextDouble() * 100 + 200);

            animationPane.getChildren().add(particle);

            TranslateTransition floatAnim = new TranslateTransition(Duration.seconds(random.nextInt(15) + 10), particle);
            floatAnim.setByY(-300);
            floatAnim.setByX((random.nextDouble() - 0.5) * 50);
            floatAnim.setCycleCount(Animation.INDEFINITE);
            floatAnim.setInterpolator(Interpolator.LINEAR);
            floatAnim.play();

            if (type != 2) {
                RotateTransition spinAnim = new RotateTransition(Duration.seconds(random.nextInt(8) + 5), particle);
                spinAnim.setByAngle(random.nextBoolean() ? 180 : -180);
                spinAnim.setCycleCount(Animation.INDEFINITE);
                spinAnim.setInterpolator(Interpolator.LINEAR);
                spinAnim.play();
            }
        }
    }

    private void setupHoverAnimation(VBox card) {
        if (card == null) return;
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), card);
        scaleUp.setToX(1.02);
        scaleUp.setToY(1.02);
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), card);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        card.setOnMouseEntered(e -> { scaleDown.stop(); scaleUp.play(); });
        card.setOnMouseExited(e -> { scaleUp.stop(); scaleDown.play(); });
    }

    // --- Banner Button Handlers ---
    @FXML void handleStartLearning(ActionEvent event) {
        NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML void handleViewLeaderboard(ActionEvent event) {
        NovaDashboardController.loadPage("/views/gamification/leaderboard.fxml");
    }

    // --- Card Navigation Handlers ---
    @FXML void goToCourses(MouseEvent event) { NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml"); }
    @FXML void goToForum(MouseEvent event) { NovaDashboardController.loadPage("/views/forum/forum_feed.fxml"); }
    @FXML void goToLibrary(MouseEvent event) { NovaDashboardController.loadPage("/views/library/BookListView.fxml"); }
    @FXML void goToQuiz(MouseEvent event) { NovaDashboardController.loadPage("/views/quiz/quiz_play_list.fxml"); }
    @FXML void goToGames(MouseEvent event) { NovaDashboardController.loadPage("/views/gamification/user_games.fxml"); }
    @FXML void goToRewards(MouseEvent event) { NovaDashboardController.loadPage("/views/gamification/user_rewards.fxml"); }
}