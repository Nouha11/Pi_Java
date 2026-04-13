package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class GameController {

    @FXML private TableView<Game>            gamesTable;
    @FXML private TableColumn<Game, String>  nameCol, typeCol, difficultyCol, categoryCol;
    @FXML private TableColumn<Game, Integer> tokenCostCol;
    @FXML private TableColumn<Game, Void>    actionsCol;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label            statusLabel;

    private final GameService gameService = new GameService();
    private ObservableList<Game> allGames = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        difficultyCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        tokenCostCol.setCellValueFactory(new PropertyValueFactory<>("tokenCost"));

        setupActionsColumn();

        typeFilter.setItems(FXCollections.observableArrayList("All","PUZZLE","MEMORY","TRIVIA","ARCADE"));
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        loadGames();
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = styledBtn("👁 View",   "#0f3460", "#c0c0e0");
            private final Button editBtn   = styledBtn("✏ Edit",   "#0f3460", "#c0c0e0");
            private final Button toggleBtn = styledBtn("⏸",        "#0f3460", "#f0c040");
            private final Button deleteBtn = styledBtn("🗑",        "transparent", "#e94560");
            private final HBox   box       = new HBox(5, viewBtn, editBtn, toggleBtn, deleteBtn);
            { box.setPadding(new Insets(2, 0, 2, 0));
              deleteBtn.setStyle(deleteBtn.getStyle() + "-fx-border-color: #e94560;"); }

            {
                viewBtn.setOnAction(e   -> showGameDetails(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> openGameForm(getTableView().getItems().get(getIndex())));
                toggleBtn.setOnAction(e -> handleToggleActive(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Game g = getTableView().getItems().get(getIndex());
                toggleBtn.setText(g.isActive() ? "⏸ Deactivate" : "▶ Activate");
                toggleBtn.setStyle(styledBtn("", "#0f3460", g.isActive() ? "#f0c040" : "#4caf50").getStyle());
                setGraphic(box);
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

    private void loadGames() {
        try {
            allGames.setAll(gameService.getAllGames());
            gamesTable.setItems(allGames);
        } catch (Exception e) { showStatus("Error loading games: " + e.getMessage(), true); }
    }

    private void applyFilters() {
        String kw   = searchField.getText().trim().toLowerCase();
        String type = typeFilter.getValue();
        gamesTable.setItems(allGames.filtered(g -> {
            boolean matchType = "All".equals(type) || g.getType().equals(type);
            boolean matchKw   = kw.isEmpty()
                    || g.getName().toLowerCase().contains(kw)
                    || g.getType().toLowerCase().contains(kw)
                    || g.getDifficulty().toLowerCase().contains(kw);
            return matchType && matchKw;
        }));
    }

    @FXML private void handleAddGame() { openGameForm(null); }

    @FXML
    private void handleExportCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Games to CSV");
        fc.setInitialFileName("games_export.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(gamesTable.getScene().getWindow());
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Name,Type,Difficulty,Category,TokenCost,RewardTokens,RewardXP,EnergyPoints,Active");
            for (Game g : gamesTable.getItems()) {
                pw.printf("\"%s\",%s,%s,%s,%d,%d,%d,%s,%s%n",
                        g.getName(), g.getType(), g.getDifficulty(), g.getCategory(),
                        g.getTokenCost(), g.getRewardTokens(), g.getRewardXP(),
                        g.getEnergyPoints() != null ? g.getEnergyPoints() : "",
                        g.isActive());
            }
            showStatus("Exported " + gamesTable.getItems().size() + " games to " + file.getName(), false);
        } catch (Exception e) { showStatus("Export error: " + e.getMessage(), true); }
    }

    private void handleToggleActive(Game g) {
        try {
            g.setActive(!g.isActive());
            gameService.updateGame(g);
            loadGames();
            showStatus(g.getName() + " is now " + (g.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

    private void handleDelete(Game g) {        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
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

    private void showGameDetails(Game g) {
        try {
            List<Reward> rewards = gameService.getRewardsForGame(g.getId());

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Game Details");

            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #1a1a2e;");
            root.setPrefWidth(520);

            // Header
            VBox header = new VBox(4);
            header.setStyle("-fx-background-color: #16213e; -fx-padding: 20 24 16 24;");
            Label title = new Label("🎮  " + g.getName());
            title.setStyle("-fx-text-fill: #e94560; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: 'Consolas';");
            Label sub = new Label(g.getType() + "  ·  " + g.getDifficulty() + "  ·  " + g.getCategory());
            sub.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            header.getChildren().addAll(title, sub);

            // Stats row
            HBox stats = new HBox(0);
            stats.setStyle("-fx-background-color: #0f3460; -fx-padding: 12 24;");
            stats.getChildren().addAll(
                statBox("🪙 Token Cost",    String.valueOf(g.getTokenCost())),
                statBox("🎁 Reward Tokens", String.valueOf(g.getRewardTokens())),
                statBox("⭐ Reward XP",     String.valueOf(g.getRewardXP())),
                statBox("⚡ Energy",        g.getEnergyPoints() != null ? String.valueOf(g.getEnergyPoints()) : "—"),
                statBox("✅ Active",        g.isActive() ? "Yes" : "No")
            );

            // Description
            VBox descBox = new VBox(6);
            descBox.setStyle("-fx-padding: 16 24 8 24;");
            Label descTitle = new Label("Description");
            descTitle.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label desc = new Label(g.getDescription() != null ? g.getDescription() : "—");
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #c0c0e0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
            descBox.getChildren().addAll(descTitle, desc);

            // Rewards section
            VBox rewardsBox = new VBox(8);
            rewardsBox.setStyle("-fx-padding: 8 24 20 24;");
            Label rwTitle = new Label("Linked Rewards  (" + rewards.size() + ")");
            rwTitle.setStyle("-fx-text-fill: #8080b0; -fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-font-weight: bold;");
            rewardsBox.getChildren().add(rwTitle);

            if (rewards.isEmpty()) {
                Label none = new Label("No rewards linked to this game.");
                none.setStyle("-fx-text-fill: #5050a0; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                rewardsBox.getChildren().add(none);
            } else {
                for (Reward r : rewards) {
                    HBox chip = new HBox(8);
                    chip.setStyle("-fx-background-color: #0f3460; -fx-padding: 8 14; " +
                                  "-fx-border-radius: 4; -fx-background-radius: 4;");
                    Label rName = new Label("🏆 " + r.getName());
                    rName.setStyle("-fx-text-fill: #e0e0f0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                    Label rType = new Label("[" + r.getType() + "]");
                    rType.setStyle("-fx-text-fill: #e94560; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");
                    Label rVal = new Label("+" + r.getValue());
                    rVal.setStyle("-fx-text-fill: #4caf50; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    chip.getChildren().addAll(rName, rType, sp, rVal);
                    rewardsBox.getChildren().add(chip);
                }
            }

            root.getChildren().addAll(header, stats, descBox, rewardsBox);

            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background-color: #1a1a2e; -fx-background: #1a1a2e;");
            scroll.setPrefHeight(460);

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

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
    }
}
