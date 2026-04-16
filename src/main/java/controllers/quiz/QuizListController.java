package controllers.quiz;

import javafx.collections.FXCollections;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class QuizListController {

    @FXML private FlowPane  cardsPane;
    @FXML private Label     lblStatus;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbSort;

    private static final String SORT_AZ        = "Title A → Z";
    private static final String SORT_ZA        = "Title Z → A";
    private static final String SORT_MOST_Q    = "Most Questions";
    private static final String SORT_FEWEST_Q  = "Fewest Questions";

    private final QuizService quizService = new QuizService();
    private List<Quiz> allQuizzes;

    @FXML
    public void initialize() {
        cmbSort.setItems(FXCollections.observableArrayList(
                SORT_AZ, SORT_ZA, SORT_MOST_Q, SORT_FEWEST_Q));
        cmbSort.setValue(SORT_AZ);
        loadData();
    }

    private void loadData() {
        allQuizzes = quizService.getAllQuizzes();
        applyFilterSort();
    }

    /** Single handler for both the search field and the sort combo. */
    @FXML
    private void handleFilterSort() {
        applyFilterSort();
    }

    private void applyFilterSort() {
        String query = txtSearch.getText().trim().toLowerCase();

        // 1. Filter by search text
        List<Quiz> result = allQuizzes.stream()
                .filter(q -> q.getTitle().toLowerCase().contains(query)
                        || (q.getDescription() != null
                            && q.getDescription().toLowerCase().contains(query)))
                .collect(Collectors.toList());

        // 2. Sort
        String sort = cmbSort.getValue();
        if (SORT_ZA.equals(sort)) {
            result.sort(Comparator.comparing(Quiz::getTitle, String.CASE_INSENSITIVE_ORDER).reversed());
        } else if (SORT_MOST_Q.equals(sort)) {
            result.sort(Comparator.comparingInt(Quiz::getQuestionCount).reversed());
        } else if (SORT_FEWEST_Q.equals(sort)) {
            result.sort(Comparator.comparingInt(Quiz::getQuestionCount));
        } else { // default: A → Z
            result.sort(Comparator.comparing(Quiz::getTitle, String.CASE_INSENSITIVE_ORDER));
        }

        renderCards(result);
    }

    private void renderCards(List<Quiz> quizzes) {
        cardsPane.getChildren().clear();
        for (Quiz quiz : quizzes) {
            cardsPane.getChildren().add(buildCard(quiz));
        }
        lblStatus.setText(quizzes.size() + " quiz" + (quizzes.size() == 1 ? "" : "zes") + " found");
    }

    private VBox buildCard(Quiz quiz) {
        // ── Icon bubble ──
        Label icon = new Label("?");
        icon.getStyleClass().add("quiz-card-icon");

        // ── Title ──
        Label title = new Label(quiz.getTitle());
        title.getStyleClass().add("quiz-card-title");
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setMaxWidth(200);

        // ── Description ──
        String desc = quiz.getDescription() != null && !quiz.getDescription().isBlank()
                ? quiz.getDescription() : "No description provided.";
        Label description = new Label(desc);
        description.getStyleClass().add("quiz-card-desc");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setMaxWidth(200);

        // ── Question count badge ──
        Label badge = new Label(quiz.getQuestionCount() + " question"
                + (quiz.getQuestionCount() == 1 ? "" : "s"));
        badge.getStyleClass().add("quiz-card-badge");

        // ── Buttons ──
        Button btnEdit   = new Button("✏  Edit");
        Button btnDelete = new Button("🗑  Delete");
        btnEdit.getStyleClass().add("btn-card-edit");
        btnDelete.getStyleClass().add("btn-card-delete");
        btnEdit.setOnAction(e -> openForm(quiz));
        btnDelete.setOnAction(e -> handleDelete(quiz));

        HBox actions = new HBox(8, btnEdit, btnDelete);
        actions.setAlignment(Pos.CENTER);

        // ── Card ──
        VBox card = new VBox(10, icon, title, description, badge, actions);
        card.getStyleClass().add("quiz-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(232);

        return card;
    }

    @FXML
    private void handleCreate() {
        openForm(null);
    }

    private void openForm(Quiz quiz) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_form.fxml"));
            Parent root = loader.load();

            QuizFormController ctrl = loader.getController();
            if (quiz != null) ctrl.loadQuiz(quiz);

            Stage stage = new Stage();
            stage.setTitle(quiz == null ? "New Quiz" : "Edit Quiz — " + quiz.getTitle());
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
                "Delete quiz \"" + quiz.getTitle() + "\"? This will also remove all its questions and choices.",
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
