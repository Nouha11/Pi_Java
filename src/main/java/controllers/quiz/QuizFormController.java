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

    @FXML private Label    formTitle;
    @FXML private Label    lblError;
    @FXML private TextField txtTitle;
    @FXML private Label    errTitle;
    @FXML private TextArea txtDescription;
    @FXML private Label    errDescription;
    @FXML private VBox     questionsContainer;
    @FXML private Label    lblNoQuestions;
    @FXML private Button   btnSave;

    private final QuizService     quizService     = new QuizService();
    private final QuestionService questionService = new QuestionService();
    private final ChoiceService   choiceService   = new ChoiceService();

    private Quiz editingQuiz = null;

    // Tracks each question card controller in order
    private final List<QuestionCardController> questionControllers = new ArrayList<>();

    @FXML
    public void initialize() {
        updateNoQuestionsLabel();
    }

    /** Called by QuizListController when editing an existing quiz. */
    public void loadQuiz(Quiz quiz) {
        this.editingQuiz = quiz;
        formTitle.setText("Edit Quiz");
        btnSave.setText("💾 Update Quiz");
        txtTitle.setText(quiz.getTitle());
        txtDescription.setText(quiz.getDescription() != null ? quiz.getDescription() : "");

        // Load existing questions + choices
        List<Question> questions = questionService.getQuestionsByQuizId(quiz.getId());
        for (Question q : questions) {
            List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
            QuestionCardController ctrl = addQuestionCard();
            ctrl.setQuestionText(q.getText());
            ctrl.setXpValue(q.getXpValue());
            ctrl.setDifficulty(q.getDifficulty());
            ctrl.setImageName(q.getImageName());
            ctrl.setChoices(choices.stream()
                    .map(c -> new QuestionCardController.ChoiceData(c.getContent(), c.isCorrect()))
                    .toList());
        }
    }

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

            int index = questionControllers.size();
            questionControllers.add(ctrl);
            ctrl.setNumber(index + 1);
            ctrl.setOnRemove(() -> removeQuestionCard(card, ctrl));

            questionsContainer.getChildren().add(card);
            updateNoQuestionsLabel();
            return ctrl;
        } catch (IOException e) {
            showError("Could not load question card: " + e.getMessage());
            return null;
        }
    }

    private void removeQuestionCard(Node card, QuestionCardController ctrl) {
        questionsContainer.getChildren().remove(card);
        questionControllers.remove(ctrl);
        // Re-number remaining cards
        for (int i = 0; i < questionControllers.size(); i++) {
            questionControllers.get(i).setNumber(i + 1);
        }
        updateNoQuestionsLabel();
    }

    @FXML
    private void handleSave() {
        hideError();
        if (!validateForm()) return;

        try {
            if (editingQuiz == null) {
                createQuiz();
            } else {
                updateQuiz();
            }
            closeWindow();
        } catch (Exception e) {
            showError("Save failed: " + e.getMessage());
        }
    }

    private void createQuiz() {
        Quiz quiz = new Quiz(txtTitle.getText().trim(),
                txtDescription.getText().trim().isEmpty() ? null : txtDescription.getText().trim());
        quizService.createQuiz(quiz);

        // Fetch back to get the generated ID
        Quiz saved = quizService.getAllQuizzes().stream()
                .filter(q -> q.getTitle().equals(quiz.getTitle()))
                .reduce((a, b) -> b) // last inserted
                .orElseThrow(() -> new RuntimeException("Could not retrieve saved quiz"));

        persistQuestionsAndChoices(saved.getId());
    }

    private void updateQuiz() {
        editingQuiz.setTitle(txtTitle.getText().trim());
        editingQuiz.setDescription(txtDescription.getText().trim().isEmpty() ? null : txtDescription.getText().trim());
        quizService.updateQuiz(editingQuiz);

        // Delete old questions/choices and re-insert
        List<Question> existing = questionService.getQuestionsByQuizId(editingQuiz.getId());
        for (Question q : existing) {
            choiceService.deleteChoicesByQuestionId(q.getId());
            questionService.deleteQuestion(q.getId());
        }
        persistQuestionsAndChoices(editingQuiz.getId());
    }

    private void persistQuestionsAndChoices(int quizId) {
        for (QuestionCardController ctrl : questionControllers) {
            Question q = new Question(ctrl.getQuestionText(), ctrl.getXpValue(), ctrl.getDifficulty(), quizId);
            q.setImageName(ctrl.getImageName());
            q.setUpdatedAt(LocalDateTime.now());
            questionService.createQuestion(q);

            for (QuestionCardController.ChoiceData cd : ctrl.getChoices()) {
                choiceService.createChoice(new Choice(cd.content(), cd.correct(), q.getId()));
            }
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    // ── Validation ────────────────────────────────────────────

    private boolean validateForm() {
        boolean ok = true;

        String title = txtTitle.getText().trim();
        if (title.isEmpty()) {
            showFieldError(errTitle, "Title is required.");
            ok = false;
        } else if (title.length() < 3) {
            showFieldError(errTitle, "Title must be at least 3 characters.");
            ok = false;
        } else if (title.length() > 255) {
            showFieldError(errTitle, "Title must not exceed 255 characters.");
            ok = false;
        } else {
            hideFieldError(errTitle);
        }

        String desc = txtDescription.getText().trim();
        if (desc.length() > 1000) {
            showFieldError(errDescription, "Description must not exceed 1000 characters.");
            ok = false;
        } else {
            hideFieldError(errDescription);
        }

        if (questionControllers.isEmpty()) {
            showError("Add at least one question before saving.");
            ok = false;
        }

        for (QuestionCardController ctrl : questionControllers) {
            if (!ctrl.validate()) ok = false;
        }

        return ok;
    }

    // ── Helpers ───────────────────────────────────────────────

    private void updateNoQuestionsLabel() {
        lblNoQuestions.setVisible(questionControllers.isEmpty());
        lblNoQuestions.setManaged(questionControllers.isEmpty());
    }

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg); lbl.setVisible(true); lbl.setManaged(true);
    }

    private void hideFieldError(Label lbl) {
        lbl.setVisible(false); lbl.setManaged(false);
    }

    private void showError(String msg) {
        lblError.setText(msg); lblError.setVisible(true); lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false); lblError.setManaged(false);
    }

    private void closeWindow() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
