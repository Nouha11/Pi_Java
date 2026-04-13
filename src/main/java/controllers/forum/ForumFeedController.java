package controllers.forum;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.forum.Post;
import services.forum.PostService;

import java.io.File;
import java.io.IOException;

public class ForumFeedController {

    @FXML private ListView<Post> postsListView;
    private PostService postService = new PostService();

    @FXML
    public void initialize() {
        refreshFeed();
        postsListView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        postsListView.setCellFactory(listView -> new ListCell<Post>() {
            @Override
            protected void updateItem(Post post, boolean empty) {
                super.updateItem(post, empty);

                if (empty || post == null) {
                    setText(null); setGraphic(null); setStyle("-fx-background-color: transparent;");
                } else {
                    // --- LEFT: UPVOTES ---
                    VBox voteBox = new VBox(5);
                    voteBox.setAlignment(Pos.TOP_CENTER);
                    voteBox.setPadding(new Insets(10, 15, 0, 10));
                    voteBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent #e2e8f0 transparent transparent; -fx-border-width: 0 1 0 0;");
                    Label upArrow = new Label("↑"); upArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff4500; -fx-font-weight: bold; -fx-cursor: hand;");
                    Label voteCount = new Label(String.valueOf(post.getUpvotes())); voteCount.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                    Label downArrow = new Label("↓"); downArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #718096; -fx-font-weight: bold; -fx-cursor: hand;");
                    voteBox.getChildren().addAll(upArrow, voteCount, downArrow);

                    // --- RIGHT: CONTENT ---
                    VBox contentBox = new VBox(8);
                    contentBox.setPadding(new Insets(10, 15, 10, 15));
                    HBox.setHgrow(contentBox, Priority.ALWAYS);

                    HBox headerRow = new HBox(5); headerRow.setAlignment(Pos.CENTER_LEFT);
                    Label dot = new Label("🟢"); dot.setStyle("-fx-font-size: 8px;");
                    Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
                    spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1a202c;");
                    Label authorLabel = new Label(" •  Posted by u/" + (post.getAuthorName() != null ? post.getAuthorName() : "Unknown"));
                    authorLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px;");
                    headerRow.getChildren().addAll(dot, spaceLabel, authorLabel);

                    Label titleLabel = new Label(post.getTitle()); titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16)); titleLabel.setStyle("-fx-text-fill: #1a202c;");
                    Label contentLabel = new Label(post.getContent()); contentLabel.setWrapText(true); contentLabel.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 13px;");

                    // --- THE UPDATED SMART IMAGE FINDER ---
                    VBox attachmentBox = new VBox();
                    if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
                        String fileName = post.getImageName();

                        // 🔥 UPDATED: Added /Pi_web/ to all the search paths!
                        // 1. Check the Symfony 'posts' folder
                        File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + fileName);

                        // 2. If not there, check the Symfony 'forum' folder
                        if (!imgFile.exists()) {
                            imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + fileName);
                        }

                        // 3. Fallback to the old testing folder just in case
                        if (!imgFile.exists()) {
                            imgFile = new File("C:/xampp/htdocs/uploads/" + fileName);
                        }

                        System.out.println("Looking for: " + fileName + " | Found? " + imgFile.exists() + " | Final Path: " + imgFile.getAbsolutePath());

                        if (imgFile.exists()) {
                            try {
                                Image img = new Image(imgFile.toURI().toString(), true);
                                ImageView imageView = new ImageView(img);
                                imageView.setFitWidth(500);
                                imageView.setPreserveRatio(true);
                                imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
                                attachmentBox.getChildren().add(imageView);
                                attachmentBox.setPadding(new Insets(10, 0, 5, 0));
                            } catch (Exception e) {
                                System.err.println("Error loading image: " + e.getMessage());
                            }
                        } else {
                            Label missingLabel = new Label("⚠️ Image file missing from htdocs folders");
                            missingLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-style: italic;");
                            attachmentBox.getChildren().add(missingLabel);
                        }
                    }

                    HBox footerRow = new HBox(15); footerRow.setPadding(new Insets(10, 0, 0, 0));
                    Label commentsLabel = new Label("💬 Comments"); commentsLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
                    Label shareLabel = new Label("🔗 Share"); shareLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
                    footerRow.getChildren().addAll(commentsLabel, shareLabel);

                    contentBox.getChildren().addAll(headerRow, titleLabel, contentLabel);
                    if (!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
                    contentBox.getChildren().add(footerRow);

                    HBox mainCard = new HBox(0); mainCard.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-background-radius: 5;");
                    mainCard.getChildren().addAll(voteBox, contentBox);

                    setGraphic(mainCard); setStyle("-fx-background-color: transparent; -fx-padding: 0 0 15 0;");
                }
            }
        });
    }

    private void refreshFeed() {
        ObservableList<Post> postList = FXCollections.observableArrayList(postService.afficher());
        postsListView.setItems(postList);
    }

    @FXML
    void handleGoToCreatePost(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();

            Stage popupStage = new Stage();
            popupStage.setTitle("Create a New Post");
            popupStage.setScene(new Scene(root, 700, 600));
            popupStage.initModality(Modality.APPLICATION_MODAL);

            popupStage.showAndWait();
            refreshFeed();

        } catch (IOException e) {
            System.err.println("❌ CRITICAL ERROR: Could not find add_post.fxml");
            e.printStackTrace();
        }
    }
}