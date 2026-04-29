package controllers.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;
import models.gamification.Game;
import services.gamification.GameRewardService;
import services.gamification.GameContentService;
import services.gamification.GameRatingService;
import utils.TwemojiUtil;
import utils.UserSession;
import java.util.*;

public class GamePlayController {
    @FXML private Label lblGameTitle, lblGameType, lblCategory, lblRewardInfo, lblTimer, lblScore, lblInstructions;
    @FXML private StackPane gameOverlay;
    @FXML private VBox overlayContent;
    @FXML private Button btnStart, btnBack;
    @FXML private ProgressBar progressBar;
    @FXML private ScrollPane gameScrollPane;
    @FXML private VBox gameContentArea;
    @FXML private HBox bottomBar;

    private Game game;
    private StackPane contentArea;
    private Timeline gameTimer;
    private int secondsLeft = 60;
    private int score = 0;
    private boolean running = false;
    private boolean isMiniGame = false;

    private List<String> memorySymbols; private List<Button> memoryCards;
    private List<Integer> flippedIndices; private int matchedPairs; private boolean canFlip;
    private List<String> wordList; private int wordIndex; private int wordScore;
    private List<String[]> triviaQuestions; private int triviaIndex; private int triviaScore;
    private int targetsHit, targetsMissed, totalTargets;
    private Timeline targetSpawner; private Button activeTarget;

    public void setGame(Game game) {
        this.game = game;
        isMiniGame = "MINI_GAME".equals(game.getCategory());
        populateHeader();
        showStartOverlay();
    }
    public void setContentArea(StackPane ca) { this.contentArea = ca; }

    private void populateHeader() {
        lblGameTitle.setText(game.getName());
        lblGameType.setText(game.getType());
        lblCategory.setText(isMiniGame ? "MINI GAME" : "FULL GAME");
        if (isMiniGame) {
            int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
            lblRewardInfo.setText("+" + ep + " Energy on completion");
            lblRewardInfo.setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;");
            lblTimer.setText("—");
            progressBar.setProgress(1.0);
            if (bottomBar != null) bottomBar.setVisible(false);
        } else {
            lblRewardInfo.setText("+" + game.getRewardTokens() + " tokens  +" + game.getRewardXP() + " XP");
            lblRewardInfo.setStyle("-fx-text-fill:#3b4fd8;-fx-font-weight:bold;");
            secondsLeft = timeLimitFor(game.getDifficulty());
            lblTimer.setText(formatTime(secondsLeft));
        }
        lblScore.setText("Score: 0");
        lblInstructions.setText(instructionsFor(game.getType(), game.getCategory()));
    }

    private void showStartOverlay() {
        gameOverlay.setVisible(true);
        overlayContent.getChildren().clear();

        // Game type icon
        StackPane ico = faCircle(typeIcon(game.getType()), 28, typeGradient(game.getType()), "white");
        ico.setPrefSize(72, 72); ico.setMaxSize(72, 72);

        // Title + badges
        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");

        Label typeBadge = new Label(game.getType());
        typeBadge.setStyle("-fx-background-color:" + typeGradient(game.getType()) + ";-fx-text-fill:white;" +
                           "-fx-background-radius:20;-fx-padding:3 12;-fx-font-size:11px;-fx-font-weight:bold;");
        Label catBadge = new Label(isMiniGame ? "MINI GAME" : "FULL GAME");
        catBadge.setStyle("-fx-background-color:" + (isMiniGame ? "#27ae60" : "#3b4fd8") + ";-fx-text-fill:white;" +
                          "-fx-background-radius:20;-fx-padding:3 12;-fx-font-size:11px;-fx-font-weight:bold;");
        HBox badgeRow = new HBox(8, typeBadge, catBadge); badgeRow.setAlignment(Pos.CENTER);

        // Description
        Label desc = new Label(game.getDescription() != null ? game.getDescription() : "");
        desc.setWrapText(true); desc.setMaxWidth(380);
        desc.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        // Info row
        Label info = new Label("Difficulty: " + game.getDifficulty() + "   |   Time: " + timeLimitFor(game.getDifficulty()) + "s");
        info.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;");

        // Reward info
        Label rewardInfo;
        if (isMiniGame) {
            int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
            rewardInfo = new Label("Complete to restore +" + ep + " Energy");
            rewardInfo.setStyle("-fx-text-fill:#27ae60;-fx-font-size:13px;-fx-font-weight:bold;");
        } else {
            rewardInfo = new Label("+" + game.getRewardTokens() + " Tokens   +" + game.getRewardXP() + " XP on completion");
            rewardInfo.setStyle("-fx-text-fill:#3b4fd8;-fx-font-size:13px;-fx-font-weight:bold;");
        }

        // Rating display
        HBox ratingDisplay = buildOverlayRating(game.getId());

        // Favorite button
        int userId = utils.SessionManager.getCurrentUserId();
        HBox favRow = new HBox();
        favRow.setAlignment(Pos.CENTER);
        if (userId > 0) {
            final boolean[] isFav = {false};
            try {
                isFav[0] = new services.gamification.FavoriteGameService().isFavorite(userId, game.getId());
            } catch (Exception ignored) {}
            Label heartLbl = new Label(isFav[0] ? "\u2764 Favorited" : "\u2661 Add to Favorites");
            heartLbl.setStyle("-fx-font-size:13px;-fx-text-fill:" + (isFav[0] ? "#e53e3e" : "#718096") + ";-fx-cursor:hand;");
            heartLbl.setOnMouseClicked(e -> {
                try {
                    boolean nowFav = new services.gamification.FavoriteGameService().toggle(userId, game.getId());
                    isFav[0] = nowFav;
                    heartLbl.setText(nowFav ? "\u2764 Favorited" : "\u2661 Add to Favorites");
                    heartLbl.setStyle("-fx-font-size:13px;-fx-text-fill:" + (nowFav ? "#e53e3e" : "#718096") + ";-fx-cursor:hand;");
                } catch (Exception ex) {}
            });
            favRow.getChildren().add(heartLbl);
        }

        // Separator
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setMaxWidth(380);

        // Linked achievements section
        javafx.scene.control.ScrollPane achievementsSection = buildLinkedAchievements(game.getId(), userId);

        if (isMiniGame) {
            Button startBtn = new Button("Start");
            startBtn.setStyle("-fx-background-color:linear-gradient(to right,#43e97b,#38f9d7);-fx-text-fill:white;" +
                              "-fx-font-weight:bold;-fx-font-size:15px;-fx-background-radius:10;-fx-padding:12 40;-fx-cursor:hand;");
            startBtn.setOnAction(e -> startGame());
            overlayContent.getChildren().addAll(ico, title, badgeRow, desc, info, rewardInfo, ratingDisplay, favRow, sep, achievementsSection, startBtn);
        } else {
            btnStart.setText("Start Game");
            btnStart.setOnAction(e -> startGame());
            overlayContent.getChildren().addAll(ico, title, badgeRow, desc, info, rewardInfo, ratingDisplay, favRow, sep, achievementsSection);
        }
    }

    /** Build the linked achievements panel for the start overlay. */
    private javafx.scene.control.ScrollPane buildLinkedAchievements(int gameId, int userId) {
        VBox section = new VBox(6);
        section.setMaxWidth(400);
        section.setAlignment(Pos.CENTER_LEFT);

        try {
            java.util.List<models.gamification.Reward> linked =
                new services.gamification.GameService().getRewardsForGame(gameId);
            if (linked.isEmpty()) {
                // Return empty invisible pane
                javafx.scene.control.ScrollPane empty = new javafx.scene.control.ScrollPane();
                empty.setVisible(false); empty.setManaged(false);
                return empty;
            }

            services.gamification.EarnedRewardService earnedSvc =
                new services.gamification.EarnedRewardService();

            Label header = new Label("Achievements you can earn (" + linked.size() + "):");
            header.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#718096;-fx-padding:0 0 4 0;");
            section.getChildren().add(header);

            for (models.gamification.Reward r : linked) {
                boolean earned = userId > 0 && earnedSvc.hasEarned(userId, r.getId());

                StackPane ico = faCircle(rewardTypeIcon(r.getType()), 14,
                    earned ? "linear-gradient(to bottom right,#27ae60,#2ecc71)"
                           : rewardGradient(r.getType()), "white");
                ico.setPrefSize(32, 32); ico.setMaxSize(32, 32);
                if (!earned) ico.setOpacity(0.45);

                Label name = new Label(r.getName());
                name.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" +
                              (earned ? "#27ae60" : "#1e2a5e") + ";");
                Label type = new Label(r.getType());
                type.setStyle("-fx-font-size:10px;-fx-text-fill:#a0aec0;");
                VBox info2 = new VBox(2, name, type);
                info2.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(info2, Priority.ALWAYS);

                Label status = new Label(earned ? "\u2705 Earned" : "\uD83D\uDD12 Locked");
                status.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" +
                                (earned ? "#27ae60" : "#a0aec0") + ";");

                HBox row = new HBox(10, ico, info2, status);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(7, 10, 7, 10));
                row.setStyle("-fx-background-color:" + (earned ? "#f0fff4" : "#f8f9ff") + ";" +
                             "-fx-background-radius:8;-fx-border-color:" +
                             (earned ? "#9ae6b4" : "#e4e8f0") + ";-fx-border-radius:8;-fx-border-width:1;");
                section.getChildren().add(row);
            }
        } catch (Exception e) {
            System.err.println("Could not load linked achievements: " + e.getMessage());
        }

        // Wrap in a ScrollPane with max height so it doesn't overflow
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(section);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMaxHeight(180); // show ~3 rows, scroll for more
        scroll.setMaxWidth(400);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:#f0f2f8;-fx-border-color:transparent;");
        return scroll;
    }

    private String rewardTypeIcon(String type) {
        return switch (type) {
            case "BADGE"        -> utils.TwemojiUtil.MEDAL;
            case "ACHIEVEMENT"  -> utils.TwemojiUtil.TROPHY;
            case "BONUS_XP"     -> utils.TwemojiUtil.STAR;
            case "BONUS_TOKENS" -> utils.TwemojiUtil.COIN;
            default             -> utils.TwemojiUtil.GIFT;
        };
    }

    private String rewardGradient(String type) {
        return switch (type) {
            case "BADGE"        -> "linear-gradient(to bottom right,#f6d365,#fda085)";
            case "ACHIEVEMENT"  -> "linear-gradient(to bottom right,#a18cd1,#fbc2eb)";
            case "BONUS_XP"     -> "linear-gradient(to bottom right,#4facfe,#00f2fe)";
            case "BONUS_TOKENS" -> "linear-gradient(to bottom right,#43e97b,#38f9d7)";
            default             -> "linear-gradient(to bottom right,#667eea,#764ba2)";
        };
    }

    private HBox buildOverlayRating(int gameId) {
        HBox row = new HBox(6); row.setAlignment(Pos.CENTER);
        try {
            double avg = new services.gamification.GameRatingService().getAverageRating(gameId);
            int count  = new services.gamification.GameRatingService().getRatingCount(gameId);
            if (count > 0) {
                for (int i = 1; i <= 5; i++) {
                    Label star = new Label(i <= Math.round(avg) ? "\u2605" : "\u2606");
                    star.setStyle("-fx-font-size:18px;-fx-text-fill:" + (i <= Math.round(avg) ? "#f6d365" : "#cbd5e0") + ";");
                    row.getChildren().add(star);
                }
                Label avgLbl = new Label(String.format("%.1f (%d ratings)", avg, count));
                avgLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#718096;");
                row.getChildren().add(avgLbl);
            } else {
                Label none = new Label("No ratings yet — be the first!");
                none.setStyle("-fx-font-size:12px;-fx-text-fill:#a0aec0;");
                row.getChildren().add(none);
            }
        } catch (Exception ignored) {}
        return row;
    }

    private void showResultOverlay(boolean passed) {
        gameOverlay.setVisible(true);
        overlayContent.getChildren().clear();
        StackPane resultIcon = passed
            ? faCircle(TwemojiUtil.TROPHY, 32, "linear-gradient(to bottom right,#f6d365,#fda085)", "white")
            : faCircle(TwemojiUtil.LOSE,   32, "linear-gradient(to bottom right,#fc5c7d,#6a3093)", "white");
        resultIcon.setPrefSize(80, 80); resultIcon.setMaxSize(80, 80);
        Label result = new Label(passed ? "Well Done!" : "Time's Up!");
        result.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + (passed ? "#27ae60" : "#e53e3e") + ";");
        Label scoreLabel = new Label(isMiniGame ? "Exercise Complete!" : "Final Score: " + score);
        scoreLabel.setStyle("-fx-font-size:16px;-fx-text-fill:#2d3748;");
        overlayContent.getChildren().addAll(resultIcon, result, scoreLabel);
        if (passed) {
            String rw = isMiniGame
                ? "+" + (game.getEnergyPoints() != null ? game.getEnergyPoints() : 0) + " Energy restored!"
                : "+" + game.getRewardTokens() + " tokens  +" + game.getRewardXP() + " XP earned!";
            Label rl = new Label(rw);
            rl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
            overlayContent.getChildren().add(rl);

            // ── Star rating widget ────────────────────────────────────────────
            int userId = utils.SessionManager.getCurrentUserId();
            if (userId > 0) {
                Separator rateSep = new Separator();
                rateSep.setStyle("-fx-background-color:#e4e8f0;");
                Label rateTitle = new Label("Rate this game");
                rateTitle.setStyle("-fx-font-size:13px;-fx-text-fill:#718096;-fx-font-weight:bold;");
                int existing = 0;
                try { existing = new GameRatingService().getUserRating(userId, game.getId()); } catch (Exception ignored) {}
                HBox stars = buildStarWidget(existing, userId, game.getId());
                overlayContent.getChildren().addAll(rateSep, rateTitle, stars);
            }
        }
        Button playAgain = new Button(isMiniGame ? "Try Again" : "Play Again");
        playAgain.setStyle("-fx-background-color:" + typeGradient(game.getType()) + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:14px;-fx-background-radius:10;-fx-padding:10 30;-fx-cursor:hand;");
        playAgain.setOnAction(e -> { score = 0; secondsLeft = timeLimitFor(game.getDifficulty()); lblScore.setText("Score: 0"); progressBar.setProgress(1.0); startGame(); });
        Button backBtn = new Button("Back to Games");
        backBtn.setStyle("-fx-background-color:#f0f2f8;-fx-text-fill:#3b4fd8;-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:10;-fx-padding:10 24;-fx-cursor:hand;-fx-border-color:#c3c9f5;-fx-border-radius:10;");
        backBtn.setOnAction(e -> goBack());
        HBox btns = new HBox(12, playAgain, backBtn);
        btns.setAlignment(Pos.CENTER);
        overlayContent.getChildren().add(btns);
    }

    @FXML private void startGame() {
        gameOverlay.setVisible(false); running = true; score = 0; lblScore.setText("Score: 0");
        if (!isMiniGame) {
            secondsLeft = timeLimitFor(game.getDifficulty());
            if (gameTimer != null) gameTimer.stop();
            gameTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
            gameTimer.setCycleCount(Timeline.INDEFINITE); gameTimer.play();
        }
        gameContentArea.getChildren().clear();
        if (isMiniGame) { buildMiniGame(); return; }
        switch (game.getType()) {
            case "MEMORY" -> buildMemoryGame();
            case "PUZZLE" -> buildWordScramble();
            case "TRIVIA" -> buildTrivia();
            case "ARCADE" -> buildArcade();
            default       -> buildGeneric();
        }
    }

    private void tick() {
        if (!running) return;
        secondsLeft--;
        lblTimer.setText(formatTime(secondsLeft));
        progressBar.setProgress((double) secondsLeft / timeLimitFor(game.getDifficulty()));
        if (secondsLeft <= 0) endGame(false);
    }

    private void endGame(boolean passed) {
        running = false;
        if (gameTimer != null) gameTimer.stop();
        if (targetSpawner != null) targetSpawner.stop();
        if (passed) grantRewards();
        showResultOverlay(passed);
    }

    private void grantRewards() {
        int userId = utils.SessionManager.getCurrentUserId();
        if (userId <= 0) return;
        try {
            GameRewardService svc = new GameRewardService();
            if (isMiniGame) {
                int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
                svc.awardMiniGameEnergy(userId, ep);
            } else {
                svc.awardGameRewards(userId, game.getRewardXP(), game.getRewardTokens());
                // Check for newly earned linked rewards and send notification
                sendAchievementNotification(userId);
            }
        } catch (Exception e) {
            System.err.println("Could not grant rewards: " + e.getMessage());
        }
    }

    private void sendAchievementNotification(int userId) {
        try {
            java.util.List<models.gamification.Reward> linkedRewards =
                new services.gamification.GameService().getRewardsForGame(game.getId());
            System.out.println("[Achievement] Game " + game.getId() + " has " + linkedRewards.size() + " linked rewards, userId=" + userId);
            if (linkedRewards.isEmpty()) return;

            services.gamification.EarnedRewardService earnedSvc = new services.gamification.EarnedRewardService();
            java.util.List<models.gamification.Reward> newlyEarned = new java.util.ArrayList<>();

            for (models.gamification.Reward r : linkedRewards) {
                boolean alreadyEarned = earnedSvc.hasEarned(userId, r.getId());
                System.out.println("[Achievement] Reward " + r.getId() + " (" + r.getName() + ") alreadyEarned=" + alreadyEarned);
                if (!alreadyEarned) {
                    earnedSvc.awardReward(userId, r.getId());
                    newlyEarned.add(r);
                }
            }

            if (!newlyEarned.isEmpty()) {
                String username = UserSession.getInstance().getUsername();
                new services.gamification.AchievementNotificationService()
                    .notifyAchievements(username, game.getName(), newlyEarned,
                                        game.getRewardXP(), game.getRewardTokens());
            }
        } catch (Exception e) {
            System.err.println("[Achievement] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── MEMORY MATCH ─────────────────────────────────────────────────────────
    private void buildMemoryGame() {
        // Try to load custom words/emojis from game_content
        String[] customWords = null;
        try {
            String json = new GameContentService().loadContent(game.getId());
            if (json != null) {
                String arr = GameContentService.extractArray(json, "words");
                if (arr != null) customWords = GameContentService.parseStringArray(arr);
            }
        } catch (Exception ignored) {}

        // Default emoji set — all rendered via Twemoji CDN (no font needed)
        String[] defaultEmojis = {
            "\uD83C\uDF4E", // 🍎 apple
            "\uD83D\uDC36", // 🐶 dog
            "\uD83C\uDF1F", // 🌟 star
            "\uD83C\uDFC6", // 🏆 trophy
            "\uD83D\uDE80", // 🚀 rocket
            "\uD83C\uDF55", // 🍕 pizza
            "\uD83D\uDCDA", // 📚 books
            "\uD83C\uDFAE", // 🎮 gamepad
            "\uD83D\uDC31", // 🐱 cat
            "\uD83C\uDF4C", // 🍌 banana
            "\uD83D\uDD2D", // 🔭 telescope
            "\uD83C\uDFA8"  // 🎨 palette
        };
        String[] colors = {
            "#f6d365","#43e97b","#a18cd1","#4facfe",
            "#fda085","#fc5c7d","#38f9d7","#fbc2eb",
            "#00f2fe","#667eea","#f093fb","#30cfd0"
        };

        int pairs = "HARD".equals(game.getDifficulty()) ? 8 : "MEDIUM".equals(game.getDifficulty()) ? 6 : 4;
        boolean useCustom = customWords != null && customWords.length >= 2;
        if (useCustom) pairs = Math.min(pairs, customWords.length);
        List<String> symList = new ArrayList<>();
        List<String> colList = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            String sym = useCustom ? customWords[i] : defaultEmojis[i % defaultEmojis.length];
            symList.add(sym); symList.add(sym);
            colList.add(colors[i % colors.length]); colList.add(colors[i % colors.length]);
        }        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < symList.size(); i++) order.add(i);
        Collections.shuffle(order);
        memorySymbols = new ArrayList<>(); List<String> shuffledColors = new ArrayList<>();
        for (int i : order) { memorySymbols.add(symList.get(i)); shuffledColors.add(colList.get(i)); }
        memoryCards = new ArrayList<>(); flippedIndices = new ArrayList<>(); matchedPairs = 0; canFlip = true;

        StackPane brainIco = faCircle("\uF5DC", 18, "linear-gradient(to right,#a18cd1,#fbc2eb)", "white");
        brainIco.setPrefSize(36, 36); brainIco.setMaxSize(36, 36);
        Label hdrTxt = new Label("Memory Match — find all " + pairs + " pairs");
        hdrTxt.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(10, brainIco, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);

        int cols = pairs <= 4 ? 4 : pairs <= 6 ? 4 : 6;
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(12); grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < memorySymbols.size(); i++) {
            final int idx = i;
            final String sym = memorySymbols.get(i);
            final String col = shuffledColors.get(i);
            Button card = new Button("?"); card.setPrefSize(80, 68);
            card.setStyle("-fx-background-color:linear-gradient(to bottom right,#3b4fd8,#5b6ef5);-fx-text-fill:white;-fx-font-size:22px;-fx-font-weight:bold;-fx-background-radius:12;-fx-cursor:hand;-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.3),8,0,0,3);");
            card.setOnAction(e -> flipMemoryCard(idx, sym, col, false));
            memoryCards.add(card); grid.add(card, i % cols, i / cols);
        }
        VBox box = new VBox(16, hdr, grid); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color:white;-fx-background-radius:16;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),12,0,0,4);");
        gameContentArea.getChildren().add(box);
    }

    private void flipMemoryCard(int idx, String sym, String col, boolean isText) {
        if (!running || !canFlip) return;
        Button card = memoryCards.get(idx);
        if (flippedIndices.contains(idx) || card.getStyle().contains("#27ae60")) return;

        // Clear the ? immediately before animation
        card.setText("");
        card.setGraphic(null);

        ScaleTransition flip1 = new ScaleTransition(Duration.millis(150), card);
        flip1.setFromX(1); flip1.setToX(0);
        flip1.setOnFinished(e -> {
            boolean isEmoji = isEmojiString(sym);

            card.setStyle("-fx-background-color:" + col + ";-fx-background-radius:12;" +
                          "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.2),8,0,0,3);-fx-cursor:hand;");

            if (isEmoji) {
                // Twemoji image via CDN
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
                iv.setFitWidth(40); iv.setFitHeight(40); iv.setPreserveRatio(true);
                card.setGraphic(iv); card.setText("");
                String url = utils.TwemojiUtil.toUrl(sym);
                if (url != null) {
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try { return new javafx.scene.image.Image(url, 40, 40, true, true, false); }
                        catch (Exception ex) { return null; }
                    }).thenAccept(img -> javafx.application.Platform.runLater(() -> {
                        if (img != null && !img.isError()) {
                            iv.setImage(img);
                        } else {
                            // Fallback: show the emoji as text
                            card.setGraphic(null);
                            card.setText(sym);
                            card.setStyle(card.getStyle() + "-fx-font-size:24px;-fx-text-fill:white;");
                        }
                    }));
                }
            } else if (!isText) {
                // FA icon — use a Label graphic so font-family applies correctly
                Label ico = new Label(sym);
                ico.setStyle("-fx-font-family:'Font Awesome 5 Free';-fx-font-weight:900;" +
                             "-fx-font-size:24px;-fx-text-fill:white;");
                card.setGraphic(ico); card.setText("");
            } else {
                // Plain text word
                card.setGraphic(null);
                card.setText(sym);
                card.setStyle(card.getStyle() + "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;");
            }

            ScaleTransition flip2 = new ScaleTransition(Duration.millis(150), card);
            flip2.setFromX(0); flip2.setToX(1); flip2.play();
        });
        flip1.play();
        flippedIndices.add(idx);
        if (flippedIndices.size() == 2) {
            canFlip = false;
            int a = flippedIndices.get(0), b = flippedIndices.get(1);
            if (memorySymbols.get(a).equals(memorySymbols.get(b))) {
                matchedPairs++; score += 100; lblScore.setText("Score: " + score);
                PauseTransition pt = new PauseTransition(Duration.millis(300));
                pt.setOnFinished(ev -> {
                    // Keep the graphic, just change background to green
                    String greenStyle = "-fx-background-color:linear-gradient(to bottom right,#27ae60,#2ecc71);" +
                                        "-fx-background-radius:12;-fx-effect:dropshadow(gaussian,rgba(39,174,96,0.4),8,0,0,3);-fx-cursor:hand;";
                    for (int i : List.of(a, b)) {
                        Button c = memoryCards.get(i);
                        // Preserve existing graphic/text, only change background
                        String existing = c.getStyle();
                        // Replace background color part
                        c.setStyle(greenStyle + (c.getGraphic() == null
                            ? "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;" : ""));
                    }
                    flippedIndices.clear(); canFlip = true;
                    int pairs = "HARD".equals(game.getDifficulty()) ? 8 : "MEDIUM".equals(game.getDifficulty()) ? 6 : 4;
                    if (matchedPairs == pairs) endGame(true);
                });
                pt.play();
            } else {
                PauseTransition pt = new PauseTransition(Duration.millis(900));
                pt.setOnFinished(ev -> {
                    for (int i : List.of(a, b)) {
                        Button c = memoryCards.get(i);
                        ScaleTransition f1 = new ScaleTransition(Duration.millis(120), c);
                        f1.setFromX(1); f1.setToX(0);
                        f1.setOnFinished(e2 -> {
                            // Reset to face-down state — clear graphic AND text
                            c.setGraphic(null);
                            c.setText("?");
                            c.setStyle("-fx-background-color:linear-gradient(to bottom right,#3b4fd8,#5b6ef5);" +
                                       "-fx-text-fill:white;-fx-font-size:22px;-fx-font-weight:bold;" +
                                       "-fx-background-radius:12;-fx-cursor:hand;" +
                                       "-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.3),8,0,0,3);");
                            ScaleTransition f2 = new ScaleTransition(Duration.millis(120), c);
                            f2.setFromX(0); f2.setToX(1); f2.play();
                        });
                        f1.play();
                    }
                    flippedIndices.clear(); canFlip = true;
                });
                pt.play();
            }
        }
    }

    /** Returns true if the string contains emoji codepoints. */
    private boolean isEmojiString(String s) {
        if (s == null) return false;
        return s.codePoints().anyMatch(cp ->
            (cp >= 0x1F300 && cp <= 0x1FAFF) ||
            (cp >= 0x2600  && cp <= 0x27BF)  ||
            (cp >= 0x1F000 && cp <= 0x1F02F) ||
            (cp >= 0x1F0A0 && cp <= 0x1F0FF) ||
            (cp == 0xFE0F));
    }

    // ── WORD SCRAMBLE ─────────────────────────────────────────────────────────
    private void buildWordScramble() {
        // Try to load custom word from game_content
        String customWord = null;
        String customHint = null;
        try {
            String json = new GameContentService().loadContent(game.getId());
            if (json != null) {
                customWord = GameContentService.extractString(json, "word");
                customHint = GameContentService.extractString(json, "hint");
            }
        } catch (Exception ignored) {}

        if (customWord != null && !customWord.isBlank()) {
            // Single custom word mode
            wordList = new java.util.ArrayList<>(List.of(customWord.toUpperCase()));
        } else {
            wordList = new ArrayList<>(Arrays.asList("STUDY","LEARN","BRAIN","FOCUS","THINK","SMART","GRADE","TEACH","WRITE","SOLVE","MEMORY","SKILL","SCIENCE","HISTORY","PHYSICS"));
            Collections.shuffle(wordList);
            int count = "HARD".equals(game.getDifficulty()) ? 8 : "MEDIUM".equals(game.getDifficulty()) ? 5 : 3;
            wordList = new ArrayList<>(wordList.subList(0, Math.min(count, wordList.size())));
        }
        final String hint = customHint;
        wordIndex = 0; wordScore = 0; showNextWord(hint);
    }

    private void showNextWord(String globalHint) {
        gameContentArea.getChildren().clear();
        if (wordIndex >= wordList.size()) { endGame(wordScore >= wordList.size() * 0.6); return; }
        String word = wordList.get(wordIndex);
        String scrambled = scramble(word);

        HBox dots = new HBox(6); dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < wordList.size(); i++) {
            Circle dot = new Circle(6);
            dot.setFill(i < wordIndex ? Color.web("#27ae60") : i == wordIndex ? Color.web("#f6d365") : Color.web("#e4e8f0"));
            dots.getChildren().add(dot);
        }
        Label progressLbl = new Label("Word " + (wordIndex + 1) + " of " + wordList.size());
        progressLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:12px;");

        // Hint label (shown if custom hint exists)
        Label hintLbl = new Label(globalHint != null && !globalHint.isBlank() ? "Hint: " + globalHint : "");
        hintLbl.setStyle("-fx-text-fill:#d97706;-fx-font-size:13px;-fx-font-style:italic;");
        hintLbl.setVisible(globalHint != null && !globalHint.isBlank());

        HBox tiles = new HBox(8); tiles.setAlignment(Pos.CENTER);
        for (char c : scrambled.toCharArray()) {
            Label tile = new Label(String.valueOf(c));
            tile.setPrefSize(48, 52); tile.setAlignment(Pos.CENTER);
            tile.setStyle("-fx-background-color:linear-gradient(to bottom,#f6d365,#fda085);-fx-text-fill:white;-fx-font-size:22px;-fx-font-weight:bold;-fx-background-radius:8;-fx-effect:dropshadow(gaussian,rgba(246,211,101,0.4),6,0,0,3);");
            tiles.getChildren().add(tile);
        }

        TextField input = new TextField();
        input.setPromptText("Type the word...");
        input.setMaxWidth(280);
        input.setStyle("-fx-font-size:20px;-fx-padding:10 16;-fx-background-radius:10;-fx-border-color:#f6d365;-fx-border-radius:10;-fx-border-width:2;-fx-text-fill:#1e2a5e;-fx-font-weight:bold;");

        Label feedback = new Label("");
        feedback.setStyle("-fx-font-size:14px;");

        Button submit = new Button("Submit");
        submit.setStyle("-fx-background-color:linear-gradient(to right,#f6d365,#fda085);-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:14px;-fx-background-radius:10;-fx-padding:10 28;-fx-cursor:hand;");
        Button skip = new Button("Skip");
        skip.setStyle("-fx-background-color:#f0f2f8;-fx-text-fill:#718096;-fx-font-size:13px;-fx-background-radius:10;-fx-padding:10 20;-fx-cursor:hand;-fx-border-color:#e4e8f0;-fx-border-radius:10;");

        submit.setOnAction(e -> {
            String ans = input.getText().trim().toUpperCase();
            if (ans.equals(word)) {
                wordScore++; score += 100; lblScore.setText("Score: " + score);
                feedback.setText("Correct!"); feedback.setStyle("-fx-text-fill:#27ae60;-fx-font-size:15px;-fx-font-weight:bold;");
                PauseTransition pt = new PauseTransition(Duration.millis(700));
                pt.setOnFinished(ev -> { wordIndex++; showNextWord(globalHint); }); pt.play();
            } else {
                feedback.setText("Not quite — try again!"); feedback.setStyle("-fx-text-fill:#e53e3e;-fx-font-size:14px;");
                input.clear();
                TranslateTransition shake = new TranslateTransition(Duration.millis(60), input);
                shake.setByX(8); shake.setCycleCount(6); shake.setAutoReverse(true); shake.play();
            }
        });
        skip.setOnAction(e -> {
            feedback.setText("Answer: " + word); feedback.setStyle("-fx-text-fill:#d97706;-fx-font-size:14px;");
            PauseTransition pt = new PauseTransition(Duration.millis(1000));
            pt.setOnFinished(ev -> { wordIndex++; showNextWord(globalHint); }); pt.play();
        });
        input.setOnAction(e -> submit.fire());
        HBox btns = new HBox(12, submit, skip); btns.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, dots, progressLbl, hintLbl, tiles, input, btns, feedback);
        card.setAlignment(Pos.CENTER); card.setPadding(new Insets(32)); card.setMaxWidth(520);
        card.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.09),14,0,0,5);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(24));
        gameContentArea.getChildren().add(wrapper);
        Platform.runLater(() -> input.requestFocus());
    }

    private String scramble(String w) {
        char[] a = w.toCharArray(); Random r = new Random();
        for (int i = a.length - 1; i > 0; i--) { int j = r.nextInt(i + 1); char t = a[i]; a[i] = a[j]; a[j] = t; }
        return new String(a);
    }

    // ── TRIVIA ────────────────────────────────────────────────────────────────
    private void buildTrivia() {
        // Try to load custom questions from game_content
        List<String[]> customQuestions = null;
        try {
            String json = new GameContentService().loadContent(game.getId());
            if (json != null) customQuestions = parseCustomTrivia(json);
        } catch (Exception ignored) {}

        triviaQuestions = (customQuestions != null && !customQuestions.isEmpty())
            ? customQuestions
            : getTriviaQuestions(game.getDifficulty());
        triviaIndex = 0; triviaScore = 0;
        showNextQuestion();
    }

    /** Parse trivia from the stored JSON format used by GameContentService.buildTriviaJson */
    private List<String[]> parseCustomTrivia(String json) {
        List<String[]> result = new ArrayList<>();
        // Find the questions array
        String arr = GameContentService.extractArray(json, "questions");
        if (arr == null) return result;
        // Each question object: {"question":"...","choices":["a","b","c","d"],"correct":N}
        int pos = 0;
        while (pos < arr.length()) {
            int objStart = arr.indexOf("{", pos);
            if (objStart == -1) break;
            int objEnd = arr.indexOf("}", objStart);
            if (objEnd == -1) break;
            String obj = arr.substring(objStart, objEnd + 1);
            String q = GameContentService.extractString(obj, "question");
            String choicesArr = GameContentService.extractArray(obj, "choices");
            String correctStr = null;
            int ci = obj.indexOf("\"correct\":");
            if (ci != -1) {
                int numStart = ci + 10;
                int numEnd = numStart;
                while (numEnd < obj.length() && Character.isDigit(obj.charAt(numEnd))) numEnd++;
                correctStr = obj.substring(numStart, numEnd);
            }
            if (q != null && choicesArr != null && correctStr != null) {
                String[] choices = GameContentService.parseStringArray(choicesArr);
                if (choices.length >= 4) {
                    result.add(new String[]{q, choices[0], choices[1], choices[2], choices[3], correctStr});
                }
            }
            pos = objEnd + 1;
        }
        return result;
    }

    private void showNextQuestion() {
        gameContentArea.getChildren().clear();
        if (triviaIndex >= triviaQuestions.size()) { endGame(triviaScore >= triviaQuestions.size() * 0.6); return; }
        String[] q = triviaQuestions.get(triviaIndex);
        int correct = Integer.parseInt(q[5]);

        ProgressBar qProgress = new ProgressBar((double) triviaIndex / triviaQuestions.size());
        qProgress.setMaxWidth(Double.MAX_VALUE);
        qProgress.setStyle("-fx-accent:#4facfe;");
        Label qNum = new Label("Question " + (triviaIndex + 1) + " / " + triviaQuestions.size() + "   Score: " + triviaScore + "/" + triviaIndex);
        qNum.setStyle("-fx-text-fill:#718096;-fx-font-size:12px;");

        Label qLabel = new Label(q[0]);
        qLabel.setWrapText(true); qLabel.setMaxWidth(580);
        qLabel.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;-fx-padding:20 24;-fx-background-color:#eef0fd;-fx-background-radius:12;");

        VBox opts = new VBox(10); opts.setMaxWidth(600);
        for (int i = 1; i <= 4; i++) {
            final int oi = i - 1;
            Button ob = new Button(String.valueOf((char)(64 + i)) + ".  " + q[i]);
            ob.setMaxWidth(Double.MAX_VALUE); ob.setAlignment(Pos.CENTER_LEFT);
            ob.setStyle("-fx-background-color:white;-fx-text-fill:#2d3748;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;-fx-border-color:#e4e8f0;-fx-border-radius:10;-fx-cursor:hand;");
            ob.setOnMouseEntered(e -> ob.setStyle("-fx-background-color:#f5f7ff;-fx-text-fill:#1e2a5e;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;-fx-border-color:#c3c9f5;-fx-border-radius:10;-fx-cursor:hand;"));
            ob.setOnMouseExited(e -> { if (!ob.isDisabled()) ob.setStyle("-fx-background-color:white;-fx-text-fill:#2d3748;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;-fx-border-color:#e4e8f0;-fx-border-radius:10;-fx-cursor:hand;"); });
            ob.setOnAction(e -> {
                opts.getChildren().forEach(n -> ((Button) n).setDisable(true));
                if (oi == correct) {
                    triviaScore++; score += 100; lblScore.setText("Score: " + score);
                    ob.setStyle("-fx-background-color:linear-gradient(to right,#27ae60,#2ecc71);-fx-text-fill:white;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;");
                } else {
                    ob.setStyle("-fx-background-color:linear-gradient(to right,#e53e3e,#fc5c7d);-fx-text-fill:white;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;");
                    ((Button) opts.getChildren().get(correct)).setStyle("-fx-background-color:linear-gradient(to right,#27ae60,#2ecc71);-fx-text-fill:white;-fx-font-size:14px;-fx-padding:13 18;-fx-background-radius:10;");
                }
                PauseTransition pt = new PauseTransition(Duration.millis(1200));
                pt.setOnFinished(ev -> { triviaIndex++; showNextQuestion(); }); pt.play();
            });
            opts.getChildren().add(ob);
        }

        VBox card = new VBox(16, qProgress, qNum, qLabel, opts);
        card.setAlignment(Pos.TOP_CENTER); card.setPadding(new Insets(28)); card.setMaxWidth(640);
        card.setStyle("-fx-background-color:white;-fx-background-radius:16;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),12,0,0,4);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(20));
        gameContentArea.getChildren().add(wrapper);
    }

    private List<String[]> getTriviaQuestions(String diff) {
        List<String[]> easy = Arrays.asList(
            new String[]{"What is 2 + 2?","3","4","5","6","1"},
            new String[]{"How many days in a week?","5","6","7","8","2"},
            new String[]{"What color is the sky?","Green","Blue","Red","Yellow","1"},
            new String[]{"Capital of France?","London","Berlin","Paris","Madrid","2"},
            new String[]{"How many legs does a spider have?","6","8","10","12","1"}
        );
        List<String[]> medium = Arrays.asList(
            new String[]{"Largest planet in solar system?","Earth","Mars","Jupiter","Saturn","2"},
            new String[]{"Who wrote Romeo and Juliet?","Dickens","Shakespeare","Austen","Twain","1"},
            new String[]{"Chemical symbol for gold?","Go","Gd","Au","Ag","2"},
            new String[]{"WWII ended in?","1943","1944","1945","1946","2"},
            new String[]{"Speed of light?","300,000 km/s","150,000 km/s","450,000 km/s","600,000 km/s","0"},
            new String[]{"How many continents?","5","6","7","8","2"},
            new String[]{"Smallest country?","Monaco","Vatican City","San Marino","Liechtenstein","1"}
        );
        List<String[]> hard = Arrays.asList(
            new String[]{"Smallest prime number?","0","1","2","3","2"},
            new String[]{"Theory of relativity?","Newton","Einstein","Hawking","Bohr","1"},
            new String[]{"Capital of Australia?","Sydney","Melbourne","Canberra","Brisbane","2"},
            new String[]{"Elements in periodic table?","108","118","128","138","1"},
            new String[]{"Longest river?","Amazon","Nile","Yangtze","Mississippi","1"},
            new String[]{"Square root of 144?","10","11","12","13","2"},
            new String[]{"Who painted Mona Lisa?","Michelangelo","Da Vinci","Raphael","Donatello","1"},
            new String[]{"Boiling point of water?","90C","95C","100C","105C","2"}
        );
        List<String[]> src = "HARD".equals(diff) ? hard : "EASY".equals(diff) ? easy : medium;
        List<String[]> shuffled = new ArrayList<>(src); Collections.shuffle(shuffled);
        int count = "HARD".equals(diff) ? 8 : "EASY".equals(diff) ? 5 : 7;
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    // ── ARCADE ────────────────────────────────────────────────────────────────
    private void buildArcade() {
        totalTargets = "HARD".equals(game.getDifficulty()) ? 16 : "MEDIUM".equals(game.getDifficulty()) ? 10 : 6;
        targetsHit = 0; targetsMissed = 0; activeTarget = null;

        Label hitLbl = new Label("Hit: 0 / " + totalTargets);
        hitLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        Label missLbl = new Label("Missed: 0");
        missLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#e53e3e;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statsBar = new HBox(16, hitLbl, spacer, missLbl); statsBar.setAlignment(Pos.CENTER_LEFT);
        statsBar.setStyle("-fx-background-color:white;-fx-padding:10 16;-fx-background-radius:10;-fx-border-color:#e4e8f0;-fx-border-radius:10;");

        Pane arena = new Pane(); arena.setPrefSize(700, 380);
        arena.setStyle("-fx-background-color:linear-gradient(to bottom right,#f8f9ff,#eef0fd);-fx-background-radius:16;-fx-border-color:#c3c9f5;-fx-border-radius:16;-fx-border-width:2;");

        VBox box = new VBox(12, statsBar, arena); box.setAlignment(Pos.TOP_CENTER); box.setPadding(new Insets(20));
        gameContentArea.getChildren().add(box);

        String[][] targets = {
            {"#f6d365","#fda085"}, {"#43e97b","#38f9d7"},
            {"#a18cd1","#fbc2eb"}, {"#4facfe","#00f2fe"},
            {"#fc5c7d","#6a3093"}
        };
        int speed = "HARD".equals(game.getDifficulty()) ? 1000 : "MEDIUM".equals(game.getDifficulty()) ? 1600 : 2200;

        targetSpawner = new Timeline(new KeyFrame(Duration.millis(speed), e -> {
            if (!running) return;
            if (activeTarget != null) {
                targetsMissed++; missLbl.setText("Missed: " + targetsMissed);
                arena.getChildren().remove(activeTarget); activeTarget = null;
            }
            if (targetsHit + targetsMissed >= totalTargets) { endGame((double) targetsHit / totalTargets >= 0.6); return; }
            String[] t = targets[new Random().nextInt(targets.length)];
            // Plain colored circle — no text, no font dependency
            Button btn = new Button(); btn.setPrefSize(64, 64);
            btn.setStyle("-fx-background-color:linear-gradient(to bottom right," + t[0] + "," + t[1] + ");" +
                         "-fx-background-radius:50;-fx-cursor:hand;" +
                         "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.3),12,0,0,4);");
            double x = 20 + Math.random() * (700 - 84), y = 20 + Math.random() * (380 - 84);
            btn.setLayoutX(x); btn.setLayoutY(y); btn.setScaleX(0); btn.setScaleY(0);
            arena.getChildren().add(btn); activeTarget = btn;
            ScaleTransition pop = new ScaleTransition(Duration.millis(200), btn);
            pop.setFromX(0); pop.setFromY(0); pop.setToX(1); pop.setToY(1); pop.play();
            btn.setOnAction(ev -> {
                if (!running) return;
                targetsHit++; score += 100; lblScore.setText("Score: " + score);
                hitLbl.setText("Hit: " + targetsHit + " / " + totalTargets);
                ScaleTransition out = new ScaleTransition(Duration.millis(150), btn); out.setToX(1.4); out.setToY(1.4);
                FadeTransition fade = new FadeTransition(Duration.millis(150), btn); fade.setToValue(0);
                ParallelTransition pt = new ParallelTransition(out, fade);
                pt.setOnFinished(evv -> arena.getChildren().remove(btn)); pt.play();
                activeTarget = null;
                if (targetsHit + targetsMissed >= totalTargets) endGame((double) targetsHit / totalTargets >= 0.6);
            });
        }));
        targetSpawner.setCycleCount(Timeline.INDEFINITE); targetSpawner.play();
    }

    // ── MINI GAME ROUTER ─────────────────────────────────────────────────────
    private void buildMiniGame() {
        String name = game.getName().toLowerCase();
        if (name.contains("stretch") || name.contains("exercise")) buildStretchGame();
        else if (name.contains("eye") || name.contains("vision"))  buildEyeRestGame();
        else if (name.contains("hydrat") || name.contains("water")) buildHydrationGame();
        else buildBreathingGame();
    }

    // ── BREATHING ────────────────────────────────────────────────────────────
    private void buildBreathingGame() {
        int cycles = 3;
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        Circle outerRing = new Circle(90); outerRing.setFill(Color.TRANSPARENT); outerRing.setStroke(Color.web("#4facfe", 0.2)); outerRing.setStrokeWidth(2);
        Circle circle = new Circle(70); circle.setFill(Color.web("#4facfe", 0.15)); circle.setStroke(Color.web("#4facfe")); circle.setStrokeWidth(3);
        StackPane circlePane = new StackPane(outerRing, circle); circlePane.setPrefSize(200, 200);
        Label instrLbl = new Label("Get Ready..."); instrLbl.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#4facfe;");
        Label cycleLbl = new Label("Cycle 0 / " + cycles); cycleLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:14px;");
        Label tipLbl = new Label("Breathe in 4s  \u00B7  Hold 4s  \u00B7  Breathe out 4s"); tipLbl.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;");
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(340); bp.setStyle("-fx-accent:#4facfe;");
        VBox card = new VBox(20, circlePane, instrLbl, cycleLbl, bp, tipLbl);
        card.setAlignment(Pos.CENTER); card.setPadding(new Insets(40)); card.setMaxWidth(480);
        card.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(79,172,254,0.2),20,0,0,6);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(30));
        gameContentArea.getChildren().add(wrapper);
        running = true;
        final int[] cyc = {0}; final String[] phases = {"Breathe In...", "Hold...", "Breathe Out..."}; final int[] pi = {0};
        Timeline breathe = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            if (!running) return;
            pi[0] = (pi[0] + 1) % 3; if (pi[0] == 0) cyc[0]++;
            instrLbl.setText(phases[pi[0]]); cycleLbl.setText("Cycle " + cyc[0] + " / " + cycles); bp.setProgress((double) cyc[0] / cycles);
            double toScale = pi[0] == 0 ? 1.5 : pi[0] == 2 ? 0.85 : 1.5;
            ScaleTransition st = new ScaleTransition(Duration.seconds(3.8), circle); st.setToX(toScale); st.setToY(toScale); st.play();
            ScaleTransition st2 = new ScaleTransition(Duration.seconds(3.8), outerRing); st2.setToX(toScale * 1.1); st2.setToY(toScale * 1.1); st2.play();
            if (cyc[0] >= cycles && pi[0] == 2) { running = false; endGame(true); }
        }));
        breathe.setCycleCount(cycles * 3 + 1); breathe.play();
    }

    // ── STRETCH ──────────────────────────────────────────────────────────────
    private void buildStretchGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        String[][] exercises = {
            {"\uF44B","Neck Rolls","Slowly roll your neck in circles","#f6d365","#fda085"},
            {"\uF44B","Shoulder Shrugs","Lift shoulders up and down","#43e97b","#38f9d7"},
            {"\uF44B","Arm Circles","Make big circles with your arms","#4facfe","#00f2fe"},
            {"\uF44B","Wrist Rotations","Rotate your wrists gently","#a18cd1","#fbc2eb"},
            {"\uF44B","Back Stretch","Reach up and stretch your back","#fc5c7d","#6a3093"}
        };
        StackPane exIcon = faCircle(exercises[0][0], 32, "linear-gradient(to bottom right," + exercises[0][3] + "," + exercises[0][4] + ")", "white");
        exIcon.setPrefSize(90, 90); exIcon.setMaxSize(90, 90);
        Label nameLbl = new Label(exercises[0][1]); nameLbl.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        Label instrLbl = new Label(exercises[0][2]); instrLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:15px;");
        Label timerLbl = new Label("10"); timerLbl.setStyle("-fx-font-size:64px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
        Label progressTxt = new Label("Exercise 1 / " + exercises.length); progressTxt.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:13px;");
        HBox stepDots = new HBox(8); stepDots.setAlignment(Pos.CENTER);
        for (int i = 0; i < exercises.length; i++) {
            Circle dot = new Circle(7); dot.setFill(i == 0 ? Color.web("#3b4fd8") : Color.web("#e4e8f0")); stepDots.getChildren().add(dot);
        }
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(340); bp.setStyle("-fx-accent:#43e97b;");
        VBox card = new VBox(18, exIcon, nameLbl, instrLbl, timerLbl, stepDots, progressTxt, bp);
        card.setAlignment(Pos.CENTER); card.setPadding(new Insets(36)); card.setMaxWidth(480);
        card.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(67,233,123,0.2),20,0,0,6);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(30));
        gameContentArea.getChildren().add(wrapper);
        running = true;
        final int[] exIdx = {0}; final int[] secs = {10};
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (!running) return;
            secs[0]--;
            timerLbl.setText(String.valueOf(secs[0]));
            timerLbl.setStyle(secs[0] <= 3 ? "-fx-font-size:64px;-fx-font-weight:bold;-fx-text-fill:#e53e3e;" : "-fx-font-size:64px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
            if (secs[0] <= 0) {
                exIdx[0]++;
                if (exIdx[0] >= exercises.length) { running = false; endGame(true); return; }
                secs[0] = 10;
                String[] ex = exercises[exIdx[0]];
                Label ico = new Label(ex[0]); ico.setStyle("-fx-font-family:'Font Awesome 5 Free';-fx-font-weight:900;-fx-font-size:32px;-fx-text-fill:white;");
                exIcon.getChildren().setAll(ico);
                exIcon.setStyle("-fx-background-color:linear-gradient(to bottom right," + ex[3] + "," + ex[4] + ");-fx-background-radius:50;");
                nameLbl.setText(ex[1]); instrLbl.setText(ex[2]);
                timerLbl.setText("10"); timerLbl.setStyle("-fx-font-size:64px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
                progressTxt.setText("Exercise " + (exIdx[0] + 1) + " / " + exercises.length);
                bp.setProgress((double) exIdx[0] / exercises.length);
                for (int i = 0; i < stepDots.getChildren().size(); i++) {
                    ((Circle) stepDots.getChildren().get(i)).setFill(i <= exIdx[0] ? Color.web("#3b4fd8") : Color.web("#e4e8f0"));
                }
            }
        }));
        t.setCycleCount(exercises.length * 10 + 5); t.play();
    }

    // ── EYE REST ─────────────────────────────────────────────────────────────
    private void buildEyeRestGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        int duration = 20;
        StackPane eyeIco = faCircle("\uF06E", 36, "linear-gradient(to bottom right,#4facfe,#00f2fe)", "white");
        eyeIco.setPrefSize(100, 100); eyeIco.setMaxSize(100, 100);
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(2), eyeIco);
        pulse.setFromX(1); pulse.setToX(1.1); pulse.setFromY(1); pulse.setToY(1.1);
        pulse.setCycleCount(ScaleTransition.INDEFINITE); pulse.setAutoReverse(true); pulse.play();
        Label instrLbl = new Label("Get Ready..."); instrLbl.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#4facfe;");
        Label tipLbl = new Label("This helps reduce eye strain from screen time"); tipLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:14px;");
        Label timerLbl = new Label(String.valueOf(duration)); timerLbl.setStyle("-fx-font-size:72px;-fx-font-weight:bold;-fx-text-fill:#4facfe;");
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(340); bp.setStyle("-fx-accent:#4facfe;");
        Label tip2 = new Label("Every 20 min, look 20 feet away for 20 seconds"); tip2.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;");
        VBox card = new VBox(20, eyeIco, instrLbl, tipLbl, timerLbl, bp, tip2);
        card.setAlignment(Pos.CENTER); card.setPadding(new Insets(40)); card.setMaxWidth(480);
        card.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(79,172,254,0.2),20,0,0,6);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(30));
        gameContentArea.getChildren().add(wrapper);
        running = true;
        final int[] secs = {duration};
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(ev -> {
            instrLbl.setText("Look Away From Screen"); tipLbl.setText("Focus on something far away...");
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                if (!running) return;
                secs[0]--; timerLbl.setText(String.valueOf(secs[0]));
                bp.setProgress((double)(duration - secs[0]) / duration);
                if (secs[0] <= 5) timerLbl.setStyle("-fx-font-size:72px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
                if (secs[0] <= 0) { running = false; pulse.stop(); endGame(true); }
            }));
            t.setCycleCount(duration + 1); t.play();
        });
        delay.play();
    }

    // ── HYDRATION ────────────────────────────────────────────────────────────
    private void buildHydrationGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        int duration = 30;
        double glassW = 80, glassH = 130;
        Rectangle glassBg = new Rectangle(glassW, glassH); glassBg.setFill(Color.web("#e3f8ff")); glassBg.setArcWidth(10); glassBg.setArcHeight(10);
        Rectangle waterFill = new Rectangle(glassW, 0); waterFill.setFill(Color.web("#4facfe", 0.7)); waterFill.setArcWidth(8); waterFill.setArcHeight(8);
        Rectangle glassBorder = new Rectangle(glassW, glassH); glassBorder.setFill(Color.TRANSPARENT); glassBorder.setStroke(Color.web("#4facfe")); glassBorder.setStrokeWidth(3); glassBorder.setArcWidth(10); glassBorder.setArcHeight(10);
        StackPane glass = new StackPane(glassBg, waterFill, glassBorder);
        glass.setPrefSize(glassW, glassH); glass.setMaxSize(glassW, glassH);
        StackPane.setAlignment(waterFill, Pos.BOTTOM_CENTER);
        Label instrLbl = new Label("Get Your Water"); instrLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#4facfe;");
        Label tipLbl = new Label("Staying hydrated improves focus and energy"); tipLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:14px;");
        Label timerLbl = new Label(duration + "s"); timerLbl.setStyle("-fx-font-size:48px;-fx-font-weight:bold;-fx-text-fill:#4facfe;");
        Label pctLbl = new Label("0% full"); pctLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(340); bp.setStyle("-fx-accent:#4facfe;");
        final double[] level = {0};
        Button drinkBtn = new Button("I'm Drinking!");
        drinkBtn.setStyle("-fx-background-color:linear-gradient(to right,#4facfe,#00f2fe);-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:15px;-fx-background-radius:12;-fx-padding:12 32;-fx-cursor:hand;-fx-effect:dropshadow(gaussian,rgba(79,172,254,0.4),10,0,0,4);");
        drinkBtn.setOnAction(e -> {
            level[0] = Math.min(level[0] + 20, 100);
            waterFill.setHeight((level[0] / 100.0) * glassH);
            pctLbl.setText((int) level[0] + "% full");
            instrLbl.setText(level[0] >= 100 ? "Glass Full! Great job!" : "Keep Going! " + (int) level[0] + "% full");
            if (level[0] >= 100) instrLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
            ScaleTransition ripple = new ScaleTransition(Duration.millis(200), drinkBtn);
            ripple.setFromX(1); ripple.setToX(0.95); ripple.setFromY(1); ripple.setToY(0.95); ripple.setAutoReverse(true); ripple.setCycleCount(2); ripple.play();
        });
        Label tip2 = new Label("Aim for 8 glasses of water per day"); tip2.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;");
        VBox card = new VBox(16, glass, instrLbl, tipLbl, pctLbl, timerLbl, drinkBtn, bp, tip2);
        card.setAlignment(Pos.CENTER); card.setPadding(new Insets(36)); card.setMaxWidth(480);
        card.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(79,172,254,0.2),20,0,0,6);");
        VBox wrapper = new VBox(card); wrapper.setAlignment(Pos.CENTER); wrapper.setPadding(new Insets(30));
        gameContentArea.getChildren().add(wrapper);
        running = true;
        final int[] secs = {duration};
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(ev -> {
            instrLbl.setText("Drink Water Now"); tipLbl.setText("Click the button as you drink");
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                if (!running) return;
                secs[0]--; timerLbl.setText(secs[0] + "s");
                bp.setProgress((double)(duration - secs[0]) / duration);
                if (secs[0] <= 0) { running = false; endGame(true); }
            }));
            t.setCycleCount(duration + 1); t.play();
        });
        delay.play();
    }

    private void buildGeneric() {
        Label l = new Label(game.getName() + " — coming soon"); l.setStyle("-fx-font-size:18px;-fx-text-fill:#718096;");
        gameContentArea.getChildren().add(l);
    }

    /** Interactive 5-star rating widget. Saves to DB on click. */
    private HBox buildStarWidget(int initialRating, int userId, int gameId) {
        HBox stars = new HBox(8);
        stars.setAlignment(Pos.CENTER);
        Label[] starLabels = new Label[5];
        final int[] selected = {initialRating};
        GameRatingService svc = new GameRatingService();

        for (int i = 1; i <= 5; i++) {
            final int n = i;
            Label star = new Label(i <= initialRating ? "\u2605" : "\u2606");
            star.setStyle("-fx-font-size:30px;-fx-cursor:hand;-fx-text-fill:" + (i <= initialRating ? "#f6d365" : "#cbd5e0") + ";");
            starLabels[i - 1] = star;

            star.setOnMouseEntered(e -> {
                for (int j = 0; j < 5; j++) {
                    boolean fill = j < n;
                    starLabels[j].setText(fill ? "\u2605" : "\u2606");
                    starLabels[j].setStyle("-fx-font-size:30px;-fx-cursor:hand;-fx-text-fill:" + (fill ? "#f6d365" : "#cbd5e0") + ";");
                }
            });
            star.setOnMouseExited(e -> {
                for (int j = 0; j < 5; j++) {
                    boolean fill = j < selected[0];
                    starLabels[j].setText(fill ? "\u2605" : "\u2606");
                    starLabels[j].setStyle("-fx-font-size:30px;-fx-cursor:hand;-fx-text-fill:" + (fill ? "#f6d365" : "#cbd5e0") + ";");
                }
            });
            star.setOnMouseClicked(e -> {
                selected[0] = n;
                try { svc.rate(userId, gameId, n); } catch (Exception ex) { System.err.println("Rating: " + ex.getMessage()); }
                for (int j = 0; j < 5; j++) {
                    boolean fill = j < selected[0];
                    starLabels[j].setText(fill ? "\u2605" : "\u2606");
                    starLabels[j].setStyle("-fx-font-size:30px;-fx-cursor:hand;-fx-text-fill:" + (fill ? "#f6d365" : "#cbd5e0") + ";");
                }
            });
            stars.getChildren().add(star);
        }
        return stars;
    }

    @FXML private void goBack() {
        running = false; if (gameTimer != null) gameTimer.stop(); if (targetSpawner != null) targetSpawner.stop();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/gamification/game_launcher.fxml"));
            Parent view = loader.load();
            GameLauncherController ctrl = loader.getController();
            ctrl.setContentArea(contentArea);
            ctrl.setInitialTab(isMiniGame ? 1 : 0);
            if (contentArea != null) contentArea.getChildren().setAll(view);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String instructionsFor(String type, String cat) {
        if ("MINI_GAME".equals(cat)) {
            String name = game.getName().toLowerCase();
            if (name.contains("stretch")) return "Follow each stretching exercise for 10 seconds. Complete all 5 to finish.";
            if (name.contains("eye") || name.contains("vision")) return "Look away from your screen at something far away for 20 seconds.";
            if (name.contains("hydrat") || name.contains("water")) return "Drink water and click the button as you sip. Complete in 30 seconds.";
            return "Follow the breathing circle: inhale 4s · hold 4s · exhale 4s. Complete 3 cycles.";
        }
        return switch (type) {
            case "MEMORY" -> "Flip cards to find matching pairs. Match all pairs before time runs out!";
            case "PUZZLE" -> "Unscramble the letters to form the correct word. Submit or skip each word.";
            case "TRIVIA" -> "Answer multiple-choice questions correctly. 60% correct to win!";
            case "ARCADE" -> "Click the colored targets as fast as you can before they disappear!";
            default       -> "Follow the on-screen instructions to complete the game.";
        };
    }

    private int timeLimitFor(String d) { return switch (d) { case "HARD" -> 45; case "MEDIUM" -> 75; default -> 120; }; }
    private String formatTime(int s) { return String.format("%d:%02d", s / 60, s % 60); }

    private StackPane faCircle(String unicode, double iconSize, String bgGradient, String iconColor) {
        // Delegate to TwemojiUtil — unicode here is an emoji char, not FA code
        return TwemojiUtil.circle(unicode, iconSize * 2.5, bgGradient, iconSize * 1.5);
    }

    private String typeIcon(String type) {
        return switch (type) { case "PUZZLE" -> TwemojiUtil.PUZZLE; case "MEMORY" -> TwemojiUtil.MEMORY; case "TRIVIA" -> TwemojiUtil.TRIVIA; case "ARCADE" -> TwemojiUtil.ARCADE; default -> TwemojiUtil.GAMEPAD; };
    }

    private String typeGradient(String type) {
        return switch (type) {
            case "PUZZLE" -> "linear-gradient(to bottom right,#f6d365,#fda085)";
            case "MEMORY" -> "linear-gradient(to bottom right,#a18cd1,#fbc2eb)";
            case "TRIVIA" -> "linear-gradient(to bottom right,#4facfe,#00f2fe)";
            case "ARCADE" -> "linear-gradient(to bottom right,#43e97b,#38f9d7)";
            default       -> "linear-gradient(to bottom right,#667eea,#764ba2)";
        };
    }
}
