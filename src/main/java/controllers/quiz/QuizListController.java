package controllers.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.quiz.Quiz;
import services.quiz.QuizService;

import java.io.IOException;
import java.util.List;

public class QuizListController {

    @FXML private FlowPane quizGrid;
    @FXML private Label    lblStatus;
    @FXML private VBox     emptyState;

    private final QuizService quizService = new QuizService();

    @FXML
    public void initialize() {
        loadData();
    }

    private void loadData() {
        quizGrid.getChildren().clear();
        List<Quiz> quizzes = quizService.getAllQuizzes();

        emptyState.setVisible(quizzes.isEmpty());
        emptyState.setManaged(quizzes.isEmpty());
        lblStatus.setText(String.valueOf(quizzes.size()));

        for (Quiz quiz : quizzes) {
            quizGrid.getChildren().add(buildQuizCard(quiz));
        }
    }

    private VBox buildQuizCard(Quiz quiz) {
        // Icon box
        Label icon = new Label("📝");
        icon.getStyleClass().add("quiz-card-icon");
        icon.setAlignment(Pos.CENTER);

        // Title
        Label title = new Label(quiz.getTitle());
        title.getStyleClass().add("quiz-card-title");
        title.setWrapText(true);
        title.setMaxWidth(200);

        // Description
        String descText = quiz.getDescription() != null && !quiz.getDescription().isBlank()
                ? quiz.getDescription() : "No description provided.";
        Label desc = new Label(descText);
        desc.getStyleClass().add("quiz-card-desc");
        desc.setMaxWidth(200);
        desc.setMaxHeight(40);

        // Action buttons
        Button btnTake   = new Button("▶ Take");
        Button btnEdit   = new Button("✏");
        Button btnDelete = new Button("🗑");
        btnTake.getStyleClass().add("btn-icon-take");
        btnEdit.getStyleClass().add("btn-icon-edit");
        btnDelete.getStyleClass().add("btn-icon-delete");

        btnTake.setOnAction(e -> openTake(quiz));
        btnEdit.setOnAction(e -> openForm(quiz));
        btnDelete.setOnAction(e -> handleDelete(quiz));

        HBox actions = new HBox(6, btnTake, btnEdit, btnDelete);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(8, 0, 0, 0));

        VBox card = new VBox(10, icon, title, desc, actions);
        card.getStyleClass().addAll("quiz-card", "card-hover");
        card.setPadding(new Insets(20));
        card.setPrefWidth(230);
        card.setMaxWidth(230);

        return card;
    }

    @FXML
    private void handleCreate() {
        openForm(null);
    }

    private void openTake(Quiz quiz) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_take.fxml"));
            Parent root = loader.load();
            QuizTakeController ctrl = loader.getController();
            ctrl.loadQuiz(quiz);

            Stage stage = new Stage();
            stage.setTitle("▶ " + quiz.getTitle());
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Could not open quiz: " + e.getMessage());
        }
    }

    private void openForm(Quiz quiz) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_form.fxml"));
            Parent root = loader.load();
            QuizFormController ctrl = loader.getController();
            if (quiz != null) ctrl.loadQuiz(quiz);

            Stage stage = new Stage();
            stage.setTitle(quiz == null ? "New Quiz" : "Edit — " + quiz.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setOnHidden(e -> loadData());
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Could not open quiz form: " + e.getMessage());
        }
    }

    private void handleDelete(Quiz quiz) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + quiz.getTitle() + "\"? This removes all its questions and choices.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                quizService.deleteQuiz(quiz.getId());
                loadData();
            }
        });
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(title);
        a.showAndWait();
    }
}
