package services.forum;

import models.forum.Post;
import org.junit.jupiter.api.*;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostServiceTest {

    static PostService service;
    static int idPostTest; // Used to pass the ID between tests

    @BeforeAll
    static void setup() {
        service = new PostService();
    }

    @Test
    @Order(1)
    void testAjouterPost() throws SQLException {
        Post p = new Post();
        p.setTitle("Test Titre Unitaire");
        p.setContent("Contenu de test pour JUnit");
        p.setAuthorId(1); // Make sure a user with ID 1 exists in your DB!

        service.ajouter(p);

        List<Post> posts = service.afficher();
        assertFalse(posts.isEmpty());

        // Verify the post was added and save its ID for the next tests
        boolean trouve = false;
        for (Post post : posts) {
            if (post.getTitle().equals("Test Titre Unitaire")) {
                trouve = true;
                idPostTest = post.getId();
                break;
            }
        }
        assertTrue(trouve);
    }

    @Test
    @Order(2)
    void testModifierPost() throws SQLException {
        Post p = new Post();
        p.setId(idPostTest);
        p.setTitle("Titre Modifie");
        p.setContent("Contenu Modifie");

        service.modifier(p);

        List<Post> posts = service.afficher();
        boolean trouve = posts.stream()
                .anyMatch(post -> post.getTitle().equals("Titre Modifie") && post.getId() == idPostTest);

        assertTrue(trouve);
    }

    @Test
    @Order(3)
    void testSupprimerPost() throws SQLException {
        service.supprimer(idPostTest);

        List<Post> posts = service.afficher();
        boolean existe = posts.stream()
                .anyMatch(p -> p.getId() == idPostTest);

        assertFalse(existe);
    }

    // Since Order(3) deletes the post, this ensures no junk is left behind if a test fails.
    @AfterAll
    static void cleanUp() throws SQLException {
        List<Post> posts = service.afficher();
        for (Post post : posts) {
            if (post.getTitle().startsWith("Test Titre") || post.getTitle().startsWith("Titre Modifie")) {
                service.supprimer(post.getId());
            }
        }
    }
}