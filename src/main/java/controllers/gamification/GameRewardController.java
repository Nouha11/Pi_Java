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

/**
 * GameRewardController — READ + DELETE controller for the rewards list view.
 *
 * Responsibilities:
 *  - Display all rewards in a TableView with icon thumbnails (READ)
 *  - Live search + type filter (no extra DB call per keystroke)
 *  - Open RewardFormController for Add (CREATE) or Edit (UPDATE)
 *  - Delete a reward with confirmation dialog (DELETE)
 *  - Toggle active/inactive status directly from the table row
 *  - Export the current filtered list to a CSV file
 *  - Show a detail dialog with linked games
 */
public class GameRewardController {

    // ── UI elements injected from reward_list.fxml ────────────────────────────
    @FXML private TableView<Reward>            rewardsTable;
    @FXML private TableColumn<Reward, String>  nameCol, typeCol;
    @FXML private TableColumn<Reward, Integer> valueCol;
    @FXML private TableColumn<Reward, Void>    iconCol;      // shows the reward image thumbnail
    @FXML private TableColumn<Reward, Void>    actionsCol;   // custom cell with action buttons
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label            statusLabel;

    // RewardService handles reward SQL; GameService is needed for linked-games queries
    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();

    // All rewards loaded once into memory; filtered view is applied on top
    private ObservableList<Reward> allRewards = FXCollections.observableArrayList();

    // ── Called automatically by JavaFX after FXML fields are injected ─────────
    @FXML
    public void initialize() {
        // Bind each column to the matching getter in the Reward model
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

        // Build the icon column (shows the reward image) and actions column
        setupIconColumn();
        setupActionsColumn();

        // Populate the type filter ComboBox with all reward types
        typeFilter.setItems(FXCollections.observableArrayList(
                "All","BADGE","ACHIEVEMENT","BONUS_XP","BONUS_TOKENS"));
        typeFilter.setValue("All");

        // Re-apply filters whenever the user changes the type or types in the search box
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        // Initial data load from the database
        loadRewards();
    }

    // ── Builds the Icon column — renders a 36x36 image thumbnail per row ──────
    private void setupIconColumn() {
        iconCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            { iv.setFitWidth(36); iv.setFitHeight(36); iv.setPreserveRatio(true); iv.setSmooth(true); }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                // Load the reward's icon from the resources folder or absolute path
                Reward r = getTableView().getItems().get(getIndex());
                iv.setImage(loadImage(r.getIcon(), 36));
                setGraphic(iv);
                setAlignment(Pos.CENTER);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    // ── Loads a reward icon image by filename or absolute path ────────────────
    // Priority: 1) classpath resource (/images/rewards/filename)
    //           2) absolute file path (legacy entries stored before the copy system)
    private Image loadImage(String nameOrPath, double size) {
        if (nameOrPath == null || nameOrPath.isBlank()) return null;
        try {
            var stream = getClass().getResourceAsStream("/images/rewards/" + nameOrPath);
            if (stream != null) return new Image(stream, size, size, true, true);
            File f = new File(nameOrPath);
            if (f.exists()) return new Image(f.toURI().toString(), size, size, true, true);
        } catch (Exception ignored) {}
        return null;
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
                viewBtn.setOnAction(e   -> showRewardDetails(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> openRewardForm(getTableView().getItems().get(getIndex())));
                toggleBtn.setOnAction(e -> handleToggleActive(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                // Update toggle button label and color based on current active state
                Reward r = getTableView().getItems().get(getIndex());
                toggleBtn.setText(r.isActive() ? "Deactivate" : "Activate");
                toggleBtn.setStyle(styledBtn("", r.isActive() ? "#fffbeb" : "#f0fdf4",
                        r.isActive() ? "#d97706" : "#16a34a").getStyle());
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

    // ── READ: fetch all rewards from DB and populate the ObservableList ───────
    private void loadRewards() {
        try {
            allRewards.setAll(rewardService.getAllRewards());
            rewardsTable.setItems(allRewards);
        } catch (Exception e) { showStatus("Error loading rewards: " + e.getMessage(), true); }
    }

    // ── FILTER: applies search keyword + type filter on the in-memory list ────
    // No DB call — uses ObservableList.filtered() for instant results
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

    // ── CREATE: opens the form with no pre-filled data (Add mode) ─────────────
    @FXML private void handleAddReward() { openRewardForm(null); }

    // ── EXPORT: writes the currently visible (filtered) rows to a CSV file ────
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

    // ── TOGGLE: flips isActive without opening the form, then persists ────────
    private void handleToggleActive(Reward r) {
        try {
            r.setActive(!r.isActive());          // flip the flag on the Java object
            rewardService.updateReward(r);       // UPDATE reward SET is_active=? WHERE id=?
            loadRewards();
            showStatus(r.getName() + " is now " + (r.isActive() ? "active" : "inactive") + ".", false);
        } catch (Exception e) { showStatus("Toggle error: " + e.getMessage(), true); }
    }

    // ── DELETE: shows a confirmation dialog, then calls the service ───────────
    // The game_rewards junction rows are removed automatically by ON DELETE CASCADE
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

    // ── VIEW DETAILS: opens a read-only dialog showing reward info + linked games
    private void showRewardDetails(Reward r) {
        try {
            // Fetch games linked to this reward via the game_rewards junction table
            List<Game> linkedGames = gameService.getGamesForReward(r.getId());

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(r.getName());

            VBox root = new VBox(0);
            root.setStyle("-fx-background-color: #f0f2f8;");
            root.setPrefWidth(520);

            // Header: shows icon image (or trophy emoji fallback) + name + type/value
            HBox header = new HBox(18);
            header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            header.setStyle("-fx-background-color: linear-gradient(to right, #3b4fd8, #5b6ef5); -fx-padding: 22 28 18 28;");

            Image img = loadImage(r.getIcon(), 56);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(56); iv.setFitHeight(56); iv.setPreserveRatio(true);
                header.getChildren().add(iv);
            } else {
                // Fallback emoji when no icon is set
                Label ico = new Label("\uD83C\uDFC6");
                ico.setStyle("-fx-font-size: 36px;");
                header.getChildren().add(ico);
            }

            VBox hText = new VBox(4);
            Label title = new Label(r.getName());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
            Label sub = new Label(r.getType() + "  \u00b7  +" + r.getValue() + " pts"
                    + (r.getRequiredLevel() != null ? "  \u00b7  Level " + r.getRequiredLevel() : ""));
            sub.setStyle("-fx-text-fill: rgba(199,210,254,0.85); -fx-font-size: 13px;");
            hText.getChildren().addAll(title, sub);
            header.getChildren().add(hText);

            // Stat chips row (type, value, min level, status)
            HBox stats = new HBox(10);
            stats.setStyle("-fx-background-color: white; -fx-padding: 14 28; " +
                           "-fx-border-color: #e4e8f0; -fx-border-width: 0 0 1 0;");
            stats.getChildren().addAll(
                    chip(r.getType(),                                                    "Type"),
                    chip("+" + r.getValue() + " pts",                                   "Value"),
                    chip(r.getRequiredLevel() != null ? "Lv " + r.getRequiredLevel() : "\u2014", "Min Level"),
                    chip(r.isActive() ? "Active" : "Inactive",                          "Status")
            );

            // Info section: requirement (how to earn) and description
            VBox infoBox = new VBox(12);
            infoBox.setStyle("-fx-background-color: white; -fx-padding: 16 28 16 28;");

            if (r.getRequirement() != null && !r.getRequirement().isBlank()) {
                Label reqLbl = new Label("How to earn");
                reqLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label req = new Label(r.getRequirement());
                req.setWrapText(true);
                req.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(reqLbl, req);
            }

            if (r.getDescription() != null && !r.getDescription().isBlank()) {
                Label descLbl = new Label("Description");
                descLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label desc = new Label(r.getDescription());
                desc.setWrapText(true);
                desc.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
                infoBox.getChildren().addAll(descLbl, desc);
            }

            if (infoBox.getChildren().isEmpty()) {
                Label none = new Label("No additional details.");
                none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                infoBox.getChildren().add(none);
            }

            // Linked games section — one card per game
            VBox gamesBox = new VBox(8);
            gamesBox.setStyle("-fx-background-color: #f8f9ff; -fx-padding: 16 28 22 28;");
            Label gLbl = new Label("\uD83C\uDFAE  Available in Games  (" + linkedGames.size() + ")");
            gLbl.setStyle("-fx-text-fill: #3b4fd8; -fx-font-size: 13px; -fx-font-weight: bold;");
            gamesBox.getChildren().add(gLbl);

            if (linkedGames.isEmpty()) {
                Label none = new Label("Not linked to any game yet.");
                none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                gamesBox.getChildren().add(none);
            } else {
                for (Game g : linkedGames) {
                    HBox row = new HBox(10);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: white; -fx-padding: 10 14; " +
                                 "-fx-border-radius: 8; -fx-background-radius: 8; " +
                                 "-fx-border-color: #e4e8f0; -fx-border-width: 1;");
                    Label gName = new Label(g.getName());
                    gName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e2a5e; -fx-font-size: 13px;");
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label gMeta = new Label(g.getType() + "  \u00b7  " + g.getDifficulty());
                    gMeta.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");
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

    // ── Unused legacy helper kept for compatibility ───────────────────────────
    private VBox statBox(String label, String value) {
        VBox b = new VBox(2);
        b.setStyle("-fx-padding: 0 20 0 0;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #1e2a5e; -fx-font-size: 14px; -fx-font-weight: bold;");
        b.getChildren().addAll(lbl, val);
        return b;
    }

    // ── UPDATE/CREATE: opens reward_form.fxml in a new window ─────────────────
    // rewardToEdit = null  -> Add mode (empty form)
    // rewardToEdit = Reward -> Edit mode (pre-filled form via setRewardToEdit)
    private void openRewardForm(Reward rewardToEdit) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/gamification/reward_form.fxml"));
            javafx.scene.Parent root = loader.load();
            RewardFormController ctrl = loader.getController();
            if (rewardToEdit != null) ctrl.setRewardToEdit(rewardToEdit);  // switch to Edit mode
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle(rewardToEdit == null ? "Add Reward" : "Edit Reward");
            stage.showAndWait();   // blocks until the form window is closed
            loadRewards();         // refresh the table after the form closes
        } catch (Exception e) { showStatus("Error opening form: " + e.getMessage(), true); }
    }

    // ── Shows a success (green) or error (red) message in the status bar ──────
    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
    }
}
