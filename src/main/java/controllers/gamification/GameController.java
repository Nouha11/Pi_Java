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

/**
 * GameController — READ + DELETE controller for the games list view.
 *
 * Responsibilities:
 *  - Display all games in a TableView (READ)
 *  - Live search + type filter (no extra DB call per keystroke)
 *  - Open GameFormController for Add (CREATE) or Edit (UPDATE)
 *  - Delete a game with confirmation dialog (DELETE)
 *  - Toggle active/inactive status directly from the table row
 *  - Export the current filtered list to a CSV file
 *  - Show a detail dialog with linked rewards
 */
public class GameController {

    // ── UI elements injected from game_list.fxml ──────────────────────────────
    @FXML private TableView<Game>            gamesTable;
    @FXML private TableColumn<Game, String>  nameCol, typeCol, difficultyCol, categoryCol;
    @FXML private TableColumn<Game, Integer> tokenCostCol;
    @FXML private TableColumn<Game, Void>    actionsCol;   // custom cell with action buttons
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label            statusLabel;

    // Service handles all SQL — controller never writes queries directly
    private final GameService gameService = new GameService();

    // All games loaded once into memory; filtered view is applied on top
    private ObservableList<Game> allGames = FXCollections.observableArrayList();

    // ── Called automatically by JavaFX after FXML fields are injected ─────────
    @FXML
    public void initialize() {
        // Bind each column to the matching getter in the Game model
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        difficultyCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        tokenCostCol.setCellValueFactory(new PropertyValueFactory<>("tokenCost"));

        // Build the custom actions column (View / Edit / Toggle / Delete buttons)
        setupActionsColumn();

        // Populate the type filter ComboBox
        typeFilter.setItems(FXCollections.observableArrayList("All","PUZZLE","MEMORY","TRIVIA","ARCADE"));
        typeFilter.setValue("All");

        // Re-apply filters whenever the user changes the type or types in the search box
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        // Initial data load from the database
        loadGames();
    }

    // ── Builds the Actions column with four inline buttons per row ────────────
    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = styledBtn("View",       "#eef0fd", "#3b4fd8");
            private final Button editBtn   = styledBtn("Edit",       "#f0fdf4", "#16a34a");
            private final Button toggleBtn = styledBtn("Deactivate", "#fffbeb", "#d97706");
            private final Button deleteBtn = styledBtn("Delete",     "#fff5f5", "#e53e3e");
            private final HBox   box       = new HBox(6, viewBtn, editBtn, toggleBtn, deleteBtn);
            { box.setPadding(new Insets(3, 4, 3, 4)); }

            {
                // Each button delegates to the corresponding handler method
                viewBtn.setOnAction(e   -> showGameDetails(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> openGameForm(getTableView().getItems().get(getIndex())));
                toggleBtn.setOnAction(e -> handleToggleActive(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                // Update toggle button label and color based on current active state
                Game g = getTableView().getItems().get(getIndex());
                toggleBtn.setText(g.isActive() ? "Deactivate" : "Activate");
                toggleBtn.setStyle(styledBtn("", g.isActive() ? "#fffbeb" : "#f0fdf4",
                        g.isActive() ? "#d97706" : "#16a34a").getStyle());
                setGraphic(box);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    // ── Helper: creates a styled button with given background and text color ──
    private Button styledBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                   "-fx-font-size: 12px; -fx-font-weight: bold;" +
                   "-fx-padding: 5 12; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
        return b;
    }

    // ── READ: fetch all games from DB and populate the ObservableList ─────────
    private void loadGames() {
        try {
            allGames.setAll(gameService.getAllGames());
            gamesTable.setItems(allGames);
        } catch (Exception e) { showStatus("Error loading games: " + e.getMessage(), true); }
    }

    // ── FILTER: applies search keyword + type filter on the in-memory list ────
    // No DB call — uses ObservableList.filtered() for instant results
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

    // ── CREATE: opens the form with no pre-filled data (Add mode) ─────────────
    @FXML private void handleAddGame() { openGameForm(null); }

    // ── EXPORT: writes the currently visible (filtered) rows to a CSV file ────
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

    // ── TOGGLE: flips isActive without opening the form, then persists ────────
    private void handleToggleActive(Game g) {
        try {
            g.setActive(!g.isActive());          // flip the flag on the Java object
            gameService.updateGame(g);           // UPDATE game SET is_active=? WHERE id=?
            loadGames();
            showStatus(g.getName() + " is now " + (g.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

    // ── DELETE: shows a confirmation dialog, then calls the service ───────────
    // The game_rewards junction rows are removed automatically by ON DELETE CASCADE
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

    // ── VIEW DETAILS: opens a read-only dialog showing game info + linked rewards
    private void showGameDetails(Game g) {
        try {
            // Fetch rewards linked to this game via the game_rewards junction table
            List<Reward> rewards = gameService.getRewardsForGame(g.getId());

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(g.getName());

            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #f0f2f8;");
            root.setPrefWidth(540);

            // Header with gradient background
            VBox header = new VBox(6);
            header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 22 28 18 28;");
            Label title = new Label("\uD83C\uDFAE  " + g.getName());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
            Label sub = new Label(g.getType() + "  ·  " + g.getDifficulty() + "  ·  " + g.getCategory());
            sub.setStyle("-fx-text-fill: rgba(199,210,254,0.85); -fx-font-size: 13px;");
            header.getChildren().addAll(title, sub);

            // Stat chips row (token cost, reward tokens, XP, energy, status)
            HBox stats = new HBox(10);
            stats.setStyle("-fx-background-color: white; -fx-padding: 14 28; " +
                           "-fx-border-color: #e4e8f0; -fx-border-width: 0 0 1 0;");
            stats.getChildren().addAll(
                    chip("\uD83E\uDE99 " + g.getTokenCost(),    "Token Cost"),
                    chip("\uD83C\uDF81 " + g.getRewardTokens(), "Reward Tokens"),
                    chip("\u2B50 " + g.getRewardXP(),           "Reward XP"),
                    chip("\u26A1 " + (g.getEnergyPoints() != null ? g.getEnergyPoints() : "—"), "Energy"),
                    chip(g.isActive() ? "Active" : "Inactive",  "Status")
            );

            // Description section
            VBox descBox = new VBox(6);
            descBox.setStyle("-fx-background-color: white; -fx-padding: 16 28 16 28;");
            Label descLbl = new Label("Description");
            descLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label desc = new Label(g.getDescription() != null && !g.getDescription().isBlank()
                    ? g.getDescription() : "No description provided.");
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
            descBox.getChildren().addAll(descLbl, desc);

            // Linked rewards section — one card per reward
            VBox rewardsBox = new VBox(8);
            rewardsBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 28 22 28;");
            Label rwLbl = new Label("\uD83C\uDFC6  Linked Rewards  (" + rewards.size() + ")");
            rwLbl.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            rewardsBox.getChildren().add(rwLbl);

            if (rewards.isEmpty()) {
                Label none = new Label("No rewards linked to this game.");
                none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                rewardsBox.getChildren().add(none);
            } else {
                for (Reward r : rewards) {
                    HBox row = new HBox(10);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: white; -fx-padding: 10 14; " +
                                 "-fx-border-radius: 8; -fx-background-radius: 8; " +
                                 "-fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    Label rName = new Label(r.getName());
                    rName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e2a5e; -fx-font-size: 13px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label rType = new Label(r.getType());
                    rType.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");
                    Label rVal = new Label("+" + r.getValue() + " pts");
                    rVal.setStyle("-fx-text-fill: #3b4fd8; -fx-font-weight: bold; -fx-font-size: 12px; " +
                                  "-fx-background-color: #eef0fd; -fx-background-radius: 6; -fx-padding: 2 8;");
                    row.getChildren().addAll(rName, sp, rType, rVal);
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
               .setStyle("-fx-background-color: #3b4fd8; -fx-text-fill: white; " +
                         "-fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 7 20;");
            dlg.showAndWait();
        } catch (Exception e) { showStatus("Error loading details: " + e.getMessage(), true); }
    }

    // ── Helper: builds a small stat chip (value on top, label below) ──────────
    private VBox chip(String value, String label) {
        VBox b = new VBox(2);
        b.setStyle("-fx-background-color: #f8f9ff; -fx-border-color: #e4e8f0; -fx-border-width: 1; " +
                   "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 14; -fx-min-width: 80;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e2a5e;");
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        b.getChildren().addAll(v, l);
        return b;
    }

    // ── UPDATE/CREATE: opens game_form.fxml in a new window ───────────────────
    // gameToEdit = null → Add mode (empty form)
    // gameToEdit = Game → Edit mode (pre-filled form via setGameToEdit)
    private void openGameForm(Game gameToEdit) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/gamification/game_form.fxml"));
            javafx.scene.Parent root = loader.load();
            GameFormController ctrl = loader.getController();
            if (gameToEdit != null) ctrl.setGameToEdit(gameToEdit);  // switch to Edit mode
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle(gameToEdit == null ? "Add Game" : "Edit Game");
            stage.showAndWait();   // blocks until the form window is closed
            loadGames();           // refresh the table after the form closes
        } catch (Exception e) { showStatus("Error opening form: " + e.getMessage(), true); }
    }

    // ── Shows a success (green) or error (red) message in the status bar ──────
    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
    }
}
