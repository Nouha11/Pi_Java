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
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import controllers.users.ProfileController;

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

    @FXML private StackPane notificationPane;
    @FXML private StackPane badgePane;
    @FXML private Label notificationCount;

    // 🔥 CHANGED TO POPUP TO FIX CSS CONFLICTS 🔥
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
            notificationService = new services.NotificationService();
            startNotificationPoller();
        }

        if (user != null && user.getRole() == User.Role.ROLE_TUTOR) {
            Platform.runLater(this::setupTutorCoursesDropdown);
        }
    }

    // ==========================================
    // 🔥 NOTIFICATION UI & ROUTING ENGINE 🔥
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
                    iconStr = "🔔";
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

                                        // 🔥 TELL IT TO AUTO-SCROLL TO THE BOTTOM 🔥
                                        if ("FORUM_REPLY".equals(n.getType())) {
                                            controllers.forum.PostDetailsController.scrollToBottomFlag = true;
                                        }

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

        MenuItem itemMyCourses = new MenuItem("📚  My Courses");
        itemMyCourses.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemMyCourses.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/TutorCourseView.fxml"); });

        MenuItem itemEnrollments = new MenuItem("📋  Enrollment Requests");
        itemEnrollments.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 8 16;");
        itemEnrollments.setOnAction(e -> { setActiveButton(btnCourses); loadPage("/views/studysession/EnrollmentRequestsView.fxml"); });

        MenuItem itemStudentProgress = new MenuItem("👥  Student Progress");
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
            if (!isCurrentlyFullscreen) btnFullscreen.setText("↙️");
            else btnFullscreen.setText("⛶");
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
    @FXML void handleShowLibrary(ActionEvent event) { setActiveButton(btnLibrary); loadPage("/views/library/BookListView.fxml"); }
    @FXML void handleShowForum(ActionEvent event) { setActiveButton(btnForum); loadPage("/views/forum/forum_feed.fxml"); }
    @FXML void handleShowQuiz(ActionEvent event) { setActiveButton(btnQuiz); loadPage("/views/quiz/quiz_play_list.fxml"); }
    @FXML void handleShowGamification(ActionEvent event) { setActiveButton(btnGames); loadPage("/views/gamification/user_games.fxml"); }
    @FXML void handleShowRewards(ActionEvent event) { setActiveButton(btnRewards); loadPage("/views/gamification/user_rewards.fxml"); }

    private void loadPageSilently(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) return;
            Parent view = FXMLLoader.load(resource);
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
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