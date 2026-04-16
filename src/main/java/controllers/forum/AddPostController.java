package controllers.forum;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import models.forum.Post;
import services.forum.PostService;

import java.io.File;
import java.util.Map;

public class AddPostController {

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private ComboBox<String> spaceCombo;
    @FXML private TextField tagsField;
    @FXML private TextField linkField;
    @FXML private Label fileNameLabel;

    @FXML private Label titleError;
    @FXML private Label spaceError;
    @FXML private Label contentError;

    private PostService postService = new PostService();
    private String selectedFileName = null;
    private String selectedFilePath = null;
    private Map<String, Integer> databaseSpaces;

    @FXML
    public void initialize() {
        databaseSpaces = postService.getSpacesMap();
        spaceCombo.getItems().addAll(databaseSpaces.keySet());

        titleField.textProperty().addListener((observable, oldValue, newValue) -> clearError(titleField, titleError));
        contentArea.textProperty().addListener((observable, oldValue, newValue) -> clearError(contentArea, contentError));
        spaceCombo.valueProperty().addListener((observable, oldValue, newValue) -> clearError(spaceCombo, spaceError));
    }

    @FXML
    void handleUploadFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Attachment");
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            selectedFileName = selectedFile.getName();
            selectedFilePath = selectedFile.getAbsolutePath();
            fileNameLabel.setText(selectedFileName);
        }
    }

    @FXML
    void handleAddPost(ActionEvent event) {
        // 1. Reset all errors first
        clearError(titleField, titleError);
        clearError(spaceCombo, spaceError);
        clearError(contentArea, contentError);

        String title = titleField.getText();
        String content = contentArea.getText();
        String spaceSelection = spaceCombo.getValue();
        String tags = tagsField.getText();
        String link = linkField.getText();

        boolean isValid = true;

        // 2. Perform Validation Checks
        if (title == null || title.trim().length() < 5) {
            showError(titleField, titleError, "Title is too short. (Min 5 characters)");
            isValid = false;
        } else if (!postService.isTitleUnique(title)) {
            showError(titleField, titleError, "A discussion with this exact title already exists!");
            isValid = false;
        }

        if (spaceSelection == null) {
            showError(spaceCombo, spaceError, "You must select a space for this post.");
            isValid = false;
        }

        if (content == null || content.trim().length() < 10) {
            showError(contentArea, contentError, "Content is too short. Please add more details.");
            isValid = false;
        }

        // Stop execution if anything failed!
        if (!isValid) {
            return;
        }

        // 3. Create the Post
        Integer spaceId = databaseSpaces.get(spaceSelection);

        // 🔥 PERFECTLY FIXED FOR USER ID 7 🔥
        Post newPost = new Post(title, content, 7, spaceId);

        newPost.setTags(tags);
        if (link != null && !link.trim().isEmpty()) newPost.setLink(link);

        if (selectedFileName != null && selectedFilePath != null) {
            try {
                java.nio.file.Path sourcePath = java.nio.file.Paths.get(selectedFilePath);
                String xamppPath = "C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + selectedFileName;
                java.nio.file.Path destPath = java.nio.file.Paths.get(xamppPath);

                java.nio.file.Files.createDirectories(destPath.getParent());
                java.nio.file.Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                newPost.setAttachmentName(selectedFileName);
                newPost.setImageName(selectedFileName);

            } catch (java.io.IOException e) {
                System.err.println("Failed to copy file to Symfony folder!");
                e.printStackTrace();
            }
        }

        // 4. Save to Database
        postService.ajouter(newPost);

        showAlert("Success", "Your post has been published to the forum!", Alert.AlertType.INFORMATION);

        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }

    private void showError(Region field, Label errorLabel, String message) {
        field.getStyleClass().add("error-input");
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError(Region field, Label errorLabel) {
        field.getStyleClass().remove("error-input");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}