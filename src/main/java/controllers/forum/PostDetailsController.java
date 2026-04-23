package controllers.forum;

import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.forum.Comment;
import models.forum.Post;
import services.forum.CommentService;
import services.forum.PostService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

public class PostDetailsController {

    @FXML private Button backButton, upvoteButton, editButton, deleteButton;
    @FXML private Button lockButton, submitCommentBtn;
    @FXML private Label breadcrumbSpaceLabel, badgeSpaceLabel, topTitleLabel;
    @FXML private Label authorLabel, dateLabel, upvoteBadgeLabel;
    @FXML private Label topCommentBadgeLabel;
    @FXML private Label contentLabel, statusLabel;
    @FXML private ImageView postImageView;
    @FXML private Label repliesCountLabel, statsRepliesLabel, statsUpvotesLabel;
    @FXML private TextArea commentArea;
    @FXML private VBox commentsContainer;

    private Post currentPost;
    private CommentService commentService = new CommentService();
    private PostService postService = new PostService();

    @FXML
    public void initialize() {
        if (utils.ForumSession.currentPost != null) {
            setPostData(utils.ForumSession.currentPost);
        }

        commentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            commentArea.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4;");
        });
    }

    public void setPostData(Post post) {
        this.currentPost = post;
        String spaceName = post.getSpaceName() != null ? post.getSpaceName() : "General";

        breadcrumbSpaceLabel.setText(spaceName + "  •");
        badgeSpaceLabel.setText(spaceName);
        topTitleLabel.setText(post.getTitle());
        authorLabel.setText(post.getAuthorName() != null ? post.getAuthorName() : "Unknown Student");

        if (post.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy");
            dateLabel.setText("Posted on " + sdf.format(post.getCreatedAt()));
        } else {
            dateLabel.setText("Posted recently");
        }

        contentLabel.setText(post.getContent());
        upvoteBadgeLabel.setText(post.getUpvotes() + " Upvotes");
        statsUpvotesLabel.setText("👍 Upvotes: " + post.getUpvotes());

        if (utils.ForumSession.upvotedPosts.contains(post.getId())) {
            currentPost.setMyVote(1);
            upvoteButton.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvoted");
        } else {
            currentPost.setMyVote(0);
            upvoteButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvote");
        }

        updateLockUI();

        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (!imgFile.exists()) imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

            if (imgFile.exists()) {
                postImageView.setImage(new Image(imgFile.toURI().toString()));
            }
        }
        loadComments();
    }

    private void updateLockUI() {
        if (currentPost.isLocked()) {
            topTitleLabel.setText("🔒 " + currentPost.getTitle());
            topTitleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");

            statusLabel.setText("🔒 Status: Locked");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            lockButton.setText("🔓 Unlock");

            commentArea.setDisable(true);
            commentArea.setPromptText("🔒 This discussion has been locked by the author or an admin.");
            submitCommentBtn.setDisable(true);
            submitCommentBtn.setStyle("-fx-background-color: #94a3b8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 6;");
        } else {
            topTitleLabel.setText(currentPost.getTitle());
            topTitleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

            statusLabel.setText("🛡 Status: Open");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            lockButton.setText("🔒 Lock");

            commentArea.setDisable(false);
            commentArea.setPromptText("Write a reply...");
            submitCommentBtn.setDisable(false);
            submitCommentBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        }
    }

    private void loadComments() {
        commentsContainer.getChildren().clear();
        List<Comment> comments = commentService.getCommentsByPost(currentPost.getId());

        String replyCount = String.valueOf(comments.size());

        repliesCountLabel.setText("Replies (" + replyCount + ")");
        statsRepliesLabel.setText("💬 Replies: " + replyCount);
        topCommentBadgeLabel.setText("💬 " + replyCount + " Comments");

        if (comments.isEmpty()) {
            Label emptyLabel = new Label("No comments yet. Be the first to share your thoughts!");
            emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
            commentsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Comment c : comments) {
            VBox commentCard = new VBox(8);
            commentCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");

            HBox headerBox = new HBox(10);
            Label author = new Label(c.getAuthorName() != null ? c.getAuthorName() : "Student");
            author.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
            Label time = new Label("• Reply");
            time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            headerBox.getChildren().addAll(author, time);

            Label content = new Label(c.getContent());
            content.setWrapText(true);
            content.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px;");

            commentCard.getChildren().addAll(headerBox, content);
            commentsContainer.getChildren().add(commentCard);
        }
    }

    @FXML
    void handleLockPost(ActionEvent event) {
        boolean newState = !currentPost.isLocked();
        currentPost.setLocked(newState);
        postService.toggleLock(currentPost.getId(), newState);
        updateLockUI();
    }

    @FXML
    void handleSubmitComment(ActionEvent event) {
        if (currentPost.isLocked()) return;

        String text = commentArea.getText();

        if (text == null || text.trim().length() < 2) {
            applyErrorStyle("Reply must be at least 2 characters!");
            return;
        }

        if (!commentService.isCommentUnique(text, currentPost.getId())) {
            applyErrorStyle("You already posted this exact reply!");
            return;
        }

        Comment newComment = new Comment(text, currentPost.getId(), 1, null);
        commentService.ajouter(newComment);

        commentArea.clear();
        commentArea.setPromptText("Write a reply...");
        commentArea.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4;");

        loadComments();
    }

    private void applyErrorStyle(String errorMessage) {
        commentArea.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-radius: 4;");
        commentArea.clear();
        commentArea.setPromptText("⚠️ " + errorMessage);

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> commentArea.setPromptText("Write a reply..."));
        pause.play();
    }

    @FXML
    void handleUpvote(ActionEvent event) {
        if (currentPost.getMyVote() != 1) {
            int changeAmount = (currentPost.getMyVote() == -1) ? 2 : 1;
            postService.updateUpvotes(currentPost.getId(), changeAmount);
            currentPost.setUpvotes(currentPost.getUpvotes() + changeAmount);
            currentPost.setMyVote(1);
            utils.ForumSession.upvotedPosts.add(currentPost.getId());
            utils.ForumSession.downvotedPosts.remove(currentPost.getId());

            upvoteButton.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvoted");
        } else {
            postService.updateUpvotes(currentPost.getId(), -1);
            currentPost.setUpvotes(currentPost.getUpvotes() - 1);
            currentPost.setMyVote(0);
            utils.ForumSession.upvotedPosts.remove(currentPost.getId());

            upvoteButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvote");
        }
        upvoteBadgeLabel.setText(currentPost.getUpvotes() + " Upvotes");
        statsUpvotesLabel.setText("👍 Upvotes: " + currentPost.getUpvotes());
    }

    @FXML
    void handleDeletePost(ActionEvent event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Post");
        confirm.setHeaderText("Are you sure you want to delete this post?");
        confirm.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            postService.supprimer(currentPost.getId());
            handleBack(null);
        }
    }

    // 🔥 COMPLETELY UPDATED EDIT METHOD 🔥
    @FXML
    void handleEditPost(ActionEvent event) {
        try {
            // 1. Load the beautiful Add/Edit window
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();

            // 2. Pass the current post data to the controller
            AddPostController controller = loader.getController();
            controller.setPostToEdit(this.currentPost);

            // 3. Create the popup window
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Edit Post");
            stage.setScene(new Scene(root));

            // 4. Wait for the user to hit 'Save Changes' and close the popup
            stage.showAndWait();

            // 5. Instantly refresh this page to show the newly updated title/content!
            setPostData(this.currentPost);

        } catch (Exception ex) {
            System.err.println("Error opening Edit window: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        controllers.NovaDashboardController.loadPage("/views/forum/forum_feed.fxml");
    }
}