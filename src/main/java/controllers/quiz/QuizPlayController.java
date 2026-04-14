package controllers.quiz;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.ChoiceService;
import services.quiz.QuestionService;

import java.util.List;

public class QuizPlayController {

    @FXML private Label       lblQuizTitle;
    @FXML private Label       lblProgress;
    @FXML private ProgressBar progressBar;
    @FXML private Label       lblDifficulty;
    @FXML private Label       lblXp;
    @FXML private Label       lblQuestion;
    @FXML private VBox        choicesBox;
    @FXML private Label       lblFeedback;
    @FXML private Button      btnNext;

    private final QuestionService questionService = new QuestionService();
    private final ChoiceService   choiceService   = new ChoiceService();

    private List<Question> questions;
    private int currentIndex = 0;
    private int score        = 0;
    private int totalXp      = 0;

    private ToggleGroup toggleGroup;

    public void loadQuiz(Quiz quiz) {
        lblQuizTitle.setText(quiz.getTitle());
        questions = questionService.getQuestionsByQuizId(quiz.getId());

        if (questions.isEmpty()) {
            lblQuestion.setText("This quiz has no questions yet.");
            btnNext.setDisable(true);
            lblProgress.setText("0 / 0");
            return;
        }

        showQuestion(0);
    }

    private void showQuestion(int index) {
        Question q = questions.get(index);

        // Load choices for this question
        List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
        q.setChoices(choices);

        // Progress
        lblProgress.setText((index + 1) + " / " + questions.size());
        progressBar.setProgress((double) index / questions.size());

        // Difficulty badge style
        String diff = q.getDifficulty() != null ? q.getDifficulty().toLowerCase() : "easy";
        lblDifficulty.setText(capitalize(diff));
        lblDifficulty.getStyleClass().setAll(difficultyBadgeClass(diff));

        lblXp.setText("⭐ " + q.getXpValue() + " XP");
        lblQuestion.setText(q.getText());

        // Feedback hidden, next disabled
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        btnNext.setDisable(true);
        btnNext.setText(index == questions.size() - 1 ? "Finish" : "Next  →");

        // Build choice radio buttons
        choicesBox.getChildren().clear();
        toggleGroup = new ToggleGroup();

        for (Choice choice : choices) {
            RadioButton rb = new RadioButton(choice.getContent());
            rb.setToggleGroup(toggleGroup);
            rb.getStyleClass().add("play-choice");
            rb.setUserData(choice);
            rb.setWrapText(true);
            rb.setMaxWidth(Double.MAX_VALUE);
            rb.setOnAction(e -> onChoiceSelected());
            choicesBox.getChildren().add(rb);
        }
    }

    private void onChoiceSelected() {
        btnNext.setDisable(false);
    }

    @FXML
    private void handleNext() {
        // Evaluate answer
        Toggle selected = toggleGroup.getSelectedToggle();
        if (selected == null) return;

        Choice chosen = (Choice) selected.getUserData();
        Question current = questions.get(currentIndex);

        // Highlight correct / wrong
        for (var node : choicesBox.getChildren()) {
            if (node instanceof RadioButton rb) {
                rb.setDisable(true);
                Choice c = (Choice) rb.getUserData();
                if (c.isCorrect()) {
                    rb.getStyleClass().add("choice-correct");
                } else if (rb == selected && !c.isCorrect()) {
                    rb.getStyleClass().add("choice-wrong");
                }
            }
        }

        if (chosen.isCorrect()) {
            score++;
            totalXp += current.getXpValue();
            showFeedback(true, "✅  Correct! +" + current.getXpValue() + " XP");
        } else {
            // Find correct answer text
            String correctText = current.getChoices().stream()
                    .filter(Choice::isCorrect)
                    .map(Choice::getContent)
                    .findFirst().orElse("—");
            showFeedback(false, "❌  Wrong. Correct answer: " + correctText);
        }

        btnNext.setDisable(false);

        // If last question, change handler to finish
        if (currentIndex == questions.size() - 1) {
            btnNext.setOnAction(e -> showResults());
        } else {
            btnNext.setOnAction(e -> {
                currentIndex++;
                showQuestion(currentIndex);
                // Re-bind next for non-last questions
                if (currentIndex < questions.size() - 1) {
                    btnNext.setOnAction(ev -> {
                        currentIndex++;
                        showQuestion(currentIndex);
                    });
                } else {
                    btnNext.setOnAction(ev -> showResults());
                }
            });
        }
    }

    private void showFeedback(boolean correct, String message) {
        lblFeedback.setText(message);
        lblFeedback.getStyleClass().setAll(correct ? "play-feedback-correct" : "play-feedback-wrong");
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    private void showResults() {
        progressBar.setProgress(1.0);
        choicesBox.getChildren().clear();
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        lblDifficulty.setVisible(false);
        lblXp.setVisible(false);

        lblQuestion.setText(
                "🎉  Quiz Complete!\n\n" +
                "Score: " + score + " / " + questions.size() + "\n" +
                "Total XP earned: " + totalXp
        );
        lblQuestion.getStyleClass().add("play-results-text");
        lblProgress.setText(score + " / " + questions.size());

        btnNext.setText("Close");
        btnNext.setDisable(false);
        btnNext.setOnAction(e -> ((Stage) btnNext.getScene().getWindow()).close());
    }

    @FXML
    private void handleQuit() {
        ((Stage) btnNext.getScene().getWindow()).close();
    }

    private String difficultyBadgeClass(String diff) {
        return switch (diff) {
            case "hard"   -> "badge-hard";
            case "medium" -> "badge-medium";
            default       -> "badge-easy";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
