package controllers;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HomeController {

    // The container from FXML
    @FXML private Pane animationPane;

    @FXML private VBox cardCourses;
    @FXML private VBox cardForum;
    @FXML private VBox cardLibrary;
    @FXML private VBox cardGames;
    @FXML private VBox cardQuiz;

    // Animation variables
    private Canvas canvas;
    private List<Particle> particles = new ArrayList<>();
    private final int NUM_PARTICLES = 50; // Number of floating dots
    private final double MAX_DISTANCE = 110.0; // How close they need to be to draw a line

    @FXML
    public void initialize() {
        // 1. Start the Network Nodes Animation
        setupNetworkAnimation();

        // 2. Staggered Entry Animation for the Module Cards
        VBox[] cards = {cardCourses, cardForum, cardLibrary, cardGames, cardQuiz};
        for (int i = 0; i < cards.length; i++) {
            VBox card = cards[i];
            if (card == null) continue;

            card.setOpacity(0);
            card.setTranslateY(40);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(600), card);
            fadeIn.setToValue(1.0);

            TranslateTransition slideUp = new TranslateTransition(Duration.millis(600), card);
            slideUp.setToY(0);
            slideUp.setInterpolator(Interpolator.SPLINE(0.25, 1, 0.5, 1));

            ParallelTransition popIn = new ParallelTransition(fadeIn, slideUp);
            popIn.setDelay(Duration.millis(100 * i));
            popIn.play();
        }
    }

    private void setupNetworkAnimation() {
        if (animationPane == null) return;

        canvas = new Canvas();
        // Bind canvas size to the pane so it resizes dynamically
        canvas.widthProperty().bind(animationPane.widthProperty());
        canvas.heightProperty().bind(animationPane.heightProperty());
        animationPane.getChildren().add(canvas);

        // Generate random particles
        Random rand = new Random();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            particles.add(new Particle(
                    rand.nextDouble() * 600,
                    rand.nextDouble() * 340,
                    (rand.nextDouble() - 0.5) * 1.0, // X velocity
                    (rand.nextDouble() - 0.5) * 1.0  // Y velocity
            ));
        }

        // The animation loop (runs at 60fps)
        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            @Override
            public void handle(long now) {
                drawAnimation();
            }
        };
        timer.start();
    }

    private void drawAnimation() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        if (width == 0 || height == 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear previous frame
        gc.clearRect(0, 0, width, height);

        // Update positions and draw connecting lines
        for (int i = 0; i < particles.size(); i++) {
            Particle p1 = particles.get(i);
            p1.update(width, height);

            for (int j = i + 1; j < particles.size(); j++) {
                Particle p2 = particles.get(j);
                double dx = p1.x - p2.x;
                double dy = p1.y - p2.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                // If particles are close enough, draw a glowing line
                if (dist < MAX_DISTANCE) {
                    double opacity = 1.0 - (dist / MAX_DISTANCE);
                    // Glowing cyan color matching your theme
                    gc.setStroke(Color.color(0.0, 0.95, 1.0, opacity * 0.4));
                    gc.setLineWidth(1.2);
                    gc.strokeLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }

        // Draw the solid particle dots on top
        gc.setFill(Color.web("#00f2fe"));
        for (Particle p : particles) {
            gc.fillOval(p.x - p.radius, p.y - p.radius, p.radius * 2, p.radius * 2);
        }
    }

    // Inner class representing a single floating dot
    private static class Particle {
        double x, y, vx, vy;
        double radius = 2.5;

        Particle(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }

        void update(double width, double height) {
            x += vx;
            y += vy;

            // Bounce off walls softly
            if (x < 0 || x > width) vx *= -1;
            if (y < 0 || y > height) vy *= -1;

            // Safety bounds to prevent escaping
            if (x < 0) x = 0;
            if (x > width) x = width;
            if (y < 0) y = 0;
            if (y > height) y = height;
        }
    }

    @FXML void handleStartLearning(ActionEvent event) {
        NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML void goToCourses(MouseEvent event) {
        NovaDashboardController.loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML void goToForum(MouseEvent event) {
        NovaDashboardController.loadPage("/views/forum/forum_feed.fxml");
    }

    @FXML void goToLibrary(MouseEvent event) {
        NovaDashboardController.loadPage("/views/library/BookListView.fxml");
    }

    @FXML void goToGames(MouseEvent event) {
        NovaDashboardController.loadPage("/views/gamification/game_launcher.fxml");
    }

    @FXML void goToQuiz(MouseEvent event) {
        NovaDashboardController.loadPage("/views/quiz/quiz_play_list.fxml");
    }
}