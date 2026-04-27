package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import models.gamification.Game;
import services.gamification.FavoriteGameService;
import services.gamification.GameService;
import utils.UserSession;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Game launcher hub — Full Games + Mini Games tabs with filter bar.
 * Uses FontAwesome 6 Solid (fa-solid-900.ttf) for icons.
 */
public class GameLauncherController {

    // FA6 Solid Unicode codepoints
    private static final String FA_PUZZLE      = "\uF12E"; // puzzle-piece
    private static final String FA_BRAIN       = "\uF5DC"; // brain
    private static final String FA_QUESTION    = "\uF059"; // circle-question
    private static final String FA_GAMEPAD     = "\uF11B"; // gamepad
    private static final String FA_BOLT        = "\uF0E7"; // bolt (energy)
    private static final String FA_COINS       = "\uF51E"; // coins
    private static final String FA_STAR        = "\uF005"; // star
    private static final String FA_PLAY        = "\uF144"; // circle-play
    private static final String FA_TROPHY      = "\uF091"; // trophy

    @FXML private TabPane          tabPane;
    @FXML private FlowPane         fullGamesPane;
    @FXML private FlowPane         miniGamesPane;
    @FXML private Label            lblFullCount;
    @FXML private Label            lblMiniCount;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private ComboBox<String> filterDifficulty;

    private final GameService gameService = new GameService();
    private final FavoriteGameService favService = new FavoriteGameService();
    private List<Game> allGames;
    private StackPane contentArea;

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }

    public void setInitialTab(int tabIndex) {
        if (tabPane != null && tabIndex >= 0 && tabIndex < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(tabIndex);
        }
    }

    @FXML
    public void initialize() {
        filterType.setItems(FXCollections.observableArrayList(
                "All Types", "PUZZLE", "MEMORY", "TRIVIA", "ARCADE"));
        filterType.setValue("All Types");

        filterDifficulty.setItems(FXCollections.observableArrayList(
                "All Levels", "EASY", "MEDIUM", "HARD"));
        filterDifficulty.setValue("All Levels");

        filterType.valueProperty().addListener((o, a, b) -> applyFilters());
        filterDifficulty.valueProperty().addListener((o, a, b) -> applyFilters());

        try {
            allGames = gameService.getAllGames().stream()
                    .filter(Game::isActive)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            allGames = List.of();
        }
        renderAll(allGames);
    }

    @FXML private void handleSearch() { applyFilters(); }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        filterType.setValue("All Types");
        filterDifficulty.setValue("All Levels");
        renderAll(allGames);
    }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = filterType.getValue();
        String diff = filterDifficulty.getValue();

        List<Game> filtered = allGames.stream().filter(g -> {
            boolean matchKw   = kw.isEmpty()
                    || g.getName().toLowerCase().contains(kw)
                    || g.getType().toLowerCase().contains(kw);
            boolean matchType = "All Types".equals(type)  || g.getType().equals(type);
            boolean matchDiff = "All Levels".equals(diff) || g.getDifficulty().equals(diff);
            return matchKw && matchType && matchDiff;
        }).collect(Collectors.toList());

        renderAll(filtered);
    }

    private void renderAll(List<Game> games) {
        List<Game> full = games.stream()
                .filter(g -> "FULL_GAME".equals(g.getCategory())).collect(Collectors.toList());
        List<Game> mini = games.stream()
                .filter(g -> "MINI_GAME".equals(g.getCategory())).collect(Collectors.toList());

        fullGamesPane.getChildren().clear();
        miniGamesPane.getChildren().clear();

        full.forEach(g -> fullGamesPane.getChildren().add(buildCard(g, false)));
        mini.forEach(g -> miniGamesPane.getChildren().add(buildCard(g, true)));

        lblFullCount.setText(full.size() + " game" + (full.size() == 1 ? "" : "s"));
        lblMiniCount.setText(mini.size() + " mini-game" + (mini.size() == 1 ? "" : "s"));
    }

    // ── Card ──────────────────────────────────────────────────────────────────
    private VBox buildCard(Game game, boolean isMini) {

        // Icon circle using FontAwesome
        Label iconLbl = faIcon(typeIcon(game.getType()), 22);
        iconLbl.setStyle(iconLbl.getStyle() + "-fx-text-fill: " + typeFg(game.getType()) + ";");
        StackPane iconCircle = new StackPane(iconLbl);
        iconCircle.setPrefSize(60, 60);
        iconCircle.setMaxSize(60, 60);
        iconCircle.setStyle("-fx-background-color: " + typeColor(game.getType())
                + "; -fx-background-radius: 50;");

        // Title
        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e;");
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(185);

        // Badges row
        Label typeBadge = textBadge(game.getType(),
                typeBadgeBg(game.getType()), typeBadgeFg(game.getType()));
        Label diffBadge = textBadge(game.getDifficulty(),
                diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge);
        badges.setAlignment(Pos.CENTER);

        // Separator
        Separator sep = new Separator();

        // Stats row
        HBox stats;
        if (isMini) {
            int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
            stats = statsRow(FA_BOLT,  "+" + ep,                    "Energy",
                             FA_COINS, String.valueOf(game.getTokenCost()), "Cost");
        } else {
            stats = statsRow(FA_COINS, "+" + game.getRewardTokens(), "Tokens",
                             FA_STAR,  "+" + game.getRewardXP(),     "XP");
        }
        stats.setStyle("-fx-background-color: #f8f9ff; -fx-background-radius: 8; -fx-padding: 8 6;");

        // Play button
        String btnBg    = isMini ? "#27ae60" : "#3b4fd8";
        String btnHover = isMini ? "#1e8449" : "#2d3fc7";
        Label playIcon = faIcon(FA_PLAY, 13);
        playIcon.setStyle(playIcon.getStyle() + "-fx-text-fill: white;");
        Label playTxt = new Label("  Play Now");
        playTxt.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        HBox btnContent = new HBox(4, playIcon, playTxt);
        btnContent.setAlignment(Pos.CENTER);

        Button btnPlay = new Button();
        btnPlay.setGraphic(btnContent);
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnPlay.setStyle("-fx-background-color: " + btnBg + ";"
                + "-fx-background-radius: 8; -fx-padding: 9 0; -fx-cursor: hand;");
        btnPlay.setOnMouseEntered(e -> btnPlay.setStyle(
                btnPlay.getStyle().replace(btnBg, btnHover)));
        btnPlay.setOnMouseExited(e -> btnPlay.setStyle(
                btnPlay.getStyle().replace(btnHover, btnBg)));
        btnPlay.setOnAction(e -> launchGame(game));

        // Favorite toggle button
        int userId = UserSession.getInstance().getUserId();
        final boolean[] isFav = {false};
        try { isFav[0] = userId > 0 && favService.isFavorite(userId, game.getId()); } catch (Exception ignored) {}
        Button btnFav = new Button(isFav[0] ? "\uF004" : "\uF08A");
        btnFav.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:14px;"
                + "-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:4 8;"
                + "-fx-text-fill:" + (isFav[0] ? "#e53e3e" : "#a0aec0") + ";");
        btnFav.setOnAction(e -> {
            if (userId <= 0) return;
            try {
                boolean nowFav = favService.toggle(userId, game.getId());
                isFav[0] = nowFav;
                btnFav.setText(nowFav ? "\uF004" : "\uF08A");
                btnFav.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:14px;"
                        + "-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:4 8;"
                        + "-fx-text-fill:" + (nowFav ? "#e53e3e" : "#a0aec0") + ";");
            } catch (Exception ex) { System.err.println("Favorite error: " + ex.getMessage()); }
        });

        HBox actionRow = new HBox(6, btnPlay, btnFav);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(btnPlay, Priority.ALWAYS);

        // Card
        VBox card = new VBox(10, iconCircle, title, badges, sep, stats, actionRow);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(18, 14, 18, 14));
        card.setPrefWidth(210);
        card.setStyle(
                "-fx-background-color: white;"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: #e4e8f0;"
                + "-fx-border-radius: 14;"
                + "-fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 3);");

        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("rgba(0,0,0,0.07)", "rgba(59,79,216,0.18)")
                .replace("#e4e8f0", "#c3c9f5")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("rgba(59,79,216,0.18)", "rgba(0,0,0,0.07)")
                .replace("#c3c9f5", "#e4e8f0")));

        return card;
    }

    // ── Stats row ─────────────────────────────────────────────────────────────
    private HBox statsRow(String icon1, String val1, String lbl1,
                          String icon2, String val2, String lbl2) {
        HBox row = new HBox(0, statCell(icon1, val1, lbl1), statCell(icon2, val2, lbl2));
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        return row;
    }

    private VBox statCell(String faCode, String value, String label) {
        Label ico = faIcon(faCode, 12);
        ico.setStyle(ico.getStyle() + "-fx-text-fill: #3b4fd8;");
        Label val = new Label(" " + value);
        val.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e;");
        HBox top = new HBox(2, ico, val);
        top.setAlignment(Pos.CENTER);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #a0aec0;");
        VBox b = new VBox(2, top, lbl);
        b.setAlignment(Pos.CENTER);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    // ── Launch ────────────────────────────────────────────────────────────────
    private void launchGame(Game game) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/gamification/game_play.fxml"));
            Parent view = loader.load();
            GamePlayController ctrl = loader.getController();
            ctrl.setGame(game);
            ctrl.setContentArea(contentArea);
            if (contentArea != null) contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Cannot load game: " + e.getMessage()).showAndWait();
            e.printStackTrace();
        }
    }

    // ── FA helper ─────────────────────────────────────────────────────────────
    private Label faIcon(String unicode, double size) {
        Label l = new Label(unicode);
        l.setStyle("-fx-font-family: 'Font Awesome 6 Free Solid'; -fx-font-size: " + size + "px;");
        return l;
    }

    private Label textBadge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";"
                + "-fx-background-radius: 20; -fx-padding: 3 10;"
                + "-fx-font-size: 10px; -fx-font-weight: bold;");
        return l;
    }

    // ── Type mappings ─────────────────────────────────────────────────────────
    private String typeIcon(String type) {
        return switch (type) {
            case "PUZZLE" -> FA_PUZZLE;
            case "MEMORY" -> FA_BRAIN;
            case "TRIVIA" -> FA_QUESTION;
            case "ARCADE" -> FA_GAMEPAD;
            default       -> FA_GAMEPAD;
        };
    }

    private String typeColor(String type) {
        return switch (type) {
            case "PUZZLE" -> "#fff8e1";
            case "MEMORY" -> "#f3e5f5";
            case "TRIVIA" -> "#e3f2fd";
            case "ARCADE" -> "#e8f5e9";
            default       -> "#f0f2f8";
        };
    }

    private String typeFg(String type) {
        return switch (type) {
            case "PUZZLE" -> "#b7791f";
            case "MEMORY" -> "#805ad5";
            case "TRIVIA" -> "#2b6cb0";
            case "ARCADE" -> "#276749";
            default       -> "#3b4fd8";
        };
    }

    private String typeBadgeBg(String type) { return typeColor(type); }
    private String typeBadgeFg(String type)  { return typeFg(type); }

    private String diffBg(String d) {
        return switch (d) {
            case "HARD"   -> "#fff5f5";
            case "MEDIUM" -> "#fffbeb";
            default       -> "#f0fff4";
        };
    }

    private String diffFg(String d) {
        return switch (d) {
            case "HARD"   -> "#e53e3e";
            case "MEDIUM" -> "#d97706";
            default       -> "#27ae60";
        };
    }
}
