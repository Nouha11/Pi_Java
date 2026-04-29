package controllers.gamification;


import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.*;

import java.util.ArrayList;
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
    @FXML private TextField puzzleNewWordField;
    @FXML private TextField puzzleNewHintField;
    @FXML private javafx.scene.layout.VBox puzzleWordsContainer; // chip list
    @FXML private Label puzzleCountLabel;
    @FXML private javafx.scene.layout.VBox puzzlePreview;
    @FXML private javafx.scene.layout.HBox puzzleTilesBox;
    @FXML private Label puzzleHintPreview;
    // MEMORY
    @FXML private javafx.scene.layout.VBox memoryContent;
    @FXML private TextField memoryNewWordField;
    @FXML private javafx.scene.layout.FlowPane memoryChipsPane;
    @FXML private Label memoryCountLabel;
    @FXML private Label lblMemoryAIStatus;
    @FXML private Button btnResolveEmojis;
    @FXML private TextArea memoryWordsArea;      // hidden raw storage
    // TRIVIA
    @FXML private javafx.scene.layout.VBox triviaContent;
    @FXML private TextField triviaTopicField;
    @FXML private Spinner<Integer> triviaCountSpinner;
    @FXML private TextArea triviaQuestionsArea;      // hidden raw storage
    @FXML private javafx.scene.layout.VBox triviaPreviewPane; // visual cards
    @FXML private Button btnGenerateAI;
    @FXML private Label lblAIStatus;
    @FXML private Button btnAddManual;
    // ARCADE
    @FXML private javafx.scene.layout.VBox arcadeContent;

    // ── Services ──────────────────────────────────────────────────────────────
    private final GameService        gameService        = new GameService();
    private final RewardService      rewardService      = new RewardService();
    private final GameContentService contentService     = new GameContentService();
    private final HuggingFaceService hfService          = new HuggingFaceService();

    private Game editingGame = null;

    // Puzzle word list: each entry is [word, hint]
    private final List<String[]> puzzleWords = new ArrayList<>();

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

        // Puzzle: live scramble preview on new-word field
        if (puzzleNewWordField != null) {
            puzzleNewWordField.textProperty().addListener((obs, o, n) -> updatePuzzlePreview());
            puzzleNewHintField.textProperty().addListener((obs, o, n) -> updatePuzzlePreview());
        }

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
                List<String[]> loaded = GameContentService.parsePuzzleWords(json);
                puzzleWords.clear();
                puzzleWords.addAll(loaded);
                javafx.application.Platform.runLater(this::refreshPuzzleChips);
            }
            case "MEMORY" -> {
                String arr = GameContentService.extractArray(json, "words");
                if (arr != null) {
                    String[] words = GameContentService.parseStringArray(arr);
                    memoryWords.clear();
                    for (String w : words) if (!w.isBlank()) memoryWords.add(w);
                    if (memoryWordsArea != null) memoryWordsArea.setText(String.join("\n", memoryWords));
                    javafx.application.Platform.runLater(this::refreshMemoryChips);
                }
            }
            case "TRIVIA" -> {
                String topic = GameContentService.extractString(json, "topic");
                if (triviaTopicField != null && topic != null) triviaTopicField.setText(topic);
                // Parse questions from stored JSON format and convert to form format for editing
                List<HuggingFaceService.TriviaQuestion> loadedQs = parseJsonToQuestions(json);
                if (!loadedQs.isEmpty()) {
                    // Rebuild the raw form text from parsed questions
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < loadedQs.size(); i++) {
                        if (i > 0) sb.append("\n---\n");
                        sb.append(loadedQs.get(i).toFormFormat());
                    }
                    if (triviaQuestionsArea != null) triviaQuestionsArea.setText(sb.toString());
                    // Render visual cards
                    javafx.application.Platform.runLater(() -> renderQuestionCards(loadedQs, null));
                } else {
                    // Fallback: try to parse as form format directly
                    if (triviaQuestionsArea != null) triviaQuestionsArea.setText(json);
                    javafx.application.Platform.runLater(() -> {
                        List<HuggingFaceService.TriviaQuestion> qs = parseRawToQuestions(
                            triviaQuestionsArea != null ? triviaQuestionsArea.getText() : "");
                        renderQuestionCards(qs, null);
                    });
                }
            }
            case "ARCADE" -> {
                // Arcade no longer has custom content — nothing to load
            }
        }
    }

    // ── PUZZLE multi-word management ──────────────────────────────────────────

    @FXML
    private void handleAddPuzzleWord() {
        if (puzzleNewWordField == null) return;
        String word = puzzleNewWordField.getText().trim().toUpperCase();
        if (word.isEmpty()) return;
        if (puzzleWords.size() >= 15) {
            puzzleNewWordField.setStyle("-fx-border-color:#e53e3e;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:8;");
            return;
        }
        String hint = puzzleNewHintField != null ? puzzleNewHintField.getText().trim() : "";
        puzzleWords.add(new String[]{word, hint});
        puzzleNewWordField.clear();
        if (puzzleNewHintField != null) puzzleNewHintField.clear();
        puzzleNewWordField.setStyle("-fx-font-size:13px;-fx-padding:8;-fx-background-radius:6;-fx-border-color:#c3c9f5;-fx-border-radius:6;");
        refreshPuzzleChips();
        updatePuzzlePreview();
    }

    private void refreshPuzzleChips() {
        if (puzzleWordsContainer == null) return;
        puzzleWordsContainer.getChildren().clear();
        for (int i = 0; i < puzzleWords.size(); i++) {
            final int idx = i;
            String[] entry = puzzleWords.get(i);
            String word = entry[0];
            String hint = entry.length > 1 ? entry[1] : "";

            // Word badge
            Label wordLbl = new Label(word);
            wordLbl.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-font-size:13px;" +
                             "-fx-font-weight:bold;-fx-background-radius:6;-fx-padding:4 10;");

            // Hint label
            Label hintLbl = new Label(hint.isEmpty() ? "no hint" : hint);
            hintLbl.setStyle("-fx-text-fill:" + (hint.isEmpty() ? "#a0aec0" : "#718096") + ";" +
                             "-fx-font-size:11px;-fx-font-style:" + (hint.isEmpty() ? "italic" : "normal") + ";");
            hintLbl.setMaxWidth(200);

            // Number badge
            Label numLbl = new Label(String.valueOf(i + 1));
            numLbl.setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-size:10px;" +
                            "-fx-font-weight:bold;-fx-background-radius:50;-fx-padding:2 6;-fx-min-width:20;-fx-alignment:center;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Remove button
            Button del = new Button("×");
            del.setStyle("-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;-fx-font-size:13px;" +
                         "-fx-font-weight:bold;-fx-background-radius:50;-fx-padding:2 7;-fx-cursor:hand;");
            del.setOnAction(e -> {
                puzzleWords.remove(idx);
                refreshPuzzleChips();
                updatePuzzlePreview();
            });

            HBox row = new HBox(10, numLbl, wordLbl, hintLbl, spacer, del);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-color:white;-fx-background-radius:8;" +
                         "-fx-border-color:#e4e8f0;-fx-border-radius:8;-fx-border-width:1;");
            puzzleWordsContainer.getChildren().add(row);
        }

        if (puzzleCountLabel != null) {
            int count = puzzleWords.size();
            String color = count == 0 ? "#e53e3e" : "#27ae60";
            puzzleCountLabel.setText(count + " word" + (count == 1 ? "" : "s") + " added" +
                                     (count == 0 ? " (add at least 1)" : ""));
            puzzleCountLabel.setStyle("-fx-font-size:11px;-fx-text-fill:" + color + ";");
        }
    }

    private void updatePuzzlePreview() {
        if (puzzlePreview == null) return;
        // Preview the word currently typed in the input field (before adding)
        String word = puzzleNewWordField != null ? puzzleNewWordField.getText().trim().toUpperCase() : "";
        // If input is empty but we have saved words, preview the first one
        if (word.isEmpty() && !puzzleWords.isEmpty()) word = puzzleWords.get(0)[0];
        if (word.isEmpty()) {
            puzzlePreview.setVisible(false); puzzlePreview.setManaged(false); return;
        }
        puzzlePreview.setVisible(true); puzzlePreview.setManaged(true);
        if (puzzleTilesBox != null) {
            puzzleTilesBox.getChildren().clear();
            String scrambled = scrambleWord(word);
            for (char c : scrambled.toCharArray()) {
                Label tile = new Label(String.valueOf(c));
                tile.setPrefSize(36, 36); tile.setAlignment(Pos.CENTER);
                tile.setStyle("-fx-background-color:linear-gradient(to bottom,#f6d365,#fda085);" +
                              "-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:bold;" +
                              "-fx-background-radius:6;");
                puzzleTilesBox.getChildren().add(tile);
            }
        }
        String hint = (puzzleNewHintField != null && !puzzleNewHintField.getText().isBlank())
            ? puzzleNewHintField.getText().trim()
            : (!puzzleWords.isEmpty() && !puzzleWords.get(0)[1].isEmpty() ? puzzleWords.get(0)[1] : "");
        if (puzzleHintPreview != null) {
            puzzleHintPreview.setText(hint.isEmpty() ? "" : "Hint: " + hint);
        }
    }

    private String scrambleWord(String w) {
        char[] a = w.toCharArray();
        java.util.Random r = new java.util.Random(42); // fixed seed for consistent preview
        for (int i = a.length - 1; i > 0; i--) { int j = r.nextInt(i + 1); char t = a[i]; a[i] = a[j]; a[j] = t; }
        return new String(a);
    }

    // ── MEMORY word management ────────────────────────────────────────────────
    private final List<String> memoryWords = new ArrayList<>();

    @FXML
    private void handleAddMemoryWord() {
        if (memoryNewWordField == null) return;
        String word = memoryNewWordField.getText().trim();
        if (word.isEmpty()) return;
        if (memoryWords.size() >= 8) {
            memoryNewWordField.setStyle("-fx-border-color:#e53e3e;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:8;");
            return;
        }
        memoryWords.add(word);
        memoryNewWordField.clear();
        memoryNewWordField.setStyle("-fx-font-size:13px;-fx-padding:8;-fx-background-radius:6;-fx-border-color:#c3c9f5;-fx-border-radius:6;");
        refreshMemoryChips();
        syncMemoryRaw();
    }

    private void refreshMemoryChips() {
        if (memoryChipsPane == null) return;
        memoryChipsPane.getChildren().clear();
        for (int i = 0; i < memoryWords.size(); i++) {
            final int idx = i;
            String word = memoryWords.get(i);
            // Show emoji if resolved, else show the raw word
            boolean isEmoji = word.codePoints().anyMatch(cp ->
                (cp >= 0x1F300 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF));
            Label chip = new Label(word);
            chip.setStyle(isEmoji
                ? "-fx-font-size:22px;-fx-background-color:#f3e5f5;-fx-background-radius:20;-fx-padding:4 10;"
                : "-fx-background-color:#f3e5f5;-fx-text-fill:#805ad5;-fx-font-size:12px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:4 10;");
            Button del = new Button("×");
            del.setStyle("-fx-background-color:#e9d8fd;-fx-text-fill:#805ad5;-fx-font-size:12px;" +
                         "-fx-font-weight:bold;-fx-background-radius:50;-fx-padding:2 6;-fx-cursor:hand;");
            del.setOnAction(e -> { memoryWords.remove(idx); refreshMemoryChips(); syncMemoryRaw(); });
            HBox chipBox = new HBox(4, chip, del); chipBox.setAlignment(Pos.CENTER);
            chipBox.setStyle("-fx-background-color:#f3e5f5;-fx-background-radius:20;-fx-padding:2 4;");
            memoryChipsPane.getChildren().add(chipBox);
        }
        if (memoryCountLabel != null) {
            int count = memoryWords.size();
            String color = count < 4 ? "#e53e3e" : count <= 8 ? "#27ae60" : "#d97706";
            memoryCountLabel.setText(count + " / 8 words added" + (count < 4 ? " (minimum 4)" : ""));
            memoryCountLabel.setStyle("-fx-font-size:11px;-fx-text-fill:" + color + ";");
        }
    }

    private void syncMemoryRaw() {
        if (memoryWordsArea != null) memoryWordsArea.setText(String.join("\n", memoryWords));
    }

    /** AI resolves typed words (even misspelled) into matching emojis. */
    @FXML
    private void handleResolveMemoryEmojis() {
        if (memoryWords.isEmpty()) {
            setMemoryAIStatus("Add some words first.", true); return;
        }
        if (btnResolveEmojis != null) { btnResolveEmojis.setDisable(true); btnResolveEmojis.setText("Resolving..."); }
        setMemoryAIStatus("AI is finding emojis... this may take 10-20 seconds.", false);

        Thread t = new Thread(() -> {
            try {
                List<String> emojis = hfService.resolveMemoryEmojis(new ArrayList<>(memoryWords));
                javafx.application.Platform.runLater(() -> {
                    // Replace words with resolved emojis
                    for (int i = 0; i < emojis.size() && i < memoryWords.size(); i++) {
                        memoryWords.set(i, emojis.get(i));
                    }
                    refreshMemoryChips();
                    syncMemoryRaw();
                    setMemoryAIStatus("Emojis resolved! Cards will show these icons.", false);
                    if (btnResolveEmojis != null) { btnResolveEmojis.setDisable(false); btnResolveEmojis.setText("Resolve with AI"); }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    setMemoryAIStatus("Error: " + e.getMessage(), true);
                    if (btnResolveEmojis != null) { btnResolveEmojis.setDisable(false); btnResolveEmojis.setText("Resolve with AI"); }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void setMemoryAIStatus(String msg, boolean isError) {
        if (lblMemoryAIStatus == null) return;
        lblMemoryAIStatus.setText(msg);
        lblMemoryAIStatus.setStyle(isError
            ? "-fx-font-size:11px;-fx-text-fill:#e53e3e;"
            : "-fx-font-size:11px;-fx-text-fill:#3b4fd8;");
    }

    @FXML
    private void handleGenerateAI() {
        String topic = triviaTopicField != null ? triviaTopicField.getText().trim() : "";
        if (topic.isEmpty()) {
            setAIStatus("Please enter a topic first.", true); return;
        }

        String difficulty = difficultyCombo.getValue() != null ? difficultyCombo.getValue() : "MEDIUM";
        int count = triviaCountSpinner != null ? triviaCountSpinner.getValue() : 5;

        if (btnGenerateAI != null) { btnGenerateAI.setDisable(true); btnGenerateAI.setText("Generating..."); }
        setAIStatus("Calling Hugging Face AI... this may take 10-30 seconds.", false);

        Thread thread = new Thread(() -> {
            try {
                List<HuggingFaceService.TriviaQuestion> questions =
                    hfService.generateTriviaQuestions(topic, count, difficulty);

                // Build raw text for storage — clean AI artifacts first
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < questions.size(); i++) {
                    if (i > 0) sb.append("\n---\n");
                    HuggingFaceService.TriviaQuestion q = questions.get(i);
                    // Clean each field before storing
                    sb.append(new HuggingFaceService.TriviaQuestion(
                        cleanAiText(q.question),
                        q.choices.stream().map(this::cleanAiText).collect(java.util.stream.Collectors.toList()),
                        q.correct
                    ).toFormFormat());
                }
                String raw = sb.toString();

                javafx.application.Platform.runLater(() -> {
                    if (triviaQuestionsArea != null) triviaQuestionsArea.setText(raw);
                    renderQuestionCards(questions, null);
                    setAIStatus("Generated " + questions.size() + " questions successfully!", false);
                    if (btnGenerateAI != null) { btnGenerateAI.setDisable(false); btnGenerateAI.setText("Generate with AI"); }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    setAIStatus("Error: " + e.getMessage(), true);
                    if (btnGenerateAI != null) { btnGenerateAI.setDisable(false); btnGenerateAI.setText("Generate with AI"); }
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void setAIStatus(String msg, boolean isError) {
        if (lblAIStatus == null) return;
        lblAIStatus.setText(msg);
        lblAIStatus.setStyle(isError
            ? "-fx-font-size:12px;-fx-text-fill:#e53e3e;"
            : "-fx-font-size:12px;-fx-text-fill:#3b4fd8;");
    }

    /** Render parsed questions as visual cards in triviaPreviewPane. */
    private void renderQuestionCards(List<HuggingFaceService.TriviaQuestion> questions, String source) {
        if (triviaPreviewPane == null) return;
        triviaPreviewPane.getChildren().clear();

        if (questions == null || questions.isEmpty()) {
            Label empty = new Label("No questions yet. Generate with AI or add manually.");
            empty.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;-fx-padding:12 0;");
            triviaPreviewPane.getChildren().add(empty);
            return;
        }

        String[] letters = {"A", "B", "C", "D"};
        for (int qi = 0; qi < questions.size(); qi++) {
            HuggingFaceService.TriviaQuestion q = questions.get(qi);

            // Question header
            Label qNum = new Label("Q" + (qi + 1));
            qNum.setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:4;-fx-padding:2 8;");
            Label qText = new Label(q.question);
            qText.setWrapText(true); qText.setMaxWidth(460);
            qText.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
            HBox qHeader = new HBox(8, qNum, qText);
            qHeader.setAlignment(Pos.TOP_LEFT);

            // Answer options
            VBox options = new VBox(4);
            for (int i = 0; i < q.choices.size() && i < 4; i++) {
                boolean isCorrect = i == q.correct;
                Label opt = new Label(letters[i] + ".  " + q.choices.get(i));
                opt.setMaxWidth(Double.MAX_VALUE);
                opt.setWrapText(true);
                if (isCorrect) {
                    opt.setStyle("-fx-background-color:#f0fff4;-fx-text-fill:#27ae60;-fx-font-weight:bold;" +
                                 "-fx-font-size:12px;-fx-padding:6 12;-fx-background-radius:6;" +
                                 "-fx-border-color:#27ae60;-fx-border-radius:6;-fx-border-width:1;");
                } else {
                    opt.setStyle("-fx-background-color:#f8f9ff;-fx-text-fill:#4a5568;" +
                                 "-fx-font-size:12px;-fx-padding:6 12;-fx-background-radius:6;" +
                                 "-fx-border-color:#e4e8f0;-fx-border-radius:6;-fx-border-width:1;");
                }
                options.getChildren().add(opt);
            }

            // Correct answer badge
            Label correctBadge = new Label("Correct: " + letters[q.correct]);
            correctBadge.setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-size:11px;" +
                                  "-fx-font-weight:bold;-fx-background-radius:4;-fx-padding:2 8;");

            // Delete button
            final int idx = qi;
            Button btnDel = new Button("Remove");
            btnDel.setStyle("-fx-background-color:transparent;-fx-text-fill:#e53e3e;-fx-font-size:11px;" +
                            "-fx-cursor:hand;-fx-border-color:#fed7d7;-fx-border-radius:4;-fx-padding:2 8;");
            btnDel.setOnAction(e -> removeQuestion(idx));

            HBox footer = new HBox(8, correctBadge, new Region(), btnDel);
            HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
            footer.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(8, qHeader, options, footer);
            card.setPadding(new Insets(12));
            card.setStyle("-fx-background-color:white;-fx-background-radius:10;" +
                          "-fx-border-color:#e4e8f0;-fx-border-radius:10;-fx-border-width:1;" +
                          "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,2);");
            triviaPreviewPane.getChildren().add(card);
        }
    }

    /** Remove a question by index and re-render. */
    private void removeQuestion(int index) {
        String raw = triviaQuestionsArea != null ? triviaQuestionsArea.getText().trim() : "";
        if (raw.isEmpty()) return;
        String[] blocks = raw.split("\\n---\\n");
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < blocks.length; i++) {
            if (i != index && !blocks[i].trim().isEmpty()) kept.add(blocks[i].trim());
        }
        String newRaw = String.join("\n---\n", kept);
        if (triviaQuestionsArea != null) triviaQuestionsArea.setText(newRaw);
        // Re-parse and re-render
        List<HuggingFaceService.TriviaQuestion> parsed = parseRawToQuestions(newRaw);
        renderQuestionCards(parsed, null);
    }

    /**
     * Parse questions from the stored JSON format (buildTriviaJson output):
     * {"topic":"...","questions":[{"question":"...","choices":["a","b","c","d"],"correct":N},...]}
     */
    private List<HuggingFaceService.TriviaQuestion> parseJsonToQuestions(String json) {
        List<HuggingFaceService.TriviaQuestion> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        String arr = GameContentService.extractArray(json, "questions");
        if (arr == null) return result;
        int pos = 0;
        while (pos < arr.length()) {
            int objStart = arr.indexOf("{", pos);
            if (objStart == -1) break;
            int depth = 0, objEnd = objStart;
            for (; objEnd < arr.length(); objEnd++) {
                if (arr.charAt(objEnd) == '{') depth++;
                else if (arr.charAt(objEnd) == '}') { depth--; if (depth == 0) { objEnd++; break; } }
            }
            String obj = arr.substring(objStart, objEnd);
            String q = GameContentService.extractString(obj, "question");
            String choicesArr = GameContentService.extractArray(obj, "choices");
            int correct = 0;
            int ci = obj.indexOf("\"correct\":");
            if (ci != -1) {
                int ns = ci + 10, ne = ns;
                while (ne < obj.length() && Character.isDigit(obj.charAt(ne))) ne++;
                try { correct = Integer.parseInt(obj.substring(ns, ne)); } catch (Exception ignored) {}
            }
            if (q != null && choicesArr != null) {
                String[] rawChoices = GameContentService.parseStringArray(choicesArr);
                if (rawChoices.length >= 4) {
                    List<String> choices = new ArrayList<>();
                    for (String c : rawChoices) choices.add(cleanAiText(c));
                    result.add(new HuggingFaceService.TriviaQuestion(cleanAiText(q), choices, correct));
                }
            }
            pos = objEnd;
        }
        return result;
    }

    /** Parse raw Q:/A:/B:/C:/D:/ANS: text back into TriviaQuestion objects for display. */
    private List<HuggingFaceService.TriviaQuestion> parseRawToQuestions(String raw) {
        List<HuggingFaceService.TriviaQuestion> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        String[] blocks = raw.split("\\n---\\n");
        for (String block : blocks) {
            String q = null; List<String> choices = new ArrayList<>(); int correct = 0;
            for (String line : block.split("\\n")) {
                line = line.trim();
                if (line.startsWith("Q:"))   q = line.substring(2).trim();
                else if (line.startsWith("A:")) choices.add(line.substring(2).trim());
                else if (line.startsWith("B:")) choices.add(line.substring(2).trim());
                else if (line.startsWith("C:")) choices.add(line.substring(2).trim());
                else if (line.startsWith("D:")) choices.add(line.substring(2).trim());
                else if (line.startsWith("ANS:")) {
                    String letter = line.substring(4).trim().toUpperCase();
                    correct = switch (letter) { case "B" -> 1; case "C" -> 2; case "D" -> 3; default -> 0; };
                }
            }
            if (q != null && choices.size() == 4) result.add(new HuggingFaceService.TriviaQuestion(q, choices, correct));
        }
        return result;
    }

    /** Show a dialog to add a question manually. */
    @FXML
    private void handleAddManual() {
        Dialog<HuggingFaceService.TriviaQuestion> dlg = new Dialog<>();
        dlg.setTitle("Add Question Manually");

        VBox content = new VBox(10); content.setPadding(new Insets(20)); content.setMinWidth(460);
        TextField qField = new TextField(); qField.setPromptText("Question text");
        qField.setStyle("-fx-font-size:13px;-fx-padding:8;-fx-background-radius:6;-fx-border-color:#c3c9f5;-fx-border-radius:6;");
        content.getChildren().add(new Label("Question:"));
        content.getChildren().add(qField);

        String[] letters = {"A", "B", "C", "D"};
        TextField[] optFields = new TextField[4];
        ToggleGroup tg = new ToggleGroup();
        for (int i = 0; i < 4; i++) {
            optFields[i] = new TextField(); optFields[i].setPromptText("Option " + letters[i]);
            optFields[i].setStyle("-fx-font-size:13px;-fx-padding:8;-fx-background-radius:6;-fx-border-color:#c3c9f5;-fx-border-radius:6;");
            RadioButton rb = new RadioButton(letters[i] + " (correct)"); rb.setToggleGroup(tg); rb.setUserData(i);
            HBox row = new HBox(8, optFields[i], rb); row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(optFields[i], Priority.ALWAYS);
            content.getChildren().addAll(row);
        }
        if (!tg.getToggles().isEmpty()) tg.getToggles().get(0).setSelected(true);

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK)
           .setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:6;-fx-padding:7 18;");

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String question = qField.getText().trim();
            if (question.isEmpty()) return null;
            List<String> choices = new ArrayList<>();
            for (TextField f : optFields) choices.add(f.getText().trim());
            int correct = tg.getSelectedToggle() != null ? (int) tg.getSelectedToggle().getUserData() : 0;
            return new HuggingFaceService.TriviaQuestion(question, choices, correct);
        });

        dlg.showAndWait().ifPresent(q -> {
            // Append to raw text
            String existing = triviaQuestionsArea != null ? triviaQuestionsArea.getText().trim() : "";
            String newEntry = q.toFormFormat();
            String newRaw = existing.isEmpty() ? newEntry : existing + "\n---\n" + newEntry;
            if (triviaQuestionsArea != null) triviaQuestionsArea.setText(newRaw);
            renderQuestionCards(parseRawToQuestions(newRaw), null);
        });
    }

    private String cleanAiText(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", " ")
                .replaceAll("\\\\(?![ntr\"\\\\])", "")
                .trim();
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
                yield puzzleWords.isEmpty() ? null
                    : GameContentService.buildPuzzleJsonMulti(puzzleWords);
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
            case "ARCADE" -> null; // Arcade is auto-configured by difficulty, no custom content needed
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
