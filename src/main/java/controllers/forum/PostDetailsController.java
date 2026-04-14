package controllers.forum;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import models.forum.Comment;
import models.forum.Post;
import services.forum.CommentService;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

public class PostDetailsController {

    @FXML private Button backButton;
    @FXML private Label breadcrumbSpaceLabel, badgeSpaceLabel, topTitleLabel;
    @FXML private Label authorLabel, dateLabel, upvoteBadgeLabel;
    @FXML private Label contentLabel;
    @FXML private ImageView postImageView;
    @FXML private Label repliesCountLabel, statsRepliesLabel, statsUpvotesLabel;
    @FXML private TextArea commentArea;
    @FXML private VBox commentsContainer;

    private Post currentPost;
    private CommentService commentService = new CommentService();

    public void setPostData(Post post) {
        this.currentPost = post;

        String spaceName = post.getSpaceName() != null ? post.getSpaceName() : "General";

        // Headers & Badges
        breadcrumbSpaceLabel.setText(spaceName + "  •");
        badgeSpaceLabel.setText(spaceName);
        topTitleLabel.setText(post.getTitle());

        // Author & Post Info
        authorLabel.setText(post.getAuthorName() != null ? post.getAuthorName() : "Unknown Student");

        // Date Formatting (Safely handle if DB date exists)
        if (post.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy");
            dateLabel.setText("Posted on " + sdf.format(post.getCreatedAt()));
        } else {
            dateLabel.setText("Posted recently");
        }

        contentLabel.setText(post.getContent());

        // Stats
        String upvotes = post.getUpvotes() + " Upvotes";
        upvoteBadgeLabel.setText(upvotes);
        statsUpvotesLabel.setText("👍 Upvotes: " + post.getUpvotes());

        // Image loading
        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (imgFile.exists()) {
                postImageView.setImage(new Image(imgFile.toURI().toString()));
            }
        }

        loadComments();
    }

    private void loadComments() {
        commentsContainer.getChildren().clear();
        List<Comment> comments = commentService.getCommentsByPost(currentPost.getId());

        // Update Reply Counters
        String replyCount = String.valueOf(comments.size());
        repliesCountLabel.setText("Replies (" + replyCount + ")");
        statsRepliesLabel.setText("💬 Replies: " + replyCount);

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
    void handleSubmitComment(ActionEvent event) {
        String text = commentArea.getText();
        if (text == null || text.trim().isEmpty()) return;

        Comment newComment = new Comment(text, currentPost.getId(), 1, null);
        commentService.ajouter(newComment);

        commentArea.clear();
        loadComments();
    }

    @FXML
    void handleBack(ActionEvent event) {
        try {
            // Safe Native JavaFX scene swapping
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/forum_feed.fxml"));
            Parent root = loader.load();
            backButton.getScene().setRoot(root);
        } catch (IOException e) {
            System.err.println("❌ Could not load forum_feed.fxml to go back.");
            e.printStackTrace();
        }
    }
}