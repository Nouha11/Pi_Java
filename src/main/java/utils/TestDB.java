package utils;

import models.forum.Post;
import services.forum.PostService;
import java.util.List;

public class TestDB {
    public static void main(String[] args) {
        PostService service = new PostService();
        List<Post> myPosts = service.afficher();

        System.out.println("--- FORUM FEED TEST ---");
        for (Post p : myPosts) {
            System.out.println("Title: " + p.getTitle());
            System.out.println("Author: " + p.getAuthorName() + " | Space: " + p.getSpaceName());
            System.out.println("Tags: " + p.getTags());
            System.out.println("-----------------------");
        }
    }
}