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

    // This map stores the real Spaces from your Database! (Name -> ID)
    private Map<String, Integer> databaseSpaces;

    @FXML
    public void initialize() {
        // 1. Fetch real spaces from the database
        databaseSpaces = postService.getSpacesMap();

        // 2. Put only the Names into the dropdown for the user to see
        spaceCombo.getItems().addAll(databaseSpaces.keySet());
    }

    @FXML
    void handleUploadFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Attachment");
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            selectedFileName = selectedFile.getName();
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

        // Get the real Database ID for the selected Space using our Map
        Integer spaceId = null;
        if (spaceSelection != null) {
            spaceId = databaseSpaces.get(spaceSelection);
        }

        // Create the Post (Author = 1 for now)
        Post newPost = new Post(title, content, 1, spaceId);
        newPost.setTags(tags);

        if (link != null && !link.trim().isEmpty()) newPost.setLink(link);
        if (selectedFileName != null) {
            newPost.setAttachmentName(selectedFileName);
            newPost.setImageName(selectedFileName);
        }

        postService.ajouter(newPost);
        showAlert("Success", "Post created successfully and synced with DB!", Alert.AlertType.INFORMATION);

        titleField.clear();
        contentArea.clear();
        tagsField.clear();
        linkField.clear();
        spaceCombo.setValue(null);
        fileNameLabel.setText("No file selected");
        selectedFileName = null;
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}