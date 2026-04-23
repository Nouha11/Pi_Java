package controllers.quiz;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class QuestionCardController {

    @FXML private Label              lblQuestionNumber;
    @FXML private Button             btnRemoveQuestion;
    @FXML private TextArea           txtQuestionText;
    @FXML private Label              errQuestionText;
    @FXML private TextField          txtXpValue;
    @FXML private Label              errXpValue;
    @FXML private ComboBox<String>   cbDifficulty;
    @FXML private Label              errDifficulty;
    @FXML private Button             btnAddChoice;
    @FXML private Label              errChoices;
    @FXML private VBox               choicesContainer;

    private final List<HBox> choiceRows = new ArrayList<>();

    @FXML
    public void initialize() {
        cbDifficulty.getItems().addAll("EASY", "MEDIUM", "HARD");
        cbDifficulty.setValue("EASY");

        // Live validation listeners
        txtQuestionText.textProperty().addListener((o, old, val) -> validateQuestionText(val));
        txtXpValue.textProperty().addListener((o, old, val) -> validateXp(val));
        cbDifficulty.valueProperty().addListener((o, old, val) -> validateDifficulty(val));

        // Only allow digits in XP field
        txtXpValue.textProperty().addListener((o, old, val) -> {
            if (!val.matches("\\d*")) txtXpValue.setText(val.replaceAll("[^\\d]", ""));
        });

        addChoiceRow("", false);
        addChoiceRow("", false);
    }

    // ── Setters ───────────────────────────────────────────────

    public void setNumber(int n)         { lblQuestionNumber.setText("Question " + n); }
    public void setOnRemove(Runnable cb) { btnRemoveQuestion.setOnAction(e -> cb.run()); }
    public void setQuestionText(String t){ txtQuestionText.setText(t); }
    public void setXpValue(int xp)       { txtXpValue.setText(String.valueOf(xp)); }
    public void setDifficulty(String d)  { cbDifficulty.setValue(d); }

    public void setChoices(List<ChoiceData> choices) {
        choiceRows.clear();
        choicesContainer.getChildren().clear();
        for (ChoiceData c : choices) addChoiceRow(c.content(), c.correct());
    }

    // ── Getters ───────────────────────────────────────────────

    public String          getQuestionText() { return txtQuestionText.getText().trim(); }
    public int             getXpValue()      { return Integer.parseInt(txtXpValue.getText().trim()); }
    public String          getDifficulty()   { return cbDifficulty.getValue(); }

    public List<ChoiceData> getChoices() {
        List<ChoiceData> list = new ArrayList<>();
        for (HBox row : choiceRows) {
            String  content = ((TextField) row.getChildren().get(0)).getText().trim();
            boolean correct = ((CheckBox)  row.getChildren().get(1)).isSelected();
            list.add(new ChoiceData(content, correct));
        }
        return list;
    }

    // ── Add choice ────────────────────────────────────────────

    @FXML
    private void handleAddChoice() {
        addChoiceRow("", false);
    }

    private void addChoiceRow(String content, boolean correct) {
        TextField tfContent = new TextField(content);
        tfContent.setPromptText("Choice text…");
        HBox.setHgrow(tfContent, Priority.ALWAYS);

        // Live: revalidate choices whenever content or correct flag changes
        tfContent.textProperty().addListener((o, old, val) -> validateChoices());

        CheckBox cbCorrect = new CheckBox("Correct");
        cbCorrect.setSelected(correct);
        cbCorrect.setStyle("-fx-font-size:11px;");
        cbCorrect.selectedProperty().addListener((o, old, val) -> validateChoices());

        Button btnRemove = new Button("✕");
        btnRemove.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:#e53e3e;" +
                "-fx-cursor:hand;-fx-padding:2 6 2 6;");

        HBox row = new HBox(8, tfContent, cbCorrect, btnRemove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("choice-row");
        row.setPadding(new Insets(6, 8, 6, 8));

        btnRemove.setOnAction(e -> {
            choicesContainer.getChildren().remove(row);
            choiceRows.remove(row);
            validateChoices();
        });

        choiceRows.add(row);
        choicesContainer.getChildren().add(row);
    }

    // ── Live validators ───────────────────────────────────────

    private boolean validateQuestionText(String val) {
        String v = val == null ? "" : val.trim();
        if (v.isEmpty()) {
            setErr(txtQuestionText, errQuestionText, "Question text is required.");
            return false;
        } else if (v.length() < 5) {
            setErr(txtQuestionText, errQuestionText, "Question must be at least 5 characters.");
            return false;
        } else if (v.length() > 500) {
            setErr(txtQuestionText, errQuestionText, "Question must not exceed 500 characters.");
            return false;
        }
        clearErr(txtQuestionText, errQuestionText);
        return true;
    }

    private boolean validateXp(String val) {
        String v = val == null ? "" : val.trim();
        if (v.isEmpty()) {
            setErr(txtXpValue, errXpValue, "XP value is required.");
            return false;
        }
        try {
            int xp = Integer.parseInt(v);
            if (xp < 1) {
                setErr(txtXpValue, errXpValue, "XP must be at least 1.");
                return false;
            } else if (xp > 1000) {
                setErr(txtXpValue, errXpValue, "XP must not exceed 1000.");
                return false;
            }
        } catch (NumberFormatException e) {
            setErr(txtXpValue, errXpValue, "XP must be a whole number.");
            return false;
        }
        clearErr(txtXpValue, errXpValue);
        return true;
    }

    private boolean validateDifficulty(String val) {
        if (val == null || val.isBlank()) {
            setErr(cbDifficulty, errDifficulty, "Select a difficulty.");
            return false;
        }
        clearErr(cbDifficulty, errDifficulty);
        return true;
    }

    private boolean validateChoices() {
        if (choiceRows.size() < 2) {
            showErr(errChoices, "Add at least 2 choices.");
            return false;
        }

        // Check for empty choice text
        boolean anyEmpty = choiceRows.stream()
                .map(r -> ((TextField) r.getChildren().get(0)).getText().trim())
                .anyMatch(String::isEmpty);
        if (anyEmpty) {
            showErr(errChoices, "All choice fields must have text.");
            return false;
        }

        // Check for duplicate choice text
        long distinctCount = choiceRows.stream()
                .map(r -> ((TextField) r.getChildren().get(0)).getText().trim().toLowerCase())
                .distinct().count();
        if (distinctCount < choiceRows.size()) {
            showErr(errChoices, "Choices must be unique.");
            return false;
        }

        long correctCount = choiceRows.stream()
                .map(r -> (CheckBox) r.getChildren().get(1))
                .filter(CheckBox::isSelected)
                .count();
        if (correctCount == 0) {
            showErr(errChoices, "Mark exactly one choice as correct.");
            return false;
        } else if (correctCount > 1) {
            showErr(errChoices, "Only one choice can be correct.");
            return false;
        }

        clearErr(null, errChoices);
        return true;
    }

    // ── Full validate (called by parent on save) ──────────────

    public boolean validate() {
        boolean ok = validateQuestionText(txtQuestionText.getText());
        ok &= validateXp(txtXpValue.getText());
        ok &= validateDifficulty(cbDifficulty.getValue());
        ok &= validateChoices();
        return ok;
    }

    // ── Helpers ───────────────────────────────────────────────

    private void setErr(Control field, Label lbl, String msg) {
        if (field != null) {
            field.getStyleClass().remove("field-invalid");
            field.getStyleClass().add("field-invalid");
        }
        showErr(lbl, msg);
    }

    private void clearErr(Control field, Label lbl) {
        if (field != null) field.getStyleClass().remove("field-invalid");
        lbl.setVisible(false);
        lbl.setManaged(false);
    }

    private void showErr(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    public record ChoiceData(String content, boolean correct) {}
}
