package controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import controllers.users.ProfileController;
import controllers.gamification.GameLauncherController;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class NovaDashboardController {

    // --- UI Shell Elements ---
    @FXML private BorderPane mainAppUI;
    @FXML private StackPane splashScreen;
    @FXML private ImageView splashLogo;

    @FXML private StackPane contentArea;
    private static StackPane staticContentArea;

    // --- Navigation Buttons ---
    @FXML private Button btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards;
    @FXML private Button btnFullscreen; // Added for the fullscreen toggle
    private List<Button> navButtons;

    // --- User Session ---
    private User currentUser;
    @FXML private StackPane avatarPane;

    @FXML
    public void initialize() {
        // --- AUTO-MAXIMIZE LOGIC ---
        Platform.runLater(() -> {
            if (mainAppUI != null && mainAppUI.getScene() != null) {
                Stage stage = (Stage) mainAppUI.getScene().getWindow();
                stage.setMaximized(true);
                stage.setFullScreenExitHint("");
            }
        });

        if (contentArea != null) {
            staticContentArea = contentArea;

            // Group buttons safely
            if (btnHome != null && btnCourses != null) {
                navButtons = Arrays.asList(btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards);
            }

            // 1. Pre-load Home
            loadPageSilently("/views/home.fxml");

            // 2. Play Splash
            if (splashScreen != null && mainAppUI != null) {
                playCinematicStartup();
            }
        }
    }

    // --- TEAMMATE'S USER SESSION LOGIC ---
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            utils.SessionManager.setCurrentUserId(user.getId());
            // Hide games & rewards for tutors
            boolean isTutor = user.getRole() == User.Role.ROLE_TUTOR;
            if (btnGames   != null) { btnGames.setVisible(!isTutor);   btnGames.setManaged(!isTutor); }
            if (btnRewards != null) { btnRewards.setVisible(!isTutor); btnRewards.setManaged(!isTutor); }
        }
    }

    @FXML
    private void onAvatarClick(javafx.scene.input.MouseEvent event) {
        if (currentUser == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/profile.fxml"));
            Parent profileView = loader.load();
            ProfileController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser, null);
            setView(profileView);
        } catch (Exception e) {
            System.err.println("Profile error: " + e.getMessage());
        }
    }

    // --- FULLSCREEN TOGGLE LOGIC ---
    @FXML
    void toggleFullscreen(ActionEvent event) {
        if (mainAppUI != null && mainAppUI.getScene() != null) {
            Stage stage = (Stage) mainAppUI.getScene().getWindow();

            boolean isCurrentlyFullscreen = stage.isFullScreen();
            stage.setFullScreen(!isCurrentlyFullscreen);

            if (!isCurrentlyFullscreen) {
                btnFullscreen.setText("↙️");
            } else {
                btnFullscreen.setText("⛶");
            }
        }
    }

    // --- CINEMATIC STARTUP ---
    private void playCinematicStartup() {
        mainAppUI.setOpacity(0);
        mainAppUI.setScaleX(0.92);
        mainAppUI.setScaleY(0.92);

        ScaleTransition pulse = null;
        if (splashLogo != null) {
            pulse = new ScaleTransition(Duration.millis(800), splashLogo);
            pulse.setByX(0.08);
            pulse.setByY(0.08);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
        }

        PauseTransition holdSplash = new PauseTransition(Duration.seconds(1.2));
        ScaleTransition finalPulse = pulse;

        holdSplash.setOnFinished(e -> {
            if (finalPulse != null) finalPulse.stop();

            FadeTransition fadeOutSplash = new FadeTransition(Duration.millis(600), splashScreen);
            fadeOutSplash.setToValue(0);
            fadeOutSplash.setOnFinished(event -> splashScreen.setVisible(false));

            FadeTransition fadeInApp = new FadeTransition(Duration.millis(800), mainAppUI);
            fadeInApp.setToValue(1);

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

    // --- BUTTON ACTIVE STATE LOGIC ---
    private void setActiveButton(Button clickedBtn) {
        if (navButtons == null || clickedBtn == null) return;

        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("nav-btn-active");
                btn.getStyleClass().remove("nav-btn-hub-active");
            }
        }

        if (clickedBtn == btnHome) {
            clickedBtn.getStyleClass().add("nav-btn-hub-active");
        } else {
            clickedBtn.getStyleClass().add("nav-btn-active");
        }
    }

    // --- BULLETPROOF ROUTER METHODS ---
    @FXML void handleShowHome(ActionEvent event) {
        setActiveButton(btnHome);
        loadPage("/views/home.fxml");
    }

    @FXML void handleShowStudySessions(ActionEvent event) {
        setActiveButton(btnCourses);
        loadPage("/views/studysession/UserStudyDashboard.fxml");
    }

    @FXML void handleShowLibrary(ActionEvent event) {
        setActiveButton(btnLibrary);
        loadPage("/views/library/BookListView.fxml");
    }

    @FXML void handleShowForum(ActionEvent event) {
        setActiveButton(btnForum);
        loadPage("/views/forum/forum_feed.fxml");
    }

    @FXML void handleShowQuiz(ActionEvent event) {
        setActiveButton(btnQuiz);
        loadPage("/views/quiz/quiz_play_list.fxml");
    }

    @FXML void handleShowGamification(ActionEvent event) {
        setActiveButton(btnGames);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/gamification/game_launcher.fxml"));
            Parent view = loader.load();
            GameLauncherController ctrl = loader.getController();
            ctrl.setContentArea(contentArea);
            setView(view);
        } catch (Exception e) {
            System.out.println("Could not load game launcher: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML void handleShowRewards(ActionEvent event) {
        setActiveButton(btnRewards);
        loadPage("/views/gamification/user_rewards.fxml");
    }

    // --- THE ANIMATION ENGINE & ERROR HANDLER ---
    private void loadPageSilently(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) return;
            Parent view = FXMLLoader.load(resource);
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("❌ Startup Error: Could not load " + fxmlPath);
        }
    }

    public static void loadPage(String fxmlPath) {
        try {
            URL resource = NovaDashboardController.class.getResource(fxmlPath);

            if (resource == null) {
                System.out.println("⚠️ ERROR: Cannot find the file at path: " + fxmlPath);
                return;
            }

            Parent view = FXMLLoader.load(resource);
            setView(view);

        } catch (Exception e) {
            System.out.println("💥 CRASH AVOIDED: The file " + fxmlPath + " has an error inside it.");
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
            slideUp.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition transition = new ParallelTransition(fadeIn, slideUp);
            transition.play();
        }
    }
}