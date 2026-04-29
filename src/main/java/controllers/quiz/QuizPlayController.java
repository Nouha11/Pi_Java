package controllers.quiz;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    @FXML private VBox        leftCol;
    @FXML private Separator   colDivider;

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

        // ── Hide all question-phase UI ────────────────────────
        if (timerRow != null) { timerRow.setVisible(false); timerRow.setManaged(false); }
        lblDifficulty.setVisible(false);  lblDifficulty.setManaged(false);
        lblXp.setVisible(false);          lblXp.setManaged(false);
        lblFeedback.setVisible(false);    lblFeedback.setManaged(false);
        hintBox.setVisible(false);        hintBox.setManaged(false);
        btnHint.setVisible(false);        btnHint.setManaged(false);
        lblQuestion.setVisible(false);    lblQuestion.setManaged(false);
        if (imgQuestion != null) { imgQuestion.setVisible(false); imgQuestion.setManaged(false); }
        // Collapse left column so results use full width
        if (leftCol != null)    { leftCol.setVisible(false);    leftCol.setManaged(false); }
        if (colDivider != null) { colDivider.setVisible(false); colDivider.setManaged(false); }

        // ── Derived stats ─────────────────────────────────────
        int total     = questions.size();
        int wrong     = (int) reviewResults.stream().filter(r -> !r.correct && !r.timedOut).count();
        int timedOut  = (int) reviewResults.stream().filter(r -> r.timedOut).count();
        int hintsUsed = (int) reviewResults.stream().filter(r -> r.hintUsed).count();
        int pctInt    = total > 0 ? (score * 100 / total) : 0;
        String pct    = pctInt + "%";

        String grade, gradeColor, gradeBg;
        String resultEmoji, resultTitle, resultSub;
        if (pctInt == 100) {
            grade = "S"; gradeColor = "#7c3aed"; gradeBg = "#f5f3ff";
            resultEmoji = "\uD83C\uDF1F"; resultTitle = "Perfect Score!";
            resultSub = "Flawless — you nailed every single question.";
        } else if (pctInt >= 80) {
            grade = "A"; gradeColor = "#047857"; gradeBg = "#f0fff4";
            resultEmoji = "\uD83D\uDCAA"; resultTitle = "Excellent!";
            resultSub = "Great work — you really know your stuff.";
        } else if (pctInt >= 60) {
            grade = "B"; gradeColor = "#b45309"; gradeBg = "#fffbeb";
            resultEmoji = "\uD83D\uDC4D"; resultTitle = "Good Job!";
            resultSub = "Solid effort — a little more practice and you'll ace it.";
        } else if (pctInt >= 40) {
            grade = "C"; gradeColor = "#c2410c"; gradeBg = "#fff7ed";
            resultEmoji = "\uD83D\uDCDA"; resultTitle = "Keep Practicing";
            resultSub = "You're getting there — review the missed questions below.";
        } else {
            grade = "D"; gradeColor = "#b91c1c"; gradeBg = "#fff5f5";
            resultEmoji = "\uD83D\uDE13"; resultTitle = "Needs Work";
            resultSub = "Don't give up — go through the answers and try again!";
        }

        lblProgress.setText(score + " / " + total);

        // ── Build the full results panel inside choicesBox ────
        choicesBox.getChildren().clear();

        // ── Hero banner ───────────────────────────────────────
        Label emojiLbl = new Label(resultEmoji);
        emojiLbl.setStyle("-fx-font-size:42px;");

        Label titleLbl = new Label(resultTitle);
        titleLbl.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;");

        Label subLbl = new Label(resultSub);
        subLbl.setStyle("-fx-font-size:13px; -fx-text-fill:#718096;");
        subLbl.setWrapText(true);

        // Grade circle
        Label gradeLbl = new Label(grade);
        gradeLbl.setStyle(
                "-fx-font-size:28px; -fx-font-weight:bold; -fx-text-fill:" + gradeColor + ";" +
                "-fx-background-color:" + gradeBg + ";" +
                "-fx-background-radius:50%; -fx-border-color:" + gradeColor + ";" +
                "-fx-border-radius:50%; -fx-border-width:2.5;" +
                "-fx-min-width:64px; -fx-min-height:64px; -fx-max-width:64px; -fx-max-height:64px;" +
                "-fx-alignment:center;");

        VBox heroText = new VBox(4, emojiLbl, titleLbl, subLbl);
        heroText.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        HBox heroBanner = new HBox(20, heroText, gradeLbl);
        heroBanner.setAlignment(Pos.CENTER_LEFT);
        heroBanner.setStyle(
                "-fx-background-color:white;" +
                "-fx-background-radius:14px;" +
                "-fx-border-color:#e8ecf8;" +
                "-fx-border-radius:14px;" +
                "-fx-border-width:1;" +
                "-fx-padding:20 24 20 24;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),12,0,0,3);");
        heroBanner.setMaxWidth(Double.MAX_VALUE);
        choicesBox.getChildren().add(heroBanner);

        // ── Score bar ─────────────────────────────────────────
        double ratio = total > 0 ? (double) score / total : 0;
        ProgressBar scoreBar = new ProgressBar(ratio);
        scoreBar.setMaxWidth(Double.MAX_VALUE);
        scoreBar.setPrefHeight(14);
        String barStyle = pctInt >= 80 ? "timer-bar-ok" : pctInt >= 40 ? "timer-bar-warn" : "timer-bar-danger";
        scoreBar.getStyleClass().setAll("timer-bar", barStyle, "results-score-bar");

        Label barPctLbl = new Label(pct);
        barPctLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e; -fx-min-width:40px; -fx-alignment:center-right;");

        HBox barRow = new HBox(10, scoreBar, barPctLbl);
        barRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(scoreBar, Priority.ALWAYS);
        choicesBox.getChildren().add(barRow);

        // ── Stat pills row ────────────────────────────────────
        HBox statsRow = new HBox(10,
                buildStatPill("\u2705  Correct",   String.valueOf(score),   "#f0fff4", "#27ae60", "#9ae6b4"),
                buildStatPill("\u274C  Wrong",      String.valueOf(wrong),   "#fff5f5", "#e53e3e", "#feb2b2"),
                buildStatPill("\u23F0  Timed Out",  String.valueOf(timedOut),"#fff7ed", "#c2410c", "#fed7aa"),
                buildStatPill("\u2B50  XP Earned",  String.valueOf(totalXp), "#fffbeb", "#b45309", "#fde68a"),
                buildStatPill("\uD83D\uDCA1  Hints", String.valueOf(hintsUsed),"#f5f3ff","#7c3aed","#ddd6fe")
        );
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.setMaxWidth(Double.MAX_VALUE);
        choicesBox.getChildren().add(statsRow);

        // ── Section heading ───────────────────────────────────
        Label reviewHeading = new Label("Answer Review");
        reviewHeading.setStyle(
                "-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;" +
                "-fx-border-color:transparent transparent #e4e8f0 transparent;" +
                "-fx-border-width:0 0 2 0; -fx-padding:4 0 8 0;");
        reviewHeading.setMaxWidth(Double.MAX_VALUE);
        choicesBox.getChildren().add(reviewHeading);

        // ── Per-question review cards ─────────────────────────
        for (int i = 0; i < reviewResults.size(); i++) {
            choicesBox.getChildren().add(buildReviewCard(i + 1, reviewResults.get(i)));
        }

        btnNext.setText("\u2714  Close");
        btnNext.setDisable(false);
        btnNext.setOnAction(e -> ((Stage) btnNext.getScene().getWindow()).close());
    }

    /** Small coloured pill used in the stats row. */
    private VBox buildStatPill(String label, String value, String bg, String fg, String border) {
        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + fg + ";");

        Label nameLbl = new Label(label);
        nameLbl.setStyle("-fx-font-size:11px; -fx-text-fill:" + fg + "; -fx-opacity:0.8;");

        VBox pill = new VBox(2, valLbl, nameLbl);
        pill.setAlignment(Pos.CENTER);
        pill.setStyle(
                "-fx-background-color:" + bg + ";" +
                "-fx-border-color:" + border + ";" +
                "-fx-border-radius:10px; -fx-background-radius:10px;" +
                "-fx-border-width:1.5; -fx-padding:12 18 12 18;");
        HBox.setHgrow(pill, Priority.ALWAYS);
        return pill;
    }

    private VBox buildReviewCard(int number, AnswerReview r) {
        // ── Card accent colour ────────────────────────────────
        String accentColor  = r.correct ? "#27ae60" : r.timedOut ? "#c2410c" : "#e53e3e";
        String cardBg       = r.correct ? "#f0fff4" : r.timedOut ? "#fff7ed" : "#fff5f5";
        String cardBorder   = r.correct ? "#9ae6b4" : r.timedOut ? "#fed7aa" : "#feb2b2";

        // ── Question number badge ─────────────────────────────
        Label qNum = new Label("Q" + number);
        qNum.setStyle(
                "-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:white;" +
                "-fx-background-color:" + accentColor + ";" +
                "-fx-background-radius:20; -fx-padding:3 9 3 9;");

        // ── Status icon ───────────────────────────────────────
        String statusIcon = r.correct ? "\u2705" : r.timedOut ? "\u23F0" : "\u274C";
        Label statusLbl = new Label(statusIcon);
        statusLbl.setStyle("-fx-font-size:14px;");

        // ── Question text ─────────────────────────────────────
        Label qText = new Label(r.question.getText());
        qText.setWrapText(true);
        qText.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;");
        qText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(qText, Priority.ALWAYS);

        HBox header = new HBox(8, qNum, statusLbl, qText);
        header.setAlignment(Pos.CENTER_LEFT);

        // ── Difficulty + XP meta row ──────────────────────────
        String diff = r.question.getDifficulty() != null
                ? capitalize(r.question.getDifficulty().toLowerCase()) : "Easy";
        String diffColor = switch (diff.toLowerCase()) {
            case "hard"   -> "#e53e3e";
            case "medium" -> "#d97706";
            default       -> "#27ae60";
        };
        Label diffLbl = new Label(diff);
        diffLbl.setStyle(
                "-fx-font-size:10px; -fx-font-weight:bold; -fx-text-fill:" + diffColor + ";" +
                "-fx-background-color:transparent; -fx-border-color:" + diffColor + ";" +
                "-fx-border-radius:10; -fx-background-radius:10; -fx-border-width:1; -fx-padding:1 7 1 7;");

        Label xpLbl = new Label("\u2B50 " + r.question.getXpValue() + " XP");
        xpLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#b45309;");

        Label hintTag = new Label("\uD83D\uDCA1 hint used");
        hintTag.setStyle("-fx-font-size:11px; -fx-text-fill:#7c3aed;");
        hintTag.setVisible(r.hintUsed);
        hintTag.setManaged(r.hintUsed);

        HBox metaRow = new HBox(8, diffLbl, xpLbl, hintTag);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // ── Separator ─────────────────────────────────────────
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#e4e8f0;");

        // ── All choices with visual state ─────────────────────
        VBox choicesList = new VBox(6);
        if (r.question.getChoices() != null) {
            for (Choice c : r.question.getChoices()) {
                boolean isCorrect   = c.isCorrect();
                boolean wasSelected = r.selected != null && r.selected.getId() == c.getId();

                String choiceBg, choiceBorder, choiceFg, choicePrefix;
                if (isCorrect) {
                    choiceBg = "#f0fff4"; choiceBorder = "#27ae60"; choiceFg = "#047857";
                    choicePrefix = "\u2705  ";
                } else if (wasSelected) {
                    choiceBg = "#fff5f5"; choiceBorder = "#e53e3e"; choiceFg = "#b91c1c";
                    choicePrefix = "\u274C  ";
                } else {
                    choiceBg = "#f8f9ff"; choiceBorder = "#e4e8f0"; choiceFg = "#718096";
                    choicePrefix = "      ";
                }

                Label choiceLbl = new Label(choicePrefix + c.getContent());
                choiceLbl.setWrapText(true);
                choiceLbl.setMaxWidth(Double.MAX_VALUE);
                choiceLbl.setStyle(
                        "-fx-font-size:12px; -fx-text-fill:" + choiceFg + ";" +
                        "-fx-background-color:" + choiceBg + ";" +
                        "-fx-border-color:" + choiceBorder + ";" +
                        "-fx-border-radius:7; -fx-background-radius:7;" +
                        "-fx-border-width:1.5; -fx-padding:8 12 8 12;" +
                        (isCorrect ? "-fx-font-weight:bold;" : "") +
                        (wasSelected && !isCorrect ? "-fx-strikethrough:false;" : ""));
                choicesList.getChildren().add(choiceLbl);
            }
        }

        // ── Timed-out notice ──────────────────────────────────
        if (r.timedOut) {
            Label timeoutNote = new Label("\u23F0  Time ran out — no answer was submitted for this question.");
            timeoutNote.setWrapText(true);
            timeoutNote.setStyle(
                    "-fx-font-size:12px; -fx-text-fill:#c2410c;" +
                    "-fx-background-color:#fff7ed; -fx-border-color:#fed7aa;" +
                    "-fx-border-radius:7; -fx-background-radius:7;" +
                    "-fx-border-width:1; -fx-padding:8 12 8 12;");
            choicesList.getChildren().add(timeoutNote);
        }

        // ── Assemble card ─────────────────────────────────────
        VBox card = new VBox(10, header, metaRow, sep, choicesList);
        card.setStyle(
                "-fx-background-color:" + cardBg + ";" +
                "-fx-border-color:" + cardBorder + ";" +
                "-fx-border-radius:12; -fx-background-radius:12;" +
                "-fx-padding:16; -fx-border-width:1.5;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),8,0,0,2);");
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
