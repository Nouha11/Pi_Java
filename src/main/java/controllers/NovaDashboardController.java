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
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.scene.image.Image;
import javafx.stage.Popup;
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

    @FXML private StackPane contentArea;
    private static StackPane staticContentArea;
    private static Parent previousView = null; // tracks the view before profile opens

    @FXML private Button btnHome, btnCourses, btnLibrary, btnForum, btnQuiz, btnGames, btnRewards;
    @FXML private Button btnFullscreen;
    private List<Button> navButtons;

    private User currentUser;
    @FXML private StackPane avatarPane;
    @FXML private javafx.scene.layout.HBox navBar;
    @FXML private ImageView  imgNavAvatar;
    @FXML private Circle     circleNavAvatar;
    @FXML private Label      lblNavInitials;
    @FXML private Button     btnTheme;
    private final services.users.GravatarService gravatarService = new services.users.GravatarService();
    private Popup themePopup;

    @FXML private StackPane notificationPane;
    @FXML private StackPane badgePane;
    @FXML private Label notificationCount;

    // ­ƒöÑ CHANGED TO POPUP TO FIX CSS CONFLICTS ­ƒöÑ
    private Popup notificationPopup;

    private Timeline notificationPoller;
    private services.NotificationService notificationService;

    private ContextMenu coursesDropdown;

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
            // Hide games & rewards for tutors
            boolean isTutor = user.getRole() == User.Role.ROLE_TUTOR;
            if (btnGames   != null) { btnGames.setVisible(!isTutor);   btnGames.setManaged(!isTutor); }
            if (btnRewards != null) { btnRewards.setVisible(!isTutor); btnRewards.setManaged(!isTutor); }
            loadNavGravatarAsync(user.getEmail(), user.getUsername());
            notificationService = new services.NotificationService();
            startNotificationPoller();
        }

        if (user != null && user.getRole() == User.Role.ROLE_TUTOR) {
            Platform.runLater(this::setupTutorCoursesDropdown);
        }
    }

    // ==========================================
    // ­ƒöÑ NOTIFICATION UI & ROUTING ENGINE ­ƒöÑ
    // ==========================================
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
                if (unread > 0) {
                    badgePane.setVisible(true);
                    notificationCount.setText(String.valueOf(unread));
                } else {
                    badgePane.setVisible(false);
                }
            });
        }).start();
    }

    @FXML
    private void onNotificationClick(javafx.scene.input.MouseEvent event) {
        if (currentUser == null || notificationService == null) return;

        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide();
            return;
        }

        notificationPopup = new Popup();
        notificationPopup.setAutoHide(true);

        VBox rootBox = new VBox();
        rootBox.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #cbd5e1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 8); -fx-min-width: 360; -fx-max-width: 360;");

        // Header Section
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12 12 0 0; -fx-padding: 15; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");
        Label headerTitle = new Label("Notifications");
        headerTitle.setStyle("-fx-font-weight: 900; -fx-font-size: 16px; -fx-text-fill: #0f172a;");
        header.getChildren().add(headerTitle);
        rootBox.getChildren().add(header);

        // Scrollable Body
        VBox itemsBox = new VBox();
        ScrollPane scroll = new ScrollPane(itemsBox);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(400);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: white; -fx-border-color: transparent;");

        List<models.Notification> notifs = notificationService.getUserNotifications(currentUser.getId());

        if (notifs.isEmpty()) {
            Label emptyLbl = new Label("You're all caught up!");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 20; -fx-font-weight: bold; -fx-font-size: 14px;");
            itemsBox.getChildren().add(emptyLbl);
        } else {
            for (models.Notification n : notifs) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(12, 15, 12, 15));
                row.setStyle(n.isRead() ? "-fx-background-color: white; -fx-cursor: hand;" : "-fx-background-color: #f8fafc; -fx-cursor: hand;");

                // Icon Builder
                StackPane iconBg = new StackPane();
                iconBg.setPrefSize(40, 40);
                iconBg.setMinSize(40, 40);
                String hexColor = n.getColor() != null ? n.getColor() : "#3b82f6";
                iconBg.setStyle("-fx-background-color: " + hexColor + "15; -fx-background-radius: 50;");

                String iconStr = n.getIcon();
                if (iconStr == null || iconStr.startsWith("bi-") || iconStr.startsWith("fa-") || iconStr.length() > 4) {
                    iconStr = "­ƒöö";
                }
                Label iconLbl = new Label(iconStr);
                iconLbl.setStyle("-fx-font-size: 18px;");
                iconBg.getChildren().add(iconLbl);

                // Text Box
                VBox textVBox = new VBox(4);
                HBox.setHgrow(textVBox, Priority.ALWAYS);

                Label title = new Label(n.getTitle());
                title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f172a;");

                Label msg = new Label(n.getMessage());
                msg.setWrapText(true);
                msg.setMaxWidth(250);
                msg.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px; -fx-line-spacing: 2px;");

                long diff = System.currentTimeMillis() - n.getCreatedAt().getTime();
                long min = diff / 60000;
                String timeStr = min == 0 ? "Just now" : (min < 60 ? min + "m ago" : (min/60) + "h ago");
                Label time = new Label(timeStr);
                time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");

                textVBox.getChildren().addAll(title, msg, time);

                Circle unreadDot = new Circle(4, Color.web("#2563eb"));
                unreadDot.setVisible(!n.isRead());

                row.getChildren().addAll(iconBg, textVBox, unreadDot);

                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #eff6ff; -fx-cursor: hand;"));
                row.setOnMouseExited(e -> row.setStyle(n.isRead() ? "-fx-background-color: white; -fx-cursor: hand;" : "-fx-background-color: #f8fafc; -fx-cursor: hand;"));

                // On Click Logic inside Popup
                row.setOnMouseClicked(e -> {
                    notificationPopup.hide();
                    if (!n.isRead()) {
                        notificationService.markAsRead(n.getId());
                        updateNotificationBadge();
                    }

                    String route = n.getActionUrl();
                    if (route != null && !route.isEmpty()) {
                        if (route.contains("post_details")) {
                            if (n.getMetadata() != null && !n.getMetadata().isEmpty()) {
                                try {
                                    int postId = Integer.parseInt(n.getMetadata());
                                    models.forum.Post targetPost = new services.forum.PostService().getPostById(postId);
                                    if (targetPost != null) {
                                        utils.ForumSession.currentPost = targetPost;
setActiveButton(btnForum);
                                        loadPage(route);
                                        return;
                                    }
                                } catch (Exception ex) { }
                            }
                        }

                        // Default Routing
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
        notificationPopup.show(notificationPane.getScene().getWindow(), p.getX() - 320, p.getY() + 40);
    }
    // ==========================================

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
                return;
            }

            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {}
    }

    private void setupTutorCoursesDropdown() {
        if (btnCourses == null) return;

        coursesDropdown = new ContextMenu();
        coursesDropdown.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0, 0, 4); -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        MenuItem itemMyCourses = new MenuItem("­ƒôÜ  My Courses");
        itemMyCourses.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemMyCourses.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/TutorCourseView.fxml"); });

        MenuItem itemEnrollments = new MenuItem("­ƒôï  Enrollment Requests");
        itemEnrollments.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemEnrollments.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/EnrollmentRequestsView.fxml"); });

        MenuItem itemStudentProgress = new MenuItem("­ƒæÑ  Student Progress");
        itemStudentProgress.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemStudentProgress.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/TutorProgressMonitorView.fxml"); });

        coursesDropdown.getItems().addAll(itemMyCourses, itemEnrollments, itemStudentProgress);

        btnCourses.setOnAction(null);
        btnCourses.setOnAction(event -> { coursesDropdown.show(btnCourses, javafx.geometry.Side.BOTTOM, 0, 4); });
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
            if (!isCurrentlyFullscreen) btnFullscreen.setText("ÔåÖ´©Å");
            else btnFullscreen.setText("ÔøÂ");
        }
    }

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

    private void setActiveButton(Button clickedBtn) {
        if (navButtons == null || clickedBtn == null) return;
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("nav-btn-active");
                btn.getStyleClass().remove("nav-btn-hub-active");
            }
        }
        if (clickedBtn == btnHome) clickedBtn.getStyleClass().add("nav-btn-hub-active");
        else clickedBtn.getStyleClass().add("nav-btn-active");
    }

    @FXML void handleShowHome(ActionEvent event) { setActiveButton(btnHome); loadPage("/views/home.fxml"); }
    @FXML void handleShowStudySessions(ActionEvent event) { setActiveButton(btnCourses); loadPage("/views/studysession/UserStudyDashboard.fxml"); }
    @FXML void handleShowLibrary(ActionEvent event) {
        setActiveButton(btnLibrary);
        // Show dropdown with Browse Books + My Library
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0, 0, 4); -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        MenuItem itemBrowse = new MenuItem("📚  Browse Books");
        itemBrowse.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemBrowse.setOnAction(e -> loadPage("/views/library/BookListView.fxml"));

        MenuItem itemMyLibrary = new MenuItem("🗂  My Library");
        itemMyLibrary.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemMyLibrary.setOnAction(e -> loadPage("/views/library/MyLibrary.fxml"));

        menu.getItems().addAll(itemBrowse, itemMyLibrary);
        menu.show(btnLibrary, javafx.geometry.Side.BOTTOM, 0, 4);
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
            view.sceneProperty().addListener((obs, old, scene) -> { if (scene != null) ThemeManager.getInstance().register(scene); });
            if (view.getScene() != null) ThemeManager.getInstance().applyToScene(view.getScene());
        } catch (Exception e) {}
    }

    public static void loadPage(String fxmlPath) {
        try {
            URL resource = NovaDashboardController.class.getResource(fxmlPath);
            if (resource == null) return;
            Parent view = FXMLLoader.load(resource);
            setView(view);
        } catch (Exception e) {}
    }

    public static void setView(Parent view) {
        if (staticContentArea != null && !staticContentArea.getChildren().isEmpty()) {
            previousView = (Parent) staticContentArea.getChildren().get(0);
        }
        if (staticContentArea != null) {
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
            view.sceneProperty().addListener((obs, old, scene) -> { if (scene != null) ThemeManager.getInstance().register(scene); });
            if (view.getScene() != null) ThemeManager.getInstance().applyToScene(view.getScene());
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

    // ── Gravatar navbar avatar ────────────────────────────────────────────────
    private void loadNavGravatarAsync(String email, String username) {
        String initials = username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase();
        if (lblNavInitials != null) lblNavInitials.setText(initials);
        CompletableFuture.supplyAsync(() -> gravatarService.getAvatarUrl(email, 40, "identicon"))
            .thenAccept(url -> Platform.runLater(() -> {
                try {
                    Image img = new Image(url, 40, 40, true, true, true);
                    img.progressProperty().addListener((obs, old, prog) -> {
                        if (prog.doubleValue() >= 1.0 && !img.isError()) {
                            if (imgNavAvatar != null) {
                                imgNavAvatar.setImage(img);
                                imgNavAvatar.setVisible(true);
                                imgNavAvatar.setManaged(true);
                            }
                            if (circleNavAvatar != null) circleNavAvatar.setVisible(false);
                            if (lblNavInitials   != null) lblNavInitials.setVisible(false);
                        }
                    });
                } catch (Exception ignored) {}
            }));
    }

    // ── Dark mode inline-style overrides ──────────────────────────────────────
    public void applyDarkModeToNodes(boolean dark) {
        if (mainAppUI != null) mainAppUI.setStyle(dark ? "-fx-background-color: #13131f;" : "-fx-background-color: #f8fafc;");
        if (navBar    != null) navBar.setStyle(dark
            ? "-fx-background-color: #1a1a2e; -fx-border-color: transparent transparent #2d2d4e transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 40 0 40;"
            : "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-padding: 0 40 0 40;");
    }

    // ── Theme popup ───────────────────────────────────────────────────────────
    @FXML
    private void onOpenThemeSettings() {
        if (themePopup == null) buildThemePopup();
        if (themePopup.isShowing()) { themePopup.hide(); return; }
        javafx.geometry.Bounds b = btnTheme.localToScreen(btnTheme.getBoundsInLocal());
        themePopup.show(btnTheme.getScene().getWindow(), b.getMinX() - 160, b.getMaxY() + 6);
    }

    // Schedule pane reference (shown inline in popup)
    private javafx.scene.layout.VBox schedulePane;
    private javafx.scene.control.Spinner<Integer> spDarkH, spDarkM, spLightH, spLightM;

    private void buildThemePopup() {
        themePopup = new Popup();
        themePopup.setAutoHide(true);

        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
        card.setStyle("-fx-background-color:#1e1e2e;-fx-border-color:#3d3d5c;-fx-border-width:1;-fx-border-radius:12;-fx-background-radius:12;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.4),20,0,0,6);-fx-padding:8 0;");
        card.setPrefWidth(260);

        javafx.scene.control.Label title = new javafx.scene.control.Label("Appearance");
        title.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;-fx-padding:6 16 8 16;");
        card.getChildren().add(title);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color:#2d2d4e;");
        card.getChildren().add(sep);

        // Mode buttons
        String[] lbls = {"\u2600  Light", "\uD83C\uDF19  Dark", "\u23F0  Schedule"};
        ThemeManager.Mode[] modes = {ThemeManager.Mode.LIGHT, ThemeManager.Mode.DARK, ThemeManager.Mode.SCHEDULED};
        for (int i = 0; i < lbls.length; i++) {
            final ThemeManager.Mode m = modes[i];
            boolean active = ThemeManager.getInstance().getMode() == m;
            javafx.scene.control.Button btn = new javafx.scene.control.Button(lbls[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-background-color:"+(active?"rgba(79,142,247,0.15)":"transparent")+";-fx-text-fill:"+(active?"#60a5fa":"#e2e8f0")+";-fx-font-size:13px;-fx-font-weight:"+(active?"bold":"normal")+";-fx-padding:10 16;-fx-cursor:hand;-fx-background-radius:0;-fx-alignment:CENTER_LEFT;-fx-pref-width:260;");
            btn.setOnAction(e -> {
                if (m == ThemeManager.Mode.LIGHT)  { ThemeManager.getInstance().setLight(); themePopup.hide(); themePopup = null; }
                else if (m == ThemeManager.Mode.DARK) { ThemeManager.getInstance().setDark(); themePopup.hide(); themePopup = null; }
                else { toggleSchedulePane(); }
            });
            card.getChildren().add(btn);
        }

        // ── Inline schedule pane ──────────────────────────────────────────────
        schedulePane = new javafx.scene.layout.VBox(10);
        schedulePane.setStyle("-fx-padding:14 16;-fx-background-color:#16162a;-fx-border-color:transparent transparent #2d2d4e transparent;-fx-border-width:0 0 1 0;");
        schedulePane.setVisible(false);
        schedulePane.setManaged(false);

        // Presets
        javafx.scene.control.Label presetLbl = new javafx.scene.control.Label("QUICK PRESETS");
        presetLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        javafx.scene.control.ComboBox<String> cbPresets = new javafx.scene.control.ComboBox<>();
        cbPresets.getItems().add("-- Custom --");
        cbPresets.getItems().addAll(ThemeManager.PRESETS.keySet());
        cbPresets.setValue("-- Custom --");
        cbPresets.setMaxWidth(Double.MAX_VALUE);
        cbPresets.setStyle("-fx-background-color:#252535;-fx-text-fill:#e2e8f0;-fx-border-color:#3d3d5c;-fx-border-radius:6;-fx-background-radius:6;");

        // Dark time row
        javafx.scene.control.Label darkLbl = new javafx.scene.control.Label("DARK AT");
        darkLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        spDarkH = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,23,20));
        spDarkM = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,59,0,5));
        spDarkH.setPrefWidth(60); spDarkM.setPrefWidth(60); spDarkH.setEditable(true); spDarkM.setEditable(true);
        javafx.scene.control.Label colon1 = new javafx.scene.control.Label(":");
        colon1.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:16px;-fx-font-weight:bold;");
        javafx.scene.layout.HBox darkRow = new javafx.scene.layout.HBox(6, spDarkH, colon1, spDarkM);
        darkRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Light time row
        javafx.scene.control.Label lightLbl = new javafx.scene.control.Label("LIGHT AT");
        lightLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        spLightH = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,23,7));
        spLightM = new javafx.scene.control.Spinner<>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0,59,0,5));
        spLightH.setPrefWidth(60); spLightM.setPrefWidth(60); spLightH.setEditable(true); spLightM.setEditable(true);
        javafx.scene.control.Label colon2 = new javafx.scene.control.Label(":");
        colon2.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:16px;-fx-font-weight:bold;");
        javafx.scene.layout.HBox lightRow = new javafx.scene.layout.HBox(6, spLightH, colon2, spLightM);
        lightRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Preview label
        javafx.scene.control.Label preview = new javafx.scene.control.Label();
        preview.setStyle("-fx-text-fill:#4f8ef7;-fx-font-size:11px;-fx-font-weight:bold;");
        Runnable updatePreview = () -> preview.setText(String.format("Dark %02d:%02d  Light %02d:%02d",
            spDarkH.getValue(), spDarkM.getValue(), spLightH.getValue(), spLightM.getValue()));
        spDarkH.valueProperty().addListener((o,a,b)->updatePreview.run());
        spDarkM.valueProperty().addListener((o,a,b)->updatePreview.run());
        spLightH.valueProperty().addListener((o,a,b)->updatePreview.run());
        spLightM.valueProperty().addListener((o,a,b)->updatePreview.run());
        updatePreview.run();

        // Preset selection
        cbPresets.setOnAction(e -> {
            String sel = cbPresets.getValue();
            if (sel == null || sel.equals("-- Custom --")) return;
            java.time.LocalTime[] t = ThemeManager.PRESETS.get(sel);
            if (t == null) return;
            spDarkH.getValueFactory().setValue(t[0].getHour());
            spDarkM.getValueFactory().setValue(t[0].getMinute());
            spLightH.getValueFactory().setValue(t[1].getHour());
            spLightM.getValueFactory().setValue(t[1].getMinute());
        });

        // Apply button
        javafx.scene.control.Button btnApply = new javafx.scene.control.Button("Apply Schedule");
        btnApply.setMaxWidth(Double.MAX_VALUE);
        btnApply.setStyle("-fx-background-color:#4f8ef7;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:12px;-fx-padding:8 0;-fx-background-radius:6;-fx-cursor:hand;");
        btnApply.setOnAction(e -> {
            ThemeManager.getInstance().setScheduled(
                java.time.LocalTime.of(spDarkH.getValue(), spDarkM.getValue()),
                java.time.LocalTime.of(spLightH.getValue(), spLightM.getValue()));
            themePopup.hide();
            themePopup = null;
        });

        schedulePane.getChildren().addAll(presetLbl, cbPresets, darkLbl, darkRow, lightLbl, lightRow, preview, btnApply);
        card.getChildren().add(schedulePane);
        themePopup.getContent().add(card);
    }

    private void toggleSchedulePane() {
        if (schedulePane == null) return;
        boolean show = !schedulePane.isVisible();
        schedulePane.setVisible(show);
        schedulePane.setManaged(show);
    }
}