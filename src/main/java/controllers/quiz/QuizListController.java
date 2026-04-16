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
    @FXML private TextField txtMinQ;
    @FXML private TextField txtMaxQ;
    @FXML private ComboBox<String> cmbSort;
    @FXML private ComboBox<String> cmbDesc;

    private static final String SORT_AZ       = "Title A \u2192 Z";
    private static final String SORT_ZA       = "Title Z \u2192 A";
    private static final String SORT_MOST_Q   = "Most Questions";
    private static final String SORT_FEWEST_Q = "Fewest Questions";

    private static final String DESC_ANY = "Any";
    private static final String DESC_YES = "Has description";
    private static final String DESC_NO  = "No description";

    private final QuizService quizService = new QuizService();
    private List<Quiz> allQuizzes;

    @FXML
    public void initialize() {
        cmbSort.setItems(FXCollections.observableArrayList(
                SORT_AZ, SORT_ZA, SORT_MOST_Q, SORT_FEWEST_Q));
        cmbSort.setValue(SORT_AZ);

        cmbDesc.setItems(FXCollections.observableArrayList(DESC_ANY, DESC_YES, DESC_NO));
        cmbDesc.setValue(DESC_ANY);

        loadData();
    }

    private void loadData() {
        allQuizzes = quizService.getAllQuizzes();
        applyFilterSort();
    }

    @FXML
    private void handleFilterSort() {
        applyFilterSort();
    }

    @FXML
    private void handleClearFilters() {
        txtSearch.clear();
        txtMinQ.clear();
        txtMaxQ.clear();
        cmbSort.setValue(SORT_AZ);
        cmbDesc.setValue(DESC_ANY);
        applyFilterSort();
    }

    private void applyFilterSort() {
        String query  = txtSearch.getText().trim().toLowerCase();
        Integer minQ  = parseIntOrNull(txtMinQ.getText());
        Integer maxQ  = parseIntOrNull(txtMaxQ.getText());
        String  desc  = cmbDesc.getValue();

        List<Quiz> result = allQuizzes.stream()
                // text search
                .filter(q -> query.isEmpty()
                        || q.getTitle().toLowerCase().contains(query)
                        || (q.getDescription() != null
                            && q.getDescription().toLowerCase().contains(query)))
                // min questions
                .filter(q -> minQ == null || q.getQuestionCount() >= minQ)
                // max questions
                .filter(q -> maxQ == null || q.getQuestionCount() <= maxQ)
                // description presence
                .filter(q -> {
                    boolean hasDesc = q.getDescription() != null && !q.getDescription().isBlank();
                    if (DESC_YES.equals(desc)) return hasDesc;
                    if (DESC_NO.equals(desc))  return !hasDesc;
                    return true;
                })
                .collect(Collectors.toList());

        // sort
        String sort = cmbSort.getValue();
        if (SORT_ZA.equals(sort)) {
            result.sort(Comparator.comparing(Quiz::getTitle, String.CASE_INSENSITIVE_ORDER).reversed());
        } else if (SORT_MOST_Q.equals(sort)) {
            result.sort(Comparator.comparingInt(Quiz::getQuestionCount).reversed());
        } else if (SORT_FEWEST_Q.equals(sort)) {
            result.sort(Comparator.comparingInt(Quiz::getQuestionCount));
        } else {
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
        Label icon = new Label("?");
        icon.getStyleClass().add("quiz-card-icon");

        Label title = new Label(quiz.getTitle());
        title.getStyleClass().add("quiz-card-title");
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setMaxWidth(200);

        String descText = quiz.getDescription() != null && !quiz.getDescription().isBlank()
                ? quiz.getDescription() : "No description provided.";
        Label description = new Label(descText);
        description.getStyleClass().add("quiz-card-desc");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setMaxWidth(200);

        Label badge = new Label(quiz.getQuestionCount() + " question"
                + (quiz.getQuestionCount() == 1 ? "" : "s"));
        badge.getStyleClass().add("quiz-card-badge");

        Button btnEdit   = new Button("\u270F  Edit");
        Button btnDelete = new Button("\uD83D\uDDD1  Delete");
        btnEdit.getStyleClass().add("btn-card-edit");
        btnDelete.getStyleClass().add("btn-card-delete");
        btnEdit.setOnAction(e -> openForm(quiz));
        btnDelete.setOnAction(e -> handleDelete(quiz));

        HBox actions = new HBox(8, btnEdit, btnDelete);
        actions.setAlignment(Pos.CENTER);

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
            stage.setTitle(quiz == null ? "New Quiz" : "Edit Quiz \u2014 " + quiz.getTitle());
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

    private static Integer parseIntOrNull(String s) {
        try { return s == null || s.isBlank() ? null : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
