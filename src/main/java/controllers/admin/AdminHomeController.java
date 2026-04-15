package controllers.admin;

import javafx.animation.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.gamification.Game;
import models.gamification.Reward;
import models.library.Loan;
import models.quiz.Quiz;
import models.studysession.Course;
import services.gamification.GameService;
import services.gamification.RewardService;
import services.library.LoanService;
import services.quiz.QuestionService;
import services.quiz.QuizService;
import services.studysession.CourseService;
import utils.MyConnection;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class AdminHomeController implements Initializable {

    // ── Header ──────────────────────────────────────────────
    @FXML private StackPane heroStack;
    @FXML private Pane      particlePane;
    @FXML private ImageView heroLogo;
    @FXML private VBox      rootVBox;
    @FXML private VBox      headerPane;
    @FXML private Label     lblGreeting;
    @FXML private Label     lblDate;
    @FXML private Label     lblTime;

    // ── Stat rows ────────────────────────────────────────────
    @FXML private HBox statsRow1, statsRow2;
    @FXML private VBox cardUsers, cardBooks, cardLoans, cardGames;
    @FXML private VBox cardRewards, cardQuizzes, cardForum;
    @FXML private Label statUsers, statUsersDetail;
    @FXML private Label statBooks, statBooksDetail;
    @FXML private Label statLoans, statLoansDetail;
    @FXML private Label statGames, statGamesDetail;
    @FXML private Label statRewards, statRewardsDetail;
    @FXML private Label statQuizzes, statQuizzesDetail;
    @FXML private Label statForum, statForumDetail;

    // ── Content row 1 ────────────────────────────────────────
    @FXML private HBox contentArea;
    @FXML private VBox coursesPanel;
    @FXML private TableView<Course> recentCoursesTable;
    @FXML private TableColumn<Course, String>  courseColName, courseColCategory, courseColDifficulty, courseColStatus;
    @FXML private VBox sidebar;
    @FXML private Label roleStudents, roleTutors, roleAdmins;

    // ── Content row 2 ────────────────────────────────────────
    @FXML private HBox row2;
    @FXML private VBox loansPanel;
    @FXML private TableView<Loan> recentLoansTable;
    @FXML private TableColumn<Loan, String> loanColBook, loanColUser, loanColStatus, loanColDate;
    @FXML private VBox gamesPanel;
    @FXML private VBox topGamesBox;

    // ── Content row 3 ────────────────────────────────────────
    @FXML private HBox row3;
    @FXML private VBox quizzesPanel;
    @FXML private TableView<Quiz> recentQuizzesTable;
    @FXML private TableColumn<Quiz, String>  quizColTitle;
    @FXML private TableColumn<Quiz, Integer> quizColQuestions;
    @FXML private VBox rewardsPanel;
    @FXML private VBox topRewardsBox;

    // ── Content row 4 ────────────────────────────────────────
    @FXML private HBox row4;
    @FXML private VBox recentGamesPanel;
    @FXML private TableView<Game> recentGamesTable;
    @FXML private TableColumn<Game, String>  gameColName, gameColType, gameColDifficulty;
    @FXML private TableColumn<Game, Integer> gameColXP;
    @FXML private VBox recentRewardsPanel;
    @FXML private TableView<Reward> recentRewardsTable;
    @FXML private TableColumn<Reward, String>  rewardColName, rewardColType;
    @FXML private TableColumn<Reward, Integer> rewardColValue;

    // ── Services ─────────────────────────────────────────────
    private final Connection      conn            = MyConnection.getInstance().getCnx();
    private final LoanService     loanService     = new LoanService();
    private final GameService     gameService     = new GameService();
    private final RewardService   rewardService   = new RewardService();
    private final QuizService     quizService     = new QuizService();
    private final QuestionService questionService = new QuestionService();
    private final CourseService   courseService   = new CourseService();

    private String adminUsername = "Admin";

    public void setAdminUsername(String username) {
        this.adminUsername = username;
        if (lblGreeting != null) updateGreeting();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateGreeting();
        startLiveClock();
        loadStats();
        setupCoursesTable();
        loadRecentCourses();
        setupLoansTable();
        loadRecentLoans();
        loadTopGames();
        setupQuizzesTable();
        loadRecentQuizzes();
        loadTopRewards();
        setupGamesRewardsTable();
        loadRecentGames();
        loadRecentRewards();
        playEntranceAnimation();
    }

    // ── Greeting ──────────────────────────────────────────────

    private void updateGreeting() {
        int hour = LocalTime.now().getHour();
        String part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        lblGreeting.setText(part + ", " + adminUsername + " \uD83D\uDC4B");
        if (lblDate != null)
            lblDate.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")));
    }

    private void startLiveClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        lblTime.setText(LocalTime.now().format(fmt));
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1),
                e -> lblTime.setText(LocalTime.now().format(fmt))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    // ── Stats ─────────────────────────────────────────────────

    private void loadStats() {
        try {
            ResultSet ru = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(is_active) active, " +
                    "SUM(role='ROLE_STUDENT') students, SUM(role='ROLE_TUTOR') tutors, " +
                    "SUM(role='ROLE_ADMIN') admins FROM user");
            if (ru.next()) {
                statUsers.setText(String.valueOf(ru.getInt("total")));
                statUsersDetail.setText(ru.getInt("active") + " active");
                roleStudents.setText(String.valueOf(ru.getInt("students")));
                roleTutors.setText(String.valueOf(ru.getInt("tutors")));
                roleAdmins.setText(String.valueOf(ru.getInt("admins")));
            }

            ResultSet rb = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(is_digital=1) digital, SUM(is_digital=0) physical FROM books");
            if (rb.next()) {
                statBooks.setText(String.valueOf(rb.getInt("total")));
                statBooksDetail.setText(rb.getInt("digital") + " digital  ·  " + rb.getInt("physical") + " physical");
            }

            ResultSet rl = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) total, SUM(status='PENDING') pending FROM loans");
            if (rl.next()) {
                statLoans.setText(String.valueOf(rl.getInt("total")));
                statLoansDetail.setText(rl.getInt("pending") + " pending");
            }

            List<Game> games = gameService.getAllGames();
            long activeGames = games.stream().filter(Game::isActive).count();
            statGames.setText(String.valueOf(games.size()));
            statGamesDetail.setText(activeGames + " active");

            List<Reward> rewards = rewardService.getAllRewards();
            long activeRewards = rewards.stream().filter(Reward::isActive).count();
            statRewards.setText(String.valueOf(rewards.size()));
            statRewardsDetail.setText(activeRewards + " active");

            List<Quiz> quizzes = quizService.getAllQuizzes();
            int totalQ = quizzes.stream()
                    .mapToInt(q -> questionService.getQuestionsByQuizId(q.getId()).size()).sum();
            statQuizzes.setText(String.valueOf(quizzes.size()));
            statQuizzesDetail.setText(totalQ + " questions total");

            ResultSet rf = conn.createStatement().executeQuery("SELECT COUNT(*) total FROM post");
            if (rf.next()) {
                statForum.setText(String.valueOf(rf.getInt("total")));
                statForumDetail.setText("posts in forum");
            }
        } catch (Exception e) {
            System.err.println("AdminHome stats error: " + e.getMessage());
        }
    }

    // ── Courses table ─────────────────────────────────────────

    private void setupCoursesTable() {
        courseColName.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        courseColCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        courseColDifficulty.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        courseColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        courseColStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s.toUpperCase()) {
                    case "PUBLISHED"   -> "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
                    case "DRAFT"       -> "-fx-text-fill: #d97706; -fx-font-weight: bold;";
                    case "ARCHIVED"    -> "-fx-text-fill: #94a3b8; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });
    }

    private void loadRecentCourses() {
        try {
            List<Course> courses = courseService.findByFilters(null, null, null, null)
                    .stream().limit(8).toList();
            recentCoursesTable.setItems(FXCollections.observableArrayList(courses));
        } catch (Exception e) {
            System.err.println("AdminHome courses error: " + e.getMessage());
        }
    }

    // ── Loans table ───────────────────────────────────────────

    private void setupLoansTable() {
        loanColBook.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        loanColUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        loanColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        loanColDate.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getRequestedAt() != null
                        ? d.getValue().getRequestedAt().toString().substring(0, 10) : ""));

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
            List<Loan> recent = loanService.afficher().stream().limit(6).toList();
            recentLoansTable.setItems(FXCollections.observableArrayList(recent));
        } catch (Exception e) {
            System.err.println("AdminHome loans error: " + e.getMessage());
        }
    }

    // ── Top Games ─────────────────────────────────────────────

    private void loadTopGames() {
        try {
            List<Game> top = gameService.getAllGames().stream()
                    .filter(Game::isActive)
                    .sorted((a, b) -> Integer.compare(b.getRewardXP(), a.getRewardXP()))
                    .limit(5).toList();

            topGamesBox.getChildren().clear();
            for (Game g : top) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 10 16; -fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

                Label icon = new Label(gameEmoji(g.getType()));
                icon.setStyle("-fx-font-size: 20px;");

                VBox info = new VBox(2);
                Label name = new Label(g.getName());
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                Label meta = new Label(g.getType() + "  ·  " + g.getDifficulty());
                meta.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                info.getChildren().addAll(name, meta);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label xp = new Label("+" + g.getRewardXP() + " XP");
                xp.setStyle("-fx-text-fill: #6366f1; -fx-font-weight: bold; -fx-font-size: 12px; " +
                            "-fx-background-color: #eef2ff; -fx-background-radius: 6; -fx-padding: 3 8;");

                row.getChildren().addAll(icon, info, xp);
                topGamesBox.getChildren().add(row);
            }
        } catch (Exception e) {
            System.err.println("AdminHome games error: " + e.getMessage());
        }
    }

    // ── Quizzes table ─────────────────────────────────────────

    private void setupQuizzesTable() {
        quizColTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        quizColQuestions.setCellValueFactory(d ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        questionService.getQuestionsByQuizId(d.getValue().getId()).size()));
    }

    private void loadRecentQuizzes() {
        try {
            List<Quiz> quizzes = quizService.getAllQuizzes().stream().limit(6).toList();
            recentQuizzesTable.setItems(FXCollections.observableArrayList(quizzes));
        } catch (Exception e) {
            System.err.println("AdminHome quizzes error: " + e.getMessage());
        }
    }

    // ── Top Rewards ───────────────────────────────────────────

    private void loadTopRewards() {
        try {
            List<Reward> top = rewardService.getAllRewards().stream()
                    .filter(Reward::isActive)
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5).toList();

            topRewardsBox.getChildren().clear();
            for (Reward r : top) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 10 16; -fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

                // Icon image or emoji fallback
                ImageView iv = loadRewardIcon(r.getIcon(), 32);
                if (iv != null) {
                    row.getChildren().add(iv);
                } else {
                    Label emoji = new Label(rewardEmoji(r.getType()));
                    emoji.setStyle("-fx-font-size: 20px;");
                    row.getChildren().add(emoji);
                }

                VBox info = new VBox(2);
                Label name = new Label(r.getName());
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                Label type = new Label(r.getType());
                type.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                info.getChildren().addAll(name, type);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label val = new Label("+" + r.getValue() + " pts");
                val.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold; -fx-font-size: 12px; " +
                             "-fx-background-color: #fffbeb; -fx-background-radius: 6; -fx-padding: 3 8;");

                row.getChildren().addAll(info, val);
                topRewardsBox.getChildren().add(row);
            }
        } catch (Exception e) {
            System.err.println("AdminHome rewards error: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private ImageView loadRewardIcon(String path, double size) {
        if (path == null || path.isBlank()) return null;
        try {
            var stream = getClass().getResourceAsStream("/images/rewards/" + path);
            if (stream != null) {
                ImageView iv = new ImageView(new Image(stream, size, size, true, true));
                iv.setFitWidth(size); iv.setFitHeight(size);
                return iv;
            }
            File f = new File(path);
            if (f.exists()) {
                ImageView iv = new ImageView(new Image(f.toURI().toString(), size, size, true, true));
                iv.setFitWidth(size); iv.setFitHeight(size);
                return iv;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String gameEmoji(String type) {
        return switch (type) {
            case "PUZZLE" -> "\uD83E\uDDE9";
            case "MEMORY" -> "\uD83E\uDDE0";
            case "TRIVIA" -> "\u2753";
            case "ARCADE" -> "\uD83D\uDD79";
            default       -> "\uD83C\uDFAE";
        };
    }

    private String rewardEmoji(String type) {
        return switch (type) {
            case "BADGE"        -> "\uD83C\uDFC5";
            case "ACHIEVEMENT"  -> "\uD83C\uDFC6";
            case "BONUS_XP"     -> "\u2B50";
            case "BONUS_TOKENS" -> "\uD83E\uDE99";
            default             -> "\uD83C\uDF81";
        };
    }

    // ── Recent Games & Rewards tables ────────────────────────

    private void setupGamesRewardsTable() {
        gameColName.setCellValueFactory(new PropertyValueFactory<>("name"));
        gameColType.setCellValueFactory(new PropertyValueFactory<>("type"));
        gameColDifficulty.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        gameColXP.setCellValueFactory(new PropertyValueFactory<>("rewardXP"));

        rewardColName.setCellValueFactory(new PropertyValueFactory<>("name"));
        rewardColType.setCellValueFactory(new PropertyValueFactory<>("type"));
        rewardColValue.setCellValueFactory(new PropertyValueFactory<>("value"));
    }

    private void loadRecentGames() {
        try {
            List<Game> games = gameService.getAllGames().stream().limit(8).toList();
            recentGamesTable.setItems(FXCollections.observableArrayList(games));
        } catch (Exception e) {
            System.err.println("AdminHome recent games error: " + e.getMessage());
        }
    }

    private void loadRecentRewards() {
        try {
            List<Reward> rewards = rewardService.getAllRewards().stream().limit(8).toList();
            recentRewardsTable.setItems(FXCollections.observableArrayList(rewards));
        } catch (Exception e) {
            System.err.println("AdminHome recent rewards error: " + e.getMessage());
        }
    }

    // ── Entrance animation ────────────────────────────────────

    private void playEntranceAnimation() {
        List<Node> cards = List.of(
                cardUsers, cardBooks, cardLoans, cardGames,
                cardRewards, cardQuizzes, cardForum);

        javafx.application.Platform.runLater(() -> {
            createFloatingParticles();

            headerPane.setOpacity(0);
            if (heroLogo != null) { heroLogo.setOpacity(0); heroLogo.setTranslateX(-30); }

            HBox heroInner = (HBox) headerPane.getChildren().get(0);
            VBox greetingBox = (VBox) heroInner.getChildren().get(1);
            greetingBox.setOpacity(0);
            greetingBox.setTranslateY(24);

            statsRow1.setOpacity(0); statsRow2.setOpacity(0);
            for (Node c : cards) { c.setOpacity(0); c.setTranslateY(28); }
            contentArea.setOpacity(0); contentArea.setTranslateY(20);
            row2.setOpacity(0);       row2.setTranslateY(20);
            row3.setOpacity(0);       row3.setTranslateY(20);
            if (row4 != null) { row4.setOpacity(0); row4.setTranslateY(20); }

            FadeTransition bgFade = new FadeTransition(Duration.millis(800), headerPane);
            bgFade.setFromValue(0); bgFade.setToValue(1);

            ParallelTransition logoAnim = new ParallelTransition();
            if (heroLogo != null) {
                FadeTransition lf = new FadeTransition(Duration.millis(700), heroLogo);
                lf.setFromValue(0); lf.setToValue(1);
                TranslateTransition ls = new TranslateTransition(Duration.millis(600), heroLogo);
                ls.setFromX(-30); ls.setToX(0); ls.setInterpolator(Interpolator.EASE_OUT);
                logoAnim.getChildren().addAll(lf, ls);
            }

            FadeTransition gf = new FadeTransition(Duration.millis(600), greetingBox);
            gf.setFromValue(0); gf.setToValue(1);
            TranslateTransition gs = new TranslateTransition(Duration.millis(600), greetingBox);
            gs.setFromY(24); gs.setToY(0); gs.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition heroAnim = new ParallelTransition(bgFade, logoAnim,
                    new ParallelTransition(gf, gs));

            FadeTransition r1 = new FadeTransition(Duration.millis(80), statsRow1);
            r1.setFromValue(0); r1.setToValue(1);
            FadeTransition r2 = new FadeTransition(Duration.millis(80), statsRow2);
            r2.setFromValue(0); r2.setToValue(1);

            ParallelTransition allCards = new ParallelTransition();
            for (int i = 0; i < cards.size(); i++) {
                Node card = cards.get(i);
                PauseTransition pause = new PauseTransition(Duration.millis(i * 65));
                TranslateTransition cs = new TranslateTransition(Duration.millis(400), card);
                cs.setFromY(28); cs.setToY(0); cs.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition cf = new FadeTransition(Duration.millis(400), card);
                cf.setFromValue(0); cf.setToValue(1);
                allCards.getChildren().add(new SequentialTransition(pause, new ParallelTransition(cs, cf)));
            }

            new SequentialTransition(
                    heroAnim,
                    new PauseTransition(Duration.millis(80)),
                    new SequentialTransition(new ParallelTransition(r1, r2), allCards),
                    new PauseTransition(Duration.millis(60)),
                    buildFadeSlide(contentArea, 20, 400),
                    new PauseTransition(Duration.millis(40)),
                    buildFadeSlide(row2, 20, 380),
                    new PauseTransition(Duration.millis(40)),
                    buildFadeSlide(row3, 20, 380),
                    new PauseTransition(Duration.millis(40)),
                    row4 != null ? buildFadeSlide(row4, 20, 380) : new PauseTransition(Duration.millis(1))
            ).play();
        });
    }

    private ParallelTransition buildFadeSlide(Node node, double fromY, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(fromY); tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT);
        return new ParallelTransition(ft, tt);
    }

    private ParallelTransition buildSlide(Node node, double fromX, double toX, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromX(fromX); tt.setToX(toX); tt.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0); ft.setToValue(1);
        return new ParallelTransition(tt, ft);
    }

    private void createFloatingParticles() {
        if (particlePane == null) return;
        java.util.Random rnd = new java.util.Random();
        particlePane.prefWidthProperty().bind(headerPane.widthProperty());
        particlePane.prefHeightProperty().bind(headerPane.heightProperty());
        for (int i = 0; i < 22; i++) {
            int size = rnd.nextInt(14) + 5;
            Rectangle rect = new Rectangle(size, size);
            rect.setFill(Color.web("#ffffff", rnd.nextDouble() * 0.13 + 0.04));
            rect.setX(rnd.nextInt(1200)); rect.setY(rnd.nextInt(220));
            rect.setRotate(rnd.nextInt(360));
            particlePane.getChildren().add(rect);
            TranslateTransition tt = new TranslateTransition(Duration.seconds(rnd.nextInt(14) + 14), rect);
            tt.setByY(-120 - rnd.nextInt(160)); tt.setByX((rnd.nextDouble() - 0.5) * 90);
            tt.setCycleCount(TranslateTransition.INDEFINITE); tt.setAutoReverse(true); tt.play();
            RotateTransition rt = new RotateTransition(Duration.seconds(rnd.nextInt(9) + 9), rect);
            rt.setByAngle(360); rt.setCycleCount(RotateTransition.INDEFINITE); rt.play();
            FadeTransition ft = new FadeTransition(Duration.seconds(rnd.nextInt(7) + 4), rect);
            ft.setFromValue(0.05); ft.setToValue(0.55);
            ft.setCycleCount(FadeTransition.INDEFINITE); ft.setAutoReverse(true); ft.play();
        }
    }

    // ── Quick actions ─────────────────────────────────────────

    @FXML private void quickAddBook()       { navigate("showBooks"); }
    @FXML private void quickUsers()         { navigate("showUsers"); }
    @FXML private void quickGameStats()     { navigate("showGameStats"); }
    @FXML private void quickForum()         { navigate("showForum"); }
    @FXML private void onViewAllCourses()   { navigate("showCourses"); }
    @FXML private void onViewAllLoans()     { navigate("showLoans"); }
    @FXML private void onViewAllGames()     { navigate("showGames"); }
    @FXML private void onViewAllQuizzes()   { navigate("showQuizzes"); }
    @FXML private void onViewAllRewards()   { navigate("showRewards"); }

    private void navigate(String method) {
        try {
            var scene = recentCoursesTable.getScene();
            if (scene == null) return;
            Object ctrl = scene.getRoot().getProperties().get("adminDashboardController");
            if (ctrl instanceof AdminDashboardController adc) {
                switch (method) {
                    case "showBooks"     -> adc.showBooks();
                    case "showUsers"     -> adc.showUsers();
                    case "showGameStats" -> adc.showGameStats();
                    case "showForum"     -> adc.showForum();
                    case "showCourses"   -> adc.showCourses();
                    case "showLoans"     -> adc.showLoans();
                    case "showGames"     -> adc.showGames();
                    case "showQuizzes"   -> adc.showQuizzes();
                    case "showRewards"   -> adc.showRewards();
                }
            }
        } catch (Exception ignored) {}
    }
}
