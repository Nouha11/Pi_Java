package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;
import services.gamification.RewardService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class GameRewardController {

    @FXML private TableView<Reward>            rewardsTable;
    @FXML private TableColumn<Reward, String>  nameCol, typeCol;
    @FXML private TableColumn<Reward, Integer> valueCol;
    @FXML private TableColumn<Reward, Void>    iconCol, actionsCol;
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

        setupIconColumn();
        setupActionsColumn();

        typeFilter.setItems(FXCollections.observableArrayList(
                "All","BADGE","ACHIEVEMENT","BONUS_XP","BONUS_TOKENS"));
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        loadRewards();
    }

    private void setupIconColumn() {
        iconCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            { iv.setFitWidth(36); iv.setFitHeight(36); iv.setPreserveRatio(true); iv.setSmooth(true); }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Reward r = getTableView().getItems().get(getIndex());
                iv.setImage(loadImage(r.getIcon(), 36));
                setGraphic(iv);
                setAlignment(Pos.CENTER);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private Image loadImage(String nameOrPath, double size) {
        if (nameOrPath == null || nameOrPath.isBlank()) return null;
        try {
            // 1. Try as classpath resource (filename stored in DB)
            var stream = getClass().getResourceAsStream("/images/rewards/" + nameOrPath);
            if (stream != null) return new Image(stream, size, size, true, true);
            // 2. Try as absolute path (legacy entries)
            File f = new File(nameOrPath);
            if (f.exists()) return new Image(f.toURI().toString(), size, size, true, true);
        } catch (Exception ignored) {}
        return null;
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = styledBtn("View",       "#eef0fd", "#3b4fd8");
            private final Button editBtn   = styledBtn("Edit",       "#f0fdf4", "#16a34a");
            private final Button toggleBtn = styledBtn("Deactivate", "#fffbeb", "#d97706");
            private final Button deleteBtn = styledBtn("Delete",     "#fff5f5", "#e53e3e");
            private final HBox   box       = new HBox(6, viewBtn, editBtn, toggleBtn, deleteBtn);
            { box.setPadding(new Insets(3, 4, 3, 4)); }

            {
                viewBtn.setOnAction(e   -> showRewardDetails(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> openRewardForm(getTableView().getItems().get(getIndex())));
                toggleBtn.setOnAction(e -> handleToggleActive(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Reward r = getTableView().getItems().get(getIndex());
                toggleBtn.setText(r.isActive() ? "Deactivate" : "Activate");
                toggleBtn.setStyle(styledBtn("", r.isActive() ? "#fffbeb" : "#f0fdf4",
                        r.isActive() ? "#d97706" : "#16a34a").getStyle());
                setGraphic(box);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private Button styledBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                   "-fx-font-size: 12px; -fx-font-weight: bold;" +
                   "-fx-padding: 5 12; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
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

    @FXML
    private void handleExportCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Rewards to CSV");
        fc.setInitialFileName("rewards_export.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(rewardsTable.getScene().getWindow());
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Name,Type,Value,Requirement,RequiredLevel,Active");
            for (Reward r : rewardsTable.getItems()) {
                pw.printf("\"%s\",%s,%d,\"%s\",%s,%s%n",
                        r.getName(), r.getType(), r.getValue(),
                        r.getRequirement() != null ? r.getRequirement() : "",
                        r.getRequiredLevel() != null ? r.getRequiredLevel() : "",
                        r.isActive());
            }
            showStatus("Exported " + rewardsTable.getItems().size() + " rewards to " + file.getName(), false);
        } catch (Exception e) { showStatus("Export error: " + e.getMessage(), true); }
    }

    private void handleToggleActive(Reward r) {
        try {
            r.setActive(!r.isActive());
            rewardService.updateReward(r);
            loadRewards();
            showStatus(r.getName() + " is now " + (r.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

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

            // Header — with icon if available
            VBox header = new VBox(4);
            header.setStyle("-fx-background-color: #16213e; -fx-padding: 20 24 16 24;");

            HBox titleRow = new HBox(14);
            titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Image img = loadImage(r.getIcon(), 56);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(56); iv.setFitHeight(56); iv.setPreserveRatio(true);
                iv.setStyle("-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.5),6,0,0,2);");
                titleRow.getChildren().add(iv);
            }

            VBox titleText = new VBox(2);
            Label title = new Label("🏆  " + r.getName());
            title.setStyle("-fx-text-fill: #e94560; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: 'Consolas';");
            Label sub = new Label(r.getType() + "  ·  Value: " + r.getValue());
            sub.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            titleText.getChildren().addAll(title, sub);
            titleRow.getChildren().add(titleText);
            header.getChildren().add(titleRow);

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
