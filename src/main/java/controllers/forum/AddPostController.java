package controllers.forum;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import models.forum.Post;
import services.forum.PostService;
import services.api.GeminiService;
import services.forum.PollService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddPostController {

    @FXML private Label mainTitleLabel;
    @FXML private Button submitButton;
    @FXML private Button enhanceButton;

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private ComboBox<String> spaceCombo;
    @FXML private TextField tagsField;
    @FXML private TextField linkField;
    @FXML private Label fileNameLabel;

    @FXML private Label titleError;
    @FXML private Label spaceError;
    @FXML private Label contentError;

    // 🔥 POLL UI ELEMENTS
    @FXML private VBox pollSection;
    @FXML private VBox pollOptionsContainer;
    @FXML private TextField pollQuestionField;
    private List<TextField> pollOptionFields = new ArrayList<>();

    private PostService postService = new PostService();
    private GeminiService geminiService = new GeminiService();
    private PollService pollService = new PollService();

    private String selectedFileName = null;
    private String selectedFilePath = null;
    private Map<String, Integer> databaseSpaces;

    private Post postToEdit = null;

    @FXML
    public void initialize() {
        new Thread(() -> {
            databaseSpaces = postService.getSpacesMap();
            Platform.runLater(() -> {
                if (databaseSpaces != null) {
                    spaceCombo.getItems().addAll(databaseSpaces.keySet());
                }
            });
        }).start();

        titleField.textProperty().addListener((o, oldV, newV) -> clearError(titleField, titleError));
        contentArea.textProperty().addListener((o, oldV, newV) -> clearError(contentArea, contentError));
        spaceCombo.valueProperty().addListener((o, oldV, newV) -> clearError(spaceCombo, spaceError));
    }

    // 🔥 NEW: Toggle Poll UI
    @FXML
    void handleTogglePoll(ActionEvent event) {
        if (pollSection != null) {
            boolean isVisible = pollSection.isVisible();
            pollSection.setVisible(!isVisible);
            pollSection.setManaged(!isVisible);

            if (!isVisible && pollOptionFields.isEmpty()) {
                handleAddPollOption(null);
                handleAddPollOption(null);
            }
        }
    }

    // 🔥 NEW: Add Poll Option
    @FXML
    void handleAddPollOption(ActionEvent event) {
        if (pollOptionFields.size() >= 5) return;
        TextField optionField = new TextField();
        optionField.setPromptText("Option " + (pollOptionFields.size() + 1));
        optionField.setStyle("-fx-pref-height: 40; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");
        pollOptionFields.add(optionField);
        if (pollOptionsContainer != null) {
            pollOptionsContainer.getChildren().add(optionField);
        }
    }

    public void setPostToEdit(Post post) {
        this.postToEdit = post;

        if (mainTitleLabel != null) mainTitleLabel.setText("Edit Discussion");
        if (submitButton != null) submitButton.setText("Save Changes");

        titleField.setText(post.getTitle());
        contentArea.setText(post.getContent());

        new Thread(() -> {
            while (databaseSpaces == null) {
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
            Platform.runLater(() -> {
                for (String spaceName : databaseSpaces.keySet()) {
                    if (databaseSpaces.get(spaceName).equals(post.getSpaceId())) {
                        spaceCombo.setValue(spaceName);
                        break;
                    }
                }
            });
        }).start();

        if (post.getTags() != null) tagsField.setText(post.getTags());
        if (post.getLink() != null) linkField.setText(post.getLink());
        if (post.getImageName() != null) fileNameLabel.setText("Current: " + post.getImageName());
    }

    @FXML
    void handleMagicEnhance(ActionEvent event) {
        String draft = contentArea.getText();

        if (draft == null || draft.trim().length() < 10) {
            showCustomAlert("Too Short", "Please write a bit more context before using the AI enhancer.", true);
            return;
        }

        contentArea.setDisable(true);
        String originalText = contentArea.getText();
        contentArea.clear();
        contentArea.setPromptText("✨ AI is analyzing and enhancing your post... Please wait.");

        if (enhanceButton != null) enhanceButton.setDisable(true);

        new Thread(() -> {
            String enhancedText = geminiService.enhancePost(originalText);

            Platform.runLater(() -> {
                contentArea.setDisable(false);
                contentArea.setPromptText("");
                if (enhanceButton != null) enhanceButton.setDisable(false);

                if (enhancedText != null && !enhancedText.contains("Error")) {
                    contentArea.setText(enhancedText);
                } else {
                    contentArea.setText(originalText);
                    showCustomAlert("AI Error", "The AI service is currently unavailable. Please try again later.", true);
                }
            });
        }).start();
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
        clearError(titleField, titleError);
        clearError(spaceCombo, spaceError);
        clearError(contentArea, contentError);

        String title = titleField.getText();
        String content = contentArea.getText();
        String spaceSelection = spaceCombo.getValue();
        String tags = tagsField.getText();
        String link = linkField.getText();

        boolean isValid = true;

        if (title == null || title.trim().length() < 5) {
            showError(titleField, titleError, "Title is too short. (Min 5 characters)");
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

        if (!isValid) return;

        Integer spaceId = databaseSpaces.get(spaceSelection);

        if (postToEdit == null) {
            if (!postService.isTitleUnique(title)) {
                showError(titleField, titleError, "A discussion with this exact title already exists!");
                return;
            }

            int currentUserId = utils.UserSession.getInstance().getUserId();
            Post newPost = new Post(title, content, currentUserId, spaceId);
            newPost.setTags(tags);
            if (link != null && !link.trim().isEmpty()) newPost.setLink(link);

            handleImageCopy(newPost);

            // 🔥 SAVES POST AND EXTRACTS ID FOR POLL LINKING
            int postId = postService.ajouterAndGetId(newPost);

            // 🔥 SAVE THE POLL
            if (pollSection != null && pollSection.isVisible() && pollQuestionField != null && !pollQuestionField.getText().trim().isEmpty()) {
                List<String> options = new ArrayList<>();
                for (TextField tf : pollOptionFields) {
                    if (!tf.getText().trim().isEmpty()) options.add(tf.getText().trim());
                }
                if (options.size() >= 2) {
                    pollService.createPoll(postId, pollQuestionField.getText().trim(), options);
                }
            }

            showCustomAlert("Success!", "Your post has been published to the forum.", false);

        } else {
            if (!title.equalsIgnoreCase(postToEdit.getTitle()) && !postService.isTitleUnique(title)) {
                showError(titleField, titleError, "A discussion with this exact title already exists!");
                return;
            }

            postToEdit.setTitle(title);
            postToEdit.setContent(content);
            postToEdit.setSpaceId(spaceId);
            postToEdit.setTags(tags);
            postToEdit.setLink(link);

            handleImageCopy(postToEdit);
            postService.modifier(postToEdit);
            showCustomAlert("Success!", "Post updated successfully.", false);
        }

        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }

    // 🔥 NEW: Beautiful Custom Popup Alert
    private void showCustomAlert(String title, String message, boolean isError) {
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            VBox box = new VBox(15);
            box.setAlignment(Pos.CENTER);
            box.setStyle("-fx-background-color: white; -fx-padding: 30; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 15, 0, 0, 5);");
            Label t = new Label(title);
            t.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: " + (isError ? "#ef4444" : "#10b981") + ";");
            Label m = new Label(message);
            m.setStyle("-fx-font-size: 14px; -fx-text-fill: #475569;");
            Button ok = new Button("Got it");
            ok.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 24; -fx-background-radius: 6; -fx-cursor: hand;");
            ok.setOnAction(e -> stage.close());
            box.getChildren().addAll(t, m, ok);
            Scene scene = new Scene(box);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.showAndWait();
        });
    }

    private void handleImageCopy(Post post) {
        if (selectedFileName != null && selectedFilePath != null) {
            try {
                java.nio.file.Path sourcePath = java.nio.file.Paths.get(selectedFilePath);
                String xamppPath = "C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + selectedFileName;
                java.nio.file.Path destPath = java.nio.file.Paths.get(xamppPath);

                java.nio.file.Files.createDirectories(destPath.getParent());
                java.nio.file.Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                post.setAttachmentName(selectedFileName);
                post.setImageName(selectedFileName);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
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
}