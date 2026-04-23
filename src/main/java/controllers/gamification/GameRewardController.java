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
import javafx.stage.FileChooser;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;
import services.gamification.RewardService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GameRewardController — displays rewards as interactive cards (FlowPane).
 * Handles READ, DELETE, toggle active, export CSV, and opens the form for CREATE/UPDATE.
 */
public class GameRewardController {

    @FXML private FlowPane         cardsPane;
    @FXML private Label            statusLabel;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;

    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();
    private List<Reward> allRewards;

    @FXML
    public void initialize() {
        typeFilter.setItems(FXCollections.observableArrayList(
                "All", "BADGE", "ACHIEVEMENT", "BONUS_XP", "BONUS_TOKENS"));
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        loadRewards();
    }

    // ── READ ──────────────────────────────────────────────────────────────────
    private void loadRewards() {
        try {
            allRewards = rewardService.getAllRewards();
            renderCards(allRewards);
        } catch (Exception e) {
            showStatus("Error loading rewards: " + e.getMessage(), true);
        }
    }

    // ── FILTER ────────────────────────────────────────────────────────────────
    @FXML private void handleSearch() { applyFilters(); }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = typeFilter.getValue();
        List<Reward> filtered = allRewards.stream().filter(r -> {
            boolean matchType = "All".equals(type) || r.getType().equals(type);
            boolean matchKw   = kw.isEmpty()
                    || r.getName().toLowerCase().contains(kw)
                    || r.getType().toLowerCase().contains(kw);
            return matchType && matchKw;
        }).collect(Collectors.toList());
        renderCards(filtered);
    }

    // ── Renders the FlowPane with one card per reward ─────────────────────────
    private void renderCards(List<Reward> rewards) {
        cardsPane.getChildren().clear();
        for (Reward r : rewards) cardsPane.getChildren().add(buildCard(r));
        statusLabel.setText(rewards.size() + " reward" + (rewards.size() == 1 ? "" : "s") + " found");
    }

    // ── Builds a single reward card ───────────────────────────────────────────
    private VBox buildCard(Reward reward) {
        // Icon image or letter fallback — no emoji encoding
        StackPane iconPane = new StackPane();
        iconPane.setStyle("-fx-background-color: " + typeBg(reward.getType()) + "; " +
                          "-fx-background-radius: 50%; -fx-min-width: 64; -fx-min-height: 64; " +
                          "-fx-max-width: 64; -fx-max-height: 64;");
        ImageView iv = loadIcon(reward.getIcon(), 48);
        if (iv != null) {
            iconPane.getChildren().add(iv);
        } else {
            Label letter = new Label(typeSymbol(reward.getType()));
            letter.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + typeFg(reward.getType()) + ";");
            iconPane.getChildren().add(letter);
        }

        // Name — dark, readable
        Label name = new Label(reward.getName());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e; " +
                      "-fx-wrap-text: true; -fx-text-alignment: center;");
        name.setWrapText(true);
        name.setMaxWidth(200);

        // Type badge
        Label typeBadge = badge(reward.getType(), typeBg(reward.getType()), typeFg(reward.getType()));

        // Value
        Label valueLbl = new Label("+" + reward.getValue() + " pts");
        valueLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1d4ed8;");

        // Active badge
        Label activeLbl = new Label(reward.isActive() ? "Active" : "Inactive");
        activeLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 12; " +
                "-fx-background-radius: 20; -fx-border-radius: 20; " +
                (reward.isActive()
                        ? "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-border-color: #86efac;"
                        : "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-border-color: #fca5a5;"));

        // Icon-only buttons with tooltips
        Button viewBtn   = iconBtn("i",  "#dbeafe", "#1d4ed8", "View details");
        Button editBtn   = iconBtn("E",  "#dcfce7", "#15803d", "Edit");
        Button toggleBtn = iconBtn(reward.isActive() ? "||" : ">", "#fef9c3", "#a16207", reward.isActive() ? "Deactivate" : "Activate");
        Button deleteBtn = iconBtn("X",  "#fee2e2", "#dc2626", "Delete");

        viewBtn.setOnAction(e   -> showRewardDetails(reward));
        editBtn.setOnAction(e   -> openRewardForm(reward));
        toggleBtn.setOnAction(e -> handleToggleActive(reward));
        deleteBtn.setOnAction(e -> handleDelete(reward));

        HBox actions = new HBox(8, viewBtn, editBtn, toggleBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(10, iconPane, name, typeBadge, valueLbl, activeLbl, actions);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; " +
                      "-fx-border-color: #e2e8f0; -fx-border-radius: 14; -fx-border-width: 1; " +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 3);");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(220);
        return card;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────
    @FXML private void handleAddReward() { openRewardForm(null); }

    // ── TOGGLE ────────────────────────────────────────────────────────────────
    private void handleToggleActive(Reward r) {
        try {
            r.setActive(!r.isActive());
            rewardService.updateReward(r);
            loadRewards();
            showStatus(r.getName() + " is now " + (r.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    private void handleDelete(Reward r) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + r.getName() + "'? This removes it from all linked games.",
                ButtonType.YES, ButtonType.NO);
        a.setTitle("Confirm Delete");
        a.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try { rewardService.deleteReward(r.getId()); loadRewards(); showStatus("Deleted.", false); }
                catch (Exception e) { showStatus("Delete error: " + e.getMessage(), true); }
            }
        });
    }

    // ── VIEW DETAILS dialog ───────────────────────────────────────────────────
    private void showRewardDetails(Reward r) {
        try {
            List<Game> linkedGames = gameService.getGamesForReward(r.getId());
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(r.getName());
            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #f0f2f8;");
            root.setPrefWidth(520);

            HBox header = new HBox(18);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 22 28 18 28;");
            ImageView hIv = loadIcon(r.getIcon(), 52);
            if (hIv != null) header.getChildren().add(hIv);
            else { Label e = new Label(typeSymbol(r.getType())); e.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + typeFg(r.getType()) + ";"); header.getChildren().add(e); }
            VBox hText = new VBox(4);
            Label title = new Label(r.getName());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
            Label sub = new Label(r.getType() + "  \u00b7  +" + r.getValue() + " pts"
                    + (r.getRequiredLevel() != null ? "  \u00b7  Level " + r.getRequiredLevel() : ""));
            sub.setStyle("-fx-text-fill: rgba(199,210,254,0.85); -fx-font-size: 13px;");
            hText.getChildren().addAll(title, sub);
            header.getChildren().add(hText);

            HBox stats = new HBox(10);
            stats.setStyle("-fx-background-color: white; -fx-padding: 14 28; -fx-border-color: #e4e8f0; -fx-border-width: 0 0 1 0;");
            stats.getChildren().addAll(
                    chip(r.getType(), "Type"),
                    chip("+" + r.getValue() + " pts", "Value"),
                    chip(r.getRequiredLevel() != null ? "Lv " + r.getRequiredLevel() : "\u2014", "Min Level"),
                    chip(r.isActive() ? "Active" : "Inactive", "Status"));

            VBox infoBox = new VBox(12);
            infoBox.setStyle("-fx-background-color: white; -fx-padding: 16 28 16 28;");
            if (r.getRequirement() != null && !r.getRequirement().isBlank()) {
                Label rl = new Label("How to earn"); rl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label rv = new Label(r.getRequirement()); rv.setWrapText(true); rv.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(rl, rv);
            }
            if (r.getDescription() != null && !r.getDescription().isBlank()) {
                Label dl = new Label("Description"); dl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label dv = new Label(r.getDescription()); dv.setWrapText(true); dv.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(dl, dv);
            }
            if (infoBox.getChildren().isEmpty()) {
                Label none = new Label("No additional details."); none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                infoBox.getChildren().add(none);
            }

            VBox gamesBox = new VBox(8);
            gamesBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 28 22 28;");
            Label gLbl = new Label("\uD83C\uDFAE  Available in Games  (" + linkedGames.size() + ")");
            gLbl.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            gamesBox.getChildren().add(gLbl);
            if (linkedGames.isEmpty()) {
                Label none = new Label("Not linked to any game yet."); none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                gamesBox.getChildren().add(none);
            } else {
                for (Game g : linkedGames) {
                    HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: white; -fx-padding: 10 14; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    Label gName = new Label(g.getName()); gName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e2a5e; -fx-font-size: 13px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label gMeta = new Label(g.getType() + "  \u00b7  " + g.getDifficulty()); gMeta.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");
                    row.getChildren().addAll(gName, sp, gMeta);
                    gamesBox.getChildren().add(row);
                }
            }
            root.getChildren().addAll(header, stats, infoBox, gamesBox);
            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background-color: #f0f2f8; -fx-background: #f0f2f8;");
            scroll.setPrefHeight(460);
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
        fc.setTitle("Export Rewards to CSV");
        fc.setInitialFileName("rewards_export.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(cardsPane.getScene().getWindow());
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Name,Type,Value,Requirement,RequiredLevel,Active");
            for (Reward r : allRewards) {
                pw.printf("\"%s\",%s,%d,\"%s\",%s,%s%n",
                        r.getName(), r.getType(), r.getValue(),
                        r.getRequirement() != null ? r.getRequirement() : "",
                        r.getRequiredLevel() != null ? r.getRequiredLevel() : "",
                        r.isActive());
            }
            showStatus("Exported " + allRewards.size() + " rewards.", false);
        } catch (Exception e) { showStatus("Export error: " + e.getMessage(), true); }
    }

    // ── Open reward_form.fxml ─────────────────────────────────────────────────
    private void openRewardForm(Reward rewardToEdit) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/gamification/reward_form.fxml"));
            javafx.scene.Parent root = loader.load();
            RewardFormController ctrl = loader.getController();
            if (rewardToEdit != null) ctrl.setRewardToEdit(rewardToEdit);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle(rewardToEdit == null ? "Add Reward" : "Edit Reward");
            stage.showAndWait();
            loadRewards();
        } catch (Exception e) { showStatus("Error opening form: " + e.getMessage(), true); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ImageView loadIcon(String path, double size) {
        if (path == null || path.isBlank()) return null;
        try {
            var stream = getClass().getResourceAsStream("/images/rewards/" + path);
            if (stream != null) { ImageView iv = new ImageView(new Image(stream, size, size, true, true)); iv.setFitWidth(size); iv.setFitHeight(size); return iv; }
            File f = new File(path);
            if (f.exists()) { ImageView iv = new ImageView(new Image(f.toURI().toString(), size, size, true, true)); iv.setFitWidth(size); iv.setFitHeight(size); return iv; }
        } catch (Exception ignored) {}
        return null;
    }

    private Button cardBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-font-size: 11px; -fx-font-weight: bold; " +
                   "-fx-padding: 5 10; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
        return b;
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-border-color: " + fg + "44; -fx-border-radius: 20; -fx-background-radius: 20; " +
                   "-fx-padding: 3 12; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private VBox chip(String value, String label) {
        VBox b = new VBox(2); b.setAlignment(Pos.CENTER);
        b.setStyle("-fx-background-color: #f8f9ff; -fx-border-color: #e4e8f0; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 14; -fx-min-width: 80;");
        Label v = new Label(value); v.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e;");
        Label l = new Label(label); l.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        b.getChildren().addAll(v, l);
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

    private String typeSymbol(String type) {
        return switch (type) { case "BADGE" -> "B"; case "ACHIEVEMENT" -> "A"; case "BONUS_XP" -> "XP"; case "BONUS_TOKENS" -> "T"; default -> "R"; };
    }

    private String typeBg(String type) {
        return switch (type) { case "BADGE" -> "#fef3c7"; case "ACHIEVEMENT" -> "#ede9fe"; case "BONUS_XP" -> "#dbeafe"; case "BONUS_TOKENS" -> "#dcfce7"; default -> "#f1f5f9"; };
    }

    private String typeFg(String type) {
        return switch (type) { case "BADGE" -> "#d97706"; case "ACHIEVEMENT" -> "#7c3aed"; case "BONUS_XP" -> "#2563eb"; case "BONUS_TOKENS" -> "#16a34a"; default -> "#475569"; };
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e53e3e;" : "-fx-text-fill: #27ae60;");
    }
}
