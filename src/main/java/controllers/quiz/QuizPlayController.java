package controllers.quiz;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.ChoiceService;
import services.quiz.QuestionService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QuizPlayController {

    // ── FXML ──────────────────────────────────────────────────
    @FXML private Label       lblQuizTitle;
    @FXML private Label       lblProgress;
    @FXML private ProgressBar progressBar;
    @FXML private HBox timerRow;
    @FXML private ProgressBar timerBar;
    @FXML private Label       lblTimer;
    @FXML private Label       lblDifficulty;
    @FXML private Label       lblXp;
    @FXML private Label       lblQuestion;
    @FXML private ImageView   imgQuestion;
    @FXML private VBox        choicesBox;
    @FXML private Label       lblFeedback;
    @FXML private Button      btnNext;

    // ── Config ────────────────────────────────────────────────
    /** Seconds allowed per question. */
    private static final int    SECONDS_PER_QUESTION = 30;
    private static final String IMAGES_DIR           = "src/main/resources/images/quiz/";

    // ── Services ──────────────────────────────────────────────
    private final QuestionService questionService = new QuestionService();
    private final ChoiceService   choiceService   = new ChoiceService();

    // ── State ─────────────────────────────────────────────────
    private List<Question> questions;
    private final List<AnswerReview> reviewResults = new ArrayList<>();
    private int  currentIndex = 0;
    private int  score        = 0;
    private int  totalXp      = 0;
    private int  timeLeft;          // seconds remaining for current question
    private boolean answered;       // true once the user picks or time runs out

    private ToggleGroup toggleGroup;
    private Timeline    countdownTimer;

    // ── Entry point ───────────────────────────────────────────

    public void loadQuiz(Quiz quiz) {
        lblQuizTitle.setText(quiz.getTitle());
        questions = questionService.getQuestionsByQuizId(quiz.getId());
        reviewResults.clear();
        currentIndex = 0;
        score = 0;
        totalXp = 0;

        if (questions.isEmpty()) {
            lblQuestion.setText("This quiz has no questions yet.");
            btnNext.setDisable(true);
            lblProgress.setText("0 / 0");
            stopTimer();
            return;
        }

        showQuestion(0);
    }

    // ── Question display ──────────────────────────────────────

    private void showQuestion(int index) {
        answered = false;
        Question q = questions.get(index);

        List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
        q.setChoices(choices);

        // Progress bar (question index)
        lblProgress.setText((index + 1) + " / " + questions.size());
        progressBar.setProgress((double) index / questions.size());

        // Difficulty + XP
        String diff = q.getDifficulty() != null ? q.getDifficulty().toLowerCase() : "easy";
        lblDifficulty.setText(capitalize(diff));
        lblDifficulty.getStyleClass().setAll(difficultyBadgeClass(diff));
        lblDifficulty.setVisible(true);
        lblXp.setText("\u2B50 " + q.getXpValue() + " XP");
        lblXp.setVisible(true);

        // Question text + image
        lblQuestion.setText(q.getText());
        lblQuestion.getStyleClass().setAll("play-question-text");
        loadQuestionImage(q.getImageName());

        // Reset feedback + next button
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        btnNext.setDisable(true);
        btnNext.setText(index == questions.size() - 1 ? "Finish" : "Next  \u2192");
        // Reset next button action to default handler
        btnNext.setOnAction(e -> handleNext());
        if (timerRow != null) {
            timerRow.setVisible(true);
            timerRow.setManaged(true);
        }

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

        // Start the per-question countdown
        startTimer();
    }

    // ── Timer ─────────────────────────────────────────────────

    private void startTimer() {
        stopTimer();
        timeLeft = SECONDS_PER_QUESTION;
        updateTimerUI();

        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLeft--;
            updateTimerUI();
            if (timeLeft <= 0) {
                onTimeUp();
            }
        }));
        countdownTimer.setCycleCount(SECONDS_PER_QUESTION);
        countdownTimer.play();
    }

    private void stopTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void updateTimerUI() {
        lblTimer.setText(String.valueOf(timeLeft));
        double ratio = (double) timeLeft / SECONDS_PER_QUESTION;
        timerBar.setProgress(ratio);

        // Color: green → orange → red as time runs out
        if (ratio > 0.5) {
            timerBar.getStyleClass().setAll("timer-bar", "timer-bar-ok");
        } else if (ratio > 0.25) {
            timerBar.getStyleClass().setAll("timer-bar", "timer-bar-warn");
        } else {
            timerBar.getStyleClass().setAll("timer-bar", "timer-bar-danger");
        }

        // Pulse the label red when ≤ 5 seconds
        if (timeLeft <= 5) {
            lblTimer.getStyleClass().setAll("timer-label", "timer-label-urgent");
        } else {
            lblTimer.getStyleClass().setAll("timer-label");
        }
    }

    /** Called when the countdown hits zero. */
    private void onTimeUp() {
        if (answered) return;
        answered = true;
        stopTimer();

        // Lock all choices and highlight the correct one
        Question current = questions.get(currentIndex);
        Choice correctChoice = null;
        for (var node : choicesBox.getChildren()) {
            if (node instanceof RadioButton rb) {
                rb.setDisable(true);
                Choice c = (Choice) rb.getUserData();
                if (c.isCorrect()) {
                    rb.getStyleClass().add("choice-correct");
                    correctChoice = c;
                }
            }
        }

        addReview(current, null, correctChoice, true);
        showFeedback(false, "\u23F0  Time's up! No answer selected.");
        btnNext.setDisable(false);

        // Wire next button for the time-up case
        if (currentIndex == questions.size() - 1) {
            btnNext.setOnAction(e -> showResults());
        } else {
            btnNext.setOnAction(e -> {
                currentIndex++;
                showQuestion(currentIndex);
            });
        }
    }

    // ── Answer handling ───────────────────────────────────────

    private void onChoiceSelected() {
        if (answered) return;   // ignore clicks after time-up
        btnNext.setDisable(false);
    }

    @FXML
    private void handleNext() {
        if (answered) return;   // already handled by time-up path
        Toggle selected = toggleGroup.getSelectedToggle();
        if (selected == null) return;

        answered = true;
        stopTimer();

        Choice chosen  = (Choice) selected.getUserData();
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

        Choice correctChoice = current.getChoices().stream()
                .filter(Choice::isCorrect)
                .findFirst().orElse(null);

        if (chosen.isCorrect()) {
            score++;
            totalXp += current.getXpValue();
            showFeedback(true, "\u2705  Correct! +" + current.getXpValue() + " XP");
        } else {
            String correctText = correctChoice != null ? correctChoice.getContent() : "\u2014";
            showFeedback(false, "\u274C  Wrong. Correct answer: " + correctText);
        }

        addReview(current, chosen, correctChoice, false);
        btnNext.setDisable(false);

        if (currentIndex == questions.size() - 1) {
            btnNext.setOnAction(e -> showResults());
        } else {
            btnNext.setOnAction(e -> {
                currentIndex++;
                showQuestion(currentIndex);
            });
        }
    }

    // ── Results ───────────────────────────────────────────────

    private void showResults() {
        stopTimer();
        progressBar.setProgress(1.0);
        timerBar.setProgress(0);
        lblTimer.setText("0");

        choicesBox.getChildren().clear();
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        lblDifficulty.setVisible(false);
        lblXp.setVisible(false);
        if (imgQuestion != null) {
            imgQuestion.setVisible(false);
            imgQuestion.setManaged(false);
        }

        lblQuestion.setText(
                "\uD83C\uDF89  Quiz Complete!\n\n" +
                "Score: " + score + " / " + questions.size() + "\n" +
                "Total XP earned: " + totalXp
        );
        lblQuestion.getStyleClass().setAll("play-results-text");
        lblProgress.setText(score + " / " + questions.size());

        if (timerRow != null) {
            timerRow.setVisible(false);
            timerRow.setManaged(false);
        }

        renderReviewSummary();

        btnNext.setText("Close");
        btnNext.setDisable(false);
        btnNext.setOnAction(e -> ((Stage) btnNext.getScene().getWindow()).close());
    }

    @FXML
    private void handleQuit() {
        stopTimer();
        ((Stage) btnNext.getScene().getWindow()).close();
    }

    private void addReview(Question question, Choice selected, Choice correct, boolean timedOut) {
        reviewResults.add(new AnswerReview(question, selected, correct, selected != null && selected.isCorrect(), timedOut));
    }

    private void renderReviewSummary() {
        choicesBox.getChildren().clear();
        if (reviewResults.isEmpty()) {
            Label emptyLabel = new Label("No answers were recorded for this quiz.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #475569;");
            choicesBox.getChildren().add(emptyLabel);
            return;
        }

        for (int i = 0; i < reviewResults.size(); i++) {
            AnswerReview review = reviewResults.get(i);
            Label questionLabel = new Label((i + 1) + ". " + review.question.getText());
            questionLabel.setWrapText(true);
            questionLabel.getStyleClass().add("play-question-text");

            Label statusLabel = new Label(review.correct ? "Result: Correct ✓" : "Result: Incorrect ✕");
            statusLabel.setStyle(review.correct
                    ? "-fx-text-fill: #047857; -fx-font-weight: bold;"
                    : "-fx-text-fill: #b91c1c; -fx-font-weight: bold;");

            String selectedText = review.timedOut ? "No answer selected" :
                    review.selected != null ? review.selected.getContent() : "No answer selected";
            Label selectedLabel = new Label("Your answer: " + selectedText);
            selectedLabel.setWrapText(true);

            String correctText = review.correctChoice != null ? review.correctChoice.getContent() : "—";
            Label correctLabel = new Label("Correct answer: " + correctText);
            correctLabel.setWrapText(true);

            VBox reviewCard = new VBox(6, questionLabel, statusLabel, selectedLabel, correctLabel);
            reviewCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 10; " +
                    "-fx-background-radius: 10; -fx-padding: 14; -fx-effect: dropshadow(gaussian, rgba(148,163,184,0.2), 8, 0, 0, 1);");
            reviewCard.setMaxWidth(Double.MAX_VALUE);

            choicesBox.getChildren().add(reviewCard);
        }
    }

    private static class AnswerReview {
        private final Question question;
        private final Choice selected;
        private final Choice correctChoice;
        private final boolean correct;
        private final boolean timedOut;

        private AnswerReview(Question question, Choice selected, Choice correctChoice, boolean correct, boolean timedOut) {
            this.question = question;
            this.selected = selected;
            this.correctChoice = correctChoice;
            this.correct = correct;
            this.timedOut = timedOut;
        }
    }

    // ── Image loading ─────────────────────────────────────────

    private void loadQuestionImage(String name) {
        if (imgQuestion == null) return;
        if (name == null || name.isBlank()) {
            imgQuestion.setImage(null);
            imgQuestion.setVisible(false);
            imgQuestion.setManaged(false);
            return;
        }

        var url = getClass().getResource("/images/quiz/" + name);
        if (url != null) {
            imgQuestion.setImage(new Image(url.toExternalForm(), true));
        } else {
            File f = new File(IMAGES_DIR + name);
            if (f.exists()) {
                imgQuestion.setImage(new Image(f.toURI().toString(), true));
            } else {
                imgQuestion.setImage(null);
                imgQuestion.setVisible(false);
                imgQuestion.setManaged(false);
                return;
            }
        }
        imgQuestion.setVisible(true);
        imgQuestion.setManaged(true);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void showFeedback(boolean correct, String message) {
        lblFeedback.setText(message);
        lblFeedback.getStyleClass().setAll(correct ? "play-feedback-correct" : "play-feedback-wrong");
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
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
