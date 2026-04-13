package controllers.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.quiz.Quiz;

import java.io.IOException;
import java.util.List;

public class QuizResultsController {

    @FXML private Label lblScorePercent;
    @FXML private Label lblScoreDetail;
    @FXML private Label lblXpEarned;
    @FXML private Label lblOutcome;
    @FXML private VBox  summaryContainer;

    private Quiz quiz;
    private int  total;
    private int  correct;
    private int  xp;
    private List<QuizTakeController.QuestionResult> results;

    // ── Entry point ───────────────────────────────────────────

    public void show(Quiz quiz, int total, int correct, int xp,
                     List<QuizTakeController.QuestionResult> results) {
        this.quiz    = quiz;
        this.total   = total;
        this.correct = correct;
        this.xp      = xp;
        this.results = results;

        int pct = total == 0 ? 0 : (int) Math.round((correct * 100.0) / total);

        lblScorePercent.setText(pct + "%");
        lblScorePercent.setStyle(scoreColor(pct) +
                "-fx-font-size:52px;-fx-font-weight:bold;");
        lblScoreDetail.setText(correct + " / " + total + " correct");
        lblXpEarned.setText("⭐ " + xp + " XP earned");
        lblOutcome.setText(outcomeMessage(pct));

        buildSummary();
    }

    // ── Per-question summary rows ─────────────────────────────

    private void buildSummary() {
        summaryContainer.getChildren().clear();
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);

            Label icon = new Label(r.correct() ? "✅" : "❌");
            icon.setMinWidth(24);

            Label qText = new Label((i + 1) + ". " + r.text());
            qText.setWrapText(true);
            qText.setMaxWidth(320);
            qText.setStyle("-fx-font-size:12px;-fx-text-fill:#37474f;");

            Label xpLabel = new Label(r.correct() ? "+" + r.xpValue() + " XP" : "0 XP");
            xpLabel.setStyle("-fx-font-size:11px;-fx-text-fill:" +
                    (r.correct() ? "#2e7d32" : "#9e9e9e") + ";-fx-font-weight:bold;");

            HBox row = new HBox(8, icon, qText, xpLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle("-fx-background-color:" + (i % 2 == 0 ? "#fafafe" : "white") +
                    ";-fx-background-radius:4;");
            HBox.setHgrow(qText, javafx.scene.layout.Priority.ALWAYS);

            summaryContainer.getChildren().add(row);
        }
    }

    // ── Actions ───────────────────────────────────────────────

    @FXML
    private void handleRetake() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_take.fxml"));
            Parent root = loader.load();
            QuizTakeController ctrl = loader.getController();
            ctrl.loadQuiz(quiz);

            Stage stage = (Stage) lblScorePercent.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(quiz.getTitle());
        } catch (IOException e) {
            lblOutcome.setText("Error reloading quiz: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) lblScorePercent.getScene().getWindow()).close();
    }

    // ── Helpers ───────────────────────────────────────────────

    private String scoreColor(int pct) {
        if (pct >= 80) return "-fx-text-fill:#2e7d32;";
        if (pct >= 50) return "-fx-text-fill:#f57f17;";
        return "-fx-text-fill:#c62828;";
    }

    private String outcomeMessage(int pct) {
        if (pct == 100) return "Perfect score! Outstanding work.";
        if (pct >= 80)  return "Great job! You really know your stuff.";
        if (pct >= 50)  return "Not bad — review the missed questions and try again.";
        return "Keep studying and give it another shot!";
    }
}
