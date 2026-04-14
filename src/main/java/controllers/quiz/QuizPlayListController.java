package controllers.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.quiz.Quiz;
import services.quiz.QuizService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class QuizPlayListController {

    @FXML private FlowPane cardsPane;
    @FXML private Label     lblStatus;
    @FXML private TextField txtSearch;

    private final QuizService quizService = new QuizService();
    private List<Quiz> allQuizzes;

    @FXML
    public void initialize() {
        allQuizzes = quizService.getAllQuizzes();
        renderCards(allQuizzes);
    }

    @FXML
    private void handleSearch() {
        String q = txtSearch.getText().trim().toLowerCase();
        List<Quiz> filtered = allQuizzes.stream()
                .filter(quiz -> quiz.getTitle().toLowerCase().contains(q)
                        || (quiz.getDescription() != null
                            && quiz.getDescription().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        renderCards(filtered);
    }

    private void renderCards(List<Quiz> quizzes) {
        cardsPane.getChildren().clear();
        for (Quiz quiz : quizzes) {
            cardsPane.getChildren().add(buildCard(quiz));
        }
        lblStatus.setText(quizzes.size() + " quiz" + (quizzes.size() == 1 ? "" : "zes") + " available");
    }

    private VBox buildCard(Quiz quiz) {
        Label icon = new Label("?");
        icon.getStyleClass().add("quiz-card-icon");

        Label title = new Label(quiz.getTitle());
        title.getStyleClass().add("quiz-card-title");
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setMaxWidth(200);

        String descText = (quiz.getDescription() != null && !quiz.getDescription().isBlank())
                ? quiz.getDescription() : "No description provided.";
        Label desc = new Label(descText);
        desc.getStyleClass().add("quiz-card-desc");
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setMaxWidth(200);

        Button btnPlay = new Button("▶  Play Quiz");
        btnPlay.getStyleClass().add("btn-play");
        btnPlay.setOnAction(e -> openPlay(quiz));

        VBox card = new VBox(12, icon, title, desc, btnPlay);
        card.getStyleClass().add("quiz-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(232);

        return card;
    }

    private void openPlay(Quiz quiz) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_play.fxml"));
            Parent root = loader.load();

            QuizPlayController ctrl = loader.getController();
            ctrl.loadQuiz(quiz);

            Stage stage = new Stage();
            stage.setTitle("Playing — " + quiz.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open quiz: " + e.getMessage(),
                    ButtonType.OK).showAndWait();
        }
    }
}
