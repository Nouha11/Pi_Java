package controllers;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.shape.Circle;
import services.users.GravatarService;
import java.util.concurrent.CompletableFuture;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import controllers.users.ProfileController;

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
    @FXML private ImageView     imgNavAvatar;
    @FXML private Circle        circleNavAvatar;
    @FXML private Label         lblNavInitials;
    private final GravatarService gravatarService = new GravatarService();

    // --- Tutor Courses dropdown ---
    private ContextMenu coursesDropdown;

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

        // Allow ROLE_STUDENT, ROLE_USER, and ROLE_TUTOR — redirect only admins
        if (user != null && user.getRole() == User.Role.ROLE_ADMIN) {
            System.err.println("[ACCESS DENIED] Role " + user.getRole() + " attempted to access Student Dashboard");
            redirectToCorrectDashboard(user);
            return;
        }

        // Store user ID globally for library module
        if (user != null) {
            utils.SessionManager.setCurrentUserId(user.getId());
        loadNavGravatarAsync(user.getEmail(), user.getUsername());
        }

        // If tutor, wire up the Courses button dropdown
        if (user != null && user.getRole() == User.Role.ROLE_TUTOR) {
            Platform.runLater(this::setupTutorCoursesDropdown);
        }
    }
    
    /**
     * Subtask 14.2: Redirect users to their correct dashboard based on role.
     * Only admins are redirected — tutors now use NovaDashboard with a dropdown.
     */
    private void redirectToCorrectDashboard(User user) {
        try {
            Stage stage = (Stage) mainAppUI.getScene().getWindow();
            FXMLLoader loader;
            Parent root;
            javafx.scene.Scene scene;

            if (user.getRole() == User.Role.ROLE_ADMIN) {
                loader = new FXMLLoader(getClass().getResource("/views/admin/AdminDashboard.fxml"));
                root = loader.load();
                controllers.admin.AdminDashboardController adminCtrl = loader.getController();
                adminCtrl.setCurrentUser(user);
                scene = new javafx.scene.Scene(root, 1280, 800);
                scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
                stage.setTitle("NOVA - Admin Dashboard");
            } else {
                // Should not reach here, but handle gracefully
                return;
            }

            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("Redirect error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sets up a dropdown on the Courses nav button for tutors.
     * Shows "My Courses" and "Enrollment Requests" as menu items.
     */
    private void setupTutorCoursesDropdown() {
        if (btnCourses == null) return;

        // Build the dropdown menu
        coursesDropdown = new ContextMenu();
        coursesDropdown.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0, 0, 4);" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 8;"
        );

        MenuItem itemMyCourses = new MenuItem("📚  My Courses");
        itemMyCourses.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;"
        );
        itemMyCourses.setOnAction(e -> {
            setActiveButton(btnCourses);
            loadPage("/views/studysession/TutorCourseView.fxml");
        });

        MenuItem itemEnrollments = new MenuItem("📋  Enrollment Requests");
        itemEnrollments.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;"
        );
        itemEnrollments.setOnAction(e -> {
            setActiveButton(btnCourses);
            loadPage("/views/studysession/EnrollmentRequestsView.fxml");
        });

        MenuItem itemStudentProgress = new MenuItem("👥  Student Progress");
        itemStudentProgress.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;"
        );
        itemStudentProgress.setOnAction(e -> {
            setActiveButton(btnCourses);
            loadPage("/views/studysession/TutorProgressMonitorView.fxml");
        });

        coursesDropdown.getItems().addAll(itemMyCourses, itemEnrollments, itemStudentProgress);

        // Replace the Courses button action with dropdown show
        btnCourses.setOnAction(null);
        btnCourses.setOnAction(event -> {
            coursesDropdown.show(btnCourses,
                javafx.geometry.Side.BOTTOM, 0, 4);
        });
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
        loadPage("/views/gamification/user_games.fxml");
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

    // ── Gravatar API: load navbar avatar asynchronously ──────────────────────
    private void loadNavGravatarAsync(String email, String username) {
        String initials = username.length() >= 2
            ? username.substring(0, 2).toUpperCase()
            : username.toUpperCase();
        if (lblNavInitials != null) lblNavInitials.setText(initials);

        CompletableFuture.supplyAsync(() ->
            gravatarService.getAvatarUrl(email, 40, "identicon")
        ).thenAccept(url -> Platform.runLater(() -> {
            try {
                Image img = new Image(url, 40, 40, true, true, true);
                img.progressProperty().addListener((obs, old, prog) -> {
                    if (prog.doubleValue() >= 1.0 && !img.isError()) {
                        imgNavAvatar.setImage(img);
                        imgNavAvatar.setVisible(true);
                        imgNavAvatar.setManaged(true);
                        if (circleNavAvatar != null) circleNavAvatar.setVisible(false);
                        if (lblNavInitials  != null) lblNavInitials.setVisible(false);
                    }
                });
            } catch (Exception ignored) {}
        }));
    }
}