package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;
import services.gamification.RewardService;

import java.util.List;

public class GameRewardController {

    @FXML private TableView<Reward>            rewardsTable;
    @FXML private TableColumn<Reward, String>  nameCol, typeCol;
    @FXML private TableColumn<Reward, Integer> valueCol;
    @FXML private TableColumn<Reward, Boolean> activeCol;
    @FXML private TableColumn<Reward, Void>    actionsCol;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label            statusLabel;

    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();
    private ObservableList<Reward> allRewards = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        setupActionsColumn();

        typeFilter.setItems(FXCollections.observableArrayList(
                "All","BADGE","ACHIEVEMENT","BONUS_XP","BONUS_TOKENS"));
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        loadRewards();
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = styledBtn("👁 View",   "#0f3460", "#c0c0e0");
            private final Button editBtn   = styledBtn("✏ Edit",   "#0f3460", "#c0c0e0");
            private final Button deleteBtn = styledBtn("🗑 Delete", "transparent", "#e94560");
            private final HBox   box       = new HBox(6, viewBtn, editBtn, deleteBtn);
            { box.setPadding(new Insets(2, 0, 2, 0));
              deleteBtn.setStyle(deleteBtn.getStyle() + "-fx-border-color: #e94560;"); }

            {
                viewBtn.setOnAction(e   -> showRewardDetails(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> openRewardForm(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private Button styledBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                   "-fx-font-family: 'Consolas'; -fx-font-size: 11px;" +
                   "-fx-padding: 4 10; -fx-border-radius: 3; -fx-background-radius: 3; -fx-cursor: hand;");
        return b;
    }

    private void loadRewards() {
        try {
            allRewards.setAll(rewardService.getAllRewards());
            rewardsTable.setItems(allRewards);
        } catch (Exception e) { showStatus("Error loading rewards: " + e.getMessage(), true); }
    }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = typeFilter.getValue();
        rewardsTable.setItems(allRewards.filtered(r -> {
            boolean matchType = "All".equals(type) || r.getType().equals(type);
            boolean matchKw   = kw.isEmpty()
                    || r.getName().toLowerCase().contains(kw)
                    || r.getType().toLowerCase().contains(kw);
            return matchType && matchKw;
        }));
    }

    @FXML private void handleAddReward() { openRewardForm(null); }

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

    private void showRewardDetails(Reward r) {
        try {
            List<Game> linkedGames = gameService.getGamesForReward(r.getId());

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Reward Details");

            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #1a1a2e;");
            root.setPrefWidth(500);

            // Header
            VBox header = new VBox(4);
            header.setStyle("-fx-background-color: #16213e; -fx-padding: 20 24 16 24;");
            Label title = new Label("🏆  " + r.getName());
            title.setStyle("-fx-text-fill: #e94560; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: 'Consolas';");
            Label sub = new Label(r.getType() + "  ·  Value: " + r.getValue());
            sub.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            header.getChildren().addAll(title, sub);

            // Stats row
            HBox stats = new HBox(0);
            stats.setStyle("-fx-background-color: #0f3460; -fx-padding: 12 24;");
            stats.getChildren().addAll(
                statBox("🏅 Type",           r.getType()),
                statBox("💎 Value",          String.valueOf(r.getValue())),
                statBox("🎯 Req. Level",     r.getRequiredLevel() != null ? String.valueOf(r.getRequiredLevel()) : "—"),
                statBox("✅ Active",         r.isActive() ? "Yes" : "No")
            );

            // Requirement & description
            VBox infoBox = new VBox(10);
            infoBox.setStyle("-fx-padding: 16 24 8 24;");

            if (r.getRequirement() != null && !r.getRequirement().isEmpty()) {
                Label reqTitle = new Label("Requirement");
                reqTitle.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label req = new Label(r.getRequirement());
                req.setWrapText(true);
                req.setStyle("-fx-text-fill: #c0c0e0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(reqTitle, req);
            }

            if (r.getDescription() != null && !r.getDescription().isEmpty()) {
                Label descTitle = new Label("Description");
                descTitle.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label desc = new Label(r.getDescription());
                desc.setWrapText(true);
                desc.setStyle("-fx-text-fill: #c0c0e0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(descTitle, desc);
            }

            // Linked games
            VBox gamesBox = new VBox(8);
            gamesBox.setStyle("-fx-padding: 8 24 20 24;");
            Label gTitle = new Label("Linked Games  (" + linkedGames.size() + ")");
            gTitle.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-font-weight: bold;");
            gamesBox.getChildren().add(gTitle);

            if (linkedGames.isEmpty()) {
                Label none = new Label("This reward is not linked to any game.");
                none.setStyle("-fx-text-fill: #5050a0; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                gamesBox.getChildren().add(none);
            } else {
                for (Game g : linkedGames) {
                    HBox chip = new HBox(8);
                    chip.setStyle("-fx-background-color: #0f3460; -fx-padding: 8 14; " +
                                  "-fx-border-radius: 4; -fx-background-radius: 4;");
                    Label gName = new Label("🎮 " + g.getName());
                    gName.setStyle("-fx-text-fill: #e0e0f0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                    Label gType = new Label("[" + g.getType() + "]");
                    gType.setStyle("-fx-text-fill: #e94560; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");
                    Label gDiff = new Label(g.getDifficulty());
                    gDiff.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    chip.getChildren().addAll(gName, gType, sp, gDiff);
                    gamesBox.getChildren().add(chip);
                }
            }

            root.getChildren().addAll(header, stats, infoBox, gamesBox);

            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background-color: #1a1a2e; -fx-background: #1a1a2e;");
            scroll.setPrefHeight(440);

            dlg.getDialogPane().setContent(scroll);
            dlg.getDialogPane().setStyle("-fx-background-color: #1a1a2e; -fx-padding: 0;");
            dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dlg.getDialogPane().lookupButton(ButtonType.CLOSE)
               .setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-family: 'Consolas';");
            dlg.showAndWait();
        } catch (Exception e) { showStatus("Error loading details: " + e.getMessage(), true); }
    }

    private VBox statBox(String label, String value) {
        VBox b = new VBox(2);
        b.setStyle("-fx-padding: 0 20 0 0;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #6060a0; -fx-font-family: 'Consolas'; -fx-font-size: 10px;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #e0e0f0; -fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-font-weight: bold;");
        b.getChildren().addAll(lbl, val);
        return b;
    }

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

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
    }
}
