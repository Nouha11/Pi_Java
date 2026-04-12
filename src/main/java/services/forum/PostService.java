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
        // REMOVED the 'tags' column from the main Post query
        String reqPost = "INSERT INTO post (title, content, author_id, space_id, upvotes, is_locked, hot_score, created_at, link, image_name, attachment_name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            // Statement.RETURN_GENERATED_KEYS is crucial! It lets us get the new Post ID back.
            PreparedStatement ps = cnx.prepareStatement(reqPost, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, p.getTitle());
            ps.setString(2, p.getContent());
            ps.setInt(3, p.getAuthorId());

            if (p.getSpaceId() != null) { ps.setInt(4, p.getSpaceId()); }
            else { ps.setNull(4, Types.INTEGER); }

            ps.setInt(5, 0);
            ps.setBoolean(6, false);
            ps.setDouble(7, 0.0);
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            ps.setString(9, p.getLink());
            ps.setString(10, p.getImageName());
            ps.setString(11, p.getAttachmentName());

            ps.executeUpdate();

            // --- 1. GET THE NEW POST ID ---
            ResultSet rsKeys = ps.getGeneratedKeys();
            int newPostId = -1;
            if (rsKeys.next()) {
                newPostId = rsKeys.getInt(1);
            }

            // --- 2. PROCESS TAGS ---
            if (newPostId != -1 && p.getTags() != null && !p.getTags().trim().isEmpty()) {
                // Split the comma-separated string into an array
                String[] tagsArray = p.getTags().split(",");

                for (String rawTag : tagsArray) {
                    String tagName = rawTag.trim();
                    if (tagName.isEmpty()) continue;

                    // Find or create the tag, get its ID
                    int tagId = getOrCreateTag(tagName);

                    // Link the post and tag in the junction table
                    if (tagId != -1) {
                        linkPostAndTag(newPostId, tagId);
                    }
                }
            }

            System.out.println("Post and Tags added successfully.");

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du post : " + e.getMessage());
        }
    }

    // --- HELPER METHODS FOR TAGS ---

    private int getOrCreateTag(String tagName) throws SQLException {
        // 1. Check if the tag already exists
        String checkReq = "SELECT id FROM tag WHERE name = ?";
        PreparedStatement checkPs = cnx.prepareStatement(checkReq);
        checkPs.setString(1, tagName);
        ResultSet rs = checkPs.executeQuery();

        if (rs.next()) {
            return rs.getInt("id"); // Tag exists, return its ID
        } else {
            // 2. Tag doesn't exist, insert it WITH the created_at timestamp!
            String insertReq = "INSERT INTO tag (name, created_at) VALUES (?, ?)";
            PreparedStatement insertPs = cnx.prepareStatement(insertReq, Statement.RETURN_GENERATED_KEYS);
            insertPs.setString(1, tagName);
            insertPs.setTimestamp(2, new Timestamp(System.currentTimeMillis())); // Supply the missing date
            insertPs.executeUpdate();

            // 3. Grab the new Tag ID
            ResultSet newKeys = insertPs.getGeneratedKeys();
            if (newKeys.next()) {
                return newKeys.getInt(1);
            }
        }
        return -1;
    }

    private void linkPostAndTag(int postId, int tagId) throws SQLException {
        // Note: Assuming your junction table columns are 'post_id' and 'tag_id'.
        String linkReq = "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)";
        PreparedStatement linkPs = cnx.prepareStatement(linkReq);
        linkPs.setInt(1, postId);
        linkPs.setInt(2, tagId);
        linkPs.executeUpdate();
    }


    // --- FETCH SPACES DYNAMICALLY ---
    // Returns a Map where the Key is the Space Name, and the Value is the Space ID
    public java.util.Map<String, Integer> getSpacesMap() {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        String req = "SELECT id, name FROM space"; // Assuming your table is named 'space'
        try {
            Statement st = cnx.createStatement();
            ResultSet rs = st.executeQuery(req);
            while (rs.next()) {
                map.put(rs.getString("name"), rs.getInt("id"));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des spaces : " + e.getMessage());
        }
        return map;
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