package controllers.gamification;


import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.*;

import java.util.List;

public class GameFormController {

    @FXML private TextField     nameField;
    @FXML private TextArea      descriptionArea;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField     tokenCostField;
    @FXML private TextField     rewardTokensField;
    @FXML private TextField     rewardXPField;
    @FXML private TextField     energyPointsField;
    @FXML private CheckBox      isActiveCheck;
    @FXML private ListView<Reward> rewardsList;
    @FXML private Button        saveBtn;
    @FXML private Button        cancelBtn;

    // Per-field error labels (shown directly under each field)
    @FXML private Label errName, errDescription;
    @FXML private Label errType, errDifficulty, errCategory;
    @FXML private Label errTokenCost, errRewardTokens, errRewardXP;
    @FXML private Label errEnergyPoints;

    // ── Services ──────────────────────────────────────────────────────────────
    private final GameService   gameService   = new GameService();
    private final RewardService rewardService = new RewardService();

    // ── State ─────────────────────────────────────────────────────────────────
    private Game editingGame = null;   // null = Add mode, non-null = Edit mode

    // ─────────────────────────────────────────────────────────────────────────
    // initialize() — called automatically by JavaFX after FXML injection
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {

        // Populate ComboBoxes
        typeCombo.setItems(FXCollections.observableArrayList(
                "PUZZLE", "MEMORY", "TRIVIA", "ARCADE"));

        difficultyCombo.setItems(FXCollections.observableArrayList(
                "EASY", "MEDIUM", "HARD"));

        categoryCombo.setItems(FXCollections.observableArrayList(
                "FULL_GAME", "MINI_GAME"));

        // Energy points field: only relevant for MINI_GAME
        energyPointsField.setDisable(true);
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMini = "MINI_GAME".equals(newVal);
            energyPointsField.setDisable(!isMini);
            if (!isMini) energyPointsField.clear();
        });

        // Allow multiple reward selection
        rewardsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Load all rewards into the list
        loadRewardsList();

        // Default state
        isActiveCheck.setSelected(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setGameToEdit() — called by GameController when opening in Edit mode
    // ─────────────────────────────────────────────────────────────────────────
    public void setGameToEdit(Game game) {
        this.editingGame = game;

        // Pre-fill all fields with existing data
        nameField.setText(game.getName());
        descriptionArea.setText(game.getDescription());
        typeCombo.setValue(game.getType());
        difficultyCombo.setValue(game.getDifficulty());
        categoryCombo.setValue(game.getCategory());
        tokenCostField.setText(String.valueOf(game.getTokenCost()));
        rewardTokensField.setText(String.valueOf(game.getRewardTokens()));
        rewardXPField.setText(String.valueOf(game.getRewardXP()));
        isActiveCheck.setSelected(game.isActive());

        if (game.getEnergyPoints() != null) {
            energyPointsField.setText(String.valueOf(game.getEnergyPoints()));
        }

        // Pre-select rewards already linked to this game
        try {
            List<Reward> assignedRewards = gameService.getRewardsForGame(game.getId());
            for (Reward assigned : assignedRewards) {
                for (Reward item : rewardsList.getItems()) {
                    if (item.getId() == assigned.getId()) {
                        rewardsList.getSelectionModel().select(item);
                    }
                }
            }
        } catch (Exception e) {
            showFieldError(errName, "Could not load linked rewards: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleSave() — validates input, then calls service to add or update
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void handleSave() {
        clearErrors();
        boolean ok = true;

        // 1. Name
        String name = nameField.getText().trim();
        if (name.isEmpty())        { showFieldError(errName, "Game name is required.");               ok = false; }
        else if (name.length() < 3){ showFieldError(errName, "Name must be at least 3 characters."); ok = false; }

        // 2. Description
        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) { showFieldError(errDescription, "Description is required."); ok = false; }

        // 3. ComboBoxes
        if (typeCombo.getValue()       == null) { showFieldError(errType,       "Select a type.");       ok = false; }
        if (difficultyCombo.getValue() == null) { showFieldError(errDifficulty, "Select a difficulty."); ok = false; }
        if (categoryCombo.getValue()   == null) { showFieldError(errCategory,   "Select a category.");   ok = false; }

        // 4. Numeric fields
        int tokenCost = 0, rewardTokens = 0, rewardXP = 0;
        try {
            tokenCost = Integer.parseInt(tokenCostField.getText().trim());
            if (tokenCost < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) { showFieldError(errTokenCost, "Must be a whole number >= 0."); ok = false; }

        try {
            rewardTokens = Integer.parseInt(rewardTokensField.getText().trim());
            if (rewardTokens < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) { showFieldError(errRewardTokens, "Must be a whole number >= 0."); ok = false; }

        try {
            rewardXP = Integer.parseInt(rewardXPField.getText().trim());
            if (rewardXP < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) { showFieldError(errRewardXP, "Must be a whole number >= 0."); ok = false; }

        // 5. Energy points (only for MINI_GAME)
        Integer energyPoints = null;
        if ("MINI_GAME".equals(categoryCombo.getValue())) {
            String ep = energyPointsField.getText().trim();
            if (ep.isEmpty()) { showFieldError(errEnergyPoints, "Required for MINI_GAME."); ok = false; }
            else {
                try {
                    energyPoints = Integer.parseInt(ep);
                    if (energyPoints < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) { showFieldError(errEnergyPoints, "Must be a whole number >= 0."); ok = false; }
            }
        }

        if (!ok) return;  // stop here — all errors are shown inline

        // 6. Uniqueness check
        try {
            int excludeId = (editingGame != null) ? editingGame.getId() : 0;
            if (gameService.gameNameExists(name, excludeId)) {
                showFieldError(errName, "A game with this name already exists!");
                return;
            }
        } catch (Exception e) {
            showFieldError(errName, "DB error: " + e.getMessage()); return;
        }

        // 7. Build and persist
        Game game = (editingGame != null) ? editingGame : new Game();
        game.setName(name);
        game.setDescription(description);
        game.setType(typeCombo.getValue());
        game.setDifficulty(difficultyCombo.getValue());
        game.setCategory(categoryCombo.getValue());
        game.setTokenCost(tokenCost);
        game.setRewardTokens(rewardTokens);
        game.setRewardXP(rewardXP);
        game.setEnergyPoints(energyPoints);
        game.setActive(isActiveCheck.isSelected());

        try {
            if (editingGame == null) {
                gameService.addGame(game);
            } else {
                gameService.updateGame(game);
            }
        } catch (Exception e) {
            showFieldError(errName, "Save error: " + e.getMessage()); return;
        }

        // 8. Sync reward links
        if (game.getId() > 0) {
            try { syncRewardLinks(game.getId()); }
            catch (Exception e) { System.err.println("Warning: reward links: " + e.getMessage()); }
        }

        closeWindow();
    }

    // ── Shows an error message directly under a specific field ────────────────
    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    // ── Hides all per-field error labels at the start of each save attempt ────
    private void clearErrors() {
        for (Label l : new Label[]{errName, errDescription, errType, errDifficulty,
                errCategory, errTokenCost, errRewardTokens, errRewardXP, errEnergyPoints}) {
            l.setText("");
            l.setVisible(false);
            l.setManaged(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleCancel() — close without saving
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void handleCancel() {
        closeWindow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Load all rewards into the ListView for linking. */
    private void loadRewardsList() {
        try {
            rewardsList.setItems(
                    FXCollections.observableArrayList(rewardService.getAllRewards())
            );
        } catch (Exception e) {
            showFieldError(errName, "Could not load rewards: " + e.getMessage());
        }
    }

    /**
     * Sync the game_rewards junction table so it exactly matches
     * whatever the user has selected in the ListView.
     */
    private void syncRewardLinks(int gameId) throws Exception {
        // Remove all existing links for this game
        List<Reward> currentLinks = gameService.getRewardsForGame(gameId);
        for (Reward r : currentLinks) {
            gameService.removeRewardFromGame(gameId, r.getId());
        }
        // Re-add only the ones selected in the UI
        for (Reward selected : rewardsList.getSelectionModel().getSelectedItems()) {
            gameService.addRewardToGame(gameId, selected.getId());
        }
    }

    private void closeWindow() {
        ((Stage) saveBtn.getScene().getWindow()).close();
    }
}
