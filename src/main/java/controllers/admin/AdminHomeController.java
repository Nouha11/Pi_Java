package controllers.admin;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import models.library.Loan;
import models.forum.Post; // 🔥 Forum Import
import services.gamification.GameService;
import services.gamification.RewardService;
import services.library.BookService;
import services.library.LoanService;
import services.quiz.QuizService;
import services.quiz.QuestionService;
import services.forum.PostService; // 🔥 Forum Import
import utils.MyConnection;

import java.sql.*;
import java.time.LocalTime;
import java.util.List;

public class AdminHomeController {

    @FXML private Label lblGreeting;

    // Stat labels
    @FXML private Label statUsers, statUsersDetail;
    @FXML private Label statBooks, statBooksDetail;
    @FXML private Label statLoans, statLoansDetail;
    @FXML private Label statGames, statGamesDetail;
    @FXML private Label statRewards, statRewardsDetail;
    @FXML private Label statQuizzes, statQuizzesDetail;
    @FXML private Label statForum, statForumDetail; // 🔥 NEW FORUM LABELS

    // Role breakdown
    @FXML private Label roleStudents, roleTutors, roleAdmins;

    // Recent loans table
    @FXML private TableView<Loan>            recentLoansTable;
    @FXML private TableColumn<Loan, String>  loanColBook, loanColUser, loanColStatus, loanColDate;

    private final Connection      conn          = MyConnection.getInstance().getCnx();
    private final BookService     bookService   = new BookService();
    private final LoanService     loanService   = new LoanService();
    private final GameService     gameService   = new GameService();
    private final RewardService   rewardService = new RewardService();
    private final QuizService     quizService   = new QuizService();
    private final QuestionService questionService = new QuestionService();
    private final PostService     postService   = new PostService(); // 🔥 NEW FORUM SERVICE

    @FXML
    public void initialize() {
        setGreeting();
        loadStats();
        setupLoansTable();
        loadRecentLoans();
    }

    private void setGreeting() {
        int hour = LocalTime.now().getHour();
        String tod = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
        lblGreeting.setText("Good " + tod + ", Admin 👋");
    }

    private void loadStats() {
        try {
            // Users
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(is_active) active, " +
                            "SUM(role='ROLE_STUDENT') students, SUM(role='ROLE_TUTOR') tutors, SUM(role='ROLE_ADMIN') admins " +
                            "FROM user");
            if (rs.next()) {
                statUsers.setText(String.valueOf(rs.getInt("total")));
                statUsersDetail.setText(rs.getInt("active") + " active");
                roleStudents.setText(String.valueOf(rs.getInt("students")));
                roleTutors.setText(String.valueOf(rs.getInt("tutors")));
                roleAdmins.setText(String.valueOf(rs.getInt("admins")));
            }

            // Books
            ResultSet rb = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(is_digital=1) digital, SUM(is_digital=0) physical FROM books");
            if (rb.next()) {
                statBooks.setText(String.valueOf(rb.getInt("total")));
                statBooksDetail.setText(rb.getInt("digital") + " digital  ·  " + rb.getInt("physical") + " physical");
            }

            // Loans
            ResultSet rl = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(status='PENDING') pending FROM loans");
            if (rl.next()) {
                statLoans.setText(String.valueOf(rl.getInt("total")));
                statLoansDetail.setText(rl.getInt("pending") + " pending approval");
            }

            // Games
            List<models.gamification.Game> games = gameService.getAllGames();
            long activeGames = games.stream().filter(models.gamification.Game::isActive).count();
            statGames.setText(String.valueOf(games.size()));
            statGamesDetail.setText(activeGames + " active");

            // Rewards
            List<models.gamification.Reward> rewards = rewardService.getAllRewards();
            long activeRewards = rewards.stream().filter(models.gamification.Reward::isActive).count();
            statRewards.setText(String.valueOf(rewards.size()));
            statRewardsDetail.setText(activeRewards + " active");

            // Quizzes
            List<models.quiz.Quiz> quizzes = quizService.getAllQuizzes();
            int totalQuestions = quizzes.stream()
                    .mapToInt(q -> questionService.getQuestionsByQuizId(q.getId()).size())
                    .sum();
            statQuizzes.setText(String.valueOf(quizzes.size()));
            statQuizzesDetail.setText(totalQuestions + " questions total");

            // 🔥 FORUM STATS 🔥
            List<Post> posts = postService.afficher();
            long openDiscussions = posts.stream().filter(p -> !p.isLocked()).count();
            statForum.setText(String.valueOf(posts.size()));
            statForumDetail.setText(openDiscussions + " open discussions");

        } catch (Exception e) {
            System.err.println("AdminHome stats error: " + e.getMessage());
        }
    }

    private void setupLoansTable() {
        loanColBook.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        loanColUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        loanColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        loanColDate.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getRequestedAt() != null
                        ? d.getValue().getRequestedAt().toString().substring(0, 10) : ""));

        // Color-code status
        loanColStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "PENDING"  -> "-fx-text-fill: #f59e0b; -fx-font-weight: bold;";
                    case "APPROVED" -> "-fx-text-fill: #3b82f6; -fx-font-weight: bold;";
                    case "ACTIVE"   -> "-fx-text-fill: #22c55e; -fx-font-weight: bold;";
                    case "RETURNED" -> "-fx-text-fill: #94a3b8; -fx-font-weight: bold;";
                    case "REJECTED" -> "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });
    }

    private void loadRecentLoans() {
        try {
            List<Loan> all = loanService.afficher();
            List<Loan> recent = all.stream().limit(6).toList();
            recentLoansTable.setItems(FXCollections.observableArrayList(recent));
        } catch (Exception e) {
            System.err.println("AdminHome loans error: " + e.getMessage());
        }
    }

    // ── Quick action buttons ──────────────────────────────────────────────────

    @FXML
    private void quickAddBook() {
        navigateDashboard("books");
    }

    @FXML private void quickUsers()     { navigateDashboard("users"); }
    @FXML private void quickGameStats() { navigateDashboard("gameStats"); }
    @FXML private void quickForum()     { navigateDashboard("forum"); } // 🔥 NEW BUTTON ROUTE

    private void navigateDashboard(String target) {
        try {
            var scene = recentLoansTable.getScene();
            if (scene == null) return;
            Object ctrl = scene.getRoot().getProperties().get("adminDashboardController");
            if (ctrl instanceof AdminDashboardController adc) {
                switch (target) {
                    case "users"     -> adc.showUsers();
                    case "books"     -> adc.showBooks();
                    case "gameStats" -> adc.showGameStats();
                    case "forum"     -> adc.showForum(); // 🔥 NEW FORUM ROUTE
                }
            }
        } catch (Exception ignored) {}
    }
}