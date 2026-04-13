package controllers.forum;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import models.forum.Post;
import services.forum.PostService;
import java.util.List;

public class ForumFeedController {

    // 1. This links directly to the fx:id you typed in Scene Builder
    @FXML
    private ListView<String> postListView;

    // 2. Bring in your finished backend service
    private PostService postService = new PostService();

    // 3. The initialize() method runs automatically the second this screen opens
    @FXML
    public void initialize() {
        loadPosts();
    }

    // 4. The logic to fetch and display the data
    private void loadPosts() {
        // Fetch all posts using your awesome JOIN method
        List<Post> posts = postService.afficher();

        // Clear the visual list just to be safe
        postListView.getItems().clear();

        // Loop through the posts and format how they look on the screen
        for (Post p : posts) {
            String spaceInfo = (p.getSpaceName() != null) ? p.getSpaceName() : "General";

            // This creates a nice readable string like: "[Mathematics] My First Java Post - by peeko"
            String displayString = "[" + spaceInfo + "] " + p.getTitle() + " - by " + p.getAuthorName();

            // Add it to the visual JavaFX list!
            postListView.getItems().add(displayString);
        }
    }
}