package controllers.admin;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AdminDashboardController {

    @FXML private StackPane contentArea;
    @FXML private Label     lblCurrentUser;
    @FXML private Label     lblCurrentRole;
    @FXML private Label     lblPageTitle;
    @FXML private Label     lblAvatarInitial;
    @FXML private VBox      navContainer;

    @FXML private Button btnToggleUsers, btnToggleLibrary, btnToggleStudy,
                         btnToggleGame, btnToggleQuiz, btnToggleForum;
    @FXML private VBox   usersGroup, libraryGroup, studyGroup,
                         gameGroup, quizGroup, forumGroup;

    @FXML private HBox navHome;
    @FXML private HBox navUsers;
    @FXML private HBox navBooks, navLoans, navPayments;
    @FXML private HBox navCourses, navPlannings, navSessions, navStudyStats;
    @FXML private HBox navGames, navRewards, navGameStats;
    @FXML private HBox navQuizzes, navQuizStats;
    @FXML private HBox navForum, navForumStats;

    // Icon labels injected from FXML — set text in Java to avoid encoding issues
    @FXML private Label iconHome, iconUsers, iconBooks, iconLoans, iconPayments;
    @FXML private Label iconCourses, iconPlannings, iconSessions, iconStudyStats;
    @FXML private Label iconGames, iconRewards, iconGameStats;
    @FXML private Label iconQuizzes, iconQuizStats, iconForum, iconForumStats;

    private List<HBox> allNavItems;
    private String adminUsername = "Admin";

    // Maps each section button to its group for toggle
    private Map<Button, VBox> sectionMap;

    @FXML
    public void initialize() {
        // Set icons from Java — no encoding issues
        iconHome.setText("\u2B21");          // hexagon
        iconUsers.setText("\uD83D\uDC65");   // people
        iconBooks.setText("\uD83D\uDCDA");   // books
        iconLoans.setText("\uD83D\uDCCB");   // clipboard
        iconPayments.setText("\uD83D\uDCB3"); // card
        iconCourses.setText("\uD83D\uDCD8"); // blue book
        iconPlannings.setText("\uD83D\uDCC5"); // calendar
        iconSessions.setText("\u23F1");      // stopwatch
        iconStudyStats.setText("\uD83D\uDCCA"); // bar chart
        iconGames.setText("\uD83C\uDFAE");   // game controller
        iconRewards.setText("\uD83C\uDFC6"); // trophy
        iconGameStats.setText("\uD83D\uDCC8"); // chart up
        iconQuizzes.setText("\uD83D\uDCDD"); // memo
        iconQuizStats.setText("\uD83D\uDCCA"); // bar chart
        iconForum.setText("\uD83D\uDCAC");   // speech bubble
        iconForumStats.setText("\uD83D\uDCCA"); // bar chart

        // Set toggle button arrows (plain ASCII)
        setToggleText(btnToggleUsers,   "USERS",         false);
        setToggleText(btnToggleLibrary, "LIBRARY",       false);
        setToggleText(btnToggleStudy,   "STUDY SESSION", false);
        setToggleText(btnToggleGame,    "GAMIFICATION",  false);
        setToggleText(btnToggleQuiz,    "QUIZ",          false);
        setToggleText(btnToggleForum,   "FORUM",         false);

        allNavItems = List.of(
                navHome, navUsers,
                navBooks, navLoans, navPayments,
                navCourses, navPlannings, navSessions, navStudyStats,
                navGames, navRewards, navGameStats,
                navQuizzes, navQuizStats, navForum, navForumStats
        );

        // Load overview
        loadView("/views/admin/AdminHome.fxml");
        lblPageTitle.setText("Overview");

        // Fullscreen + entrance animation after scene is ready
        Platform.runLater(() -> {
            if (contentArea.getScene() != null) {
                Stage stage = (Stage) contentArea.getScene().getWindow();
                stage.setMaximized(true);
                contentArea.getScene().getRoot().getProperties()
                        .put("adminDashboardController", this);
                playEntranceAnimation();
            }
        });
    }

    private void playEntranceAnimation() {
        if (navContainer == null) return;
        navContainer.setOpacity(0);
        navContainer.setTranslateX(-20);

        FadeTransition fade = new FadeTransition(Duration.millis(500), navContainer);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), navContainer);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide).play();

        // Stagger content area
        contentArea.setOpacity(0);
        contentArea.setTranslateY(16);
        PauseTransition delay = new PauseTransition(Duration.millis(200));
        delay.setOnFinished(e -> {
            FadeTransition cf = new FadeTransition(Duration.millis(400), contentArea);
            cf.setToValue(1);
            TranslateTransition ct = new TranslateTransition(Duration.millis(400), contentArea);
            ct.setToY(0);
            ct.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(cf, ct).play();
        });
        delay.play();
    }

    public void setCurrentUser(User user) {
        if (user == null) return;
        adminUsername = user.getUsername();
        lblCurrentUser.setText(adminUsername);
        lblCurrentRole.setText(user.getRole().name());
        if (lblAvatarInitial != null && !adminUsername.isEmpty())
            lblAvatarInitial.setText(String.valueOf(adminUsername.charAt(0)).toUpperCase());
    }

    // ── TOGGLES ───────────────────────────────────────────────────────────────

    @FXML public void toggleUsers()   { toggle(usersGroup,   btnToggleUsers,   "USERS"); }
    @FXML public void toggleLibrary() { toggle(libraryGroup, btnToggleLibrary, "LIBRARY"); }
    @FXML public void toggleStudy()   { toggle(studyGroup,   btnToggleStudy,   "STUDY SESSION"); }
    @FXML public void toggleGame()    { toggle(gameGroup,    btnToggleGame,    "GAMIFICATION"); }
    @FXML public void toggleQuiz()    { toggle(quizGroup,    btnToggleQuiz,    "QUIZ"); }
    @FXML public void toggleForum()   { toggle(forumGroup,   btnToggleForum,   "FORUM"); }

    private void toggle(VBox group, Button btn, String label) {
        boolean open = !group.isVisible();
        group.setVisible(open);
        group.setManaged(open);
        setToggleText(btn, label, open);

        if (open) {
            group.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), group);
            ft.setToValue(1);
            ft.play();
        }
    }

    private void setToggleText(Button btn, String label, boolean open) {
        btn.setText(label + (open ? "  v" : "  >"));
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    @FXML public void showHome()       { nav(navHome,       "Overview",         "/views/admin/AdminHome.fxml"); }
    @FXML public void showUsers()      { nav(navUsers,      "Users",            "/views/users/user-list.fxml"); }
    @FXML public void showBooks()      { nav(navBooks,      "Books",            "/views/library/BookView.fxml"); }
    @FXML public void showLoans()      { nav(navLoans,      "Loans",            "/views/library/LoanView.fxml"); }
    @FXML public void showPayments()   { nav(navPayments,   "Payments",         "/views/library/PaymentView.fxml"); }
    @FXML public void showCourses()    { nav(navCourses,    "Courses",          "/views/studysession/CourseView.fxml"); }
    @FXML public void showPlannings()  { nav(navPlannings,  "Plannings",        "/views/studysession/PlanningView.fxml"); }
    @FXML public void showSessions()   { nav(navSessions,   "Study Sessions",   "/views/studysession/StudySessionView.fxml"); }
    @FXML public void showStudyStats() { nav(navStudyStats, "Study Stats",      "/views/studysession/StatsView.fxml"); }
    @FXML public void showGames()      { nav(navGames,      "Games",            "/views/gamification/game_list.fxml"); }
    @FXML public void showRewards()    { nav(navRewards,    "Rewards",          "/views/gamification/reward_list.fxml"); }
    @FXML public void showGameStats()  { nav(navGameStats,  "Game Stats",       "/views/gamification/stats.fxml"); }
    @FXML public void showQuizzes()    { nav(navQuizzes,    "Quizzes",          "/views/quiz/quiz_list.fxml"); }
    @FXML public void showQuizStats()  { nav(navQuizStats,  "Quiz Statistics",  "/views/quiz/quiz_stats.fxml"); }
    @FXML public void showForum()      { nav(navForum,      "Forum Management", "/views/forum/admin/admin_forum.fxml"); }
    @FXML public void showForumStats() { nav(navForumStats, "Forum Statistics", "/views/forum/admin/forum_stats.fxml"); }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void nav(HBox active, String title, String fxmlPath) {
        setActive(active);
        lblPageTitle.setText(title);
        loadViewAnimated(fxmlPath);
    }

    private void setActive(HBox active) {
        for (HBox item : allNavItems) {
            item.getStyleClass().removeAll("admin-nav-row-active");
            if (!item.getStyleClass().contains("admin-nav-row"))
                item.getStyleClass().add("admin-nav-row");
            item.getChildren().forEach(n -> {
                if (n instanceof Label lbl) {
                    lbl.getStyleClass().removeAll("admin-nav-icon-active", "admin-nav-text-active");
                    if (!lbl.getStyleClass().contains("admin-nav-icon")
                            && !lbl.getStyleClass().contains("admin-nav-text")) {
                        // icon labels have no text styleclass — skip
                    }
                }
            });
        }
        active.getStyleClass().remove("admin-nav-row");
        active.getStyleClass().add("admin-nav-row-active");
        boolean first = true;
        for (var n : active.getChildren()) {
            if (n instanceof Label lbl) {
                if (first) {
                    lbl.getStyleClass().removeAll("admin-nav-icon");
                    lbl.getStyleClass().add("admin-nav-icon-active");
                    first = false;
                } else {
                    lbl.getStyleClass().removeAll("admin-nav-text");
                    lbl.getStyleClass().add("admin-nav-text-active");
                }
            }
        }
    }

    private void loadViewAnimated(String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent view = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof AdminHomeController homeCtrl)
                homeCtrl.setAdminUsername(adminUsername);
            view.setOpacity(0);
            view.setTranslateY(20);
            contentArea.getChildren().setAll(view);
            FadeTransition ft = new FadeTransition(Duration.millis(350), view);
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), view);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        } catch (IOException e) {
            System.err.println("Admin nav error [" + path + "]: " + e.getMessage());
        }
    }

    private void loadView(String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent view = loader.load();
            // Pass username to home controller if applicable
            Object ctrl = loader.getController();
            if (ctrl instanceof AdminHomeController homeCtrl && adminUsername != null)
                homeCtrl.setAdminUsername(adminUsername);
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("Admin nav error [" + path + "]: " + e.getMessage());
        }
    }

    @FXML
    private void onLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 580);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setMaximized(false);
            stage.setTitle("NOVA - Sign In");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("Logout error: " + e.getMessage());
        }
    }
}
