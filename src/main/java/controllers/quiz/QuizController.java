package controllers.quiz;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import models.quiz.Quiz;
import services.QuizService;

public class QuizController {
    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private Button createButton;
    @FXML
    private Button updateButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label messageLabel;

    private QuizService quizService;
    private Quiz currentQuiz; // To track which quiz we're editing

    @FXML
    public void initialize() {
        this.quizService = new QuizService();
        this.currentQuiz = null;
    }

    // --- CREATE ---
    @FXML
    private void handleCreateQuiz() {
        String title = titleField.getText().trim();
        String description = descriptionField.getText().trim();

        // Validation
        if (!validateInput(title, description)) {
            return;
        }

        // Create Quiz object
        Quiz quiz = new Quiz(title, description.isEmpty() ? null : description);

        // Save to database
        try {
            quizService.createQuiz(quiz);
            showSuccess("Quiz created successfully!");
            clearFields();
        } catch (Exception e) {
            showError("Error creating quiz: " + e.getMessage());
        }
    }

    // --- READ (Load quiz into form) ---
    public void loadQuiz(int quizId) {
        try {
            currentQuiz = quizService.getQuizById(quizId);
            if (currentQuiz != null) {
                titleField.setText(currentQuiz.getTitle());
                descriptionField.setText(currentQuiz.getDescription() != null ? currentQuiz.getDescription() : "");
                
                // Hide create button, show update/delete buttons
                createButton.setVisible(false);
                updateButton.setVisible(true);
                deleteButton.setVisible(true);
                
                showSuccess("Quiz loaded successfully!");
            } else {
                showError("Quiz not found!");
            }
        } catch (Exception e) {
            showError("Error loading quiz: " + e.getMessage());
        }
    }

    // --- UPDATE ---
    @FXML
    private void handleUpdateQuiz() {
        if (currentQuiz == null) {
            showError("No quiz selected for update!");
            return;
        }

        String title = titleField.getText().trim();
        String description = descriptionField.getText().trim();

        // Validation
        if (!validateInput(title, description)) {
            return;
        }

        // Update quiz object
        currentQuiz.setTitle(title);
        currentQuiz.setDescription(description.isEmpty() ? null : description);

        // Save to database
        try {
            quizService.updateQuiz(currentQuiz);
            showSuccess("Quiz updated successfully!");
        } catch (Exception e) {
            showError("Error updating quiz: " + e.getMessage());
        }
    }

    // --- DELETE ---
    @FXML
    private void handleDeleteQuiz() {
        if (currentQuiz == null) {
            showError("No quiz selected for deletion!");
            return;
        }

        try {
            quizService.deleteQuiz(currentQuiz.getId());
            showSuccess("Quiz deleted successfully!");
            clearFields();
            resetForm();
        } catch (Exception e) {
            showError("Error deleting quiz: " + e.getMessage());
        }
    }

    // --- HELPERS ---
    @FXML
    private void handleCancel() {
        clearFields();
        resetForm();
        messageLabel.setText("");
    }

    private void clearFields() {
        titleField.clear();
        descriptionField.clear();
    }

    private void resetForm() {
        currentQuiz = null;
        
        // Show create button, hide update/delete buttons
        if (createButton != null) {
            createButton.setVisible(true);
        }
        if (updateButton != null) {
            updateButton.setVisible(false);
        }
        if (deleteButton != null) {
            deleteButton.setVisible(false);
        }
    }

    private boolean validateInput(String title, String description) {
        if (title.isEmpty()) {
            showError("Quiz title cannot be empty!");
            return false;
        }

        if (title.length() < 3) {
            showError("Quiz title must be at least 3 characters long!");
            return false;
        }

        if (title.length() > 255) {
            showError("Quiz title must not exceed 255 characters!");
            return false;
        }

        if (description.length() > 1000) {
            showError("Description must not exceed 1000 characters!");
            return false;
        }

        return true;
    }

    private void showSuccess(String message) {
        messageLabel.setStyle("-fx-text-fill: #27ae60;");
        messageLabel.setText(message);
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: #e74c3c;");
        messageLabel.setText(message);
    }
}
