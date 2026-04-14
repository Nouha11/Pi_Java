package controllers.admin;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import models.users.User;

import java.io.IOException;
import java.util.List;

public class AdminDashboardController {

    @FXML private StackPane contentArea;
    @FXML private Label     lblCurrentUser;
    @FXML private Label     lblCurrentRole;
    @FXML private Label     lblPageTitle;

    // 🔥 Added Forum Toggle Button
    @FXML private Button btnToggleUsers, btnToggleLibrary, btnToggleStudy, btnToggleGame, btnToggleQuiz, btnToggleForum;
    // 🔥 Added Forum VBox Group
    @FXML private VBox   usersGroup, libraryGroup, studyGroup, gameGroup, quizGroup, forumGroup;

    @FXML private HBox navHome;
    @FXML private HBox navUsers;
    @FXML private HBox navBooks, navLoans, navPayments;
    @FXML private HBox navCourses, navPlannings, navSessions, navStudyStats;
    @FXML private HBox navGames, navRewards, navGameStats;
    @FXML private HBox navQuizzes;
    @FXML private HBox navForum; // 🔥 Added Forum Nav Item

    private List<HBox> allNavItems;

    @FXML
    public void initialize() {
        allNavItems = List.of(
                navHome, navUsers,
                navBooks, navLoans, navPayments,
                navCourses, navPlannings, navSessions, navStudyStats,
                navGames, navRewards, navGameStats,
                navQuizzes, navForum // 🔥 Added to the list so highlighting works perfectly
        );
        // Show homepage by default instead of users
        loadView("/views/admin/AdminHome.fxml");
        lblPageTitle.setText("Overview");
    }

    public void setCurrentUser(User user) {
        if (user == null) return;
        lblCurrentUser.setText(user.getUsername());
        lblCurrentRole.setText(user.getRole().name());
        // Store reference so child views can navigate back
        javafx.application.Platform.runLater(() -> {
            if (contentArea.getScene() != null)
                contentArea.getScene().getRoot().getProperties()
                        .put("adminDashboardController", this);
        });
    }

    // ── TOGGLES ───────────────────────────────────────────────────────────────

    @FXML public void toggleUsers()   { toggle(usersGroup,   btnToggleUsers,   "USERS"); }
    @FXML public void toggleLibrary() { toggle(libraryGroup, btnToggleLibrary, "LIBRARY"); }
    @FXML public void toggleStudy()   { toggle(studyGroup,   btnToggleStudy,   "STUDY SESSION"); }
    @FXML public void toggleGame()    { toggle(gameGroup,    btnToggleGame,    "GAMIFICATION"); }
    @FXML public void toggleQuiz()    { toggle(quizGroup,    btnToggleQuiz,    "QUIZ"); }
    @FXML public void toggleForum()   { toggle(forumGroup,   btnToggleForum,   "FORUM"); } // 🔥 New Toggle

    private void toggle(VBox group, Button btn, String label) {
        boolean open = !group.isVisible();
        group.setVisible(open);
        group.setManaged(open);
        btn.setText(label + (open ? "  ▾" : "  ▸"));
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    @FXML public void showHome()       { nav(navHome,       "Overview",       "/views/admin/AdminHome.fxml"); }
    @FXML public void showUsers()      { nav(navUsers,      "Users",          "/views/users/user-list.fxml"); }
    @FXML public void showBooks()      { nav(navBooks,      "Books",          "/views/library/BookView.fxml"); }
    @FXML public void showLoans()      { nav(navLoans,      "Loans",          "/views/library/LoanView.fxml"); }
    @FXML public void showPayments()   { nav(navPayments,   "Payments",       "/views/library/PaymentView.fxml"); }
    @FXML public void showCourses()    { nav(navCourses,    "Courses",        "/views/studysession/CourseView.fxml"); }
    @FXML public void showPlannings()  { nav(navPlannings,  "Plannings",      "/views/studysession/PlanningView.fxml"); }
    @FXML public void showSessions()   { nav(navSessions,   "Study Sessions", "/views/studysession/StudySessionView.fxml"); }
    @FXML public void showStudyStats() { nav(navStudyStats, "Study Stats",    "/views/studysession/StatsView.fxml"); }
    @FXML public void showGames()      { nav(navGames,      "Games",          "/views/gamification/game_list.fxml"); }
    @FXML public void showRewards()    { nav(navRewards,    "Rewards",        "/views/gamification/reward_list.fxml"); }
    @FXML public void showGameStats()  { nav(navGameStats,  "Game Stats",     "/views/gamification/stats.fxml"); }
    @FXML public void showQuizzes()    { nav(navQuizzes,    "Quizzes",        "/views/quiz/quiz_list.fxml"); }

    // 🔥 NEW: Routes to your beautiful Admin Forum Data Grid
    @FXML public void showForum()      { nav(navForum,      "Forum Management", "/views/forum/admin/admin_forum.fxml"); }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void nav(HBox active, String title, String fxmlPath) {
        setActive(active);
        lblPageTitle.setText(title);
        loadView(fxmlPath);
    }

    private void setActive(HBox active) {
        for (HBox item : allNavItems) {
            item.getStyleClass().removeAll("admin-nav-row-active");
            if (!item.getStyleClass().contains("admin-nav-row"))
                item.getStyleClass().add("admin-nav-row");

            // reset icon + text labels
            item.getChildren().forEach(n -> {
                if (n instanceof Label lbl) {
                    lbl.getStyleClass().removeAll("admin-nav-icon-active", "admin-nav-text-active");
                    if (!lbl.getStyleClass().contains("admin-nav-icon") &&
                            !lbl.getStyleClass().contains("admin-nav-text")) {
                    }
                    if (lbl.getStyleClass().isEmpty()) {
                    }
                }
            });
        }

        // Activate the clicked item
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

    private void loadView(String path) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(path));
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("Admin nav error [" + path + "]: " + e.getMessage());
        }
    }
}