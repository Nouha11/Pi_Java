package utils;

import models.forum.Post;
import java.util.HashSet;
import java.util.Set;

public class ForumSession {
    // Holds the clicked post for the details page
    public static Post currentPost = null;

    public static Set<Integer> upvotedPosts = new HashSet<>();
    public static Set<Integer> downvotedPosts = new HashSet<>();
}
