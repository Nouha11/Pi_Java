package controllers.forum;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.forum.Post;
import services.forum.PostService;

import java.util.List;
import java.util.Optional;

public class AdminForumController {

    @FXML
    private VBox postsContainer;

    private PostService postService = new PostService();

    @FXML
    public void initialize() {
        loadPosts();
    }

    private void loadPosts() {
        postsContainer.getChildren().clear();
        List<Post> posts = postService.afficher();

        if (posts.isEmpty()) {
            Label emptyLabel = new Label("No discussions found in the forum.");
            emptyLabel.setStyle("-fx-padding: 20; -fx-text-fill: #94a3b8; -fx-font-style: italic;");
            postsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Post post : posts) {
            postsContainer.getChildren().add(createPostRow(post));
        }
    }

    private HBox createPostRow(Post post) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 15 20; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-background-color: white;");

        // Hover Effect
        row.setOnMouseEntered(e -> row.setStyle("-fx-padding: 15 20; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-background-color: #f8fafc;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-padding: 15 20; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-background-color: white;"));

        // 1. Post Details (Title & Author - No ID)
        VBox detailsBox = new VBox(4);
        detailsBox.setPrefWidth(350);
        Label titleLabel = new Label(post.getTitle());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
        Label authorLabel = new Label("Posted by @" + (post.getAuthorName() != null ? post.getAuthorName() : "Unknown"));
        authorLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        detailsBox.getChildren().addAll(titleLabel, authorLabel);

        // 2. Space Pill
        Label spaceLabel = new Label(post.getSpaceName() != null ? post.getSpaceName() : "General");
        spaceLabel.setPrefWidth(120);
        spaceLabel.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-padding: 4 10; -fx-background-radius: 12; -fx-font-size: 12px; -fx-font-weight: bold;");

        // 3. Upvotes
        Label upvotesLabel = new Label(post.getUpvotes() + " pts");
        upvotesLabel.setPrefWidth(80);
        upvotesLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 13px;");

        // 4. Status
        Label statusLabel = new Label(post.isLocked() ? "🔒 Locked" : "🟢 Open");
        statusLabel.setPrefWidth(100);
        statusLabel.setStyle(post.isLocked() ? "-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 13px;" : "-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 13px;");

        // Spacer to push actions to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 5. Actions (Modern Pill Buttons instead of standard JavaFX Buttons)
        HBox actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.setPrefWidth(160);

        Label btnLock = new Label(post.isLocked() ? "🔓 Unlock" : "🔒 Lock");
        btnLock.setStyle("-fx-cursor: hand; -fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;");
        btnLock.setOnMouseClicked(e -> {
            postService.toggleLock(post.getId(), !post.isLocked());
            loadPosts(); // Refresh list to show updated status
        });

        Label btnDelete = new Label("🗑 Delete");
        btnDelete.setStyle("-fx-cursor: hand; -fx-background-color: #ffe4e6; -fx-text-fill: #e11d48; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;");
        btnDelete.setOnMouseClicked(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Post");
            confirm.setHeaderText("Delete this post?");
            confirm.setContentText("This action cannot be undone and will delete all associated comments.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                postService.supprimer(post.getId());
                loadPosts(); // Refresh list
            }
        });

        // Add hover effects for the action buttons
        btnLock.setOnMouseEntered(e -> btnLock.setStyle("-fx-cursor: hand; -fx-background-color: #fde68a; -fx-text-fill: #b45309; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;"));
        btnLock.setOnMouseExited(e -> btnLock.setStyle("-fx-cursor: hand; -fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;"));

        btnDelete.setOnMouseEntered(e -> btnDelete.setStyle("-fx-cursor: hand; -fx-background-color: #fecdd3; -fx-text-fill: #be123c; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;"));
        btnDelete.setOnMouseExited(e -> btnDelete.setStyle("-fx-cursor: hand; -fx-background-color: #ffe4e6; -fx-text-fill: #e11d48; -fx-padding: 6 14; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;"));

        actionsBox.getChildren().addAll(btnLock, btnDelete);

        row.getChildren().addAll(detailsBox, spaceLabel, upvotesLabel, statusLabel, spacer, actionsBox);
        return row;
    }
}