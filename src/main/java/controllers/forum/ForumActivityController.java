package controllers.forum;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import models.forum.Post;
import services.forum.PostService;

import java.util.List;
import java.util.Map;

public class ForumActivityController {
    @FXML private Label usernameLabel;
    @FXML private Label postsCountLabel;
    @FXML private Label commentsCountLabel;
    @FXML private Label upvotesCountLabel;
    @FXML private VBox recentPostsContainer;

    private final PostService postService = new PostService();

    public void loadUserData(int userId, String username) {
        usernameLabel.setText("u/" + username);

        new Thread(() -> {
            Map<String, Integer> stats = postService.getUserStats(userId);
            List<Post> userPosts = postService.getPostsByUserId(userId);

            Platform.runLater(() -> {
                postsCountLabel.setText(String.valueOf(stats.getOrDefault("posts", 0)));
                commentsCountLabel.setText(String.valueOf(stats.getOrDefault("comments", 0)));
                upvotesCountLabel.setText(String.valueOf(stats.getOrDefault("upvotes", 0)));

                recentPostsContainer.getChildren().clear();

                if (userPosts.isEmpty()) {
                    Label empty = new Label("No recent activity.");
                    empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
                    recentPostsContainer.getChildren().add(empty);
                } else {
                    for (Post p : userPosts) {
                        Label pTitle = new Label("📝 " + p.getTitle());
                        pTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");

                        Label pSpace = new Label("in " + (p.getSpaceName() != null ? p.getSpaceName() : "General"));
                        pSpace.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

                        VBox pBox = new VBox(4, pTitle, pSpace);
                        pBox.setStyle("-fx-padding: 12; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8;");

                        recentPostsContainer.getChildren().add(pBox);
                    }
                }
            });
        }).start();
    }
}