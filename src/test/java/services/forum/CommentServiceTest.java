package services.forum;

import models.forum.Comment;
import org.junit.jupiter.api.*;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CommentServiceTest {

    static CommentService service;
    static int idCommentTest;

    @BeforeAll
    static void setup() {
        service = new CommentService();
    }

    @Test
    @Order(1)
    void testAjouterComment() throws SQLException {
        Comment c = new Comment();
        c.setContent("Ceci est un commentaire de test JUnit");
        c.setPostId(1);   // Make sure a post with ID 1 exists in your DB!
        c.setAuthorId(1); // Make sure a user with ID 1 exists in your DB!
        c.setSolution(false);

        service.ajouter(c);

        List<Comment> comments = service.getCommentsByPost(1);
        assertFalse(comments.isEmpty());

        // Verify insertion and capture ID
        boolean trouve = false;
        for (Comment comment : comments) {
            if (comment.getContent().equals("Ceci est un commentaire de test JUnit")) {
                trouve = true;
                idCommentTest = comment.getId();
                break;
            }
        }
        assertTrue(trouve);
    }

    @Test
    @Order(2)
    void testMarkAsSolution() throws SQLException {
        service.markAsSolution(idCommentTest);

        List<Comment> comments = service.getCommentsByPost(1);
        boolean estSolution = comments.stream()
                .anyMatch(c -> c.getId() == idCommentTest && c.isSolution());

        assertTrue(estSolution);
    }

    @Test
    @Order(3)
    void testSupprimerComment() throws SQLException {
        service.supprimer(idCommentTest);

        List<Comment> comments = service.getCommentsByPost(1);
        boolean existe = comments.stream()
                .anyMatch(c -> c.getId() == idCommentTest);

        assertFalse(existe);
    }

    // Test for your specific anti-spam feature
    @Test
    @Order(4)
    void testIsCommentUnique() throws SQLException {
        // Since we deleted the comment in Order 3, testing the same string should be unique again
        boolean isUnique = service.isCommentUnique("Ceci est un commentaire de test JUnit", 1);
        assertTrue(isUnique);
    }
}