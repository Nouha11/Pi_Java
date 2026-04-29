package controllers.quiz;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
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
import services.quiz.HuggingFaceService;
import services.quiz.QuestionService;
import services.quiz.TranslationService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuizPlayController {

    // ── FXML ──────────────────────────────────────────────────
    @FXML private Label       lblQuizTitle;
    @FXML private Label       lblProgress;
    @FXML private ProgressBar progressBar;
    @FXML private HBox        timerRow;
    @FXML private ProgressBar timerBar;
    @FXML private Label       lblTimer;
    @FXML private Label       lblDifficulty;
    @FXML private Label       lblXp;
    @FXML private Label       lblQuestion;
    @FXML private ImageView   imgQuestion;
    @FXML private VBox        choicesBox;
    @FXML private VBox        hintBox;
    @FXML private Label       lblHint;
    @FXML private Label       lblFeedback;
    @FXML private Button      btnNext;
    @FXML private Button      btnHint;
    @FXML private ComboBox<String> cmbLanguage;

    // ── Config ────────────────────────────────────────────────
    /** Seconds allowed per question. */
    private static final int    SECONDS_PER_QUESTION = 30;
    private static final String IMAGES_DIR           = "src/main/resources/images/quiz/";

    // ── Services ──────────────────────────────────────────────
    private final QuestionService    questionService    = new QuestionService();
    private final ChoiceService      choiceService      = new ChoiceService();
    private final HuggingFaceService hfService          = new HuggingFaceService();
    private final TranslationService translationService = new TranslationService();

    // ── State ─────────────────────────────────────────────────
    private List<Question> questions;
    private final List<AnswerReview> reviewResults = new ArrayList<>();
    private int  currentIndex = 0;
    private int  score        = 0;
    private int  totalXp      = 0;
    private int  timeLeft;
    private boolean answered;
    private boolean hintUsed;

    /** Cache: questionId → Map<langCode, translatedText> to avoid re-calling the API */
    private final Map<Integer, Map<String, String>> translationCache = new HashMap<>();
    /** Cache: questionId → Map<langCode, List<translatedChoiceText>> */
    private final Map<Integer, Map<String, List<String>>> choiceTranslationCache = new HashMap<>();

    private String selectedLangCode = "en"; // default English = no translation

    private ToggleGroup toggleGroup;
    private Timeline    countdownTimer;
    private ChangeListener<Boolean> focusListener;
    private ChangeListener<Boolean> minimizeListener;

    // ── Entry point ───────────────────────────────────────────

    public void loadQuiz(Quiz quiz) {
        lblQuizTitle.setText(quiz.getTitle());
        questions = questionService.getQuestionsByQuizId(quiz.getId());
        reviewResults.clear();
        translationCache.clear();
        choiceTranslationCache.clear();
        currentIndex = 0;
        score = 0;
        totalXp = 0;

        // Populate language selector
        cmbLanguage.setItems(FXCollections.observableArrayList(
                TranslationService.LANGUAGES.keySet()));
        cmbLanguage.setValue("English");
        selectedLangCode = "en";

        if (questions.isEmpty()) {
            lblQuestion.setText("This quiz has no questions yet.");
            btnNext.setDisable(true);
            lblProgress.setText("0 / 0");
            stopTimer();
            removeFocusListener();
            return;
        }

        // Add focus listener to detect cheating (deferred until scene is ready)
        Platform.runLater(() -> {
            if (lblQuizTitle.getScene() != null) {
                Stage stage = (Stage) lblQuizTitle.getScene().getWindow();
                focusListener = (obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        handleCheatingDetected();
                    }
                };
                stage.focusedProperty().addListener(focusListener);

                minimizeListener = (obs, wasMinimized, isMinimized) -> {
                    if (isMinimized) {
                        handleCheatingDetected();
                    }
                };
                stage.iconifiedProperty().addListener(minimizeListener);
            }
        });

        showQuestion(0);
    }

    // ── Language / Translation ────────────────────────────────

    @FXML
    private void handleLanguageChange() {
        String selected = cmbLanguage.getValue();
        if (selected == null) return;
        selectedLangCode = TranslationService.LANGUAGES.getOrDefault(selected, "en");

        // Re-render the current question in the new language (background thread)
        if (questions != null && !questions.isEmpty() && !answered) {
            applyTranslationToCurrentQuestion();
        }
    }

    /**
     * Translates the current question + choices and updates the UI.
     * Uses cache so the same question is never translated twice for the same language.
     */
    private void applyTranslationToCurrentQuestion() {
        if ("en".equals(selectedLangCode)) {
            // English = show originals immediately
            Question q = questions.get(currentIndex);
            lblQuestion.setText(q.getText());
            updateChoiceLabels(q.getChoices().stream()
                    .map(Choice::getContent).collect(Collectors.toList()));
            return;
        }

        Question q = questions.get(currentIndex);
        int qId = q.getId();

        // Check question text cache
        String cachedQ = translationCache
                .getOrDefault(qId, Map.of()).get(selectedLangCode);
        List<String> cachedChoices = choiceTranslationCache
                .getOrDefault(qId, Map.of()).get(selectedLangCode);

        if (cachedQ != null && cachedChoices != null) {
            lblQuestion.setText(cachedQ);
            updateChoiceLabels(cachedChoices);
            return;
        }

        // Translate on background thread
        String langCode = selectedLangCode;
        Thread t = new Thread(() -> {
            String translatedQ = translationService.translate(q.getText(), langCode);

            List<String> translatedChoices = q.getChoices().stream()
                    .map(c -> translationService.translate(c.getContent(), langCode))
                    .collect(Collectors.toList());

            // Store in cache
            translationCache.computeIfAbsent(qId, k -> new HashMap<>())
                    .put(langCode, translatedQ);
            choiceTranslationCache.computeIfAbsent(qId, k -> new HashMap<>())
                    .put(langCode, translatedChoices);

            Platform.runLater(() -> {
                // Only apply if the user hasn't moved to the next question
                if (currentIndex < questions.size()
                        && questions.get(currentIndex).getId() == qId
                        && langCode.equals(selectedLangCode)) {
                    lblQuestion.setText(translatedQ);
                    updateChoiceLabels(translatedChoices);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** Updates the text of existing RadioButton choices without rebuilding them. */
    private void updateChoiceLabels(List<String> texts) {
        var children = choicesBox.getChildren();
        for (int i = 0; i < children.size() && i < texts.size(); i++) {
            if (children.get(i) instanceof RadioButton rb) {
                rb.setText(texts.get(i));
            }
        }
    }

    // ── Question display ──────────────────────────────────────

    private void showQuestion(int index) {
        answered  = false;
        hintUsed  = false;
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

        // Reset hint box
        hintBox.setVisible(false);
        hintBox.setManaged(false);
        lblHint.setText("");
        btnHint.setDisable(false);
        btnHint.setText("\uD83D\uDCA1  Hint  (\u2212\u00BD XP)");

        // Reset feedback + next button
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        btnNext.setDisable(true);
        btnNext.setText(index == questions.size() - 1 ? "Finish" : "Next  \u2192");
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

        // Apply translation if a non-English language is selected
        applyTranslationToCurrentQuestion();
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

    // ── Hint ──────────────────────────────────────────────────

    @FXML
    private void handleHint() {
        if (hintUsed || answered) return;
        hintUsed = true;
        btnHint.setDisable(true);
        btnHint.setText("\uD83D\uDCA1  Loading…");

        // Pause the timer while fetching
        if (countdownTimer != null) countdownTimer.pause();

        Question current = questions.get(currentIndex);

        // Build choices string for context
        String choicesText = current.getChoices() == null ? "" :
                current.getChoices().stream()
                        .map(Choice::getContent)
                        .collect(Collectors.joining(", "));

        // Halve the XP for this question immediately
        int originalXp = current.getXpValue();
        int halvedXp   = Math.max(1, originalXp / 2);
        current.setXpValue(halvedXp);
        lblXp.setText("\u2B50 " + halvedXp + " XP  (\u2212\u00BD hint penalty)");

        // Call HuggingFace on a background thread
        Thread hintThread = new Thread(() -> {
            String hint = hfService.generateHint(current.getText(), choicesText);
            Platform.runLater(() -> {
                lblHint.setText(hint);
                hintBox.setVisible(true);
                hintBox.setManaged(true);
                btnHint.setText("\uD83D\uDCA1  Hint used");
                // Resume timer
                if (countdownTimer != null) countdownTimer.play();
            });
        });
        hintThread.setDaemon(true);
        hintThread.start();
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
        removeFocusListener();
        progressBar.setProgress(1.0);

        // Hide question-phase UI
        if (timerRow != null) { timerRow.setVisible(false); timerRow.setManaged(false); }
        lblDifficulty.setVisible(false);
        lblXp.setVisible(false);
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        hintBox.setVisible(false);
        hintBox.setManaged(false);
        btnHint.setVisible(false);
        btnHint.setManaged(false);
        if (imgQuestion != null) { imgQuestion.setVisible(false); imgQuestion.setManaged(false); }

        // Score header in the question label
        int total = questions.size();
        String pct = total > 0 ? (score * 100 / total) + "%" : "—";
        String emoji = score == total ? "\uD83C\uDF1F" : score >= total / 2 ? "\uD83D\uDCAA" : "\uD83D\uDCDA";
        lblQuestion.setText(emoji + "  Quiz Complete!");
        lblQuestion.getStyleClass().setAll("play-results-text");
        lblProgress.setText(score + " / " + total);

        // Reuse timer bar as score bar
        timerBar.setProgress(total > 0 ? (double) score / total : 0);
        timerBar.getStyleClass().setAll("timer-bar",
                score == total ? "timer-bar-ok" : score >= total / 2 ? "timer-bar-warn" : "timer-bar-danger");
        lblTimer.setText(pct);
        lblTimer.getStyleClass().setAll("timer-label");
        if (timerRow != null) { timerRow.setVisible(true); timerRow.setManaged(true); }

        // Build summary
        choicesBox.getChildren().clear();

        Label statsLabel = new Label(
                "Score: " + score + " / " + total + "   \u00B7   XP earned: " + totalXp + "   \u00B7   " + pct);
        statsLabel.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;" +
                " -fx-background-color:#eef0fd; -fx-background-radius:8; -fx-padding:10 16 10 16;");
        statsLabel.setMaxWidth(Double.MAX_VALUE);
        choicesBox.getChildren().add(statsLabel);

        Label divider = new Label("Answer Review");
        divider.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#4a5568; -fx-padding:12 0 4 0;");
        choicesBox.getChildren().add(divider);

        for (int i = 0; i < reviewResults.size(); i++) {
            choicesBox.getChildren().add(buildReviewCard(i + 1, reviewResults.get(i)));
        }

        btnNext.setText("Close");
        btnNext.setDisable(false);
        btnNext.setOnAction(e -> ((Stage) btnNext.getScene().getWindow()).close());
    }

    private VBox buildReviewCard(int number, AnswerReview r) {
        Label qNum = new Label("Q" + number);
        qNum.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:white;" +
                " -fx-background-color:" + (r.correct ? "#27ae60" : "#e53e3e") + ";" +
                " -fx-background-radius:20; -fx-padding:2 8 2 8;");

        Label qText = new Label(r.question.getText());
        qText.setWrapText(true);
        qText.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;");
        qText.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox(8, qNum, qText);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String yourAnswerText = r.timedOut ? "\u23F0  No answer (timed out)"
                : r.selected != null ? r.selected.getContent() : "\u2014";
        Label yourAnswer = new Label((r.correct ? "\u2713" : "\u2717") + "  Your answer:  " + yourAnswerText);
        yourAnswer.setWrapText(true);
        yourAnswer.setStyle("-fx-font-size:12px; -fx-text-fill:" + (r.correct ? "#047857" : "#b91c1c") + ";");

        VBox card = new VBox(8, header, yourAnswer);

        if (!r.correct) {
            String correctText = r.correctChoice != null ? r.correctChoice.getContent() : "\u2014";
            Label correctAnswer = new Label("\u2713  Correct answer:  " + correctText);
            correctAnswer.setWrapText(true);
            correctAnswer.setStyle("-fx-font-size:12px; -fx-text-fill:#047857;");
            card.getChildren().add(correctAnswer);
        }

        String diff = r.question.getDifficulty() != null
                ? capitalize(r.question.getDifficulty().toLowerCase()) : "Easy";
        Label meta = new Label(diff + "  \u00B7  " + r.question.getXpValue() + " XP"
                + (r.hintUsed ? "  \u00B7  \uD83D\uDCA1 hint used" : ""));
        meta.setStyle("-fx-font-size:11px; -fx-text-fill:#a0aec0;");
        card.getChildren().add(meta);

        card.setStyle("-fx-background-color:" + (r.correct ? "#f0fff4" : "#fff5f5") + ";" +
                " -fx-border-color:" + (r.correct ? "#9ae6b4" : "#feb2b2") + ";" +
                " -fx-border-radius:10; -fx-background-radius:10; -fx-padding:14; -fx-border-width:1.5;");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    @FXML
    private void handleQuit() {
        removeFocusListener();
        stopTimer();
        ((Stage) btnNext.getScene().getWindow()).close();
    }

    private void handleCheatingDetected() {
        stopTimer();
        removeFocusListener();

        // Clear the UI and show cheating message
        choicesBox.getChildren().clear();
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
        lblDifficulty.setVisible(false);
        lblXp.setVisible(false);
        if (imgQuestion != null) {
            imgQuestion.setVisible(false);
            imgQuestion.setManaged(false);
        }
        if (timerRow != null) {
            timerRow.setVisible(false);
            timerRow.setManaged(false);
        }

        lblQuestion.setText(
                "\uD83D\uDEAB  Quiz Terminated\n\n" +
                "Suspected cheating detected.\n" +
                "The quiz window lost focus.\n\n" +
                "Your answers have been discarded."
        );
        lblQuestion.getStyleClass().setAll("play-results-text");
        lblProgress.setText("0 / 0");

        btnNext.setText("Close");
        btnNext.setDisable(false);
        btnNext.setOnAction(e -> ((Stage) btnNext.getScene().getWindow()).close());
    }

    private void removeFocusListener() {
        if (lblQuizTitle.getScene() != null) {
            Stage stage = (Stage) lblQuizTitle.getScene().getWindow();
            if (focusListener != null) {
                stage.focusedProperty().removeListener(focusListener);
                focusListener = null;
            }
            if (minimizeListener != null) {
                stage.iconifiedProperty().removeListener(minimizeListener);
                minimizeListener = null;
            }
        }
    }

    private void addReview(Question question, Choice selected, Choice correct, boolean timedOut) {
        reviewResults.add(new AnswerReview(question, selected, correct,
                selected != null && selected.isCorrect(), timedOut, hintUsed));
    }

    private void renderReviewSummary() {
        // kept for compatibility — actual rendering now in showResults/buildReviewCard
    }

    private static class AnswerReview {
        private final Question question;
        private final Choice selected;
        private final Choice correctChoice;
        private final boolean correct;
        private final boolean timedOut;
        private final boolean hintUsed;

        private AnswerReview(Question question, Choice selected, Choice correctChoice,
                             boolean correct, boolean timedOut, boolean hintUsed) {
            this.question     = question;
            this.selected     = selected;
            this.correctChoice = correctChoice;
            this.correct      = correct;
            this.timedOut     = timedOut;
            this.hintUsed     = hintUsed;
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
