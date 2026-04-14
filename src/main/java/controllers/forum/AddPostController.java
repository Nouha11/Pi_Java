package controllers.forum;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
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

    private PostService postService = new PostService();

    private String selectedFileName = null;
    private String selectedFilePath = null;

    private Map<String, Integer> databaseSpaces;

    @FXML
    public void initialize() {
        databaseSpaces = postService.getSpacesMap();
        spaceCombo.getItems().addAll(databaseSpaces.keySet());
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
        String title = titleField.getText();
        String content = contentArea.getText();
        String spaceSelection = spaceCombo.getValue();
        String tags = tagsField.getText();
        String link = linkField.getText();

        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            showAlert("Validation Error", "Title and Content are required!", Alert.AlertType.ERROR);
            return;
        }

        Integer spaceId = null;
        if (spaceSelection != null) {
            spaceId = databaseSpaces.get(spaceSelection);
        }

        Post newPost = new Post(title, content, 1, spaceId);
        newPost.setTags(tags);
        if (link != null && !link.trim().isEmpty()) newPost.setLink(link);

        // --- THE NESTED SYMFONY FILE COPY MAGIC ---
        if (selectedFileName != null && selectedFilePath != null) {
            try {
                java.nio.file.Path sourcePath = java.nio.file.Paths.get(selectedFilePath);

                // 🔥 UPDATED: Added /Pi_web/ to the path!
                String xamppPath = "C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + selectedFileName;
                java.nio.file.Path destPath = java.nio.file.Paths.get(xamppPath);

                // SAFETY: Ensures the folder structure exists
                java.nio.file.Files.createDirectories(destPath.getParent());

                java.nio.file.Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                newPost.setAttachmentName(selectedFileName);
                newPost.setImageName(selectedFileName);

            } catch (java.io.IOException e) {
                System.err.println("Failed to copy file to Symfony folder!");
                e.printStackTrace();
            }
        }

        postService.ajouter(newPost);
        showAlert("Success", "Post created and image uploaded to server!", Alert.AlertType.INFORMATION);

        titleField.clear();
        contentArea.clear();
        tagsField.clear();
        linkField.clear();
        spaceCombo.setValue(null);
        fileNameLabel.setText("No file selected");
        selectedFileName = null;
        selectedFilePath = null;
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}