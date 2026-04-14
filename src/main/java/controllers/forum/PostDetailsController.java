package controllers.forum;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
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
import services.forum.PostService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;

public class PostDetailsController {

    @FXML private Button backButton;
    @FXML private Button upvoteButton;
    @FXML private Label breadcrumbSpaceLabel, badgeSpaceLabel, topTitleLabel;
    @FXML private Label authorLabel, dateLabel, upvoteBadgeLabel;
    @FXML private Label contentLabel;
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

        // 🔥 READ GLOBAL MEMORY to keep button blue if already clicked!
        if (utils.ForumSession.upvotedPosts.contains(post.getId())) {
            currentPost.setMyVote(1);
            upvoteButton.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvoted");
        } else {
            currentPost.setMyVote(0);
            upvoteButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvote");
        }

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

    // 🔥 PREVENTS INFINITE UPVOTES AND SAVES TO DB
    @FXML
    void handleUpvote(ActionEvent event) {
        if (currentPost.getMyVote() != 1) {
            int changeAmount = (currentPost.getMyVote() == -1) ? 2 : 1;
            postService.updateUpvotes(currentPost.getId(), changeAmount);
            currentPost.setUpvotes(currentPost.getUpvotes() + changeAmount);

            // Save to memory
            currentPost.setMyVote(1);
            utils.ForumSession.upvotedPosts.add(currentPost.getId());
            utils.ForumSession.downvotedPosts.remove(currentPost.getId());

            upvoteButton.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvoted");
        } else {
            postService.updateUpvotes(currentPost.getId(), -1);
            currentPost.setUpvotes(currentPost.getUpvotes() - 1);

            // Remove from memory
            currentPost.setMyVote(0);
            utils.ForumSession.upvotedPosts.remove(currentPost.getId());

            upvoteButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
            upvoteButton.setText("👍 Upvote");
        }

        upvoteBadgeLabel.setText(currentPost.getUpvotes() + " Upvotes");
        statsUpvotesLabel.setText("👍 Upvotes: " + currentPost.getUpvotes());
    }

    @FXML
    void handleBack(ActionEvent event) {
        controllers.NovaDashboardController.loadPage("/views/forum/forum_feed.fxml");
    }
}