package controllers.quiz;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for a single question card inside the quiz form.
 * Manages its own choices list and optional image.
 */
public class QuestionCardController {

    // ── FXML fields ───────────────────────────────────────────
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

    // Image controls
    @FXML private Button    btnPickImage;
    @FXML private Button    btnClearImage;
    @FXML private Label     lblImageName;
    @FXML private ImageView imgPreview;

    // ── State ─────────────────────────────────────────────────
    /** Filename only (e.g. "abc123.png") stored in the DB. */
    private String imageName = null;

    /** Absolute path to the quiz images resource folder. */
    private static final String IMAGES_DIR =
            "src/main/resources/images/quiz/";

    private final List<HBox> choiceRows = new ArrayList<>();

    // ── Init ──────────────────────────────────────────────────

    @FXML
    public void initialize() {
        cbDifficulty.getItems().addAll("EASY", "MEDIUM", "HARD");
        cbDifficulty.setValue("EASY");
        addChoiceRow("", false);
        addChoiceRow("", false);
    }

    public void setNumber(int n) {
        lblQuestionNumber.setText("Question " + n);
    }

    public void setOnRemove(Runnable callback) {
        btnRemoveQuestion.setOnAction(e -> callback.run());
    }

    // ── Image handling ────────────────────────────────────────

    @FXML
    private void handlePickImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Question Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        Stage stage = (Stage) btnPickImage.getScene().getWindow();
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) return;

        try {
            // Copy into resources/images/quiz/ with a unique name to avoid collisions
            String ext = chosen.getName().substring(chosen.getName().lastIndexOf('.'));
            String uniqueName = "q_" + System.currentTimeMillis() + ext;
            Path dest = Paths.get(IMAGES_DIR + uniqueName);
            Files.createDirectories(dest.getParent());
            Files.copy(chosen.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            imageName = uniqueName;
            showImagePreview(dest.toUri().toString());
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not copy image: " + e.getMessage(),
                    ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void handleClearImage() {
        imageName = null;
        imgPreview.setImage(null);
        imgPreview.setVisible(false);
        imgPreview.setManaged(false);
        btnClearImage.setVisible(false);
        btnClearImage.setManaged(false);
        lblImageName.setVisible(false);
        lblImageName.setManaged(false);
    }

    private void showImagePreview(String uri) {
        try {
            imgPreview.setImage(new Image(uri, true));
            imgPreview.setVisible(true);
            imgPreview.setManaged(true);
            lblImageName.setText(imageName);
            lblImageName.setVisible(true);
            lblImageName.setManaged(true);
            btnClearImage.setVisible(true);
            btnClearImage.setManaged(true);
        } catch (Exception e) {
            // image load failed — just skip preview
        }
    }

    // ── Choices ───────────────────────────────────────────────

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
        btnRemove.setStyle("-fx-background-color:transparent;-fx-text-fill:#c62828;" +
                "-fx-cursor:hand;-fx-padding:2 6 2 6;");

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

    // ── Validation ────────────────────────────────────────────

    public boolean validate() {
        boolean ok = true;

        if (txtQuestionText.getText().trim().isEmpty()) {
            showFieldError(errQuestionText, "Question text is required.");
            ok = false;
        } else {
            hideFieldError(errQuestionText);
        }

        try {
            int xp = Integer.parseInt(txtXpValue.getText().trim());
            if (xp < 0) throw new NumberFormatException();
            hideFieldError(errXpValue);
        } catch (NumberFormatException e) {
            showFieldError(errXpValue, "XP must be a positive number.");
            ok = false;
        }

        if (cbDifficulty.getValue() == null) {
            showFieldError(errDifficulty, "Select a difficulty.");
            ok = false;
        } else {
            hideFieldError(errDifficulty);
        }

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

    // ── Data getters ──────────────────────────────────────────

    public String getQuestionText() { return txtQuestionText.getText().trim(); }
    public int    getXpValue()      { return Integer.parseInt(txtXpValue.getText().trim()); }
    public String getDifficulty()   { return cbDifficulty.getValue(); }
    public String getImageName()    { return imageName; }

    public List<ChoiceData> getChoices() {
        List<ChoiceData> list = new ArrayList<>();
        for (HBox row : choiceRows) {
            String  content = ((TextField) row.getChildren().get(0)).getText().trim();
            boolean correct = ((CheckBox)  row.getChildren().get(1)).isSelected();
            list.add(new ChoiceData(content, correct));
        }
        return list;
    }

    // ── Pre-fill for edit mode ────────────────────────────────

    public void setQuestionText(String text) { txtQuestionText.setText(text); }
    public void setXpValue(int xp)           { txtXpValue.setText(String.valueOf(xp)); }
    public void setDifficulty(String d)      { cbDifficulty.setValue(d); }

    public void setImageName(String name) {
        if (name == null || name.isBlank()) return;
        imageName = name;
        // Try to load preview from resources
        var url = getClass().getResource("/images/quiz/" + name);
        if (url != null) {
            showImagePreview(url.toExternalForm());
        } else {
            // Fall back to file path
            File f = new File(IMAGES_DIR + name);
            if (f.exists()) showImagePreview(f.toURI().toString());
        }
    }

    public void setChoices(List<ChoiceData> choices) {
        choiceRows.clear();
        choicesContainer.getChildren().clear();
        for (ChoiceData c : choices) addChoiceRow(c.content(), c.correct());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg); lbl.setVisible(true); lbl.setManaged(true);
    }

    private void hideFieldError(Label lbl) {
        lbl.setVisible(false); lbl.setManaged(false);
    }

    /** Simple data carrier for a choice. */
    public record ChoiceData(String content, boolean correct) {}
}
