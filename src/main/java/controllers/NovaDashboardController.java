package controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.scene.image.Image;
import utils.ThemeManager;
import java.util.concurrent.CompletableFuture;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import controllers.users.ProfileController;
import controllers.gamification.GameLauncherController;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class NovaDashboardController {

    @FXML private BorderPane mainAppUI;
    @FXML private StackPane splashScreen;
    @FXML private ImageView splashLogo;
    @FXML private Region bgOverlay;

    @FXML private StackPane contentArea;
    private static StackPane staticContentArea;
    private static Parent previousView = null;

    @FXML private Button btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards;
    @FXML private Button btnFullscreen;
    private List<Button> navButtons;

    private User currentUser;
    @FXML private StackPane avatarPane;
    @FXML private VBox navBar;
    private boolean isSidebarVisible = true;
    @FXML private Circle circleNavAvatar;
    @FXML private Label lblNavInitials;
    @FXML private Button btnTheme;

    private final services.users.GravatarService gravatarService = new services.users.GravatarService();
    private Popup themePopup;
    private ContextMenu coursesDropdown;

    // --- Notification Components Restored ---
    @FXML private StackPane notificationPane;
    @FXML private StackPane badgePane;
    @FXML private Label notificationCount;
    private Timeline notificationPoller;
    private services.NotificationService notificationService;
    private Popup notificationPopup;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            if (mainAppUI != null && mainAppUI.getScene() != null) {
                Stage stage = (Stage) mainAppUI.getScene().getWindow();
                stage.setMaximized(true);
                ThemeManager.getInstance().register(stage.getScene());
                ThemeManager.getInstance().setDashboardController(NovaDashboardController.this);
                ThemeManager.getInstance().applyToScene(stage.getScene());
                stage.setFullScreenExitHint("");
            }
        });

        if (contentArea != null) {
            staticContentArea = contentArea;

            if (btnHome != null && btnCourses != null) {
                navButtons = Arrays.asList(btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards);
            }

            loadPageSilently("/views/home.fxml");

            if (splashScreen != null && mainAppUI != null) {
                playCinematicStartup();
            }
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;

        if (user != null && user.getRole() == User.Role.ROLE_ADMIN) {
            redirectToCorrectDashboard(user);
            return;
        }

        if (user != null) {
            utils.SessionManager.setCurrentUserId(user.getId());
            boolean isTutor = user.getRole() == User.Role.ROLE_TUTOR;
            if (btnGames   != null) { btnGames.setVisible(!isTutor);   btnGames.setManaged(!isTutor); }
            if (btnRewards != null) { btnRewards.setVisible(!isTutor); btnRewards.setManaged(!isTutor); }
            loadNavGravatarAsync(user.getEmail(), user.getUsername());

            // Restored Notification Logic
            notificationService = new services.NotificationService();
            startNotificationPoller();
        }

        if (user != null && user.getRole() == User.Role.ROLE_TUTOR) {
            Platform.runLater(this::setupTutorCoursesDropdown);
        }
    }

    private void startNotificationPoller() {
        updateNotificationBadge();
        notificationPoller = new Timeline(new KeyFrame(Duration.seconds(10), e -> updateNotificationBadge()));
        notificationPoller.setCycleCount(Animation.INDEFINITE);
        notificationPoller.play();
    }

    private void updateNotificationBadge() {
        if (currentUser == null || notificationService == null) return;
        new Thread(() -> {
            int unread = notificationService.getUnreadCount(currentUser.getId());
            Platform.runLater(() -> {
                if (unread > 0) { badgePane.setVisible(true); notificationCount.setText(String.valueOf(unread)); }
                else { badgePane.setVisible(false); }
            });
        }).start();
    }

    @FXML
    private void onNotificationClick(javafx.scene.input.MouseEvent event) {
        if (currentUser == null || notificationService == null) return;

        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide(); return;
        }

        boolean isDark = ThemeManager.getInstance().getMode() == ThemeManager.Mode.DARK;
        String bgMain = isDark ? "rgba(24, 24, 27, 0.95)" : "rgba(255, 255, 255, 0.95)";
        String bgHeader = isDark ? "rgba(9, 9, 11, 0.95)" : "rgba(248, 250, 252, 0.95)";
        String bgRowUnread = isDark ? "rgba(39, 39, 42, 0.8)" : "white";
        String bgRowRead = isDark ? "transparent" : "#f8fafc";
        String border = isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.1)";
        String textPrim = isDark ? "#f1f5f9" : "#0f172a";
        String textSec = isDark ? "#94a3b8" : "#475569";
        String hoverBg = isDark ? "rgba(63, 63, 70, 0.8)" : "#eff6ff";

        notificationPopup = new Popup();
        notificationPopup.setAutoHide(true);

        VBox rootBox = new VBox();
        rootBox.setStyle("-fx-background-color: " + bgMain + "; -fx-background-radius: 20; -fx-border-radius: 20; -fx-border-color: " + border + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 40, 0, 0, 15); -fx-min-width: 420; -fx-max-width: 420;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color: " + bgHeader + "; -fx-background-radius: 20 20 0 0; -fx-padding: 25 30; -fx-border-color: transparent transparent " + border + " transparent; -fx-border-width: 0 0 1 0;");
        Label headerTitle = new Label("Notifications");
        headerTitle.setStyle("-fx-font-weight: 900; -fx-font-size: 18px; -fx-text-fill: " + textPrim + ";");
        header.getChildren().add(headerTitle);
        rootBox.getChildren().add(header);

        VBox itemsBox = new VBox();
        ScrollPane scroll = new ScrollPane(itemsBox);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(500);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        List<models.Notification> notifs = notificationService.getUserNotifications(currentUser.getId());

        if (notifs.isEmpty()) {
            Label emptyLbl = new Label("You're all caught up! Zero notifications.");
            emptyLbl.setStyle("-fx-text-fill: " + textSec + "; -fx-padding: 40; -fx-font-weight: bold; -fx-font-size: 14px; -fx-alignment: center;");
            itemsBox.getChildren().add(emptyLbl);
        } else {
            for (models.Notification n : notifs) {
                HBox row = new HBox(15);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(20, 25, 20, 25));

                String rowBaseStyle = "-fx-background-color: " + (n.isRead() ? bgRowRead : bgRowUnread) + "; -fx-cursor: hand; -fx-border-color: transparent transparent " + border + " transparent; -fx-border-width: 0 0 1 0;";
                row.setStyle(rowBaseStyle);

                StackPane iconBg = new StackPane();
                iconBg.setPrefSize(45, 45); iconBg.setMinSize(45, 45);
                String hexColor = n.getColor() != null ? n.getColor() : "#00f2fe";
                iconBg.setStyle("-fx-background-color: " + hexColor + "20; -fx-background-radius: 50;");

                String iconStr = n.getIcon();
                if (iconStr == null || iconStr.startsWith("bi-") || iconStr.startsWith("fa-") || iconStr.length() > 4) iconStr = "📌";
                Label iconLbl = new Label(iconStr); iconLbl.setStyle("-fx-font-size: 20px;");
                iconBg.getChildren().add(iconLbl);

                VBox textVBox = new VBox(6); HBox.setHgrow(textVBox, Priority.ALWAYS);
                Label title = new Label(n.getTitle()); title.setStyle("-fx-font-weight: 900; -fx-font-size: 14px; -fx-text-fill: " + textPrim + ";");
                Label msg = new Label(n.getMessage()); msg.setWrapText(true); msg.setMaxWidth(280); msg.setStyle("-fx-text-fill: " + textSec + "; -fx-font-size: 13px; -fx-line-spacing: 3px;");

                long min = (System.currentTimeMillis() - n.getCreatedAt().getTime()) / 60000;
                Label time = new Label(min == 0 ? "Just now" : (min < 60 ? min + "m ago" : (min/60) + "h ago"));
                time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");

                textVBox.getChildren().addAll(title, msg, time);
                Circle unreadDot = new Circle(5, Color.web("#00f2fe")); unreadDot.setVisible(!n.isRead());
                row.getChildren().addAll(iconBg, textVBox, unreadDot);

                row.setOnMouseEntered(e -> row.setStyle(rowBaseStyle.replace(n.isRead() ? bgRowRead : bgRowUnread, hoverBg)));
                row.setOnMouseExited(e -> row.setStyle(rowBaseStyle));

                row.setOnMouseClicked(e -> {
                    notificationPopup.hide();
                    if (!n.isRead()) { notificationService.markAsRead(n.getId()); updateNotificationBadge(); }
                    String route = n.getActionUrl();
                    if (route != null && !route.isEmpty()) {
                        if (route.contains("post_details") && n.getMetadata() != null) {
                            try {
                                models.forum.Post targetPost = new services.forum.PostService().getPostById(Integer.parseInt(n.getMetadata()));
                                if (targetPost != null) { utils.ForumSession.currentPost = targetPost; setActiveButton(btnForum); loadPage(route); return; }
                            } catch (Exception ex) { }
                        }
                        if (route.contains("forum")) setActiveButton(btnForum);
                        else if (route.contains("quiz")) setActiveButton(btnQuiz);
                        else if (route.contains("library")) setActiveButton(btnLibrary);
                        else if (route.contains("gamification")) setActiveButton(btnGames);
                        loadPage(route);
                    }
                });
                itemsBox.getChildren().add(row);
            }
        }

        rootBox.getChildren().add(scroll);
        notificationPopup.getContent().add(rootBox);
        Point2D p = notificationPane.localToScreen(0.0, 0.0);
        notificationPopup.show(notificationPane.getScene().getWindow(), p.getX() - 360, p.getY() + 50);
    }

    private void redirectToCorrectDashboard(User user) {
        try {
            Stage stage = (Stage) mainAppUI.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/admin/AdminDashboard.fxml"));
            Parent root = loader.load();
            controllers.admin.AdminDashboardController adminCtrl = loader.getController();
            adminCtrl.setCurrentUser(user);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
            stage.setTitle("NOVA - Admin Dashboard");
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("Redirect error: " + e.getMessage());
        }
    }

    private void setupTutorCoursesDropdown() {
        if (btnCourses == null) return;
        boolean isDark = ThemeManager.getInstance().getMode() == ThemeManager.Mode.DARK;
        String bgMain = isDark ? "rgba(24, 24, 27, 0.95)" : "rgba(255, 255, 255, 0.95)";
        String border = isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.1)";
        String textPrim = isDark ? "#f8fafc" : "#0f172a";

        coursesDropdown = new ContextMenu();
        coursesDropdown.setStyle("-fx-background-color: " + bgMain + "; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 30, 0, 0, 10); -fx-border-color: " + border + "; -fx-border-radius: 16; -fx-padding: 8;");

        MenuItem itemMyCourses = new MenuItem("📚  My Courses");
        itemMyCourses.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 10 20;");
        itemMyCourses.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/TutorCourseView.fxml"); });

        MenuItem itemEnrollments = new MenuItem("📋  Enrollment Requests");
        itemEnrollments.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 10 20;");
        itemEnrollments.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/EnrollmentRequestsView.fxml"); });

        MenuItem itemStudentProgress = new MenuItem("👥  Student Progress");
        itemStudentProgress.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 10 20;");
        itemStudentProgress.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/TutorProgressMonitorView.fxml"); });

        MenuItem itemAnalytics = new MenuItem("📊  Analytics");
        itemAnalytics.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 10 20;");
        itemAnalytics.setOnAction(e -> { setActiveButton(btnCourses); loadTutorAnalytics(); });

        MenuItem itemCalendar = new MenuItem("📅  Calendar");
        itemCalendar.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 10 20;");
        itemCalendar.setOnAction(e -> { setActiveButton(btnCourses); loadTutorCalendar(); });

        coursesDropdown.getItems().addAll(itemMyCourses, itemEnrollments, itemStudentProgress, new SeparatorMenuItem(), itemAnalytics, itemCalendar);

        btnCourses.setOnAction(null);
        btnCourses.setOnAction(event -> coursesDropdown.show(btnCourses, javafx.geometry.Side.BOTTOM, 0, 15));
    }

    private void loadTutorAnalytics() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/TutorAnalyticsDashboardView.fxml"));
            Parent view = loader.load();
            controllers.studysession.TutorAnalyticsDashboardController ctrl = loader.getController();
            if (currentUser != null) ctrl.setTutorId(currentUser.getId());
            setView(view);
        } catch (Exception e) {}
    }

    private void loadTutorCalendar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CalendarPlannerView.fxml"));
            Parent view = loader.load();
            controllers.studysession.CalendarPlannerController ctrl = loader.getController();
            if (currentUser != null) ctrl.setCurrentUser(currentUser);
            setView(view);
        } catch (Exception e) {}
    }

    @FXML
    private void onAvatarClick(javafx.scene.input.MouseEvent event) {
        if (currentUser == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/profile.fxml"));
            Parent profileView = loader.load();
            ProfileController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser, null, previousView);
            setView(profileView);
        } catch (Exception e) {}
    }

    @FXML
    void toggleFullscreen(ActionEvent event) {
        if (mainAppUI != null && mainAppUI.getScene() != null) {
            Stage stage = (Stage) mainAppUI.getScene().getWindow();
            boolean isCurrentlyFullscreen = stage.isFullScreen();
            stage.setFullScreen(!isCurrentlyFullscreen);
            btnFullscreen.setText(isCurrentlyFullscreen ? "⛶" : "🗗");
        }
    }

    @FXML
    private void onToggleSidebar(ActionEvent event) {
        if (isSidebarVisible) {
            navBar.setManaged(false);
            navBar.setVisible(false);
            isSidebarVisible = false;
        } else {
            navBar.setManaged(true);
            navBar.setVisible(true);
            isSidebarVisible = true;
        }
    }

    private void playCinematicStartup() {
        mainAppUI.setOpacity(0); mainAppUI.setScaleX(0.97); mainAppUI.setScaleY(0.97);
        ScaleTransition pulse = null;
        if (splashLogo != null) {
            pulse = new ScaleTransition(Duration.millis(800), splashLogo);
            pulse.setByX(0.05); pulse.setByY(0.05); pulse.setCycleCount(Animation.INDEFINITE); pulse.setAutoReverse(true); pulse.play();
        }

        PauseTransition holdSplash = new PauseTransition(Duration.seconds(1.2));
        ScaleTransition finalPulse = pulse;

        holdSplash.setOnFinished(e -> {
            if (finalPulse != null) finalPulse.stop();
            FadeTransition fadeOutSplash = new FadeTransition(Duration.millis(500), splashScreen); fadeOutSplash.setToValue(0); fadeOutSplash.setOnFinished(event -> splashScreen.setVisible(false));
            FadeTransition fadeInApp = new FadeTransition(Duration.millis(700), mainAppUI); fadeInApp.setToValue(1);
            ScaleTransition scaleInApp = new ScaleTransition(Duration.millis(700), mainAppUI); scaleInApp.setToX(1); scaleInApp.setToY(1); scaleInApp.setInterpolator(Interpolator.SPLINE(0.25, 1, 0.5, 1));
            new ParallelTransition(fadeInApp, scaleInApp).play();
            fadeOutSplash.play();
        });
        holdSplash.play();
    }

    private void setActiveButton(Button clickedBtn) {
        if (navButtons == null || clickedBtn == null) return;
        for (Button btn : navButtons) {
            if (btn != null) { btn.getStyleClass().remove("nav-btn-active"); btn.getStyleClass().remove("nav-btn-hub-active"); }
        }
        if (clickedBtn == btnHome) clickedBtn.getStyleClass().add("nav-btn-hub-active");
        else clickedBtn.getStyleClass().add("nav-btn-active");
    }

    @FXML void handleShowHome(ActionEvent event) { setActiveButton(btnHome); loadPage("/views/home.fxml"); }
    @FXML void handleShowStudySessions(ActionEvent event) { setActiveButton(btnCourses); loadPage("/views/studysession/UserStudyDashboard.fxml"); }
    @FXML void handleShowRewards(ActionEvent event) { setActiveButton(btnRewards); loadPage("/views/gamification/user_rewards.fxml"); }
    @FXML void handleShowLeaderboard(ActionEvent event) { loadPage("/views/gamification/leaderboard.fxml"); }

    @FXML void handleShowLibrary(ActionEvent event) {
        setActiveButton(btnLibrary);
        ContextMenu menu = new ContextMenu();
        boolean isDark = ThemeManager.getInstance().getMode() == ThemeManager.Mode.DARK;
        String bgMain = isDark ? "rgba(24, 24, 27, 0.95)" : "rgba(255, 255, 255, 0.95)";
        String border = isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.1)";
        String textPrim = isDark ? "#f8fafc" : "#0f172a";

        menu.setStyle("-fx-background-color: " + bgMain + "; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 30, 0, 0, 10); -fx-border-color: " + border + "; -fx-border-radius: 16; -fx-padding: 10;");
        MenuItem itemBrowse = new MenuItem("📚  Browse Books");
        itemBrowse.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 12 25;");
        itemBrowse.setOnAction(e -> loadPage("/views/library/BookListView.fxml"));
        MenuItem itemMyLibrary = new MenuItem("🗂  My Library");
        itemMyLibrary.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textPrim + "; -fx-padding: 12 25;");
        itemMyLibrary.setOnAction(e -> loadPage("/views/library/MyLibrary.fxml"));

        menu.getItems().addAll(itemBrowse, itemMyLibrary);
        menu.show(btnLibrary, javafx.geometry.Side.BOTTOM, 0, 15);
    }

    @FXML void handleShowForum(ActionEvent event) { setActiveButton(btnForum); loadPage("/views/forum/forum_feed.fxml"); }
    @FXML void handleShowQuiz(ActionEvent event) { setActiveButton(btnQuiz); loadPage("/views/quiz/quiz_play_list.fxml"); }

    @FXML void handleShowGamification(ActionEvent event) {
        setActiveButton(btnGames);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/gamification/game_launcher.fxml"));
            Parent view = loader.load();
            GameLauncherController ctrl = loader.getController();
            ctrl.setContentArea(contentArea);
            setView(view);
        } catch (Exception e) {}
    }

    private void loadPageSilently(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) return;
            Parent view = FXMLLoader.load(resource);
            ThemeManager.getInstance().applyToParent(view);
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
        } catch (Exception e) {}
    }

    public static void setView(Parent view) {
        if (staticContentArea != null && !staticContentArea.getChildren().isEmpty()) previousView = (Parent) staticContentArea.getChildren().get(0);
        if (staticContentArea != null) {
            staticContentArea.getChildren().clear();
            ThemeManager.getInstance().applyToParent(view);
            staticContentArea.getChildren().add(view);

            view.setOpacity(0); view.setTranslateY(40);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), view); fadeIn.setToValue(1.0);
            TranslateTransition slideUp = new TranslateTransition(Duration.millis(500), view); slideUp.setToY(0);
            slideUp.setInterpolator(Interpolator.SPLINE(0.25, 1, 0.5, 1));
            new ParallelTransition(fadeIn, slideUp).play();
        }
    }

    public static void loadPage(String fxmlPath) {
        try {
            Parent view = FXMLLoader.load(NovaDashboardController.class.getResource(fxmlPath));
            setView(view);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadNavGravatarAsync(String email, String username) {
        String initials = username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase();
        if (lblNavInitials != null) lblNavInitials.setText(initials);

        CompletableFuture.supplyAsync(() -> gravatarService.getAvatarUrl(email, 120, "identicon"))
                .thenAccept(url -> Platform.runLater(() -> {
                    try {
                        Image img = new Image(url, 120, 120, true, true, true);
                        img.progressProperty().addListener((obs, old, prog) -> {
                            if (prog.doubleValue() >= 1.0 && !img.isError()) {
                                if (circleNavAvatar != null) circleNavAvatar.setFill(new ImagePattern(img));
                                if (lblNavInitials != null) lblNavInitials.setVisible(false);
                            }
                        });
                    } catch (Exception ignored) {}
                }));
    }

    public void applyDarkModeToNodes(boolean dark) {
        if (bgOverlay != null) bgOverlay.setStyle("");
        if (navBar != null) {
            navBar.setStyle("-fx-border-color: " + (dark ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.05)") +
                    "; -fx-border-width: 0 1 0 0; -fx-padding: 35 20 35 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 15, 0, 2, 0);");
        }
        if (themePopup != null && themePopup.isShowing()) { themePopup.hide(); themePopup = null; }
        if (notificationPopup != null && notificationPopup.isShowing()) { notificationPopup.hide(); notificationPopup = null; }
    }

    @FXML
    private void onOpenThemeSettings() {
        if (themePopup == null) buildThemePopup();
        if (themePopup.isShowing()) { themePopup.hide(); return; }
        javafx.geometry.Bounds b = btnTheme.localToScreen(btnTheme.getBoundsInLocal());
        themePopup.show(btnTheme.getScene().getWindow(), b.getMinX() - 140, b.getMaxY() + 15);
    }

    private javafx.scene.layout.VBox schedulePane;
    private javafx.scene.control.Spinner<Integer> spDarkH, spDarkM, spLightH, spLightM;

    private void buildThemePopup() {
        themePopup = new Popup();
        themePopup.setAutoHide(true);

        boolean isDark = ThemeManager.getInstance().getMode() == ThemeManager.Mode.DARK;
        String bgMain = isDark ? "rgba(24, 24, 27, 0.95)" : "rgba(255, 255, 255, 0.95)";
        String border = isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.1)";
        String textTitle = isDark ? "#94a3b8" : "#64748b";
        String sepColor = isDark ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.05)";
        String textMain = isDark ? "#f8fafc" : "#0f172a";
        String btnHover = isDark ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.05)";
        String bgSub = isDark ? "rgba(9, 9, 11, 0.95)" : "rgba(241, 245, 249, 0.95)";

        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
        card.setStyle("-fx-background-color:" + bgMain + "; -fx-border-color:" + border + "; -fx-border-width:1; -fx-border-radius:20; -fx-background-radius:20; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.4),30,0,0,15); -fx-padding:10 0;");
        card.setPrefWidth(280);

        javafx.scene.control.Label title = new javafx.scene.control.Label("Appearance");
        title.setStyle("-fx-text-fill:" + textTitle + "; -fx-font-size:12px; -fx-font-weight:900; -fx-padding:10 20 12 20; -fx-letter-spacing: 1px;");
        card.getChildren().add(title);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color:" + sepColor + ";");
        card.getChildren().add(sep);

        String[] lbls = {"☀  Light Theme", "🌙  Dark Theme", "⏱  Automated Schedule"};
        ThemeManager.Mode[] modes = {ThemeManager.Mode.LIGHT, ThemeManager.Mode.DARK, ThemeManager.Mode.SCHEDULED};

        for (int i = 0; i < lbls.length; i++) {
            final ThemeManager.Mode m = modes[i];
            boolean active = ThemeManager.getInstance().getMode() == m;
            javafx.scene.control.Button btn = new javafx.scene.control.Button(lbls[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-background-color:" + (active ? btnHover : "transparent") + "; -fx-text-fill:" + (active ? "#00f2fe" : textMain) + "; -fx-font-size:14px; -fx-font-weight:" + (active ? "900" : "bold") + "; -fx-padding:14 25; -fx-cursor:hand; -fx-background-radius:0; -fx-alignment:CENTER_LEFT; -fx-pref-width:280;");
            btn.setOnAction(e -> {
                if (m == ThemeManager.Mode.LIGHT) { ThemeManager.getInstance().setLight(); themePopup.hide(); themePopup = null; }
                else if (m == ThemeManager.Mode.DARK) { ThemeManager.getInstance().setDark(); themePopup.hide(); themePopup = null; }
                else { toggleSchedulePane(); }
            });
            card.getChildren().add(btn);
        }

        schedulePane = new javafx.scene.layout.VBox(12);
        schedulePane.setStyle("-fx-padding:20 25; -fx-background-color:" + bgSub + "; -fx-border-color:transparent transparent " + sepColor + " transparent; -fx-border-width:0 0 1 0;");
        schedulePane.setVisible(false);
        schedulePane.setManaged(false);

        javafx.scene.control.Label presetLbl = new javafx.scene.control.Label("QUICK PRESETS");
        presetLbl.setStyle("-fx-text-fill:" + textTitle + "; -fx-font-size:11px; -fx-font-weight:900;");
        javafx.scene.control.ComboBox<String> cbPresets = new javafx.scene.control.ComboBox<>();
        cbPresets.getItems().add("-- Custom --");
        cbPresets.getItems().addAll(ThemeManager.PRESETS.keySet());
        cbPresets.setValue("-- Custom --");
        cbPresets.setMaxWidth(Double.MAX_VALUE);
        cbPresets.setStyle("-fx-background-color:" + (isDark ? "#1e293b" : "white") + "; -fx-text-fill:" + textMain + "; -fx-border-color:" + border + "; -fx-border-radius:10; -fx-background-radius:10; -fx-font-size:13px;");

        javafx.scene.control.Label darkLbl = new javafx.scene.control.Label("DARK MODE AT");
        darkLbl.setStyle("-fx-text-fill:" + textTitle + "; -fx-font-size:11px; -fx-font-weight:bold;");
        spDarkH = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,23,20));
        spDarkM = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,59,0,5));
        spDarkH.setPrefWidth(65); spDarkM.setPrefWidth(65); spDarkH.setEditable(true); spDarkM.setEditable(true);
        javafx.scene.control.Label colon1 = new javafx.scene.control.Label(":");
        colon1.setStyle("-fx-text-fill:" + textMain + "; -fx-font-size:18px; -fx-font-weight:bold;");
        javafx.scene.layout.HBox darkRow = new javafx.scene.layout.HBox(8, spDarkH, colon1, spDarkM);
        darkRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Label lightLbl = new javafx.scene.control.Label("LIGHT MODE AT");
        lightLbl.setStyle("-fx-text-fill:" + textTitle + "; -fx-font-size:11px; -fx-font-weight:bold;");
        spLightH = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,23,7));
        spLightM = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,59,0,5));
        spLightH.setPrefWidth(65); spLightM.setPrefWidth(65); spLightH.setEditable(true); spLightM.setEditable(true);
        javafx.scene.control.Label colon2 = new javafx.scene.control.Label(":");
        colon2.setStyle("-fx-text-fill:" + textMain + "; -fx-font-size:18px; -fx-font-weight:bold;");
        javafx.scene.layout.HBox lightRow = new javafx.scene.layout.HBox(8, spLightH, colon2, spLightM);
        lightRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Button btnApply = new javafx.scene.control.Button("Apply Settings");
        btnApply.setMaxWidth(Double.MAX_VALUE);
        btnApply.setStyle("-fx-background-color: linear-gradient(to right, #4facfe, #00f2fe); -fx-text-fill:#09090b; -fx-font-weight:900; -fx-font-size:14px; -fx-padding:10 0; -fx-background-radius:10; -fx-cursor:hand;");
        btnApply.setOnAction(e -> {
            ThemeManager.getInstance().setScheduled(
                    java.time.LocalTime.of(spDarkH.getValue(), spDarkM.getValue()),
                    java.time.LocalTime.of(spLightH.getValue(), spLightM.getValue()));
            themePopup.hide();
            themePopup = null;
        });

        schedulePane.getChildren().addAll(presetLbl, cbPresets, darkLbl, darkRow, lightLbl, lightRow, btnApply);
        card.getChildren().add(schedulePane);
        themePopup.getContent().add(card);
    }

    private void toggleSchedulePane() {
        if (schedulePane == null) return;
        boolean show = !schedulePane.isVisible();
        schedulePane.setVisible(show);
        schedulePane.setManaged(show);
    }

    public void applyDarkModeToContentArea(boolean dark) {
        if (staticContentArea == null || staticContentArea.getChildren().isEmpty()) return;
        javafx.scene.Parent currentView = (javafx.scene.Parent) staticContentArea.getChildren().get(0);
        utils.DarkModeApplier.applyToNode(currentView, dark);
    }
}