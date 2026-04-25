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

    // Per-field error labels
    @FXML private Label errName, errDescription;
    @FXML private Label errType, errDifficulty, errCategory;
    @FXML private Label errTokenCost, errRewardTokens, errRewardXP;
    @FXML private Label errEnergyPoints;

    // ── Content section fields ────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox contentSection;
    // PUZZLE
    @FXML private javafx.scene.layout.VBox puzzleContent;
    @FXML private TextField puzzleWordField;
    @FXML private TextField puzzleHintField;
    // MEMORY
    @FXML private javafx.scene.layout.VBox memoryContent;
    @FXML private TextArea memoryWordsArea;
    // TRIVIA
    @FXML private javafx.scene.layout.VBox triviaContent;
    @FXML private TextField triviaTopicField;
    @FXML private TextArea triviaQuestionsArea;
    // ARCADE
    @FXML private javafx.scene.layout.VBox arcadeContent;
    @FXML private TextArea arcadeSentencesArea;

    // ── Services ──────────────────────────────────────────────────────────────
    private final GameService        gameService        = new GameService();
    private final RewardService      rewardService      = new RewardService();
    private final GameContentService contentService     = new GameContentService();

    private Game editingGame = null;

    @FXML
    public void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList("PUZZLE", "MEMORY", "TRIVIA", "ARCADE"));
        difficultyCombo.setItems(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));
        categoryCombo.setItems(FXCollections.observableArrayList("FULL_GAME", "MINI_GAME"));

        energyPointsField.setDisable(true);
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMini = "MINI_GAME".equals(newVal);
            energyPointsField.setDisable(!isMini);
            if (!isMini) energyPointsField.clear();
        });

        // Show/hide content panels based on type
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateContentSection(newVal));

        rewardsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadRewardsList();
        isActiveCheck.setSelected(true);

        // Hide content section initially
        if (contentSection != null) contentSection.setVisible(false);
        if (contentSection != null) contentSection.setManaged(false);
    }

    private void updateContentSection(String type) {
        if (contentSection == null) return;
        boolean show = type != null;
        contentSection.setVisible(show);
        contentSection.setManaged(show);
        if (!show) return;

        // Hide all sub-panels
        for (var panel : new javafx.scene.layout.VBox[]{puzzleContent, memoryContent, triviaContent, arcadeContent}) {
            if (panel != null) { panel.setVisible(false); panel.setManaged(false); }
        }
        // Show the relevant one
        javafx.scene.layout.VBox active = switch (type) {
            case "PUZZLE" -> puzzleContent;
            case "MEMORY" -> memoryContent;
            case "TRIVIA" -> triviaContent;
            case "ARCADE" -> arcadeContent;
            default -> null;
        };
        if (active != null) { active.setVisible(true); active.setManaged(true); }
    }

    public void setGameToEdit(Game game) {
        this.editingGame = game;
        nameField.setText(game.getName());
        descriptionArea.setText(game.getDescription());
        typeCombo.setValue(game.getType());
        difficultyCombo.setValue(game.getDifficulty());
        categoryCombo.setValue(game.getCategory());
        tokenCostField.setText(String.valueOf(game.getTokenCost()));
        rewardTokensField.setText(String.valueOf(game.getRewardTokens()));
        rewardXPField.setText(String.valueOf(game.getRewardXP()));
        isActiveCheck.setSelected(game.isActive());
        if (game.getEnergyPoints() != null) energyPointsField.setText(String.valueOf(game.getEnergyPoints()));

        // Load existing content
        try {
            String json = contentService.loadContent(game.getId());
            if (json != null) populateContentFields(game.getType(), json);
        } catch (Exception e) { System.err.println("Could not load content: " + e.getMessage()); }

        // Pre-select linked rewards
        try {
            List<Reward> assignedRewards = gameService.getRewardsForGame(game.getId());
            for (Reward assigned : assignedRewards) {
                for (Reward item : rewardsList.getItems()) {
                    if (item.getId() == assigned.getId()) rewardsList.getSelectionModel().select(item);
                }
            }
        } catch (Exception e) { showFieldError(errName, "Could not load linked rewards: " + e.getMessage()); }
    }

    private void populateContentFields(String type, String json) {
        switch (type) {
            case "PUZZLE" -> {
                String word = GameContentService.extractString(json, "word");
                String hint = GameContentService.extractString(json, "hint");
                if (puzzleWordField != null && word != null) puzzleWordField.setText(word);
                if (puzzleHintField != null && hint != null) puzzleHintField.setText(hint);
            }
            case "MEMORY" -> {
                String arr = GameContentService.extractArray(json, "words");
                if (memoryWordsArea != null && arr != null) {
                    String[] words = GameContentService.parseStringArray(arr);
                    memoryWordsArea.setText(String.join("\n", words));
                }
            }
            case "TRIVIA" -> {
                String topic = GameContentService.extractString(json, "topic");
                if (triviaTopicField != null && topic != null) triviaTopicField.setText(topic);
                // Show raw JSON in the questions area for editing
                if (triviaQuestionsArea != null) triviaQuestionsArea.setText(json);
            }
            case "ARCADE" -> {
                String arr = GameContentService.extractArray(json, "sentences");
                if (arcadeSentencesArea != null && arr != null) {
                    String[] sentences = GameContentService.parseStringArray(arr);
                    arcadeSentencesArea.setText(String.join("\n", sentences));
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        clearErrors();
        boolean ok = true;

        String name = nameField.getText().trim();
        if (name.isEmpty())         { showFieldError(errName, "Game name is required.");               ok = false; }
        else if (name.length() < 3) { showFieldError(errName, "Name must be at least 3 characters."); ok = false; }

        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) { showFieldError(errDescription, "Description is required."); ok = false; }

        if (typeCombo.getValue()       == null) { showFieldError(errType,       "Select a type.");       ok = false; }
        if (difficultyCombo.getValue() == null) { showFieldError(errDifficulty, "Select a difficulty."); ok = false; }
        if (categoryCombo.getValue()   == null) { showFieldError(errCategory,   "Select a category.");   ok = false; }

        int tokenCost = 0, rewardTokens = 0, rewardXP = 0;
        try { tokenCost = Integer.parseInt(tokenCostField.getText().trim()); if (tokenCost < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { showFieldError(errTokenCost, "Must be a whole number >= 0."); ok = false; }

        try { rewardTokens = Integer.parseInt(rewardTokensField.getText().trim()); if (rewardTokens < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { showFieldError(errRewardTokens, "Must be a whole number >= 0."); ok = false; }

        try { rewardXP = Integer.parseInt(rewardXPField.getText().trim()); if (rewardXP < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { showFieldError(errRewardXP, "Must be a whole number >= 0."); ok = false; }

        Integer energyPoints = null;
        if ("MINI_GAME".equals(categoryCombo.getValue())) {
            String ep = energyPointsField.getText().trim();
            if (ep.isEmpty()) { showFieldError(errEnergyPoints, "Required for MINI_GAME."); ok = false; }
            else { try { energyPoints = Integer.parseInt(ep); if (energyPoints < 0) throw new NumberFormatException(); }
                   catch (NumberFormatException e) { showFieldError(errEnergyPoints, "Must be a whole number >= 0."); ok = false; } }
        }

        if (!ok) return;

        try {
            int excludeId = (editingGame != null) ? editingGame.getId() : 0;
            if (gameService.gameNameExists(name, excludeId)) { showFieldError(errName, "A game with this name already exists!"); return; }
        } catch (Exception e) { showFieldError(errName, "DB error: " + e.getMessage()); return; }

        Game game = (editingGame != null) ? editingGame : new Game();
        game.setName(name); game.setDescription(description);
        game.setType(typeCombo.getValue()); game.setDifficulty(difficultyCombo.getValue());
        game.setCategory(categoryCombo.getValue()); game.setTokenCost(tokenCost);
        game.setRewardTokens(rewardTokens); game.setRewardXP(rewardXP);
        game.setEnergyPoints(energyPoints); game.setActive(isActiveCheck.isSelected());

        try {
            if (editingGame == null) gameService.addGame(game);
            else gameService.updateGame(game);
        } catch (Exception e) { showFieldError(errName, "Save error: " + e.getMessage()); return; }

        // Save game content
        if (game.getId() > 0) {
            try { saveGameContent(game.getId(), game.getType()); }
            catch (Exception e) { System.err.println("Warning: content save: " + e.getMessage()); }
            try { syncRewardLinks(game.getId()); }
            catch (Exception e) { System.err.println("Warning: reward links: " + e.getMessage()); }
        }

        closeWindow();
    }

    private void saveGameContent(int gameId, String type) throws Exception {
        String json = switch (type) {
            case "PUZZLE" -> {
                String word = puzzleWordField != null ? puzzleWordField.getText().trim() : "";
                String hint = puzzleHintField != null ? puzzleHintField.getText().trim() : "";
                yield word.isEmpty() ? null : GameContentService.buildPuzzleJson(word, hint);
            }
            case "MEMORY" -> {
                String words = memoryWordsArea != null ? memoryWordsArea.getText().trim() : "";
                yield words.isEmpty() ? null : GameContentService.buildMemoryJson(words);
            }
            case "TRIVIA" -> {
                String topic = triviaTopicField != null ? triviaTopicField.getText().trim() : "";
                String questions = triviaQuestionsArea != null ? triviaQuestionsArea.getText().trim() : "";
                yield questions.isEmpty() ? null : GameContentService.buildTriviaJson(topic, questions);
            }
            case "ARCADE" -> {
                String sentences = arcadeSentencesArea != null ? arcadeSentencesArea.getText().trim() : "";
                yield sentences.isEmpty() ? null : GameContentService.buildArcadeJson(sentences);
            }
            default -> null;
        };
        if (json != null) contentService.saveContent(gameId, json);
    }

    private void showFieldError(Label lbl, String msg) { lbl.setText(msg); lbl.setVisible(true); lbl.setManaged(true); }

    private void clearErrors() {
        for (Label l : new Label[]{errName, errDescription, errType, errDifficulty, errCategory, errTokenCost, errRewardTokens, errRewardXP, errEnergyPoints}) {
            l.setText(""); l.setVisible(false); l.setManaged(false);
        }
    }

    @FXML private void handleCancel() { closeWindow(); }

    private void loadRewardsList() {
        try { rewardsList.setItems(FXCollections.observableArrayList(rewardService.getAllRewards())); }
        catch (Exception e) { showFieldError(errName, "Could not load rewards: " + e.getMessage()); }
    }

    private void syncRewardLinks(int gameId) throws Exception {
        List<Reward> currentLinks = gameService.getRewardsForGame(gameId);
        for (Reward r : currentLinks) gameService.removeRewardFromGame(gameId, r.getId());
        for (Reward selected : rewardsList.getSelectionModel().getSelectedItems()) gameService.addRewardToGame(gameId, selected.getId());
    }

    private void closeWindow() { ((Stage) saveBtn.getScene().getWindow()).close(); }
}
