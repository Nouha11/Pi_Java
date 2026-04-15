package controllers.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.ChoiceService;
import services.quiz.QuestionService;
import services.quiz.QuizService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuizFormController {

    @FXML private Label     formTitle;
    @FXML private Label     lblError;
    @FXML private TextField txtTitle;
    @FXML private Label     errTitle;
    @FXML private TextArea  txtDescription;
    @FXML private Label     errDescription;
    @FXML private VBox      questionsContainer;
    @FXML private Label     lblNoQuestions;
    @FXML private Button    btnSave;

    private final QuizService     quizService     = new QuizService();
    private final QuestionService questionService = new QuestionService();
    private final ChoiceService   choiceService   = new ChoiceService();

    private Quiz editingQuiz = null;
    private final List<QuestionCardController> questionControllers = new ArrayList<>();

    @FXML
    public void initialize() {
        updateNoQuestionsLabel();
        attachLiveValidation();
    }

    // ── Live validation wiring ────────────────────────────────

    private void attachLiveValidation() {
        // Title: validate on every keystroke
        txtTitle.textProperty().addListener((obs, oldVal, newVal) -> validateTitle(newVal));

        // Description: validate on every keystroke
        txtDescription.textProperty().addListener((obs, oldVal, newVal) -> validateDescription(newVal));
    }

    private boolean validateTitle(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            setFieldError(txtTitle, errTitle, "Title is required.");
            return false;
        } else if (v.length() < 3) {
            setFieldError(txtTitle, errTitle, "Title must be at least 3 characters.");
            return false;
        } else if (v.length() > 255) {
            setFieldError(txtTitle, errTitle, "Title must not exceed 255 characters.");
            return false;
        }
        clearFieldError(txtTitle, errTitle);
        return true;
    }

    private boolean validateDescription(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() > 1000) {
            setFieldError(txtDescription, errDescription,
                    "Description must not exceed 1000 characters (" + v.length() + "/1000).");
            return false;
        }
        clearFieldError(txtDescription, errDescription);
        return true;
    }

    // ── Load existing quiz (edit mode) ────────────────────────

    public void loadQuiz(Quiz quiz) {
        this.editingQuiz = quiz;
        formTitle.setText("Edit Quiz");
        btnSave.setText("💾  Update Quiz");
        txtTitle.setText(quiz.getTitle());
        txtDescription.setText(quiz.getDescription() != null ? quiz.getDescription() : "");

        List<Question> questions = questionService.getQuestionsByQuizId(quiz.getId());
        for (Question q : questions) {
            List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
            QuestionCardController ctrl = addQuestionCard();
            ctrl.setQuestionText(q.getText());
            ctrl.setXpValue(q.getXpValue());
            ctrl.setDifficulty(q.getDifficulty());
            ctrl.setChoices(choices.stream()
                    .map(c -> new QuestionCardController.ChoiceData(c.getContent(), c.isCorrect()))
                    .toList());
        }
    }

    // ── Question cards ────────────────────────────────────────

    @FXML
    private void handleAddQuestion() {
        addQuestionCard();
    }

    private QuestionCardController addQuestionCard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/question_card.fxml"));
            QuestionCardController ctrl = new QuestionCardController();
            loader.setController(ctrl);
            Node card = loader.load();

            questionControllers.add(ctrl);
            ctrl.setNumber(questionControllers.size());
            ctrl.setOnRemove(() -> removeQuestionCard(card, ctrl));

            questionsContainer.getChildren().add(card);
            updateNoQuestionsLabel();
            return ctrl;
        } catch (IOException e) {
            showBanner("Could not load question card: " + e.getMessage());
            return null;
        }
    }

    private void removeQuestionCard(Node card, QuestionCardController ctrl) {
        questionsContainer.getChildren().remove(card);
        questionControllers.remove(ctrl);
        for (int i = 0; i < questionControllers.size(); i++) {
            questionControllers.get(i).setNumber(i + 1);
        }
        updateNoQuestionsLabel();
    }

    // ── Save ──────────────────────────────────────────────────

    @FXML
    private void handleSave() {
        hideBanner();
        if (!validateAll()) return;

        try {
            if (editingQuiz == null) createQuiz();
            else                     updateQuiz();
            closeWindow();
        } catch (Exception e) {
            showBanner("Save failed: " + e.getMessage());
        }
    }

    private boolean validateAll() {
        boolean ok = true;

        ok &= validateTitle(txtTitle.getText());
        ok &= validateDescription(txtDescription.getText());

        if (questionControllers.isEmpty()) {
            showBanner("Add at least one question before saving.");
            ok = false;
        }

        for (QuestionCardController ctrl : questionControllers) {
            if (!ctrl.validate()) ok = false;
        }

        return ok;
    }

    private void createQuiz() {
        Quiz quiz = new Quiz(txtTitle.getText().trim(),
                txtDescription.getText().trim().isEmpty() ? null : txtDescription.getText().trim());
        quizService.createQuiz(quiz);

        Quiz saved = quizService.getAllQuizzes().stream()
                .filter(q -> q.getTitle().equals(quiz.getTitle()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new RuntimeException("Could not retrieve saved quiz"));

        persistQuestionsAndChoices(saved.getId());
    }

    private void updateQuiz() {
        editingQuiz.setTitle(txtTitle.getText().trim());
        editingQuiz.setDescription(txtDescription.getText().trim().isEmpty()
                ? null : txtDescription.getText().trim());
        quizService.updateQuiz(editingQuiz);

        List<Question> existing = questionService.getQuestionsByQuizId(editingQuiz.getId());
        for (Question q : existing) {
            choiceService.deleteChoicesByQuestionId(q.getId());
            questionService.deleteQuestion(q.getId());
        }
        persistQuestionsAndChoices(editingQuiz.getId());
    }

    private void persistQuestionsAndChoices(int quizId) {
        for (QuestionCardController ctrl : questionControllers) {
            Question q = new Question(ctrl.getQuestionText(), ctrl.getXpValue(),
                    ctrl.getDifficulty(), quizId);
            q.setUpdatedAt(LocalDateTime.now());
            questionService.createQuestion(q);

            for (QuestionCardController.ChoiceData cd : ctrl.getChoices()) {
                choiceService.createChoice(new Choice(cd.content(), cd.correct(), q.getId()));
            }
        }
    }

    @FXML
    private void handleCancel() { closeWindow(); }

    // ── UI helpers ────────────────────────────────────────────

    private void setFieldError(Control field, Label errLabel, String msg) {
        field.getStyleClass().remove("field-invalid");
        field.getStyleClass().add("field-invalid");
        errLabel.setText(msg);
        errLabel.setVisible(true);
        errLabel.setManaged(true);
    }

    private void clearFieldError(Control field, Label errLabel) {
        field.getStyleClass().remove("field-invalid");
        errLabel.setVisible(false);
        errLabel.setManaged(false);
    }

    private void showBanner(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideBanner() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void updateNoQuestionsLabel() {
        boolean empty = questionControllers.isEmpty();
        lblNoQuestions.setVisible(empty);
        lblNoQuestions.setManaged(empty);
    }

    private void closeWindow() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
