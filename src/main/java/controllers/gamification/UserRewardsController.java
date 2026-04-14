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
import services.gamification.RewardService;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class UserRewardsController {

    @FXML private FlowPane         cardsPane;
    @FXML private Label            lblStatus;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> filterType;

    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();
    private List<Reward> allRewards;

    @FXML
    public void initialize() {
        filterType.setItems(FXCollections.observableArrayList(
                "All Types", "BADGE", "ACHIEVEMENT", "BONUS_XP", "BONUS_TOKENS"));
        filterType.setValue("All Types");
        filterType.valueProperty().addListener((o, a, b) -> applyFilters());

        try {
            allRewards = rewardService.getAllRewards().stream()
                    .filter(Reward::isActive).collect(Collectors.toList());
        } catch (Exception e) {
            allRewards = List.of();
        }
        renderCards(allRewards);
    }

    @FXML
    private void handleSearch() { applyFilters(); }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = filterType.getValue();

        List<Reward> filtered = allRewards.stream().filter(r -> {
            boolean matchKw   = kw.isEmpty() || r.getName().toLowerCase().contains(kw);
            boolean matchType = "All Types".equals(type) || r.getType().equals(type);
            return matchKw && matchType;
        }).collect(Collectors.toList());

        renderCards(filtered);
    }

    private void renderCards(List<Reward> rewards) {
        cardsPane.getChildren().clear();
        for (Reward r : rewards) cardsPane.getChildren().add(buildCard(r));
        lblStatus.setText(rewards.size() + " reward" + (rewards.size() == 1 ? "" : "s") + " available");
    }

    private VBox buildCard(Reward reward) {
        // ── Icon image or fallback emoji ──
        ImageView iv = loadIcon(reward.getIcon(), 56);
        StackPane iconPane = new StackPane();
        iconPane.setStyle("-fx-background-color: " + typeBg(reward.getType()) + "; " +
                          "-fx-background-radius: 50%; -fx-min-width: 64; -fx-min-height: 64; " +
                          "-fx-max-width: 64; -fx-max-height: 64;");
        if (iv != null) {
            iconPane.getChildren().add(iv);
        } else {
            Label emoji = new Label(typeEmoji(reward.getType()));
            emoji.setStyle("-fx-font-size: 26px;");
            iconPane.getChildren().add(emoji);
        }

        // ── Name ──
        Label name = new Label(reward.getName());
        name.getStyleClass().add("quiz-card-title");
        name.setWrapText(true);
        name.setTextAlignment(TextAlignment.CENTER);
        name.setMaxWidth(210);

        // ── Type badge ──
        Label typeBadge = badge(reward.getType(), typeBg(reward.getType()), typeFg(reward.getType()));

        // ── Value ──
        Label value = new Label("+" + reward.getValue() + " pts");
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #3b4fd8;");

        // ── Requirement snippet ──
        if (reward.getRequirement() != null && !reward.getRequirement().isBlank()) {
            Label req = new Label(reward.getRequirement());
            req.getStyleClass().add("quiz-card-desc");
            req.setWrapText(true);
            req.setTextAlignment(TextAlignment.CENTER);
            req.setMaxWidth(210);
        }

        // ── View button ──
        Button btnView = new Button("👁  View Details");
        btnView.getStyleClass().add("btn-card-edit");
        btnView.setOnAction(e -> showRewardDetails(reward));

        VBox card = new VBox(10, iconPane, name, typeBadge, value, btnView);
        card.getStyleClass().add("quiz-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(240);
        return card;
    }

    private void showRewardDetails(Reward reward) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(reward.getName());

        VBox root = new VBox(0);
        root.setPrefWidth(480);
        root.setStyle("-fx-background-color: #f0f2f8;");

        // Header with icon
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 20 24;");

        ImageView hIv = loadIcon(reward.getIcon(), 52);
        StackPane hIcon = new StackPane();
        hIcon.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 50%; " +
                       "-fx-min-width: 60; -fx-min-height: 60; -fx-max-width: 60; -fx-max-height: 60;");
        if (hIv != null) hIcon.getChildren().add(hIv);
        else {
            Label e = new Label(typeEmoji(reward.getType()));
            e.setStyle("-fx-font-size: 28px;");
            hIcon.getChildren().add(e);
        }

        VBox hText = new VBox(4);
        Label hName = new Label(reward.getName());
        hName.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label hSub = new Label(reward.getType() + "  ·  +" + reward.getValue() + " pts"
                + (reward.getRequiredLevel() != null ? "  ·  Level " + reward.getRequiredLevel() : ""));
        hSub.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        hText.getChildren().addAll(hName, hSub);
        header.getChildren().addAll(hIcon, hText);

        // Info section
        VBox infoBox = new VBox(12);
        infoBox.setStyle("-fx-background-color: white; -fx-padding: 16 24;");

        if (reward.getDescription() != null && !reward.getDescription().isBlank()) {
            Label descTitle = new Label("Description");
            descTitle.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label desc = new Label(reward.getDescription());
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");
            infoBox.getChildren().addAll(descTitle, desc);
        }

        if (reward.getRequirement() != null && !reward.getRequirement().isBlank()) {
            Label reqTitle = new Label("How to earn");
            reqTitle.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label req = new Label(reward.getRequirement());
            req.setWrapText(true);
            req.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");
            infoBox.getChildren().addAll(reqTitle, req);
        }

        // Linked games
        VBox gamesBox = new VBox(8);
        gamesBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 24 20 24;");
        try {
            List<Game> games = gameService.getGamesForReward(reward.getId());
            Label gTitle = new Label("🎮  Available in Games  (" + games.size() + ")");
            gTitle.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            gamesBox.getChildren().add(gTitle);

            if (games.isEmpty()) {
                Label none = new Label("Not linked to any game yet.");
                none.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");
                gamesBox.getChildren().add(none);
            } else {
                for (Game g : games) {
                    HBox chip = new HBox(10);
                    chip.setAlignment(Pos.CENTER_LEFT);
                    chip.setStyle("-fx-background-color: white; -fx-padding: 10 14; " +
                                  "-fx-border-radius: 8; -fx-background-radius: 8; " +
                                  "-fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    Label gIcon = new Label(gameEmoji(g.getType()));
                    gIcon.setStyle("-fx-font-size: 20px;");
                    VBox gInfo = new VBox(2);
                    Label gName = new Label(g.getName());
                    gName.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748; -fx-font-size: 13px;");
                    Label gMeta = new Label(g.getType() + "  ·  " + g.getDifficulty());
                    gMeta.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");
                    gInfo.getChildren().addAll(gName, gMeta);
                    HBox.setHgrow(gInfo, Priority.ALWAYS);
                    chip.getChildren().addAll(gIcon, gInfo);
                    gamesBox.getChildren().add(chip);
                }
            }
        } catch (Exception ex) {
            gamesBox.getChildren().add(new Label("Could not load games."));
        }

        root.getChildren().addAll(header, infoBox, gamesBox);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f0f2f8; -fx-background: #f0f2f8;");
        scroll.setPrefHeight(460);

        dlg.getDialogPane().setContent(scroll);
        dlg.getDialogPane().setStyle("-fx-background-color: #f0f2f8; -fx-padding: 0;");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().lookupButton(ButtonType.CLOSE)
           .setStyle("-fx-background-color: #3b4fd8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 7 18;");
        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ImageView loadIcon(String path, double size) {
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

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-border-color: " + fg + "44; -fx-border-radius: 20; -fx-background-radius: 20; " +
                   "-fx-padding: 3 12; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private String typeEmoji(String type) {
        return switch (type) {
            case "BADGE"        -> "🏅";
            case "ACHIEVEMENT"  -> "🏆";
            case "BONUS_XP"     -> "⭐";
            case "BONUS_TOKENS" -> "🪙";
            default             -> "🎁";
        };
    }

    private String gameEmoji(String type) {
        return switch (type) {
            case "PUZZLE"  -> "🧩";
            case "MEMORY"  -> "🧠";
            case "TRIVIA"  -> "❓";
            case "ARCADE"  -> "🕹";
            default        -> "🎮";
        };
    }

    private String typeBg(String type) {
        return switch (type) {
            case "BADGE"        -> "#fff8e1";
            case "ACHIEVEMENT"  -> "#f3e5f5";
            case "BONUS_XP"     -> "#e3f2fd";
            case "BONUS_TOKENS" -> "#e8f5e9";
            default             -> "#f0f2f8";
        };
    }

    private String typeFg(String type) {
        return switch (type) {
            case "BADGE"        -> "#d97706";
            case "ACHIEVEMENT"  -> "#7b1fa2";
            case "BONUS_XP"     -> "#1565c0";
            case "BONUS_TOKENS" -> "#2e7d32";
            default             -> "#3b4fd8";
        };
    }
}
