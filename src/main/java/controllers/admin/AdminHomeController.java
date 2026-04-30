package controllers.admin;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import services.api.GeoLocationService;
import utils.MyConnection;

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

    // ── Content row containers ────────────────────────────────────────
    @FXML private HBox contentArea, row2, row3, row4;
    @FXML private VBox coursesPanel, sidebar, loansPanel, gamesPanel, topGamesBox;
    @FXML private javafx.scene.layout.HBox geoWidget;
    @FXML private javafx.scene.control.Label lblGeoLocation;
    @FXML private javafx.scene.control.Label lblGeoTimezone;
    @FXML private javafx.scene.control.Label lblGeoIp;
    @FXML private javafx.scene.control.Label lblGeoCurrency;
    private final GeoLocationService geoService = new GeoLocationService();
    @FXML private VBox quizzesPanel, rewardsPanel, topRewardsBox;
    @FXML private VBox recentGamesPanel, recentRewardsPanel;

    @FXML private Label roleStudents, roleTutors, roleAdmins;

    // ── New Dynamic List Containers ───────────────────
    @FXML private VBox coursesListContainer;
    @FXML private VBox loansListContainer;
    @FXML private VBox quizzesListContainer;
    @FXML private VBox gamesListContainer;
    @FXML private VBox rewardsListContainer;

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
        loadGeoLocationAsync();

        loadRecentCourses();
        loadRecentLoans();
        loadTopGames();
        loadRecentQuizzes();
        loadTopRewards();
        loadRecentGames();
        loadRecentRewards();

        playEntranceAnimation();
    }

    // ── Greeting & Stats ──────────────────────────────────────────────

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

    // ── Card Builders ─────────────────────────────────────────

    /** A mini card with a left accent bar, icon circle, title + subtitle, and right badge */
    private HBox createCard(String accentColor, String iconText, String iconBg,
                            String title, String subtitle, String badgeText, String badgeStyle) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 3);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);"));

        // Icon circle
        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(38, 38); iconCircle.setMaxSize(38, 38);
        iconCircle.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 50;");
        Label iconLbl = new Label(iconText);
        iconLbl.setStyle("-fx-font-size: 16px;");
        iconCircle.getChildren().add(iconLbl);

        // Text
        VBox text = new VBox(3);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        titleLbl.setWrapText(true);
        text.getChildren().add(titleLbl);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            text.getChildren().add(subLbl);
        }

        card.getChildren().addAll(iconCircle, text);

        if (badgeText != null && !badgeText.isBlank()) {
            Label badge = new Label(badgeText);
            badge.setStyle(badgeStyle + " -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 20;");
            card.getChildren().add(badge);
        }
        return card;
    }

    private Label createEmptyLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-padding: 24; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
        return lbl;
    }

    private void loadRecentCourses() {
        try {
            List<Course> courses = courseService.findByFilters(null, null, null, null).stream().limit(8).toList();
            coursesListContainer.getChildren().clear();
            if (courses.isEmpty()) { coursesListContainer.getChildren().add(createEmptyLabel("No recent courses found.")); return; }
            for (Course c : courses) {
                String statusVal = c.getStatus() != null ? c.getStatus().toUpperCase() : "";
                String[] badge = switch (statusVal) {
                    case "COMPLETED"   -> new String[]{"Completed",   "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"};
                    case "IN_PROGRESS" -> new String[]{"In Progress", "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;"};
                    default            -> new String[]{"Not Started", "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"};
                };
                String diff = c.getDifficulty() != null ? c.getDifficulty() : "";
                String accent = switch (diff.toUpperCase()) {
                    case "ADVANCED"     -> "#ef4444";
                    case "INTERMEDIATE" -> "#f59e0b";
                    default             -> "#10b981";
                };
                HBox card = createCard(accent, "B", "#f0fdf4",
                        c.getCourseName() != null ? c.getCourseName() : "—",
                        (c.getCategory() != null ? c.getCategory() : "") + "  ·  " + diff,
                        badge[0], badge[1]);
                coursesListContainer.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome courses error: " + e.getMessage()); }
    }

    private void loadRecentLoans() {
        try {
            List<Loan> recent = loanService.afficher().stream().limit(6).toList();
            loansListContainer.getChildren().clear();
            if (recent.isEmpty()) { loansListContainer.getChildren().add(createEmptyLabel("No recent loan requests.")); return; }
            for (Loan l : recent) {
                String st = l.getStatus() != null ? l.getStatus().toUpperCase() : "";
                String[] badge = switch (st) {
                    case "PENDING"  -> new String[]{"Pending",  "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"};
                    case "APPROVED" -> new String[]{"Approved", "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;"};
                    case "ACTIVE"   -> new String[]{"Active",   "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"};
                    case "RETURNED" -> new String[]{"Returned", "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"};
                    case "REJECTED" -> new String[]{"Rejected", "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;"};
                    default         -> new String[]{st,         "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"};
                };
                String accent = switch (st) {
                    case "ACTIVE"   -> "#10b981";
                    case "APPROVED" -> "#3b82f6";
                    case "REJECTED" -> "#ef4444";
                    default         -> "#f59e0b";
                };
                String date = l.getRequestedAt() != null ? l.getRequestedAt().toString().substring(0, 10) : "";
                String sub = (l.getUserName() != null ? l.getUserName() : "Unknown") + (date.isBlank() ? "" : "  ·  " + date);
                HBox card = createCard(accent, "L", "#fffbeb",
                        l.getBookTitle() != null ? l.getBookTitle() : "Unknown Book",
                        sub, badge[0], badge[1]);
                loansListContainer.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome loans error: " + e.getMessage()); }
    }

    private void loadRecentQuizzes() {
        try {
            List<Quiz> quizzes = quizService.getAllQuizzes().stream().limit(6).toList();
            quizzesListContainer.getChildren().clear();
            if (quizzes.isEmpty()) { quizzesListContainer.getChildren().add(createEmptyLabel("No quizzes found.")); return; }
            for (Quiz q : quizzes) {
                int qCount = questionService.getQuestionsByQuizId(q.getId()).size();
                HBox card = createCard("#6366f1", "Q", "#eef2ff",
                        q.getTitle() != null ? q.getTitle() : "—",
                        null,
                        qCount + " questions",
                        "-fx-background-color: #eef2ff; -fx-text-fill: #6366f1;");
                quizzesListContainer.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome quizzes error: " + e.getMessage()); }
    }

    private void loadRecentGames() {
        try {
            List<Game> games = gameService.getAllGames().stream().limit(8).toList();
            gamesListContainer.getChildren().clear();
            if (games.isEmpty()) { gamesListContainer.getChildren().add(createEmptyLabel("No games found.")); return; }
            for (Game g : games) {
                String diff = g.getDifficulty() != null ? g.getDifficulty().toUpperCase() : "";
                String accent = switch (diff) {
                    case "HARD"   -> "#ef4444";
                    case "MEDIUM" -> "#f59e0b";
                    default       -> "#10b981";
                };
                HBox card = createCard(accent, "G", "#ecfdf5",
                        g.getName() != null ? g.getName() : "—",
                        (g.getType() != null ? g.getType() : "") + "  ·  " + diff,
                        "+" + g.getRewardXP() + " XP",
                        "-fx-background-color: #eef2ff; -fx-text-fill: #6366f1;");
                gamesListContainer.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome games error: " + e.getMessage()); }
    }

    private void loadRecentRewards() {
        try {
            List<Reward> rewards = rewardService.getAllRewards().stream().limit(8).toList();
            rewardsListContainer.getChildren().clear();
            if (rewards.isEmpty()) { rewardsListContainer.getChildren().add(createEmptyLabel("No rewards found.")); return; }
            for (Reward r : rewards) {
                HBox card = createCard("#f59e0b", "R", "#fffbeb",
                        r.getName() != null ? r.getName() : "—",
                        r.getType() != null ? r.getType() : "—",
                        "+" + r.getValue() + " pts",
                        "-fx-background-color: #fffbeb; -fx-text-fill: #d97706;");
                rewardsListContainer.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome rewards error: " + e.getMessage()); }
    }

    // ── Top Games & Rewards ─────────────────────────────────────────────

    private void loadTopGames() {
        try {
            List<Game> top = gameService.getAllGames().stream()
                    .filter(Game::isActive)
                    .sorted((a, b) -> Integer.compare(b.getRewardXP(), a.getRewardXP()))
                    .limit(5).toList();
            topGamesBox.getChildren().clear();
            for (Game g : top) {
                HBox card = createCard("#6366f1", "G", "#eef2ff",
                        g.getName() != null ? g.getName() : "—",
                        (g.getType() != null ? g.getType() : "") + "  ·  " + g.getDifficulty(),
                        "+" + g.getRewardXP() + " XP",
                        "-fx-background-color: #eef2ff; -fx-text-fill: #6366f1;");
                topGamesBox.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome top games error: " + e.getMessage()); }
    }

    private void loadTopRewards() {
        try {
            List<Reward> top = rewardService.getAllRewards().stream()
                    .filter(Reward::isActive)
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5).toList();
            topRewardsBox.getChildren().clear();
            for (Reward r : top) {
                HBox card = createCard("#f59e0b", "R", "#fffbeb",
                        r.getName() != null ? r.getName() : "—",
                        r.getType() != null ? r.getType() : "—",
                        "+" + r.getValue() + " pts",
                        "-fx-background-color: #fffbeb; -fx-text-fill: #d97706;");
                topRewardsBox.getChildren().add(card);
            }
        } catch (Exception e) { System.err.println("AdminHome top rewards error: " + e.getMessage()); }
    }

    // ── Entrance animation ────────────────────────────────────

    private void playEntranceAnimation() {
        List<Node> cards = List.of(cardUsers, cardBooks, cardLoans, cardGames, cardRewards, cardQuizzes, cardForum);

        Platform.runLater(() -> {
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

            ParallelTransition heroAnim = new ParallelTransition(bgFade, logoAnim, new ParallelTransition(gf, gs));

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

    private void createFloatingParticles() {
        if (particlePane == null) return;
        java.util.Random rnd = new java.util.Random();
        particlePane.prefWidthProperty().bind(headerPane.widthProperty());
        particlePane.prefHeightProperty().bind(headerPane.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(particlePane.widthProperty());
        clip.heightProperty().bind(particlePane.heightProperty());
        particlePane.setClip(clip);

        String[] colors = {"#818cf8", "#c7d2fe", "#6366f1", "#a5b4fc", "#e0e7ff"};

        for (int i = 0; i < 32; i++) {
            Node particle;
            int type = rnd.nextInt(3);
            double size = rnd.nextDouble() * 9 + 4;

            if (type == 0) {
                Rectangle rect = new Rectangle(size, size);
                rect.setFill(Color.TRANSPARENT);
                rect.setStroke(Color.web(colors[rnd.nextInt(colors.length)]));
                rect.setStrokeWidth(1.5);
                particle = rect;
            } else if (type == 1) {
                javafx.scene.text.Text cross = new javafx.scene.text.Text("+");
                cross.setFont(javafx.scene.text.Font.font("Consolas", size * 1.6));
                cross.setFill(Color.web(colors[rnd.nextInt(colors.length)]));
                particle = cross;
            } else {
                javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(size / 2);
                dot.setFill(Color.web(colors[rnd.nextInt(colors.length)]));
                dot.setEffect(new javafx.scene.effect.GaussianBlur(4));
                particle = dot;
            }

            particle.setOpacity(rnd.nextDouble() * 0.4 + 0.08);
            particle.setTranslateX(rnd.nextDouble() * 1400);
            particle.setTranslateY(rnd.nextDouble() * 160 + 60);
            particlePane.getChildren().add(particle);

            TranslateTransition tt = new TranslateTransition(Duration.seconds(rnd.nextInt(14) + 10), particle);
            tt.setByY(-280);
            tt.setByX((rnd.nextDouble() - 0.5) * 50);
            tt.setCycleCount(TranslateTransition.INDEFINITE);
            tt.setInterpolator(Interpolator.LINEAR);
            tt.play();

            if (type != 2) {
                RotateTransition rt = new RotateTransition(Duration.seconds(rnd.nextInt(8) + 5), particle);
                rt.setByAngle(rnd.nextBoolean() ? 180 : -180);
                rt.setCycleCount(RotateTransition.INDEFINITE);
                rt.setInterpolator(Interpolator.LINEAR);
                rt.play();
            }
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
            var scene = coursesListContainer.getScene(); // Using a node we know exists
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

    // ── IPGeolocation + RestCountries ─────────────────────────────────────────
    private void loadGeoLocationAsync() {
        if (lblGeoLocation == null) return;
        lblGeoLocation.setText("Detecting...");

        new Thread(() -> {
            GeoLocationService.GeoInfo info = geoService.fetchCurrentLocation();
            javafx.application.Platform.runLater(() -> {
                if (info == null) {
                    if (lblGeoLocation != null) lblGeoLocation.setText("Unavailable");
                    return;
                }
                if (lblGeoLocation != null) lblGeoLocation.setText(info.getSummary());
                if (lblGeoTimezone != null) lblGeoTimezone.setText(info.getTimezoneDisplay());
                if (lblGeoIp       != null) lblGeoIp.setText(info.ip);
                if (lblGeoCurrency != null) lblGeoCurrency.setText(
                    info.currency.isBlank() ? "—" : info.currency);
            });
        }, "GeoLocation").start();
    }
}