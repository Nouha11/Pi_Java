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
        } catch (Exception e) { allRewards = List.of(); }
        renderCards(allRewards);
    }

    @FXML private void handleSearch() { applyFilters(); }

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
        ImageView iv = loadIcon(reward.getIcon(), 56);
        StackPane iconPane;
        if (iv != null) {
            iconPane = new StackPane(iv);
            iconPane.setStyle("-fx-background-color: " + rewardGradient(reward.getType()) + "; -fx-background-radius: 50; -fx-min-width: 64; -fx-min-height: 64; -fx-max-width: 64; -fx-max-height: 64;");
        } else {
            iconPane = faCircle(rewardTypeIcon(reward.getType()), 22, rewardGradient(reward.getType()), "white");
            iconPane.setPrefSize(64, 64); iconPane.setMaxSize(64, 64);
        }

        Label name = new Label(reward.getName());
        name.getStyleClass().add("quiz-card-title");
        name.setWrapText(true);
        name.setTextAlignment(TextAlignment.CENTER);
        name.setMaxWidth(210);

        Label typeBadge = badge(reward.getType(), typeBg(reward.getType()), typeFg(reward.getType()));

        Label value = new Label("+" + reward.getValue() + " pts");
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #3b4fd8;");

        Button btnView = new Button("\uF06E  View Details");
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

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 20 24;");
        ImageView hIv = loadIcon(reward.getIcon(), 52);
        StackPane hIcon;
        if (hIv != null) {
            hIcon = new StackPane(hIv);
            hIcon.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 50; -fx-min-width: 60; -fx-min-height: 60; -fx-max-width: 60; -fx-max-height: 60;");
        } else {
            hIcon = faCircle(rewardTypeIcon(reward.getType()), 24, "rgba(255,255,255,0.15)", "white");
            hIcon.setPrefSize(60, 60); hIcon.setMaxSize(60, 60);
        }
        VBox hText = new VBox(4);
        Label hName = new Label(reward.getName());
        hName.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label hSub = new Label(reward.getType() + "  \u00B7  +" + reward.getValue() + " pts"
                + (reward.getRequiredLevel() != null ? "  \u00B7  Level " + reward.getRequiredLevel() : ""));
        hSub.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        hText.getChildren().addAll(hName, hSub);
        header.getChildren().addAll(hIcon, hText);

        VBox infoBox = new VBox(12);
        infoBox.setStyle("-fx-background-color: white; -fx-padding: 16 24;");
        if (reward.getDescription() != null && !reward.getDescription().isBlank()) {
            Label descTitle = new Label("Description");
            descTitle.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label desc = new Label(reward.getDescription());
            desc.setWrapText(true); desc.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");
            infoBox.getChildren().addAll(descTitle, desc);
        }
        if (reward.getRequirement() != null && !reward.getRequirement().isBlank()) {
            Label reqTitle = new Label("How to earn");
            reqTitle.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label req = new Label(reward.getRequirement());
            req.setWrapText(true); req.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");
            infoBox.getChildren().addAll(reqTitle, req);
        }

        VBox gamesBox = new VBox(8);
        gamesBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 24 20 24;");
        try {
            List<Game> games = gameService.getGamesForReward(reward.getId());
            StackPane gameIcon = faCircle("\uF11B", 14, "linear-gradient(to bottom right, #43e97b, #38f9d7)", "white");
            gameIcon.setPrefSize(28, 28); gameIcon.setMaxSize(28, 28);
            Label gTitle = new Label("Available in Games  (" + games.size() + ")");
            gTitle.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            HBox gHeader = new HBox(8, gameIcon, gTitle);
            gHeader.setAlignment(Pos.CENTER_LEFT);
            gamesBox.getChildren().add(gHeader);
            if (games.isEmpty()) {
                Label none = new Label("Not linked to any game yet.");
                none.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");
                gamesBox.getChildren().add(none);
            } else {
                for (Game g : games) {
                    HBox chip = new HBox(10);
                    chip.setAlignment(Pos.CENTER_LEFT);
                    chip.setStyle("-fx-background-color: white; -fx-padding: 10 14; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    StackPane gIcon = faCircle(typeIcon(g.getType()), 14, typeGradient(g.getType()), "white");
                    gIcon.setPrefSize(32, 32); gIcon.setMaxSize(32, 32);
                    VBox gInfo = new VBox(2);
                    Label gName = new Label(g.getName());
                    gName.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748; -fx-font-size: 13px;");
                    Label gMeta = new Label(g.getType() + "  \u00B7  " + g.getDifficulty());
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

    private StackPane faCircle(String unicode, double iconSize, String bgGradient, String iconColor) {
        Label ico = new Label(unicode);
        ico.setStyle("-fx-font-family: 'Font Awesome 5 Free'; -fx-font-weight: 900; -fx-font-size: " + iconSize + "px; -fx-text-fill: " + iconColor + ";");
        StackPane sp = new StackPane(ico);
        sp.setPrefSize(56, 56); sp.setMaxSize(56, 56);
        sp.setStyle("-fx-background-color: " + bgGradient + "; -fx-background-radius: 50;");
        return sp;
    }

    private ImageView loadIcon(String path, double size) {
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

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-border-color: " + fg + "44; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 3 12; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private String rewardTypeIcon(String type) {
        return switch (type) {
            case "BADGE"        -> "\uF5A2";
            case "ACHIEVEMENT"  -> "\uF091";
            case "BONUS_XP"     -> "\uF005";
            case "BONUS_TOKENS" -> "\uF51E";
            default             -> "\uF06B";
        };
    }

    private String rewardGradient(String type) {
        return switch (type) {
            case "BADGE"        -> "linear-gradient(to bottom right, #f6d365, #fda085)";
            case "ACHIEVEMENT"  -> "linear-gradient(to bottom right, #a18cd1, #fbc2eb)";
            case "BONUS_XP"     -> "linear-gradient(to bottom right, #4facfe, #00f2fe)";
            case "BONUS_TOKENS" -> "linear-gradient(to bottom right, #43e97b, #38f9d7)";
            default             -> "linear-gradient(to bottom right, #667eea, #764ba2)";
        };
    }

    private String typeIcon(String type) {
        return switch (type) {
            case "PUZZLE" -> "\uF12E"; case "MEMORY" -> "\uF5DC";
            case "TRIVIA" -> "\uF059"; case "ARCADE" -> "\uF11B";
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

    private String typeBg(String type) {
        return switch (type) {
            case "BADGE" -> "#fff8e1"; case "ACHIEVEMENT" -> "#f3e5f5";
            case "BONUS_XP" -> "#e3f2fd"; case "BONUS_TOKENS" -> "#e8f5e9";
            default -> "#f0f2f8";
        };
    }

    private String typeFg(String type) {
        return switch (type) {
            case "BADGE" -> "#d97706"; case "ACHIEVEMENT" -> "#7b1fa2";
            case "BONUS_XP" -> "#1565c0"; case "BONUS_TOKENS" -> "#2e7d32";
            default -> "#3b4fd8";
        };
    }
}
