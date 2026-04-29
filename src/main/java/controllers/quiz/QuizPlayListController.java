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

public class QuizPlayListController {

    @FXML private FlowPane  cardsPane;
    @FXML private Label     lblStatus;
    @FXML private TextField txtSearch;
    @FXML private TextField txtMinQ;
    @FXML private TextField txtMaxQ;
    @FXML private ComboBox<String> cmbSort;
    @FXML private ComboBox<String> cmbDesc;

    // Pagination controls
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label  lblPage;

    private static final int PAGE_SIZE = 12;

    private static final String SORT_AZ       = "Title A \u2192 Z";
    private static final String SORT_ZA       = "Title Z \u2192 A";
    private static final String SORT_MOST_Q   = "Most Questions";
    private static final String SORT_FEWEST_Q = "Fewest Questions";

    private static final String DESC_ANY = "Any";
    private static final String DESC_YES = "Has description";
    private static final String DESC_NO  = "No description";

    private final QuizService quizService = new QuizService();
    private List<Quiz> allQuizzes;
    private List<Quiz> filteredQuizzes;
    private int currentPage = 1;

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
        currentPage = 1;
        applyFilterSort();
    }

    @FXML
    private void handleClearFilters() {
        txtSearch.clear();
        txtMinQ.clear();
        txtMaxQ.clear();
        cmbSort.setValue(SORT_AZ);
        cmbDesc.setValue(DESC_ANY);
        currentPage = 1;
        applyFilterSort();
    }

    @FXML
    private void handlePrev() {
        if (currentPage > 1) {
            currentPage--;
            renderCurrentPage();
        }
    }

    @FXML
    private void handleNext() {
        if (currentPage < totalPages()) {
            currentPage++;
            renderCurrentPage();
        }
    }

    private void applyFilterSort() {
        String  query = txtSearch.getText().trim().toLowerCase();
        Integer minQ  = parseIntOrNull(txtMinQ.getText());
        Integer maxQ  = parseIntOrNull(txtMaxQ.getText());
        String  desc  = cmbDesc.getValue();

        filteredQuizzes = allQuizzes.stream()
                .filter(q -> query.isEmpty()
                        || q.getTitle().toLowerCase().contains(query)
                        || (q.getDescription() != null
                            && q.getDescription().toLowerCase().contains(query)))
                .filter(q -> minQ == null || q.getQuestionCount() >= minQ)
                .filter(q -> maxQ == null || q.getQuestionCount() <= maxQ)
                .filter(q -> {
                    boolean hasDesc = q.getDescription() != null && !q.getDescription().isBlank();
                    if (DESC_YES.equals(desc)) return hasDesc;
                    if (DESC_NO.equals(desc))  return !hasDesc;
                    return true;
                })
                .collect(Collectors.toList());

        String sort = cmbSort.getValue();
        if (SORT_ZA.equals(sort)) {
            filteredQuizzes.sort(Comparator.comparing(Quiz::getTitle, String.CASE_INSENSITIVE_ORDER).reversed());
        } else if (SORT_MOST_Q.equals(sort)) {
            filteredQuizzes.sort(Comparator.comparingInt(Quiz::getQuestionCount).reversed());
        } else if (SORT_FEWEST_Q.equals(sort)) {
            filteredQuizzes.sort(Comparator.comparingInt(Quiz::getQuestionCount));
        } else {
            filteredQuizzes.sort(Comparator.comparing(Quiz::getTitle, String.CASE_INSENSITIVE_ORDER));
        }

        renderCurrentPage();
    }

    private void renderCurrentPage() {
        int total      = filteredQuizzes.size();
        int totalPages = totalPages();

        if (currentPage < 1) currentPage = 1;
        if (currentPage > totalPages && totalPages > 0) currentPage = totalPages;

        int from = (currentPage - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, total);
        List<Quiz> page = total == 0 ? List.of() : filteredQuizzes.subList(from, to);

        cardsPane.getChildren().clear();
        for (Quiz quiz : page) {
            cardsPane.getChildren().add(buildCard(quiz));
        }

        if (total == 0) {
            lblStatus.setText("No quizzes available");
        } else {
            lblStatus.setText("Showing " + (from + 1) + "–" + to + " of " + total
                    + " quiz" + (total == 1 ? "" : "zes"));
        }

        lblPage.setText(totalPages == 0 ? "Page 0 / 0" : "Page " + currentPage + " / " + totalPages);
        btnPrev.setDisable(currentPage <= 1);
        btnNext.setDisable(currentPage >= totalPages || totalPages == 0);
    }

    private int totalPages() {
        if (filteredQuizzes == null || filteredQuizzes.isEmpty()) return 0;
        return (int) Math.ceil((double) filteredQuizzes.size() / PAGE_SIZE);
    }

    // ── Card builder ──────────────────────────────────────────

    private VBox buildCard(Quiz quiz) {
        // ── Colour accent based on question count ─────────────
        int qCount = quiz.getQuestionCount();
        String accentColor, accentBg, accentBorder;
        String diffLabel;
        if (qCount >= 15) {
            accentColor = "#e53e3e"; accentBg = "#fff5f5"; accentBorder = "#feb2b2";
            diffLabel = "Hard";
        } else if (qCount >= 8) {
            accentColor = "#d97706"; accentBg = "#fffbeb"; accentBorder = "#fcd34d";
            diffLabel = "Medium";
        } else {
            accentColor = "#27ae60"; accentBg = "#f0fff4"; accentBorder = "#9ae6b4";
            diffLabel = "Easy";
        }

        // ── Left accent strip ─────────────────────────────────
        Region strip = new Region();
        strip.setPrefWidth(5);
        strip.setMinWidth(5);
        strip.setMaxWidth(5);
        strip.setStyle("-fx-background-color:" + accentColor + "; -fx-background-radius:12 0 0 12;");

        // ── Icon circle ───────────────────────────────────────
        Label iconLbl = new Label("?");
        iconLbl.setStyle(
                "-fx-font-size:20px; -fx-font-weight:bold;" +
                "-fx-text-fill:" + accentColor + ";" +
                "-fx-background-color:" + accentBg + ";" +
                "-fx-background-radius:50%;" +
                "-fx-border-color:" + accentBorder + ";" +
                "-fx-border-radius:50%; -fx-border-width:2;" +
                "-fx-min-width:48px; -fx-min-height:48px;" +
                "-fx-max-width:48px; -fx-max-height:48px;" +
                "-fx-alignment:center;");

        // ── Title ─────────────────────────────────────────────
        Label titleLbl = new Label(quiz.getTitle());
        titleLbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1e2a5e;");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(Double.MAX_VALUE);

        // ── Description ───────────────────────────────────────
        String descText = quiz.getDescription() != null && !quiz.getDescription().isBlank()
                ? quiz.getDescription() : "No description provided.";
        // Truncate long descriptions
        if (descText.length() > 110) descText = descText.substring(0, 107) + "…";
        Label descLbl = new Label(descText);
        descLbl.setStyle("-fx-font-size:12px; -fx-text-fill:#718096;");
        descLbl.setWrapText(true);
        descLbl.setMaxWidth(Double.MAX_VALUE);

        // ── Tags row: question count + difficulty ─────────────
        Label qBadge = new Label("📝  " + qCount + " question" + (qCount == 1 ? "" : "s"));
        qBadge.setStyle(
                "-fx-font-size:11px; -fx-font-weight:bold;" +
                "-fx-text-fill:#3b4fd8;" +
                "-fx-background-color:#eef0fd;" +
                "-fx-border-color:#c3c9f5;" +
                "-fx-border-radius:20; -fx-background-radius:20;" +
                "-fx-border-width:1; -fx-padding:3 10 3 10;");

        Label diffBadge = new Label(diffLabel);
        diffBadge.setStyle(
                "-fx-font-size:11px; -fx-font-weight:bold;" +
                "-fx-text-fill:" + accentColor + ";" +
                "-fx-background-color:" + accentBg + ";" +
                "-fx-border-color:" + accentBorder + ";" +
                "-fx-border-radius:20; -fx-background-radius:20;" +
                "-fx-border-width:1; -fx-padding:3 10 3 10;");

        HBox tagsRow = new HBox(8, qBadge, diffBadge);
        tagsRow.setAlignment(Pos.CENTER_LEFT);

        // ── Text block ────────────────────────────────────────
        VBox textBlock = new VBox(6, titleLbl, descLbl, tagsRow);
        textBlock.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(textBlock, Priority.ALWAYS);

        // ── Play button ───────────────────────────────────────
        Button btnPlay = new Button("▶  Play");
        btnPlay.setStyle(
                "-fx-background-color:linear-gradient(to bottom,#4a5ef7,#3b4fd8);" +
                "-fx-text-fill:white; -fx-font-weight:bold;" +
                "-fx-background-radius:8; -fx-cursor:hand;" +
                "-fx-padding:9 20 9 20; -fx-font-size:12px;" +
                "-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.35),8,0,0,2);");
        btnPlay.setOnMouseEntered(e -> btnPlay.setStyle(
                "-fx-background-color:linear-gradient(to bottom,#5b6ef5,#4a5ef7);" +
                "-fx-text-fill:white; -fx-font-weight:bold;" +
                "-fx-background-radius:8; -fx-cursor:hand;" +
                "-fx-padding:9 20 9 20; -fx-font-size:12px;" +
                "-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.5),10,0,0,3);"));
        btnPlay.setOnMouseExited(e -> btnPlay.setStyle(
                "-fx-background-color:linear-gradient(to bottom,#4a5ef7,#3b4fd8);" +
                "-fx-text-fill:white; -fx-font-weight:bold;" +
                "-fx-background-radius:8; -fx-cursor:hand;" +
                "-fx-padding:9 20 9 20; -fx-font-size:12px;" +
                "-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.35),8,0,0,2);"));
        btnPlay.setOnAction(e -> openPlay(quiz));

        VBox actionCol = new VBox(btnPlay);
        actionCol.setAlignment(Pos.CENTER);

        // ── Inner HBox (icon + text + button) ─────────────────
        HBox inner = new HBox(14, iconLbl, textBlock, actionCol);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(14, 16, 14, 14));
        HBox.setHgrow(inner, Priority.ALWAYS);

        // ── Outer card (strip + inner) ────────────────────────
        HBox card = new HBox(strip, inner);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(380);
        card.setMaxWidth(380);
        card.setStyle(
                "-fx-background-color:white;" +
                "-fx-background-radius:12;" +
                "-fx-border-color:#e8ecf8;" +
                "-fx-border-radius:12;" +
                "-fx-border-width:1;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),12,0,0,3);");
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color:white;" +
                "-fx-background-radius:12;" +
                "-fx-border-color:#3b4fd8;" +
                "-fx-border-radius:12;" +
                "-fx-border-width:1.5;" +
                "-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.15),16,0,0,4);"));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color:white;" +
                "-fx-background-radius:12;" +
                "-fx-border-color:#e8ecf8;" +
                "-fx-border-radius:12;" +
                "-fx-border-width:1;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),12,0,0,3);"));

        // Wrap in VBox so FlowPane treats it as a block
        VBox wrapper = new VBox(card);
        wrapper.setPrefWidth(380);
        wrapper.setMaxWidth(380);
        return wrapper;
    }

    private void openPlay(Quiz quiz) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/quiz/quiz_play.fxml"));
            Parent root = loader.load();
            QuizPlayController ctrl = loader.getController();
            ctrl.loadQuiz(quiz);

            Stage stage = new Stage();
            stage.setTitle("Playing \u2014 " + quiz.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(true);
            stage.setMinWidth(800);
            stage.setMinHeight(560);
            stage.show();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open quiz: " + e.getMessage(),
                    ButtonType.OK).showAndWait();
        }
    }

    private static Integer parseIntOrNull(String s) {
        try { return s == null || s.isBlank() ? null : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
