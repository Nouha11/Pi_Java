package controllers;

import javafx.animation.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NovaDashboardController {

    // --- Elements for the Splash Screen Animation ---
    @FXML private BorderPane mainAppUI;
    @FXML private StackPane splashScreen;
    @FXML private ImageView splashLogo; // ⬅️ The missing ImageView for the breathing animation!

    // --- Existing Navigation Elements ---
    @FXML private StackPane contentArea;
    private static StackPane staticContentArea;

    // --- Buttons for Active State Logic ---
    @FXML private Button btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards;
    private List<Button> navButtons;

    @FXML
    public void initialize() {
        if (contentArea != null) {
            staticContentArea = contentArea;

            // Group buttons for easy management (Check for null to prevent crashes if FXML isn't linked yet)
            if (btnHome != null && btnCourses != null) {
                navButtons = Arrays.asList(btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards);
            }

            // 1. Pre-load the home page silently in the background
            loadPageSilently("/views/home.fxml");

            // 2. Play the awesome startup animation
            if (splashScreen != null && mainAppUI != null) {
                playCinematicStartup();
            }
        }
    }

    // 🎬 THE CINEMATIC SPLASH ANIMATION 🎬
    private void playCinematicStartup() {
        mainAppUI.setOpacity(0);
        mainAppUI.setScaleX(0.92);
        mainAppUI.setScaleY(0.92);

        // 1. Make the logo "breathe" while syncing (only if linked in FXML)
        ScaleTransition pulse = null;
        if (splashLogo != null) {
            pulse = new ScaleTransition(Duration.millis(800), splashLogo);
            pulse.setByX(0.08);
            pulse.setByY(0.08);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
        }

        // 2. Hold for 1.2 seconds, then transition
        PauseTransition holdSplash = new PauseTransition(Duration.seconds(1.2));
        ScaleTransition finalPulse = pulse; // For use inside the lambda

        holdSplash.setOnFinished(e -> {
            if (finalPulse != null) finalPulse.stop(); // Stop the breathing

            // Fade out splash
            FadeTransition fadeOutSplash = new FadeTransition(Duration.millis(600), splashScreen);
            fadeOutSplash.setToValue(0);
            fadeOutSplash.setOnFinished(event -> splashScreen.setVisible(false));

            // Fade in the App UI
            FadeTransition fadeInApp = new FadeTransition(Duration.millis(800), mainAppUI);
            fadeInApp.setToValue(1);

            // Buttery Smooth Scale Up
            ScaleTransition scaleInApp = new ScaleTransition(Duration.millis(800), mainAppUI);
            scaleInApp.setToX(1);
            scaleInApp.setToY(1);
            scaleInApp.setInterpolator(Interpolator.EASE_BOTH);

            ParallelTransition showApp = new ParallelTransition(fadeInApp, scaleInApp);

            fadeOutSplash.play();
            showApp.play();
        });

        holdSplash.play();
    }

    // --- BUTTON CLICK LOGIC (Keeps them 'Clicked') ---
    private void setActiveButton(Button clickedBtn) {
        if (navButtons == null) return; // Safety check

        // Clear active states from ALL buttons
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-btn-active");
            btn.getStyleClass().remove("nav-btn-hub-active");
        }

        // Add active state to the specific button
        if (clickedBtn == btnHome) {
            clickedBtn.getStyleClass().add("nav-btn-hub-active");
        } else {
            clickedBtn.getStyleClass().add("nav-btn-active");
        }
    }

    // --- NAVIGATION HANDLERS ---
    @FXML void handleShowHome(ActionEvent event) {
        setActiveButton(btnHome);
        loadPage("/views/home.fxml");
    }

    @FXML void handleShowStudySessions(ActionEvent event) {
        setActiveButton(btnCourses);
        loadPage("/views/studysession/MainDashboard.fxml");
    }

    @FXML void handleShowLibrary(ActionEvent event) {
        setActiveButton(btnLibrary);
        loadPage("/views/library/BookListView.fxml");
    }

    @FXML void handleShowForum(ActionEvent event) {
        setActiveButton(btnForum);
        // Note: Using the path from your folder screenshot!
        loadPage("/views/forum/forum_feed.fxml");
    }

    @FXML void handleShowQuiz(ActionEvent event) {
        setActiveButton(btnQuiz);
        loadPage("/views/quiz/quiz_list.fxml");
    }

    @FXML void handleShowGamification(ActionEvent event) {
        setActiveButton(btnGames);
        loadPage("/views/gamification/GameDashboard.fxml");
    }

    @FXML void handleShowRewards(ActionEvent event) {
        setActiveButton(btnRewards);
        loadPage("/views/gamification/reward_list.fxml");
    }

    // --- EXISTING ROUTING / PAGE ANIMATION ENGINE ---
    private void loadPageSilently(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
        } catch (IOException e) {
            System.err.println("❌ Could not load silently: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public static void loadPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(NovaDashboardController.class.getResource(fxmlPath));
            Parent view = loader.load();
            setView(view);
        } catch (IOException e) {
            System.err.println("❌ Could not load: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public static void setView(Parent view) {
        if (staticContentArea != null) {
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);

            view.setOpacity(0);
            view.setTranslateY(30);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(450), view);
            fadeIn.setToValue(1.0);

            TranslateTransition slideUp = new TranslateTransition(Duration.millis(450), view);
            slideUp.setToY(0);
            slideUp.setInterpolator(Interpolator.EASE_OUT); // Smooth slide

            ParallelTransition transition = new ParallelTransition(fadeIn, slideUp);
            transition.play();
        }
    }
}