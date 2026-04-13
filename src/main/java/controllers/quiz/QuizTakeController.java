package controllers.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.ChoiceService;
import services.quiz.QuestionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizTakeController {

    @FXML private Label       lblQuizTitle;
    @FXML private Label       lblProgress;
    @FXML private Label       lblXp;
    @FXML private ProgressBar progressBar;
    @FXML private Label       lblDifficulty;
    @FXML private Label       lblXpValue;
    @FXML private Label       lblQuestionText;
    @FXML private Label       lblFeedback;
    @FXML private VBox        choicesContainer;
    @FXML private Button      btnNext;

    private final QuestionService questionService = new QuestionService();
    private final ChoiceService   choiceService   = new ChoiceService();

    private Quiz           quiz;
    private List<Question> questions;
    private int            currentIndex = 0;
    private int            xpEarned     = 0;
    private int            correctCount = 0;

    // Tracks per-question result for the results screen
    private final List<QuestionResult> results = new ArrayList<>();

    // ── Entry point ───────────────────────────────────────────

    public void loadQuiz(Quiz quiz) {
        this.quiz = quiz;
        lblQuizTitle.setText(quiz.getTitle());

        questions = questionService.getQuestionsByQuizId(quiz.getId());
        // Load choices for each question
        for (Question q : questions) {
            q.setChoices(choiceService.getChoicesByQuestionId(q.getId()));
        }

        if (questions.isEmpty()) {
            lblQuestionText.setText("This quiz has no questions yet.");
            btnNext.setDisable(true);
            return;
        }

        showQuestion(0);
    }

    // ── Question rendering ────────────────────────────────────

    private void showQuestion(int index) {
        currentIndex = index;
        Question q = questions.get(index);

        lblProgress.setText("Question " + (index + 1) + " of " + questions.size());
        progressBar.setProgress((double) index / questions.size());
        lblXp.setText("⭐ " + xpEarned + " XP");

        lblDifficulty.setText(q.getDifficulty());
        lblDifficulty.getStyleClass().setAll(difficultyBadgeClass(q.getDifficulty()));
        lblXpValue.setText("+" + q.getXpValue() + " XP");
        lblQuestionText.setText(q.getText());

        hideFeedback();
        btnNext.setDisable(true);
        btnNext.setText(index == questions.size() - 1 ? "Finish ✓" : "Next →");

        choicesContainer.getChildren().clear();
        List<Choice> choices = new ArrayList<>(q.getChoices());
        Collections.shuffle(choices);

        for (Choice c : choices) {
            Button btn = new Button(c.getContent());
            btn.getStyleClass().add("choice-btn");
            btn.setWrapText(true);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setUserData(c);
            btn.setOnAction(e -> onChoiceSelected(btn, choices, q));
            choicesContainer.getChildren().add(btn);
        }
    }

    // ── Answer handling ───────────────────────────────────────

    private void onChoiceSelected(Button selected, List<Choice> choices, Question q) {
        Choice chosen = (Choice) selected.getUserData();
        boolean correct = chosen.isCorrect();

        // Lock and colour all buttons
        for (var node : choicesContainer.getChildren()) {
            Button btn = (Button) node;
            btn.setDisable(true);
            Choice c = (Choice) btn.getUserData();
            if (c.isCorrect()) {
                btn.getStyleClass().setAll("choice-btn-correct");
            } else if (btn == selected) {
                btn.getStyleClass().setAll("choice-btn-wrong");
            } else {
                btn.getStyleClass().setAll("choice-btn-dim");
            }
        }

        if (correct) {
            xpEarned += q.getXpValue();
            correctCount++;
            showFeedback("✅  Correct! +" + q.getXpValue() + " XP", true);
        } else {
            String correctText = choices.stream()
                    .filter(Choice::isCorrect).map(Choice::getContent).findFirst().orElse("?");
            showFeedback("❌  Wrong. Correct answer: " + correctText, false);
        }

        results.add(new QuestionResult(q.getText(), correct, q.getXpValue()));
        lblXp.setText("⭐ " + xpEarned + " XP");
        btnNext.setDisable(false);
    }

    @FXML
    private void handleNext() {
        if (currentIndex < questions.size() - 1) {
            showQuestion(currentIndex + 1);
        } else {
            openResults();
        }
    }

    @FXML
    private void handleQuit() {
        ((Stage) btnNext.getScene().getWindow()).close();
    }

    // ── Results screen ────────────────────────────────────────

    private void openResults() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_results.fxml"));
            Parent root = loader.load();

            QuizResultsController ctrl = loader.getController();
            ctrl.show(quiz, questions.size(), correctCount, xpEarned, results);

            Stage stage = (Stage) btnNext.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Results — " + quiz.getTitle());
        } catch (IOException e) {
            showFeedback("Could not load results screen: " + e.getMessage(), false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private void showFeedback(String msg, boolean correct) {
        lblFeedback.setText(msg);
        lblFeedback.setStyle(correct
                ? "-fx-background-color:#f0fdf4;-fx-text-fill:#166534;-fx-font-weight:bold;" +
                  "-fx-font-size:13px;-fx-background-radius:10;-fx-padding:12 16 12 16;"
                : "-fx-background-color:#fef2f2;-fx-text-fill:#991b1b;-fx-font-weight:bold;" +
                  "-fx-font-size:13px;-fx-background-radius:10;-fx-padding:12 16 12 16;");
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    private void hideFeedback() {
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
    }

    private String difficultyBadgeClass(String d) {
        return switch (d == null ? "" : d.toUpperCase()) {
            case "HARD"   -> "badge-hard";
            case "MEDIUM" -> "badge-medium";
            default       -> "badge-easy";
        };
    }

    /** Simple result record per question. */
    public record QuestionResult(String text, boolean correct, int xpValue) {}
}
