package controllers.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import models.gamification.Game;
import services.gamification.EnergyService;
import services.gamification.FavoriteGameService;
import services.gamification.GameRatingService;
import services.gamification.GameService;
import services.gamification.WellnessAIService;
import utils.TwemojiUtil;
import utils.UserSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GameLauncherController {

    @FXML private VBox     mainContainer;
    @FXML private FlowPane fullGamesPane;
    @FXML private FlowPane miniGamesPane;
    @FXML private Label    lblFullCount;
    @FXML private Label    lblMiniCount;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private ComboBox<String> filterDifficulty;
    @FXML private Button   btnTabFull;
    @FXML private Button   btnTabMini;
    @FXML private VBox     sectionFull;
    @FXML private VBox     sectionMini;

    // ── Energy bar FXML nodes ─────────────────────────────────────────────────
    @FXML private HBox   energyBarBox;      // compact toolbar bar
    @FXML private Label  lblEnergyValue;    // "100" in toolbar
    @FXML private Region energyBarFill;     // fill region in toolbar
    @FXML private Label  lblRegenTimer;     // "next in 3m" in toolbar
    @FXML private Region energyBarBig;      // fill region in mini banner
    @FXML private Label  lblEnergyBig;      // "100 / 100" in banner
    @FXML private Label  lblEnergyPct;      // "100%" in banner
    @FXML private Label  lblRegenInfo;      // regen text in banner
    @FXML private Label  lblNextRegen;      // countdown in banner
    @FXML private Label  lblLowEnergyWarn;  // warning label

    // ── Wellness AI Coach FXML nodes ──────────────────────────────────────────
    @FXML private VBox      wellnessSection;
    @FXML private TextField wellnessInput;
    @FXML private Button    btnGetAdvice;
    @FXML private VBox      wellnessResponse;
    @FXML private VBox      wellnessTipCard;
    @FXML private Label     lblAIEnergyBadge;   // "100 / 100" live text
    @FXML private Region    aiEnergyFill;        // mini fill bar
    @FXML private Label     lblAIEnergyStatus;   // "Energy Full" / "Low energy"
    @FXML private Label     lblUrgencyIcon;
    @FXML private Label     lblUrgencyLabel;
    @FXML private Label     lblUrgencyBadge;
    @FXML private Label     lblWellnessTip;
    @FXML private Label     lblRecommendedIcon;
    @FXML private Label     lblRecommendedGame;
    @FXML private Label     lblRecommendedReason;
    @FXML private Button    btnPlayRecommended;
    @FXML private Label     lblWellnessStatus;

    private final GameService             gameService    = new GameService();
    private final FavoriteGameService     favService     = new FavoriteGameService();
    private final GameRatingService       ratingService  = new GameRatingService();
    private final EnergyService           energyService  = new EnergyService();
    private final WellnessAIService       wellnessAI     = new WellnessAIService();

    private String recommendedGameType  = null;
    // Auto-advice: re-generate when energy crosses a threshold boundary
    private int    lastAutoAdviceEnergy = -1;

    private List<Game> allGames;
    private StackPane  contentArea;
    private boolean    showingFull = true;

    private Timeline energyRefreshTimer;
    private Timeline regenCountdownTimer;
    private int      secondsUntilRegen = 0;
    // Cached energy for wellness AI
    private int      lastKnownEnergy   = 100;

    public void setContentArea(StackPane ca) { this.contentArea = ca; }

    public void setInitialTab(int tabIndex) {
        if (tabIndex == 1) switchToMini();
        else switchToFull();
    }

    @FXML
    public void initialize() {
        filterType.setItems(FXCollections.observableArrayList("All Types","PUZZLE","MEMORY","TRIVIA","ARCADE"));
        filterType.setValue("All Types");
        filterDifficulty.setItems(FXCollections.observableArrayList("All Levels","EASY","MEDIUM","HARD"));
        filterDifficulty.setValue("All Levels");
        filterType.valueProperty().addListener((o,a,b) -> applyFilters());
        filterDifficulty.valueProperty().addListener((o,a,b) -> applyFilters());
        try {
            allGames = gameService.getAllGames().stream().filter(Game::isActive).collect(Collectors.toList());
        } catch (Exception e) { allGames = List.of(); }
        renderAll(allGames);
        switchToFull();

        // Load energy bar on background thread
        loadEnergyAsync();

        // Auto-refresh energy from DB every 30 seconds
        energyRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(30), e -> loadEnergyAsync()));
        energyRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        energyRefreshTimer.play();

        // Countdown ticker — every second
        regenCountdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickCountdown()));
        regenCountdownTimer.setCycleCount(Timeline.INDEFINITE);
        regenCountdownTimer.play();
    }

    // ── Energy bar logic ──────────────────────────────────────────────────────

    /** Fetch energy from DB on a background thread, then update UI. */
    public void loadEnergyAsync() {
        int userId = utils.SessionManager.getCurrentUserId();
        if (userId <= 1) userId = utils.UserSession.getInstance().getUserId();
        if (userId <= 0) return;
        final int uid = userId;
        Thread t = new Thread(() -> {
            try {
                int[] snap = energyService.getEnergySnapshot(uid); // [energy, secondsUntilRegen]
                Platform.runLater(() -> updateEnergyUI(snap[0], snap[1]));
            } catch (Exception e) {
                System.err.println("[Energy] Load failed: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** Update all energy bar nodes with the given energy value. */
    private void updateEnergyUI(int energy, int secsUntilRegen) {
        energy = Math.max(0, Math.min(100, energy));
        lastKnownEnergy = energy;
        secondsUntilRegen = secsUntilRegen;
        double pct = energy / 100.0;

        // ── Toolbar compact bar ───────────────────────────────────────────────
        if (lblEnergyValue != null) lblEnergyValue.setText(String.valueOf(energy));
        if (energyBarFill != null) {
            energyBarFill.setPrefWidth(120 * pct);
            energyBarFill.setStyle(energyFillStyle(energy, false));
        }
        if (lblRegenTimer != null) {
            lblRegenTimer.setText(energy >= 100 ? "Full!" : "next in " + formatSeconds(secsUntilRegen));
            lblRegenTimer.setStyle("-fx-font-size:10px;-fx-text-fill:" + (energy >= 100 ? "#27ae60" : "#718096") + ";");
        }

        // ── Banner large bar ──────────────────────────────────────────────────
        if (lblEnergyBig != null) lblEnergyBig.setText(energy + " / 100");
        if (energyBarBig != null) {
            // Bind fill width to parent track width × percentage
            javafx.scene.Node track = energyBarBig.getParent();
            if (track instanceof StackPane sp) {
                double trackW = sp.getWidth();
                if (trackW > 0) {
                    energyBarBig.setPrefWidth(trackW * pct);
                } else {
                    // Not laid out yet — bind once width is known
                    final int finalEnergy = energy;
                    sp.widthProperty().addListener((obs, o, n) -> {
                        if (n.doubleValue() > 0) {
                            energyBarBig.setPrefWidth(n.doubleValue() * (finalEnergy / 100.0));
                        }
                    });
                }
            }
            energyBarBig.setStyle(energyFillStyle(energy, true));
        }
        if (lblEnergyPct != null) lblEnergyPct.setText(energy + "%");
        if (lblRegenInfo != null) {
            lblRegenInfo.setText(energy >= 100
                ? "Energy Full! Ready to play"
                : "\u27F3 +1 energy every 5 minutes");
        }
        if (lblNextRegen != null) {
            lblNextRegen.setText(energy >= 100 ? "" : "Next: " + formatSeconds(secsUntilRegen));
        }

        // ── Low energy warning ────────────────────────────────────────────────
        if (lblLowEnergyWarn != null) {
            boolean low = energy <= 20 && energy > 0;
            boolean depleted = energy <= 0;
            lblLowEnergyWarn.setVisible(low || depleted);
            lblLowEnergyWarn.setManaged(low || depleted);
            if (depleted) {
                lblLowEnergyWarn.setText("\u26A0 Energy depleted! Play mini games or wait for regeneration.");
                lblLowEnergyWarn.setStyle("-fx-font-size:11px;-fx-text-fill:#fc5c7d;-fx-font-weight:bold;");
            } else if (low) {
                lblLowEnergyWarn.setText("\u26A0 Low energy! Play a mini game to restore it faster.");
                lblLowEnergyWarn.setStyle("-fx-font-size:11px;-fx-text-fill:#fbd38d;-fx-font-weight:bold;");
            }
        }

        // ── Wellness AI mini energy bar — always live ─────────────────────────
        if (lblAIEnergyBadge != null) {
            String col = energy > 50 ? "#27ae60" : energy > 20 ? "#d97706" : "#e53e3e";
            lblAIEnergyBadge.setText(energy + " / 100");
            lblAIEnergyBadge.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:" + col + ";");
        }
        if (aiEnergyFill != null) {
            aiEnergyFill.setPrefWidth(130 * pct);
            String gradient = energy > 50 ? "linear-gradient(to right,#43e97b,#38f9d7)"
                            : energy > 20 ? "linear-gradient(to right,#f6d365,#fda085)"
                            :               "linear-gradient(to right,#fc5c7d,#6a3093)";
            aiEnergyFill.setStyle("-fx-background-color:" + gradient + ";-fx-background-radius:4;");
        }
        if (lblAIEnergyStatus != null) {
            String statusText = energy >= 100 ? "Energy Full — great time to study!"
                              : energy >= 80  ? "High energy — you're doing well"
                              : energy >= 50  ? "Moderate energy — consider a break soon"
                              : energy >= 20  ? "Low energy — a mini game will help"
                              :                 "Critically low — take a break now!";
            String statusCol  = energy >= 80 ? "#27ae60" : energy >= 50 ? "#d97706" : "#e53e3e";
            lblAIEnergyStatus.setText(statusText);
            lblAIEnergyStatus.setStyle("-fx-font-size:10px;-fx-text-fill:" + statusCol + ";");
        }

        // ── Auto-generate advice when energy crosses a threshold ──────────────
        // Only auto-generate when energy is LOW (≤50) — not when it's high
        int threshold = energy >= 80 ? 80 : energy >= 50 ? 50 : energy >= 20 ? 20 : 0;
        if (threshold != lastAutoAdviceEnergy) {
            lastAutoAdviceEnergy = threshold;
            // Only auto-generate when energy is actually low enough to warrant advice
            if (energy <= 50) {
                autoGenerateAdvice(energy);
            } else {
                // High energy — just hide any stale advice and reset button
                if (wellnessResponse != null) { wellnessResponse.setVisible(false); wellnessResponse.setManaged(false); }
                if (btnGetAdvice != null) { btnGetAdvice.setDisable(false); btnGetAdvice.setText("Get Advice"); }
                setWellnessStatus("", false);
            }
        }
    }

    /** Tick the countdown every second without hitting the DB. */
    private void tickCountdown() {
        if (secondsUntilRegen <= 0) return;
        secondsUntilRegen--;
        if (secondsUntilRegen <= 0) {
            // Regen point just arrived — reload from DB
            loadEnergyAsync();
            return;
        }
        String timeStr = formatSeconds(secondsUntilRegen);
        if (lblRegenTimer != null) {
            String cur = lblRegenTimer.getText();
            if (cur.startsWith("next in")) lblRegenTimer.setText("next in " + timeStr);
        }
        if (lblNextRegen != null) {
            String cur = lblNextRegen.getText();
            if (cur.startsWith("Next:")) lblNextRegen.setText("Next: " + timeStr);
        }
    }

    private String energyFillStyle(int energy, boolean large) {
        String gradient;
        if (energy > 50)      gradient = "linear-gradient(to right,#43e97b,#38f9d7)";
        else if (energy > 20) gradient = "linear-gradient(to right,#f6d365,#fda085)";
        else                  gradient = "linear-gradient(to right,#fc5c7d,#6a3093)";
        String radius = large ? "9" : "4";
        return "-fx-background-color:" + gradient + ";-fx-background-radius:" + radius + ";";
    }

    private String formatSeconds(int secs) {
        if (secs <= 0) return "0s";
        int m = secs / 60, s = secs % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    /** Called by GamePlayController after a mini game completes to refresh the bar. */
    public void refreshEnergyAfterMiniGame() {
        loadEnergyAsync();
    }

    /** Stop timers when navigating away. */
    public void stopTimers() {
        if (energyRefreshTimer  != null) energyRefreshTimer.stop();
        if (regenCountdownTimer != null) regenCountdownTimer.stop();
    }

    // ── Wellness AI Coach handlers ────────────────────────────────────────────

    @FXML
    private void handleGetWellnessAdvice() {
        if (btnGetAdvice == null) return;
        String feeling = wellnessInput != null ? wellnessInput.getText().trim() : "";
        btnGetAdvice.setDisable(true);
        btnGetAdvice.setText("Thinking...");
        setWellnessStatus("Asking your AI wellness coach...", false);
        if (wellnessResponse != null) { wellnessResponse.setVisible(false); wellnessResponse.setManaged(false); }
        final int energy = lastKnownEnergy;
        Thread t = new Thread(() -> {
            try {
                WellnessAIService.WellnessAdvice advice = wellnessAI.getAdvice(energy, feeling);
                javafx.application.Platform.runLater(() -> showWellnessAdvice(advice));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    setWellnessStatus("Could not reach AI. Check your API key.", true);
                    btnGetAdvice.setDisable(false);
                    btnGetAdvice.setText("Get Advice");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** Auto-generates advice silently when energy crosses a threshold. */
    private void autoGenerateAdvice(int energy) {
        if (btnGetAdvice != null) btnGetAdvice.setDisable(true);
        setWellnessStatus("Updating wellness advice for your energy level...", false);
        Thread t = new Thread(() -> {
            try {
                WellnessAIService.WellnessAdvice advice = wellnessAI.getAdvice(energy, "");
                javafx.application.Platform.runLater(() -> {
                    showWellnessAdvice(advice);
                    setWellnessStatus("", false); // clear status after auto-update
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    setWellnessStatus("", false);
                    if (btnGetAdvice != null) { btnGetAdvice.setDisable(false); btnGetAdvice.setText("Get Advice"); }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void showWellnessAdvice(WellnessAIService.WellnessAdvice advice) {
        String urgencyIcon, tipBg, tipBorder, urgencyColor, urgencyBg;
        String urgencyText;
        switch (advice.urgency) {
            case "HIGH" -> {
                urgencyIcon = "\u26A0"; tipBg = "#fff5f5"; tipBorder = "#fc5c7d";
                urgencyColor = "#e53e3e"; urgencyBg = "#fff5f5"; urgencyText = "HIGH PRIORITY";
            }
            case "MEDIUM" -> {
                urgencyIcon = "\u26A1"; tipBg = "#fffbeb"; tipBorder = "#f6d365";
                urgencyColor = "#d97706"; urgencyBg = "#fffbeb"; urgencyText = "MODERATE";
            }
            default -> {
                urgencyIcon = "\u2705"; tipBg = "#f0fff4"; tipBorder = "#9ae6b4";
                urgencyColor = "#27ae60"; urgencyBg = "#f0fff4"; urgencyText = "ALL GOOD";
            }
        }

        if (lblUrgencyIcon  != null) lblUrgencyIcon.setText(urgencyIcon);
        if (lblUrgencyLabel != null) {
            lblUrgencyLabel.setText("Wellness Tip");
            lblUrgencyLabel.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" + urgencyColor + ";");
        }
        if (lblUrgencyBadge != null) {
            lblUrgencyBadge.setText(urgencyText);
            lblUrgencyBadge.setStyle("-fx-background-color:" + urgencyBg + ";-fx-text-fill:" + urgencyColor +
                                     ";-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
        }
        if (lblWellnessTip  != null) lblWellnessTip.setText(advice.tip);
        if (wellnessTipCard != null)
            wellnessTipCard.setStyle("-fx-background-color:" + tipBg + ";-fx-background-radius:10;" +
                                     "-fx-border-color:" + tipBorder + ";-fx-border-radius:10;-fx-border-width:1;-fx-padding:12 16;");

        String gameLabel = switch (advice.gameType) {
            case "BREATHING"  -> "Breathing Exercise";
            case "STRETCH"    -> "Quick Stretch";
            case "EYE"        -> "Eye Rest";
            case "HYDRATION"  -> "Hydration Break";
            default           -> advice.gameType;
        };
        String gameIcon = switch (advice.gameType) {
            case "BREATHING"  -> "\uD83C\uDF2C";
            case "STRETCH"    -> "\uD83D\uDCAA";
            case "EYE"        -> "\uD83D\uDC41";
            case "HYDRATION"  -> "\uD83D\uDCA7";
            default           -> "\uD83C\uDFAE";
        };
        if (lblRecommendedIcon   != null) lblRecommendedIcon.setText(gameIcon);
        if (lblRecommendedGame   != null) lblRecommendedGame.setText("Recommended: " + gameLabel);
        if (lblRecommendedReason != null) lblRecommendedReason.setText(advice.gameReason);

        recommendedGameType = advice.gameType;

        if (wellnessResponse != null) { wellnessResponse.setVisible(true); wellnessResponse.setManaged(true); }
        if (btnGetAdvice != null) { btnGetAdvice.setDisable(false); btnGetAdvice.setText("Refresh Advice"); }
    }

    @FXML
    private void handlePlayRecommended() {
        if (recommendedGameType == null || allGames == null) return;
        // Find a mini game matching the recommended type by name/description
        String keyword = switch (recommendedGameType) {
            case "BREATHING"  -> "breath";
            case "STRETCH"    -> "stretch";
            case "EYE"        -> "eye";
            case "HYDRATION"  -> "hydrat";
            default           -> "breath";
        };
        Game match = allGames.stream()
            .filter(g -> "MINI_GAME".equals(g.getCategory()))
            .filter(g -> g.getName().toLowerCase().contains(keyword) ||
                         (g.getDescription() != null && g.getDescription().toLowerCase().contains(keyword)))
            .findFirst()
            // Fallback: any mini game
            .orElse(allGames.stream().filter(g -> "MINI_GAME".equals(g.getCategory())).findFirst().orElse(null));
        if (match != null) launchGame(match);
    }

    private void setWellnessStatus(String msg, boolean isError) {
        if (lblWellnessStatus == null) return;
        lblWellnessStatus.setText(msg);
        lblWellnessStatus.setVisible(!msg.isEmpty());
        lblWellnessStatus.setManaged(!msg.isEmpty());
        lblWellnessStatus.setStyle("-fx-font-size:11px;-fx-text-fill:" + (isError ? "#e53e3e" : "#718096") + ";");
    }

    @FXML private void handleSearch() { applyFilters(); }
    @FXML private void handleClearFilters() {
        searchField.clear(); filterType.setValue("All Types"); filterDifficulty.setValue("All Levels");
        renderAll(allGames);
    }
    @FXML private void handleShowFull() { switchToFull(); }
    @FXML private void handleShowMini() { switchToMini(); }

    private void switchToFull() {
        showingFull = true;
        sectionFull.setVisible(true); sectionFull.setManaged(true);
        sectionMini.setVisible(false); sectionMini.setManaged(false);
        btnTabFull.setStyle(tabActiveStyle());
        btnTabMini.setStyle(tabInactiveStyle());
    }
    private void switchToMini() {
        showingFull = false;
        sectionFull.setVisible(false); sectionFull.setManaged(false);
        sectionMini.setVisible(true); sectionMini.setManaged(true);
        btnTabFull.setStyle(tabInactiveStyle());
        btnTabMini.setStyle(tabActiveStyle());
    }

    private String tabActiveStyle() {
        return "-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;" +
               "-fx-background-radius:8 8 0 0;-fx-padding:10 24;-fx-cursor:hand;-fx-border-color:transparent;";
    }
    private String tabInactiveStyle() {
        return "-fx-background-color:#f0f2f8;-fx-text-fill:#718096;-fx-font-size:13px;" +
               "-fx-background-radius:8 8 0 0;-fx-padding:10 24;-fx-cursor:hand;-fx-border-color:transparent;";
    }

    private void applyFilters() {
        String kw = searchField.getText().trim().toLowerCase();
        String type = filterType.getValue();
        String diff = filterDifficulty.getValue();
        List<Game> filtered = allGames.stream().filter(g -> {
            boolean matchKw   = kw.isEmpty() || g.getName().toLowerCase().contains(kw) || g.getType().toLowerCase().contains(kw);
            boolean matchType = "All Types".equals(type) || g.getType().equals(type);
            boolean matchDiff = "All Levels".equals(diff) || g.getDifficulty().equals(diff);
            return matchKw && matchType && matchDiff;
        }).collect(Collectors.toList());
        renderAll(filtered);
    }

    private void renderAll(List<Game> games) {
        List<Game> full = games.stream().filter(g -> "FULL_GAME".equals(g.getCategory())).collect(Collectors.toList());
        List<Game> mini = games.stream().filter(g -> "MINI_GAME".equals(g.getCategory())).collect(Collectors.toList());
        fullGamesPane.getChildren().clear();
        miniGamesPane.getChildren().clear();
        full.forEach(g -> fullGamesPane.getChildren().add(buildCard(g, false)));
        mini.forEach(g -> miniGamesPane.getChildren().add(buildCard(g, true)));
        lblFullCount.setText(full.size() + " game" + (full.size()==1?"":"s"));
        lblMiniCount.setText(mini.size() + " mini-game" + (mini.size()==1?"":"s"));
    }
    private VBox buildCard(Game game, boolean isMini) {
        // Twemoji icon circle
        StackPane iconCircle = TwemojiUtil.circle(typeEmoji(game.getType()), 64, typeGradient(game.getType()), 38);
        iconCircle.setMaxSize(64, 64);

        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        title.setWrapText(true); title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER); title.setMaxWidth(190);

        Label typeBadge = badge(game.getType(), typeBadgeBg(game.getType()), typeBadgeFg(game.getType()));
        Label diffBadge = badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge); badges.setAlignment(Pos.CENTER);

        Separator sep = new Separator();

        // Stats — tokens/XP for full, energy for mini
        HBox stats;
        if (isMini) {
            int ep = game.getEnergyPoints()!=null?game.getEnergyPoints():0;
            stats = statsRow("Energy", "+"+ep, "#27ae60", "Cost", String.valueOf(game.getTokenCost()), "#718096");
        } else {
            stats = statsRow("Tokens", "+"+game.getRewardTokens(), "#b7791f", "XP", "+"+game.getRewardXP(), "#2b6cb0");
        }
        stats.setStyle("-fx-background-color:#f8f9ff;-fx-background-radius:8;-fx-padding:8 6;");

        // Rating row
        HBox ratingRow = buildRatingDisplay(game.getId());
        int userId = utils.SessionManager.getCurrentUserId();
        if (userId > 0) {
            Button btnRate = new Button("Rate");
            btnRate.setStyle("-fx-background-color:transparent;-fx-text-fill:#3b4fd8;-fx-font-size:11px;-fx-cursor:hand;-fx-underline:true;-fx-padding:0 4;");
            btnRate.setOnAction(e -> showRatingDialog(game, ratingRow));
            ratingRow.getChildren().add(btnRate);
        }

        // Play button — plain text, no FA icon
        String btnBg    = isMini ? "#27ae60" : "#3b4fd8";
        String btnHover = isMini ? "#1e8449" : "#2d3fc7";
        Button btnPlay = new Button(isMini ? "Play Mini Game" : "Play Now");
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnPlay.setStyle("-fx-background-color:"+btnBg+";-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:8;-fx-padding:10 0;-fx-cursor:hand;");
        btnPlay.setOnMouseEntered(e -> btnPlay.setStyle(btnPlay.getStyle().replace(btnBg, btnHover)));
        btnPlay.setOnMouseExited(e -> btnPlay.setStyle(btnPlay.getStyle().replace(btnHover, btnBg)));
        btnPlay.setOnAction(e -> launchGame(game));

        // Favorite button
        final boolean[] isFav = {false};
        try { isFav[0] = userId>0 && favService.isFavorite(userId, game.getId()); } catch (Exception ignored) {}
        Label heartLbl = new Label(isFav[0] ? "\u2764" : "\u2661");
        heartLbl.setStyle("-fx-font-size:16px;-fx-text-fill:"+(isFav[0]?"#e53e3e":"#a0aec0")+";");
        Button btnFav = new Button(); btnFav.setGraphic(heartLbl);
        btnFav.setStyle("-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:4 8;");
        btnFav.setOnAction(e -> {
            if (userId<=0) return;
            try {
                boolean nowFav = favService.toggle(userId, game.getId());
                isFav[0] = nowFav;
                heartLbl.setText(nowFav?"\u2764":"\u2661");
                heartLbl.setStyle("-fx-font-size:16px;-fx-text-fill:"+(nowFav?"#e53e3e":"#a0aec0")+";");
            } catch (Exception ex) {}
        });

        HBox actionRow = new HBox(6, btnPlay, btnFav);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(btnPlay, Priority.ALWAYS);

        VBox card = new VBox(10, iconCircle, title, badges, sep, stats, ratingRow, actionRow);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(18,14,18,14));
        card.setPrefWidth(210);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#e4e8f0;-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,3);");
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle().replace("rgba(0,0,0,0.07)","rgba(59,79,216,0.18)").replace("#e4e8f0","#c3c9f5")));
        card.setOnMouseExited(e  -> card.setStyle(card.getStyle().replace("rgba(59,79,216,0.18)","rgba(0,0,0,0.07)").replace("#c3c9f5","#e4e8f0")));
        return card;
    }

    private HBox buildRatingDisplay(int gameId) {
        HBox row = new HBox(4); row.setAlignment(Pos.CENTER);
        try {
            double avg = ratingService.getAverageRating(gameId);
            int count  = ratingService.getRatingCount(gameId);
            if (count == 0) {
                Label none = new Label("No ratings yet"); none.setStyle("-fx-text-fill:#cbd5e0;-fx-font-size:11px;");
                row.getChildren().add(none);
            } else {
                for (int i=1;i<=5;i++) {
                    Label star = new Label(i<=Math.round(avg)?"\u2605":"\u2606");
                    star.setStyle("-fx-font-size:14px;-fx-text-fill:"+(i<=Math.round(avg)?"#f6d365":"#cbd5e0")+";");
                    row.getChildren().add(star);
                }
                Label avgLbl = new Label(String.format(" %.1f (%d)",avg,count));
                avgLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#718096;");
                row.getChildren().add(avgLbl);
            }
        } catch (Exception e) { row.getChildren().add(new Label("")); }
        return row;
    }

    private void showRatingDialog(Game game, HBox ratingRowToRefresh) {
        int userId = utils.SessionManager.getCurrentUserId();
        if (userId<=0) return;
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Rate: "+game.getName());
        VBox content = new VBox(14); content.setAlignment(Pos.CENTER); content.setPadding(new Insets(24)); content.setMinWidth(300);
        Label title = new Label("How would you rate this game?"); title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        final int[] selected = {0};
        try { selected[0] = ratingService.getUserRating(userId, game.getId()); } catch (Exception ignored) {}
        Label[] starLabels = new Label[5];
        HBox stars = new HBox(10); stars.setAlignment(Pos.CENTER);
        for (int i=1;i<=5;i++) {
            final int n=i;
            Label star = new Label(i<=selected[0]?"\u2605":"\u2606");
            star.setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(i<=selected[0]?"#f6d365":"#cbd5e0")+";");
            starLabels[i-1]=star;
            star.setOnMouseEntered(e->{ for(int j=0;j<5;j++){boolean f=j<n;starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            star.setOnMouseExited(e->{ for(int j=0;j<5;j++){boolean f=j<selected[0];starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            star.setOnMouseClicked(e->{ selected[0]=n; for(int j=0;j<5;j++){boolean f=j<selected[0];starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            stars.getChildren().add(star);
        }
        content.getChildren().addAll(title, stars, new Label("Click a star to rate"));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK).setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:6;-fx-padding:7 18;");
        dlg.setResultConverter(bt -> {
            if (bt==ButtonType.OK && selected[0]>0) {
                try {
                    ratingService.rate(userId, game.getId(), selected[0]);
                    HBox fresh = buildRatingDisplay(game.getId());
                    ratingRowToRefresh.getChildren().setAll(fresh.getChildren());
                    Button btnRate = new Button("Rate");
                    btnRate.setStyle("-fx-background-color:transparent;-fx-text-fill:#3b4fd8;-fx-font-size:11px;-fx-cursor:hand;-fx-underline:true;-fx-padding:0 4;");
                    btnRate.setOnAction(ev -> showRatingDialog(game, ratingRowToRefresh));
                    ratingRowToRefresh.getChildren().add(btnRate);
                } catch (Exception ex) {}
            }
            return null;
        });
        dlg.showAndWait();
    }

    private HBox statsRow(String lbl1, String val1, String col1, String lbl2, String val2, String col2) {
        HBox row = new HBox(0, statCell(lbl1, val1, col1), statCell(lbl2, val2, col2));
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        return row;
    }
    private VBox statCell(String label, String value, String valueColor) {
        Label val = new Label(value);
        val.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + valueColor + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#a0aec0;");
        VBox b = new VBox(2, val, lbl); b.setAlignment(Pos.CENTER); b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void launchGame(Game game) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/gamification/game_play.fxml"));
            Parent view = loader.load();
            GamePlayController ctrl = loader.getController();
            ctrl.setGame(game); ctrl.setContentArea(contentArea);
            if (contentArea!=null) contentArea.getChildren().setAll(view);
        } catch (Exception e) { new Alert(Alert.AlertType.ERROR,"Cannot load game: "+e.getMessage()).showAndWait(); }
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-background-radius:20;-fx-padding:3 10;-fx-font-size:10px;-fx-font-weight:bold;");
        return l;
    }

    private String typeEmoji(String t) { return switch(t){case "PUZZLE"->TwemojiUtil.PUZZLE;case "MEMORY"->TwemojiUtil.MEMORY;case "TRIVIA"->TwemojiUtil.TRIVIA;case "ARCADE"->TwemojiUtil.ARCADE;default->TwemojiUtil.GAMEPAD;}; }
    private String typeGradient(String t) { return switch(t){case "PUZZLE"->"linear-gradient(to bottom right,#f6d365,#fda085)";case "MEMORY"->"linear-gradient(to bottom right,#a18cd1,#fbc2eb)";case "TRIVIA"->"linear-gradient(to bottom right,#4facfe,#00f2fe)";case "ARCADE"->"linear-gradient(to bottom right,#43e97b,#38f9d7)";default->"linear-gradient(to bottom right,#667eea,#764ba2)";}; }
    private String typeBadgeBg(String t) { return switch(t){case "PUZZLE"->"#fff8e1";case "MEMORY"->"#f3e5f5";case "TRIVIA"->"#e3f2fd";case "ARCADE"->"#e8f5e9";default->"#eef0fd";}; }
    private String typeBadgeFg(String t) { return switch(t){case "PUZZLE"->"#b7791f";case "MEMORY"->"#805ad5";case "TRIVIA"->"#2b6cb0";case "ARCADE"->"#276749";default->"#3b4fd8";}; }
    private String diffBg(String d) { return switch(d){case "HARD"->"#fff5f5";case "MEDIUM"->"#fffbeb";default->"#f0fff4";}; }
    private String diffFg(String d) { return switch(d){case "HARD"->"#e53e3e";case "MEDIUM"->"#d97706";default->"#27ae60";}; }
}
