package controllers.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import services.gamification.LeaderboardService;
import utils.UserSession;

import java.util.List;

public class LeaderboardController {

    @FXML private TextField        searchField;
    @FXML private ComboBox<String> sortCombo;
    @FXML private VBox             leaderboardList;
    @FXML private Label            lblTotal;
    @FXML private VBox             myRankSection;

    private final LeaderboardService service = new LeaderboardService();

    @FXML
    public void initialize() {
        sortCombo.getItems().addAll("Top XP", "Top Tokens", "Top Level");
        sortCombo.setValue("Top XP");
        sortCombo.valueProperty().addListener((o, a, b) -> loadLeaderboard());
        searchField.textProperty().addListener((o, a, b) -> {
            // Debounce
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(Duration.millis(300));
            pt.setOnFinished(e -> loadLeaderboard());
            pt.play();
        });
        loadMyRank();
        loadLeaderboard();
    }

    @FXML private void handleSearch()       { loadLeaderboard(); }
    @FXML private void handleClearSearch()  { searchField.clear(); loadLeaderboard(); }

    private void loadLeaderboard() {
        String search = searchField.getText().trim();
        String sort   = switch (sortCombo.getValue()) {
            case "Top Tokens" -> "tokens";
            case "Top Level"  -> "level";
            default           -> "xp";
        };

        leaderboardList.getChildren().clear();
        Label loading = new Label("Loading...");
        loading.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:14px;-fx-padding:20;");
        leaderboardList.getChildren().add(loading);

        Thread t = new Thread(() -> {
            try {
                List<LeaderboardService.PlayerEntry> entries = service.getLeaderboard(search, sort, 50);
                Platform.runLater(() -> {
                    leaderboardList.getChildren().clear();
                    if (entries.isEmpty()) {
                        Label empty = new Label("No players found.");
                        empty.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:14px;-fx-padding:20;");
                        leaderboardList.getChildren().add(empty);
                    } else {
                        if (lblTotal != null) lblTotal.setText(entries.size() + " players");
                        for (LeaderboardService.PlayerEntry e : entries) {
                            VBox row = buildRow(e);
                            row.setOpacity(0);
                            leaderboardList.getChildren().add(row);
                            FadeTransition ft = new FadeTransition(Duration.millis(200), row);
                            ft.setDelay(Duration.millis(e.rank * 30L));
                            ft.setToValue(1); ft.play();
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    leaderboardList.getChildren().clear();
                    Label err = new Label("Error: " + e.getMessage());
                    err.setStyle("-fx-text-fill:#e53e3e;-fx-font-size:13px;-fx-padding:20;");
                    leaderboardList.getChildren().add(err);
                });
            }
        });
        t.setDaemon(true); t.start();
    }

    private void loadMyRank() {
        int userId = UserSession.getInstance().getUserId();
        if (userId <= 0 || myRankSection == null) return;

        Thread t = new Thread(() -> {
            try {
                LeaderboardService.PlayerEntry me = service.getPlayerStats(userId);
                if (me == null) return;
                Platform.runLater(() -> {
                    myRankSection.getChildren().clear();

                    // Build the my-rank card programmatically
                    Label header = new Label("Your Ranking");
                    header.setStyle("-fx-text-fill:rgba(255,255,255,0.75);-fx-font-size:12px;-fx-font-weight:bold;-fx-padding:0 0 10 0;");

                    // Rank
                    VBox rankBox = new VBox(4);
                    rankBox.setAlignment(Pos.CENTER);
                    Label rankLbl = new Label("#" + me.rank);
                    rankLbl.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:white;");
                    Label rankSub = new Label("Your Rank");
                    rankSub.setStyle("-fx-text-fill:rgba(255,255,255,0.65);-fx-font-size:11px;");
                    rankBox.getChildren().addAll(rankLbl, rankSub);
                    HBox.setHgrow(rankBox, Priority.ALWAYS);

                    // Level
                    VBox levelBox = new VBox(4);
                    levelBox.setAlignment(Pos.CENTER);
                    Label levelLbl = new Label(me.levelName + " (Lv." + me.level + ")");
                    levelLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;");
                    ProgressBar pb = new ProgressBar(me.progressPct / 100.0);
                    pb.setPrefWidth(120);
                    pb.setStyle("-fx-accent:#f6d365;");
                    Label progLbl = new Label(me.progressPct + "% to next level");
                    progLbl.setStyle("-fx-text-fill:rgba(255,255,255,0.65);-fx-font-size:10px;");
                    levelBox.getChildren().addAll(levelLbl, pb, progLbl);
                    HBox.setHgrow(levelBox, Priority.ALWAYS);

                    // XP
                    VBox xpBox = new VBox(4);
                    xpBox.setAlignment(Pos.CENTER);
                    Label xpLbl = new Label(me.totalXp + " XP");
                    xpLbl.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:white;");
                    Label xpSub = new Label("Total XP");
                    xpSub.setStyle("-fx-text-fill:rgba(255,255,255,0.65);-fx-font-size:11px;");
                    xpBox.getChildren().addAll(xpLbl, xpSub);
                    HBox.setHgrow(xpBox, Priority.ALWAYS);

                    // Tokens
                    VBox tokBox = new VBox(4);
                    tokBox.setAlignment(Pos.CENTER);
                    Label tokLbl = new Label(me.totalTokens + " Tokens");
                    tokLbl.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#f6d365;");
                    Label tokSub = new Label("Tokens");
                    tokSub.setStyle("-fx-text-fill:rgba(255,255,255,0.65);-fx-font-size:11px;");
                    tokBox.getChildren().addAll(tokLbl, tokSub);
                    HBox.setHgrow(tokBox, Priority.ALWAYS);

                    HBox statsRow = new HBox(0, rankBox, levelBox, xpBox, tokBox);
                    statsRow.setAlignment(Pos.CENTER);

                    myRankSection.getChildren().addAll(header, statsRow);
                    myRankSection.setVisible(true);
                    myRankSection.setManaged(true);
                });
            } catch (Exception ignored) {}
        });
        t.setDaemon(true); t.start();
    }

    // ── Row builder ───────────────────────────────────────────────────────────
    private VBox buildRow(LeaderboardService.PlayerEntry e) {
        // Rank badge
        StackPane rankBadge = new StackPane();
        rankBadge.setPrefSize(44, 44); rankBadge.setMaxSize(44, 44);
        Circle circle = new Circle(22);
        Label rankLbl = new Label();

        switch (e.rank) {
            case 1 -> {
                circle.setFill(Color.web("#FFD700"));
                rankLbl.setText("\uD83C\uDFC6"); rankLbl.setStyle("-fx-font-size:20px;");
            }
            case 2 -> {
                circle.setFill(Color.web("#C0C0C0"));
                rankLbl.setText("\uD83E\uDD48"); rankLbl.setStyle("-fx-font-size:20px;");
            }
            case 3 -> {
                circle.setFill(Color.web("#CD7F32"));
                rankLbl.setText("\uD83E\uDD49"); rankLbl.setStyle("-fx-font-size:20px;");
            }
            default -> {
                circle.setFill(Color.web("#f0f2f8"));
                rankLbl.setText(String.valueOf(e.rank));
                rankLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#718096;");
            }
        }
        rankBadge.getChildren().addAll(circle, rankLbl);

        // Avatar circle with initials
        StackPane avatar = new StackPane();
        avatar.setPrefSize(44, 44); avatar.setMaxSize(44, 44);
        Circle avatarCircle = new Circle(22);
        avatarCircle.setFill(Color.web(avatarColor(e.rank)));
        Label initials = new Label(e.initials);
        initials.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;");
        avatar.getChildren().addAll(avatarCircle, initials);

        // Player info
        Label username = new Label(e.username);
        username.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        Label levelLbl = new Label(e.levelName + " · Lv." + e.level);
        levelLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#718096;");

        // Progress bar
        ProgressBar pb = new ProgressBar(e.progressPct / 100.0);
        pb.setPrefWidth(120);
        pb.setStyle("-fx-accent:" + levelColor(e.level) + ";-fx-pref-height:5;");

        VBox playerInfo = new VBox(3, username, levelLbl, pb);
        playerInfo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(playerInfo, Priority.ALWAYS);

        // XP stat
        VBox xpBox = statBox(formatNum(e.totalXp), "XP", "#3b4fd8");
        // Tokens stat
        VBox tokBox = statBox(formatNum(e.totalTokens), "Tokens", "#b7791f");

        HBox row = new HBox(14, rankBadge, avatar, playerInfo, xpBox, tokBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        // Highlight top 3
        String bg = e.rank <= 3
            ? (e.rank == 1 ? "linear-gradient(to right,#fffbeb,#fff8e1)"
             : e.rank == 2 ? "linear-gradient(to right,#f8f9ff,#f0f2f8)"
             : "linear-gradient(to right,#fff5f0,#fff0eb)")
            : "white";

        VBox wrapper = new VBox(row);
        wrapper.setStyle("-fx-background-color:" + bg + ";-fx-border-color:#e4e8f0;" +
                         "-fx-border-width:0 0 1 0;");
        wrapper.setOnMouseEntered(ev -> wrapper.setStyle(
            "-fx-background-color:#f5f7ff;-fx-border-color:#e4e8f0;-fx-border-width:0 0 1 0;"));
        wrapper.setOnMouseExited(ev -> wrapper.setStyle(
            "-fx-background-color:" + bg + ";-fx-border-color:#e4e8f0;-fx-border-width:0 0 1 0;"));
        return wrapper;
    }

    private VBox statBox(String value, String label, String color) {
        Label v = new Label(value); v.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
        Label l = new Label(label); l.setStyle("-fx-font-size:10px;-fx-text-fill:#a0aec0;");
        VBox b = new VBox(2, v, l); b.setAlignment(Pos.CENTER); b.setMinWidth(70);
        return b;
    }

    private String formatNum(int n) {
        if (n >= 1000) return String.format("%.1fk", n / 1000.0);
        return String.valueOf(n);
    }

    private String avatarColor(int rank) {
        String[] colors = {"#3b4fd8","#27ae60","#e53e3e","#d97706","#805ad5","#2b6cb0","#276749"};
        return colors[(rank - 1) % colors.length];
    }

    private String levelColor(int level) {
        if (level >= 15) return "#f6d365";
        if (level >= 10) return "#3b4fd8";
        if (level >= 5)  return "#27ae60";
        return "#a0aec0";
    }
}
