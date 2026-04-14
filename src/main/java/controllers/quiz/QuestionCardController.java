package controllers.quiz;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for a single question card inside the quiz form.
 * Manages its own choices list dynamically.
 */
public class QuestionCardController {

    @FXML private Label    lblQuestionNumber;
    @FXML private Button   btnRemoveQuestion;
    @FXML private TextArea txtQuestionText;
    @FXML private Label    errQuestionText;
    @FXML private TextField txtXpValue;
    @FXML private Label    errXpValue;
    @FXML private ComboBox<String> cbDifficulty;
    @FXML private Label    errDifficulty;
    @FXML private Button   btnAddChoice;
    @FXML private Label    errChoices;
    @FXML private VBox     choicesContainer;

    // Each entry: [TextField content, CheckBox isCorrect]
    private final List<HBox> choiceRows = new ArrayList<>();

    @FXML
    public void initialize() {
        cbDifficulty.getItems().addAll("EASY", "MEDIUM", "HARD");
        cbDifficulty.setValue("EASY");
        // Start with two blank choices
        addChoiceRow("", false);
        addChoiceRow("", false);
    }

    public void setNumber(int n) {
        lblQuestionNumber.setText("Question " + n);
    }

    public void setOnRemove(Runnable callback) {
        btnRemoveQuestion.setOnAction(e -> callback.run());
    }

    @FXML
    private void handleAddChoice() {
        addChoiceRow("", false);
    }

    private void addChoiceRow(String content, boolean correct) {
        TextField tfContent = new TextField(content);
        tfContent.setPromptText("Choice text…");
        HBox.setHgrow(tfContent, Priority.ALWAYS);

        CheckBox cbCorrect = new CheckBox("Correct");
        cbCorrect.setSelected(correct);
        cbCorrect.setStyle("-fx-font-size:11px;");

        Button btnRemove = new Button("✕");
        btnRemove.setStyle("-fx-background-color:transparent;-fx-text-fill:#c62828;-fx-cursor:hand;-fx-padding:2 6 2 6;");

        HBox row = new HBox(8, tfContent, cbCorrect, btnRemove);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("choice-row");
        row.setPadding(new Insets(6, 8, 6, 8));

        btnRemove.setOnAction(e -> {
            choicesContainer.getChildren().remove(row);
            choiceRows.remove(row);
        });

        choiceRows.add(row);
        choicesContainer.getChildren().add(row);
    }

    // ── Validation & data extraction ──────────────────────────

    public boolean validate() {
        boolean ok = true;

        // Question text
        if (txtQuestionText.getText().trim().isEmpty()) {
            showFieldError(errQuestionText, "Question text is required.");
            ok = false;
        } else {
            hideFieldError(errQuestionText);
        }

        // XP value
        try {
            int xp = Integer.parseInt(txtXpValue.getText().trim());
            if (xp < 0) throw new NumberFormatException();
            hideFieldError(errXpValue);
        } catch (NumberFormatException e) {
            showFieldError(errXpValue, "XP must be a positive number.");
            ok = false;
        }

        // Difficulty
        if (cbDifficulty.getValue() == null) {
            showFieldError(errDifficulty, "Select a difficulty.");
            ok = false;
        } else {
            hideFieldError(errDifficulty);
        }

        // Choices — need at least 2, exactly one correct
        if (choiceRows.size() < 2) {
            showFieldError(errChoices, "Add at least 2 choices.");
            ok = false;
        } else {
            long correctCount = choiceRows.stream()
                    .map(r -> (CheckBox) r.getChildren().get(1))
                    .filter(CheckBox::isSelected)
                    .count();
            if (correctCount != 1) {
                showFieldError(errChoices, "Exactly one choice must be marked correct.");
                ok = false;
            } else {
                hideFieldError(errChoices);
            }
        }

        return ok;
    }

    public String getQuestionText()  { return txtQuestionText.getText().trim(); }
    public int    getXpValue()       { return Integer.parseInt(txtXpValue.getText().trim()); }
    public String getDifficulty()    { return cbDifficulty.getValue(); }

    /** Returns list of [content, isCorrect] pairs for each choice row. */
    public List<ChoiceData> getChoices() {
        List<ChoiceData> list = new ArrayList<>();
        for (HBox row : choiceRows) {
            String  content = ((TextField) row.getChildren().get(0)).getText().trim();
            boolean correct = ((CheckBox)  row.getChildren().get(1)).isSelected();
            list.add(new ChoiceData(content, correct));
        }
        return list;
    }

    // ── Pre-fill for edit mode ─────────────────────────────────

    public void setQuestionText(String text)  { txtQuestionText.setText(text); }
    public void setXpValue(int xp)            { txtXpValue.setText(String.valueOf(xp)); }
    public void setDifficulty(String d)       { cbDifficulty.setValue(d); }

    public void setChoices(List<ChoiceData> choices) {
        choiceRows.clear();
        choicesContainer.getChildren().clear();
        for (ChoiceData c : choices) addChoiceRow(c.content(), c.correct());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void hideFieldError(Label lbl) {
        lbl.setVisible(false);
        lbl.setManaged(false);
    }

    /** Simple data carrier for a choice. */
    public record ChoiceData(String content, boolean correct) {}
}
