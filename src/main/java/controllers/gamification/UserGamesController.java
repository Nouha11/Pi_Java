package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class UserGamesController {

    @FXML private FlowPane         cardsPane;
    @FXML private Label            lblStatus;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private ComboBox<String> filterDifficulty;

    private final GameService gameService = new GameService();
    private List<Game> allGames;

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
                    .filter(Game::isActive).collect(Collectors.toList());
        } catch (Exception e) { allGames = List.of(); }
        renderCards(allGames);
    }

    @FXML private void handleSearch() { applyFilters(); }

    private void applyFilters() {
        String kw = searchField.getText().trim().toLowerCase();
        String type = filterType.getValue();
        String diff = filterDifficulty.getValue();
        List<Game> filtered = allGames.stream().filter(g -> {
            boolean matchKw   = kw.isEmpty() || g.getName().toLowerCase().contains(kw);
            boolean matchType = "All Types".equals(type) || g.getType().equals(type);
            boolean matchDiff = "All Levels".equals(diff) || g.getDifficulty().equals(diff);
            return matchKw && matchType && matchDiff;
        }).collect(Collectors.toList());
        renderCards(filtered);
    }

    private void renderCards(List<Game> games) {
        cardsPane.getChildren().clear();
        for (Game g : games) cardsPane.getChildren().add(buildCard(g));
        lblStatus.setText(games.size() + " game" + (games.size() == 1 ? "" : "s") + " available");
    }

    private VBox buildCard(Game game) {
        StackPane icon = faCircle(typeIcon(game.getType()), 22, typeGradient(game.getType()), "white");

        Label title = new Label(game.getName());
        title.getStyleClass().add("quiz-card-title");
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setMaxWidth(210);

        Label typeBadge = badge(game.getType(), "#eef0fd", "#3b4fd8");
        Label diffBadge = badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge);
        badges.setAlignment(Pos.CENTER);

        VBox tokens = miniStat("\uF51E", game.getTokenCost() + "", "Cost");
        VBox xp     = miniStat("\uF005", game.getRewardXP() + "", "XP");
        VBox rTok   = miniStat("\uF06B", game.getRewardTokens() + "", "Reward");
        HBox stats = new HBox(10, tokens, xp, rTok);
        stats.setAlignment(Pos.CENTER);
        stats.setStyle("-fx-background-color: #f8f9ff; -fx-background-radius: 8; -fx-padding: 8 12;");

        Button btnView = new Button("\uF06E  View Details");
        btnView.getStyleClass().add("btn-card-edit");
        btnView.setOnAction(e -> showGameDetails(game));

        VBox card = new VBox(12, icon, title, badges, stats, btnView);
        card.getStyleClass().add("quiz-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(248);
        return card;
    }

    private void showGameDetails(Game game) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(game.getName());
        VBox root = new VBox(0);
        root.setPrefWidth(500);
        root.setStyle("-fx-background-color: #f0f2f8;");

        VBox header = new VBox(4);
        header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 20 24 16 24;");
        Label title = new Label(game.getName());
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label sub = new Label(game.getType() + "  \u00B7  " + game.getDifficulty() + "  \u00B7  " + game.getCategory());
        sub.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        header.getChildren().addAll(title, sub);

        HBox stats = new HBox(0);
        stats.setStyle("-fx-background-color: white; -fx-padding: 14 24; -fx-border-color: #e4e8f0; -fx-border-width: 0 0 1 0;");
        stats.getChildren().addAll(
                statBox("Token Cost",    String.valueOf(game.getTokenCost())),
                statBox("Reward Tokens", String.valueOf(game.getRewardTokens())),
                statBox("Reward XP",     String.valueOf(game.getRewardXP())),
                statBox("Energy",        game.getEnergyPoints() != null ? String.valueOf(game.getEnergyPoints()) : "\u2014")
        );

        VBox descBox = new VBox(6);
        descBox.setStyle("-fx-background-color: white; -fx-padding: 16 24 16 24;");
        Label descTitle = new Label("Description");
        descTitle.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label desc = new Label(game.getDescription() != null && !game.getDescription().isBlank()
                ? game.getDescription() : "No description provided.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");
        descBox.getChildren().addAll(descTitle, desc);

        VBox rewardsBox = new VBox(8);
        rewardsBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 24 20 24;");
        try {
            List<Reward> rewards = gameService.getRewardsForGame(game.getId());
            StackPane trophyIcon = faCircle("\uF091", 14, "linear-gradient(to bottom right, #f6d365, #fda085)", "white");
            trophyIcon.setPrefSize(28, 28); trophyIcon.setMaxSize(28, 28);
            Label rwTitle = new Label("Linked Rewards  (" + rewards.size() + ")");
            rwTitle.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            HBox rwHeader = new HBox(8, trophyIcon, rwTitle);
            rwHeader.setAlignment(Pos.CENTER_LEFT);
            rewardsBox.getChildren().add(rwHeader);
            if (rewards.isEmpty()) {
                Label none = new Label("No rewards linked to this game.");
                none.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");
                rewardsBox.getChildren().add(none);
            } else {
                for (Reward r : rewards) {
                    HBox chip = new HBox(10);
                    chip.setAlignment(Pos.CENTER_LEFT);
                    chip.setStyle("-fx-background-color: white; -fx-padding: 10 14; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    ImageView iv = loadRewardIcon(r.getIcon(), 36);
                    if (iv != null) chip.getChildren().add(iv);
                    else {
                        StackPane ico = faCircle("\uF5A2", 18, "linear-gradient(to bottom right, #f6d365, #fda085)", "white");
                        ico.setPrefSize(36, 36); ico.setMaxSize(36, 36);
                        chip.getChildren().add(ico);
                    }
                    VBox info = new VBox(2);
                    Label rName = new Label(r.getName());
                    rName.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748; -fx-font-size: 13px;");
                    Label rType = new Label(r.getType() + "  \u00B7  +" + r.getValue() + " pts");
                    rType.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");
                    info.getChildren().addAll(rName, rType);
                    HBox.setHgrow(info, Priority.ALWAYS);
                    chip.getChildren().add(info);
                    rewardsBox.getChildren().add(chip);
                }
            }
        } catch (Exception e) {
            rewardsBox.getChildren().add(new Label("Could not load rewards."));
        }

        root.getChildren().addAll(header, stats, descBox, rewardsBox);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f0f2f8; -fx-background: #f0f2f8;");
        scroll.setPrefHeight(480);
        dlg.getDialogPane().setContent(scroll);
        dlg.getDialogPane().setStyle("-fx-background-color: #f0f2f8; -fx-padding: 0;");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().lookupButton(ButtonType.CLOSE)
           .setStyle("-fx-background-color: #3b4fd8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 7 18;");
        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StackPane faCircle(String unicode, double iconSize, String bgGradient, String iconColor) {
        Label ico = new Label(unicode);
        ico.setStyle("-fx-font-family: 'Font Awesome 5 Free'; -fx-font-weight: 900; -fx-font-size: " + iconSize + "px; -fx-text-fill: " + iconColor + ";");
        StackPane sp = new StackPane(ico);
        sp.setPrefSize(56, 56); sp.setMaxSize(56, 56);
        sp.setStyle("-fx-background-color: " + bgGradient + "; -fx-background-radius: 50;");
        return sp;
    }

    private ImageView loadRewardIcon(String path, double size) {
        if (path == null || path.isBlank()) return null;
        try {
            var stream = getClass().getResourceAsStream("/images/rewards/" + path);
            if (stream != null) {
                ImageView iv = new ImageView(new Image(stream, size, size, true, true));
                iv.setFitWidth(size); iv.setFitHeight(size); return iv;
            }
            File f = new File(path);
            if (f.exists()) {
                ImageView iv = new ImageView(new Image(f.toURI().toString(), size, size, true, true));
                iv.setFitWidth(size); iv.setFitHeight(size); return iv;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private VBox statBox(String label, String value) {
        VBox b = new VBox(2); b.setStyle("-fx-padding: 0 20 0 0;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #718096; -fx-font-size: 10px;");
        Label val = new Label(value); val.setStyle("-fx-text-fill: #1e2a5e; -fx-font-size: 16px; -fx-font-weight: bold;");
        b.getChildren().addAll(lbl, val); return b;
    }

    private VBox miniStat(String faCode, String value, String label) {
        VBox b = new VBox(1); b.setAlignment(Pos.CENTER);
        Label ico = new Label(faCode);
        ico.setStyle("-fx-font-family: 'Font Awesome 5 Free'; -fx-font-weight: 900; -fx-font-size: 11px; -fx-text-fill: #3b4fd8;");
        Label v = new Label(value); v.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2d3748;");
        HBox top = new HBox(3, ico, v); top.setAlignment(Pos.CENTER);
        Label l = new Label(label); l.setStyle("-fx-font-size: 10px; -fx-text-fill: #a0aec0;");
        b.getChildren().addAll(top, l); return b;
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-border-color: " + fg + "33; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private String typeIcon(String type) {
        return switch (type) {
            case "PUZZLE" -> "\uF12E";
            case "MEMORY" -> "\uF5DC";
            case "TRIVIA" -> "\uF059";
            case "ARCADE" -> "\uF11B";
            default       -> "\uF11B";
        };
    }

    private String typeGradient(String type) {
        return switch (type) {
            case "PUZZLE" -> "linear-gradient(to bottom right, #f6d365, #fda085)";
            case "MEMORY" -> "linear-gradient(to bottom right, #a18cd1, #fbc2eb)";
            case "TRIVIA" -> "linear-gradient(to bottom right, #4facfe, #00f2fe)";
            case "ARCADE" -> "linear-gradient(to bottom right, #43e97b, #38f9d7)";
            default       -> "linear-gradient(to bottom right, #667eea, #764ba2)";
        };
    }

    private String diffBg(String d) {
        return switch (d) { case "HARD" -> "#fff5f5"; case "MEDIUM" -> "#fffbeb"; default -> "#f0fff4"; };
    }
    private String diffFg(String d) {
        return switch (d) { case "HARD" -> "#e53e3e"; case "MEDIUM" -> "#d97706"; default -> "#27ae60"; };
    }
}
