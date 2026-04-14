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
        postsListView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-border-width: 0;");

        postsListView.setCellFactory(listView -> new ListCell<Post>() {
            @Override
            protected void updateItem(Post post, boolean empty) {
                super.updateItem(post, empty);

                if (empty || post == null) {
                    setText(null); setGraphic(null); setStyle("-fx-background-color: transparent;");
                } else {
                    VBox voteBox = new VBox(5);
                    voteBox.setAlignment(Pos.TOP_CENTER);
                    voteBox.setPadding(new Insets(12, 12, 0, 12));
                    voteBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent #e2e8f0 transparent transparent; -fx-border-width: 0 1 0 0; -fx-background-radius: 8 0 0 8;");
                    Label upArrow = new Label("↑"); upArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff4500; -fx-font-weight: bold;");
                    Label voteCount = new Label(String.valueOf(post.getUpvotes())); voteCount.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
                    Label downArrow = new Label("↓"); downArrow.setStyle("-fx-font-size: 16px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
                    voteBox.getChildren().addAll(upArrow, voteCount, downArrow);

                    VBox contentBox = new VBox(10);
                    contentBox.setPadding(new Insets(12, 20, 12, 20));
                    HBox.setHgrow(contentBox, Priority.ALWAYS);

                    HBox headerRow = new HBox(6); headerRow.setAlignment(Pos.CENTER_LEFT);
                    Label dot = new Label("🟢"); dot.setStyle("-fx-font-size: 8px; -fx-text-fill: #22c55e;");
                    Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
                    spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");
                    Label authorLabel = new Label(" •  Posted by u/" + (post.getAuthorName() != null ? post.getAuthorName() : "Unknown"));
                    authorLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
                    headerRow.getChildren().addAll(dot, spaceLabel, authorLabel);

                    Label titleLabel = new Label(post.getTitle()); titleLabel.setFont(Font.font("System", FontWeight.BOLD, 17)); titleLabel.setStyle("-fx-text-fill: #0f172a;");
                    Label contentLabel = new Label(post.getContent()); contentLabel.setWrapText(true); contentLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px; -fx-line-spacing: 2px;");

                    VBox attachmentBox = new VBox();
                    if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
                        File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
                        if (!imgFile.exists()) imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

                        if (imgFile.exists()) {
                            try {
                                Image img = new Image(imgFile.toURI().toString(), true);
                                ImageView imageView = new ImageView(img);
                                imageView.setFitWidth(550);
                                imageView.setPreserveRatio(true);
                                attachmentBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-padding: 2; -fx-background-color: white; -fx-background-radius: 6;");
                                attachmentBox.getChildren().add(imageView);
                                VBox.setMargin(attachmentBox, new Insets(10, 0, 5, 0));
                            } catch (Exception e) {}
                        }
                    }

                    HBox footerRow = new HBox(20); footerRow.setPadding(new Insets(10, 0, 0, 0));
                    Label commentsLabel = new Label("💬 Comments");
                    commentsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");
                    Label shareLabel = new Label("🔗 Share");
                    shareLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");
                    footerRow.getChildren().addAll(commentsLabel, shareLabel);

                    contentBox.getChildren().addAll(headerRow, titleLabel, contentLabel);
                    if (!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
                    contentBox.getChildren().add(footerRow);

                    HBox mainCard = new HBox(0);
                    mainCard.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 8, 0, 0, 3); -fx-cursor: hand;");
                    mainCard.getChildren().addAll(voteBox, contentBox);

                    setGraphic(mainCard);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0 0 20 0;");

                    // 🔥 THE ABSOLUTE FIX FOR SINGLE CLICK ROUTING 🔥
                    setOnMouseClicked(e -> {
                        if (!empty && post != null) {
                            openPostDetails(post);
                        }
                    });
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
            e.printStackTrace();
        }
    }

    private void openPostDetails(Post post) {
        // 1. Save the clicked post to our new session
        utils.ForumSession.currentPost = post;

        // 2. Use your existing dashboard router to change the page safely!
        // 🔥 THIS KEEPS YOUR NAVBAR INTACT! 🔥
        controllers.NovaDashboardController.loadPage("/views/forum/student/post_details.fxml");
    }

}