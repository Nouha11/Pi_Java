package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GameController — displays games as interactive cards (FlowPane).
 * Handles READ, DELETE, toggle active, export CSV, and opens the form for CREATE/UPDATE.
 */
public class GameController {

    @FXML private FlowPane         cardsPane;
    @FXML private Label            statusLabel;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;

    private final GameService gameService = new GameService();
    private List<Game> allGames;

    @FXML
    public void initialize() {
        typeFilter.setItems(FXCollections.observableArrayList(
                "All", "PUZZLE", "MEMORY", "TRIVIA", "ARCADE"));
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        loadGames();
    }

    // ── READ: load all games and render cards ─────────────────────────────────
    private void loadGames() {
        try {
            allGames = gameService.getAllGames();
            renderCards(allGames);
        } catch (Exception e) {
            showStatus("Error loading games: " + e.getMessage(), true);
        }
    }

    // ── FILTER: search + type filter on in-memory list ────────────────────────
    @FXML private void handleSearch() { applyFilters(); }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = typeFilter.getValue();
        List<Game> filtered = allGames.stream().filter(g -> {
            boolean matchType = "All".equals(type) || g.getType().equals(type);
            boolean matchKw   = kw.isEmpty()
                    || g.getName().toLowerCase().contains(kw)
                    || g.getType().toLowerCase().contains(kw)
                    || g.getDifficulty().toLowerCase().contains(kw);
            return matchType && matchKw;
        }).collect(Collectors.toList());
        renderCards(filtered);
    }

    // ── Renders the FlowPane with one card per game ───────────────────────────
    private void renderCards(List<Game> games) {
        cardsPane.getChildren().clear();
        for (Game g : games) cardsPane.getChildren().add(buildCard(g));
        statusLabel.setText(games.size() + " game" + (games.size() == 1 ? "" : "s") + " found");
    }

    // ── Builds a single game card ─────────────────────────────────────────────
    private VBox buildCard(Game game) {
        // Type icon — plain ASCII symbols, no emoji encoding issues
        Label icon = new Label(typeSymbol(game.getType()));
        icon.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; " +
                      "-fx-background-color: " + typeColor(game.getType()) + "; " +
                      "-fx-background-radius: 50%; " +
                      "-fx-min-width: 64; -fx-min-height: 64; -fx-max-width: 64; -fx-max-height: 64; " +
                      "-fx-alignment: center; -fx-text-fill: " + typeIconColor(game.getType()) + ";");

        // Title — dark, readable
        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e; " +
                       "-fx-wrap-text: true; -fx-text-alignment: center;");
        title.setWrapText(true);
        title.setMaxWidth(200);

        // Badges
        Label typeBadge = badge(game.getType(), typeColor(game.getType()), typeIconColor(game.getType()));
        Label diffBadge = badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge);
        badges.setAlignment(Pos.CENTER);

        // Stats row
        HBox stats = new HBox(14,
                miniStat(String.valueOf(game.getTokenCost()), "Cost"),
                miniStat(String.valueOf(game.getRewardXP()),  "XP"),
                miniStat(String.valueOf(game.getRewardTokens()), "Tokens"));
        stats.setAlignment(Pos.CENTER);
        stats.setStyle("-fx-background-color: #f0f4ff; -fx-background-radius: 8; -fx-padding: 8 14;");

        // Active badge
        Label activeLbl = new Label(game.isActive() ? "Active" : "Inactive");
        activeLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 12; " +
                "-fx-background-radius: 20; -fx-border-radius: 20; " +
                (game.isActive()
                        ? "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-border-color: #86efac;"
                        : "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-border-color: #fca5a5;"));

        // Icon-only action buttons with tooltips
        Button viewBtn   = iconBtn("?",  "#dbeafe", "#1d4ed8", "View details");
        Button editBtn   = iconBtn("/",  "#dcfce7", "#15803d", "Edit");
        Button toggleBtn = iconBtn(game.isActive() ? "||" : ">", "#fef9c3", "#a16207", game.isActive() ? "Deactivate" : "Activate");
        Button deleteBtn = iconBtn("X",  "#fee2e2", "#dc2626", "Delete");

        viewBtn.setOnAction(e   -> showGameDetails(game));
        editBtn.setOnAction(e   -> openGameForm(game));
        toggleBtn.setOnAction(e -> handleToggleActive(game));
        deleteBtn.setOnAction(e -> handleDelete(game));

        HBox actions = new HBox(8, viewBtn, editBtn, toggleBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(10, icon, title, badges, stats, activeLbl, actions);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; " +
                      "-fx-border-color: #e2e8f0; -fx-border-radius: 14; -fx-border-width: 1; " +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 3);");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(220);
        return card;
    }

    // ── CREATE: open form in Add mode ─────────────────────────────────────────
    @FXML private void handleAddGame() { openGameForm(null); }

    // ── TOGGLE active/inactive ────────────────────────────────────────────────
    private void handleToggleActive(Game g) {
        try {
            g.setActive(!g.isActive());
            gameService.updateGame(g);
            loadGames();
            showStatus(g.getName() + " is now " + (g.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

    // ── DELETE with confirmation ──────────────────────────────────────────────
    private void handleDelete(Game g) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + g.getName() + "'? This removes all reward links too.",
                ButtonType.YES, ButtonType.NO);
        a.setTitle("Confirm Delete");
        a.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try { gameService.deleteGame(g.getId()); loadGames(); showStatus("Deleted.", false); }
                catch (Exception e) { showStatus("Delete error: " + e.getMessage(), true); }
            }
        });
    }

    // ── VIEW DETAILS dialog ───────────────────────────────────────────────────
    private void showGameDetails(Game g) {
        try {
            List<Reward> rewards = gameService.getRewardsForGame(g.getId());
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(g.getName());
            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #f0f2f8;");
            root.setPrefWidth(540);

            VBox header = new VBox(6);
            header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 22 28 18 28;");
            Label title = new Label("[" + g.getType() + "]  " + g.getName());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
            Label sub = new Label(g.getType() + "  \u00b7  " + g.getDifficulty() + "  \u00b7  " + g.getCategory());
            sub.setStyle("-fx-text-fill: rgba(199,210,254,0.85); -fx-font-size: 13px;");
            header.getChildren().addAll(title, sub);

            HBox stats = new HBox(10);
            stats.setStyle("-fx-background-color: white; -fx-padding: 14 28; -fx-border-color: #e4e8f0; -fx-border-width: 0 0 1 0;");
            stats.getChildren().addAll(
                    chip("\uD83E\uDE99 " + g.getTokenCost(),    "Token Cost"),
                    chip("\uD83C\uDF81 " + g.getRewardTokens(), "Reward Tokens"),
                    chip("\u2B50 " + g.getRewardXP(),           "Reward XP"),
                    chip("\u26A1 " + (g.getEnergyPoints() != null ? g.getEnergyPoints() : "\u2014"), "Energy"),
                    chip(g.isActive() ? "Active" : "Inactive", "Status"));

            VBox descBox = new VBox(6);
            descBox.setStyle("-fx-background-color: white; -fx-padding: 16 28 16 28;");
            Label descLbl = new Label("Description");
            descLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label desc = new Label(g.getDescription() != null && !g.getDescription().isBlank()
                    ? g.getDescription() : "No description provided.");
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
            descBox.getChildren().addAll(descLbl, desc);

            VBox rewardsBox = new VBox(8);
            rewardsBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 28 22 28;");
            Label rwLbl = new Label("\uD83C\uDFC6  Linked Rewards  (" + rewards.size() + ")");
            rwLbl.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            rewardsBox.getChildren().add(rwLbl);
            if (rewards.isEmpty()) {
                Label none = new Label("No rewards linked.");
                none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                rewardsBox.getChildren().add(none);
            } else {
                for (Reward r : rewards) {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: white; -fx-padding: 10 14; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    Label rName = new Label(r.getName());
                    rName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e2a5e; -fx-font-size: 13px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label rVal = new Label("+" + r.getValue() + " pts");
                    rVal.setStyle("-fx-text-fill: #3b4fd8; -fx-font-weight: bold; -fx-font-size: 12px; -fx-background-color: #eef0fd; -fx-background-radius: 6; -fx-padding: 2 8;");
                    row.getChildren().addAll(rName, sp, rVal);
                    rewardsBox.getChildren().add(row);
                }
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
               .setStyle("-fx-background-color: #3b4fd8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 7 20;");
            dlg.showAndWait();
        } catch (Exception e) { showStatus("Error: " + e.getMessage(), true); }
    }

    // ── EXPORT CSV ────────────────────────────────────────────────────────────
    @FXML
    private void handleExportCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Games to CSV");
        fc.setInitialFileName("games_export.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(cardsPane.getScene().getWindow());
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Name,Type,Difficulty,Category,TokenCost,RewardTokens,RewardXP,Active");
            for (Game g : allGames) {
                pw.printf("\"%s\",%s,%s,%s,%d,%d,%d,%s%n",
                        g.getName(), g.getType(), g.getDifficulty(), g.getCategory(),
                        g.getTokenCost(), g.getRewardTokens(), g.getRewardXP(), g.isActive());
            }
            showStatus("Exported " + allGames.size() + " games.", false);
        } catch (Exception e) { showStatus("Export error: " + e.getMessage(), true); }
    }

    // ── Open game_form.fxml ───────────────────────────────────────────────────
    private void openGameForm(Game gameToEdit) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/gamification/game_form.fxml"));
            javafx.scene.Parent root = loader.load();
            GameFormController ctrl = loader.getController();
            if (gameToEdit != null) ctrl.setGameToEdit(gameToEdit);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle(gameToEdit == null ? "Add Game" : "Edit Game");
            stage.showAndWait();
            loadGames();
        } catch (Exception e) { showStatus("Error opening form: " + e.getMessage(), true); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Button cardBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-font-size: 11px; -fx-font-weight: bold; " +
                   "-fx-padding: 5 10; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
        return b;
    }

    private Button iconBtn(String symbol, String bg, String fg, String tip) {
        Button b = new Button(symbol);
        b.setTooltip(new Tooltip(tip));
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-font-size: 14px; -fx-font-weight: bold; " +
                   "-fx-min-width: 34; -fx-min-height: 34; -fx-max-width: 34; -fx-max-height: 34; " +
                   "-fx-background-radius: 8; -fx-cursor: hand;");
        return b;
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-border-color: " + fg + "; -fx-border-width: 1; " +
                   "-fx-border-radius: 20; -fx-background-radius: 20; " +
                   "-fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private VBox miniStat(String value, String label) {
        VBox b = new VBox(2); b.setAlignment(Pos.CENTER);
        Label v = new Label(value); v.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label l = new Label(label); l.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        b.getChildren().addAll(v, l);
        return b;
    }

    private VBox chip(String value, String label) {
        VBox b = new VBox(2); b.setAlignment(Pos.CENTER);
        b.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1; " +
                   "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 14; -fx-min-width: 80;");
        Label v = new Label(value); v.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        Label l = new Label(label); l.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        b.getChildren().addAll(v, l);
        return b;
    }

    private String typeSymbol(String type) {
        return switch (type) { case "PUZZLE" -> "#"; case "MEMORY" -> "M"; case "TRIVIA" -> "?"; case "ARCADE" -> "A"; default -> "G"; };
    }

    private String typeColor(String type) {
        return switch (type) { case "PUZZLE" -> "#fef3c7"; case "MEMORY" -> "#ede9fe"; case "TRIVIA" -> "#dbeafe"; case "ARCADE" -> "#dcfce7"; default -> "#f1f5f9"; };
    }

    private String typeIconColor(String type) {
        return switch (type) { case "PUZZLE" -> "#d97706"; case "MEMORY" -> "#7c3aed"; case "TRIVIA" -> "#2563eb"; case "ARCADE" -> "#16a34a"; default -> "#475569"; };
    }

    private String diffBg(String d) {
        return switch (d) { case "HARD" -> "#fee2e2"; case "MEDIUM" -> "#fef9c3"; default -> "#dcfce7"; };
    }

    private String diffFg(String d) {
        return switch (d) { case "HARD" -> "#dc2626"; case "MEDIUM" -> "#a16207"; default -> "#15803d"; };
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e53e3e;" : "-fx-text-fill: #27ae60;");
    }
}
