package controllers.forum;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.forum.Post;
import services.forum.PostService;

import java.io.IOException;

public class ForumFeedController {

    @FXML private ListView<Post> postsListView;
    @FXML private TextField quickPostField;

    private PostService postService = new PostService();

    @FXML
    public void initialize() {
        refreshFeed(); // Moved to a helper method so we can call it after closing the pop-up

        postsListView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        postsListView.setCellFactory(listView -> new ListCell<Post>() {
            @Override
            protected void updateItem(Post post, boolean empty) {
                super.updateItem(post, empty);

                if (empty || post == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    // --- LEFT: UPVOTES ---
                    VBox voteBox = new VBox(5);
                    voteBox.setAlignment(Pos.TOP_CENTER);
                    voteBox.setPadding(new Insets(10, 15, 0, 10));
                    voteBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent #e2e8f0 transparent transparent; -fx-border-width: 0 1 0 0;");

                    Label upArrow = new Label("↑");
                    upArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff4500; -fx-font-weight: bold; -fx-cursor: hand;");
                    Label voteCount = new Label(String.valueOf(post.getUpvotes()));
                    voteCount.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                    Label downArrow = new Label("↓");
                    downArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #718096; -fx-font-weight: bold; -fx-cursor: hand;");

                    voteBox.getChildren().addAll(upArrow, voteCount, downArrow);

                    // --- RIGHT: CONTENT ---
                    VBox contentBox = new VBox(8);
                    contentBox.setPadding(new Insets(10, 15, 10, 15));
                    HBox.setHgrow(contentBox, Priority.ALWAYS);

                    // 1. Header
                    HBox headerRow = new HBox(5);
                    headerRow.setAlignment(Pos.CENTER_LEFT);
                    Label dot = new Label("🟢");
                    dot.setStyle("-fx-font-size: 8px;");
                    Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
                    spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1a202c;");
                    Label authorLabel = new Label(" •  Posted by u/" + (post.getAuthorName() != null ? post.getAuthorName() : "Unknown"));
                    authorLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px;");
                    headerRow.getChildren().addAll(dot, spaceLabel, authorLabel);

                    // 2. Title & Text
                    Label titleLabel = new Label(post.getTitle());
                    titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
                    titleLabel.setStyle("-fx-text-fill: #1a202c;");

                    Label contentLabel = new Label(post.getContent());
                    contentLabel.setWrapText(true);
                    contentLabel.setMaxHeight(60);
                    contentLabel.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");

                    // 3. BLUE PILL TAGS
                    HBox tagsBox = new HBox(8);
                    if (post.getTags() != null && !post.getTags().isEmpty()) {
                        String[] tagsArray = post.getTags().split(",");
                        for (String tag : tagsArray) {
                            if (!tag.trim().isEmpty()) {
                                Label pill = new Label("# " + tag.trim());
                                pill.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-padding: 3 10 3 10; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: bold;");
                                tagsBox.getChildren().add(pill);
                            }
                        }
                    }

                    // 4. ACTUAL IMAGE / ATTACHMENT DISPLAY (XAMPP LINK)
                    VBox attachmentBox = new VBox();
                    if (post.getImageName() != null || post.getAttachmentName() != null) {
                        String fileName = post.getImageName() != null ? post.getImageName() : post.getAttachmentName();
                        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

                        // If it's an image, render it!
                        if (fileExtension.equals("jpg") || fileExtension.equals("jpeg") || fileExtension.equals("png") || fileExtension.equals("gif")) {
                            // Ensure XAMPP is running to see this!
                            String imageUrl = "http://localhost/uploads/" + fileName.replace(" ", "%20");

                            javafx.scene.image.Image img = new javafx.scene.image.Image(imageUrl, true);
                            javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(img);
                            imageView.setFitWidth(400);
                            imageView.setPreserveRatio(true);

                            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(400, 400);
                            clip.setArcWidth(15);
                            clip.setArcHeight(15);
                            imageView.setClip(clip);

                            attachmentBox.getChildren().add(imageView);
                            attachmentBox.setPadding(new Insets(10, 0, 5, 0));

                        } else {
                            Label attachLabel = new Label("📄 " + fileName + "\nClick to Download");
                            attachLabel.setAlignment(Pos.CENTER);
                            attachLabel.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-padding: 20; -fx-text-fill: #475569; -fx-cursor: hand;");
                            attachLabel.setMaxWidth(Double.MAX_VALUE);
                            attachmentBox.getChildren().add(attachLabel);
                            attachmentBox.setPadding(new Insets(10, 0, 5, 0));
                        }
                    }

                    // 5. Footer Actions
                    HBox footerRow = new HBox(15);
                    footerRow.setPadding(new Insets(10, 0, 0, 0));
                    Label commentsLabel = new Label("💬 Comments");
                    commentsLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
                    Label shareLabel = new Label("🔗 Share");
                    shareLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
                    Label saveLabel = new Label("🔖 Save");
                    saveLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
                    footerRow.getChildren().addAll(commentsLabel, shareLabel, saveLabel);

                    contentBox.getChildren().addAll(headerRow, titleLabel, contentLabel);
                    if (!tagsBox.getChildren().isEmpty()) contentBox.getChildren().add(tagsBox);
                    if (!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
                    contentBox.getChildren().add(footerRow);

                    // --- COMBINE LEFT AND RIGHT ---
                    HBox mainCard = new HBox(0);
                    mainCard.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-background-radius: 5;");
                    mainCard.getChildren().addAll(voteBox, contentBox);

                    setGraphic(mainCard);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0 0 15 0;");
                }
            }
        });
    }

    private void refreshFeed() {
        ObservableList<Post> postList = FXCollections.observableArrayList(postService.afficher());
        postsListView.setItems(postList);
    }

    @FXML
    void handleGoToCreatePost(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();

            // Pop-up logic!
            Stage popupStage = new Stage();
            popupStage.setTitle("Create a New Post");
            popupStage.setScene(new Scene(root, 700, 600));
            popupStage.initModality(Modality.APPLICATION_MODAL);

            popupStage.showAndWait(); // Pauses the Feed until you close the Create window

            refreshFeed(); // Refresh the list automatically so the new post appears!

        } catch (IOException e) {
            System.err.println("❌ CRITICAL ERROR: Could not find add_post.fxml");
            e.printStackTrace();
        }
    }
}