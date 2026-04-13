package services.forum;

import models.forum.Post;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostService {

    // We declare the connection object
    private Connection cnx;

    public PostService() {
        // We get the single connection from our Singleton class
        cnx = MyConnection.getInstance().getCnx();
    }

    // --- CREATE (Ajouter) ---
    public void ajouter(Post p) {
        // We use ? placeholders to prevent SQL Injection
        String req = "INSERT INTO post (title, content, author_id, space_id, upvotes, is_locked, hot_score, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, p.getTitle());
            ps.setString(2, p.getContent());
            ps.setInt(3, p.getAuthorId());

            // Because space_id can be NULL (as seen in your screenshot)
            if (p.getSpaceId() != null) {
                ps.setInt(4, p.getSpaceId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.setInt(5, 0); // Default 0 upvotes for a new post
            ps.setBoolean(6, false); // Default not locked (tinyint 0)
            ps.setDouble(7, 0.0); // Default hot_score
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis())); // Current time

            ps.executeUpdate();
            System.out.println("Post ajouté avec succès ! ✅");

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du post : " + e.getMessage());
        }
    }

    // --- READ (Afficher avec Jointure) ---
    public List<Post> afficher() {
        List<Post> posts = new ArrayList<>();

        // The magic query that fulfills your "pas d'affichage d'id" requirement!
        String req = "SELECT p.*, u.username AS author_name, s.name AS space_name " +
                "FROM post p " +
                "JOIN user u ON p.author_id = u.id " +
                "LEFT JOIN space s ON p.space_id = s.id";

        try {
            Statement st = cnx.createStatement();
            ResultSet rs = st.executeQuery(req);

            while (rs.next()) {
                Post p = new Post();
                p.setId(rs.getInt("id"));
                p.setTitle(rs.getString("title"));
                p.setContent(rs.getString("content"));
                p.setUpvotes(rs.getInt("upvotes"));
                p.setLocked(rs.getBoolean("is_locked"));
                p.setHotScore(rs.getDouble("hot_score"));
                p.setCreatedAt(rs.getTimestamp("created_at"));

                // We map the raw IDs just in case we need them for updates/deletes later
                p.setAuthorId(rs.getInt("author_id"));
                if (rs.getObject("space_id") != null) {
                    p.setSpaceId(rs.getInt("space_id"));
                }

                // 🔥 HERE IS THE MAGIC: We save the actual names for the JavaFX UI 🔥
                p.setAuthorName(rs.getString("author_name"));
                p.setSpaceName(rs.getString("space_name"));

                posts.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'affichage des posts : " + e.getMessage());
        }

        return posts;
    }
    // --- UPDATE (Modifier) ---
    public void modifier(Post p) {
        // We update the title, content, space_id, and updated_at based on the post ID
        String req = "UPDATE post SET title = ?, content = ?, space_id = ?, updated_at = ? WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, p.getTitle());
            ps.setString(2, p.getContent());

            if (p.getSpaceId() != null) {
                ps.setInt(3, p.getSpaceId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }

            // Set the current time for the updated_at column
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

            // The ID of the post we want to update
            ps.setInt(5, p.getId());

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("Post modifié avec succès !");
            } else {
                System.out.println("Aucun post trouvé avec cet ID pour la modification.");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la modification du post : " + e.getMessage());
        }
    }

    // --- DELETE (Supprimer) ---
    public void supprimer(int id) {
        String req = "DELETE FROM post WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            int rowsDeleted = ps.executeUpdate();
            if (rowsDeleted > 0) {
                System.out.println("Post supprimé avec succès ! 🗑️");
            } else {
                System.out.println("Aucun post trouvé avec cet ID pour la suppression.");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la suppression du post : " + e.getMessage());
        }
    }

}