package services.forum;

import models.forum.Comment;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CommentService {

    private Connection cnx;

    public CommentService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // --- CREATE (Ajouter) ---
    public void ajouter(Comment c) {
        String req = "INSERT INTO comment (content, post_id, author_id, parent_id, is_solution, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, c.getContent());
            ps.setInt(2, c.getPostId());
            ps.setInt(3, c.getAuthorId());

            // Handle nullable parent_id (for nested replies)
            if (c.getParentId() != null) {
                ps.setInt(4, c.getParentId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.setBoolean(5, false); // Default is_solution to false
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            ps.executeUpdate();
            System.out.println("Commentaire ajouté avec succès ! 💬");
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du commentaire : " + e.getMessage());
        }
    }

    // --- READ (Afficher avec Jointure) ---
    public List<Comment> afficher() {
        List<Comment> comments = new ArrayList<>();

        // Joining with the user table to get the author's username
        String req = "SELECT c.*, u.username AS author_name " +
                "FROM comment c " +
                "JOIN user u ON c.author_id = u.id";

        try {
            Statement st = cnx.createStatement();
            ResultSet rs = st.executeQuery(req);

            while (rs.next()) {
                Comment c = new Comment();
                c.setId(rs.getInt("id"));
                c.setContent(rs.getString("content"));
                c.setPostId(rs.getInt("post_id"));
                c.setAuthorId(rs.getInt("author_id"));

                if (rs.getObject("parent_id") != null) {
                    c.setParentId(rs.getInt("parent_id"));
                }

                c.setSolution(rs.getBoolean("is_solution"));
                c.setCreatedAt(rs.getTimestamp("created_at"));

                // 🔥 The Magic: Saving the string name for your JavaFX UI
                c.setAuthorName(rs.getString("author_name"));

                comments.add(c);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'affichage des commentaires : " + e.getMessage());
        }
        return comments;
    }

    // --- UPDATE (Modifier) ---
    public void modifier(Comment c) {
        // Users usually only edit the content of a comment
        String req = "UPDATE comment SET content = ?, updated_at = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, c.getContent());
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, c.getId());

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("Commentaire modifié avec succès ! ✏️");
            } else {
                System.out.println("Aucun commentaire trouvé avec cet ID.");
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la modification du commentaire : " + e.getMessage());
        }
    }

    // --- DELETE (Supprimer) ---
    public void supprimer(int id) {
        String req = "DELETE FROM comment WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            int rowsDeleted = ps.executeUpdate();
            if (rowsDeleted > 0) {
                System.out.println("Commentaire supprimé avec succès ! 🗑️");
            } else {
                System.out.println("Aucun commentaire trouvé avec cet ID.");
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la suppression du commentaire : " + e.getMessage());
        }
    }
}