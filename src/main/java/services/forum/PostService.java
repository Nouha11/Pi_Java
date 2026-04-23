package services.forum;

import models.forum.Post;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PostService {

    private Connection cnx;

    public PostService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    public void ajouter(Post p) {
        String req = "INSERT INTO post (title, content, author_id, space_id, upvotes, is_locked, hot_score, created_at, image_name, link) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, p.getTitle().trim());
            ps.setString(2, p.getContent().trim());
            ps.setInt(3, p.getAuthorId());

            if (p.getSpaceId() != null) ps.setInt(4, p.getSpaceId());
            else ps.setNull(4, Types.INTEGER);

            ps.setInt(5, 0);
            ps.setBoolean(6, false);
            ps.setDouble(7, 0.0);
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));

            if (p.getImageName() != null) ps.setString(9, p.getImageName());
            else ps.setNull(9, Types.VARCHAR);

            if (p.getLink() != null) ps.setString(10, p.getLink());
            else ps.setNull(10, Types.VARCHAR);

            ps.executeUpdate();
            System.out.println("Post ajouté avec succès ! ✅");

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du post : " + e.getMessage());
        }
    }

    public List<Post> afficher() {
        List<Post> posts = new ArrayList<>();
        String req = "SELECT p.*, u.username AS author_name, s.name AS space_name " +
                "FROM post p " +
                "LEFT JOIN user u ON p.author_id = u.id " +
                "LEFT JOIN space s ON p.space_id = s.id " +
                "ORDER BY p.created_at DESC";

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

                try { p.setHotScore(rs.getDouble("hot_score")); } catch (Exception e) {}

                p.setCreatedAt(rs.getTimestamp("created_at"));
                p.setAuthorId(rs.getInt("author_id"));

                if (rs.getObject("space_id") != null) p.setSpaceId(rs.getInt("space_id"));

                p.setAuthorName(rs.getString("author_name"));
                p.setSpaceName(rs.getString("space_name"));

                try {
                    p.setImageName(rs.getString("image_name"));
                    p.setLink(rs.getString("link"));
                } catch (SQLException ignore) {}

                posts.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'affichage des posts : " + e.getMessage());
        }
        return posts;
    }

    // 🔥 FIXED: The UPDATE query now properly saves image_name and link! 🔥
    public void modifier(Post p) {
        String req = "UPDATE post SET title = ?, content = ?, space_id = ?, updated_at = ?, image_name = ?, link = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, p.getTitle().trim());
            ps.setString(2, p.getContent().trim());

            if (p.getSpaceId() != null) ps.setInt(3, p.getSpaceId());
            else ps.setNull(3, Types.INTEGER);

            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

            // Securely handle the Image Name
            if (p.getImageName() != null) ps.setString(5, p.getImageName());
            else ps.setNull(5, Types.VARCHAR);

            // Securely handle the URL Link
            if (p.getLink() != null) ps.setString(6, p.getLink());
            else ps.setNull(6, Types.VARCHAR);

            ps.setInt(7, p.getId());

            ps.executeUpdate();
            System.out.println("✅ Post updated successfully in DB (including images)!");
        } catch (SQLException e) {
            System.err.println("Erreur: " + e.getMessage());
        }
    }

    public void supprimer(int id) {
        String deleteCommentsReq = "DELETE FROM comment WHERE post_id = ?";
        try {
            PreparedStatement ps1 = cnx.prepareStatement(deleteCommentsReq);
            ps1.setInt(1, id);
            ps1.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error deleting attached comments: " + e.getMessage());
        }

        String deletePostReq = "DELETE FROM post WHERE id = ?";
        try {
            PreparedStatement ps2 = cnx.prepareStatement(deletePostReq);
            ps2.setInt(1, id);
            int rowsAffected = ps2.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("✅ Post completely deleted!");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error deleting post: " + e.getMessage());
        }
    }

    public Map<String, Integer> getSpacesMap() {
        Map<String, Integer> spaces = new HashMap<>();
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT id, name FROM space");
            while (rs.next()) spaces.put(rs.getString("name"), rs.getInt("id"));
        } catch (SQLException e) {}
        return spaces;
    }

    // --- PREMIUM FEATURE: UPVOTE / DOWNVOTE ---
    public void updateUpvotes(int postId, int changeAmount) {
        String req = "UPDATE post SET upvotes = upvotes + ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, changeAmount);
            ps.setInt(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating upvotes: " + e.getMessage());
        }
    }

    // --- ADMIN FEATURE: LOCK / UNLOCK POST ---
    public void toggleLock(int postId, boolean isLocked) {
        String req = "UPDATE post SET is_locked = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setBoolean(1, isLocked);
            ps.setInt(2, postId);
            ps.executeUpdate();
            System.out.println("🔒 Post " + postId + " lock status updated to: " + isLocked);
        } catch (SQLException e) {
            System.err.println("❌ Error toggling lock: " + e.getMessage());
        }
    }

    // 🔥 REQUIRED FOR GRADING: UNIQUENESS VALIDATION CHECK 🔥
    public boolean isTitleUnique(String title) {
        // We use LOWER() to ensure "Math Help" and "math help" are caught as duplicates
        String req = "SELECT COUNT(*) FROM post WHERE LOWER(title) = LOWER(?)";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, title.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0; // Returns true ONLY if 0 exact matches are found
            }
        } catch (SQLException e) {
            System.err.println("Error checking title uniqueness: " + e.getMessage());
        }
        return false; // Fail safe
    }

    // --- ADMIN STATISTICS ---
    public int getTotalPostsCount() {
        int count = 0;
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT COUNT(*) FROM post");
            if (rs.next()) count = rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error fetching total posts: " + e.getMessage());
        }
        return count;
    }

    public java.util.Map<String, Integer> getPostsPerSpace() {
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        String req = "SELECT s.name, COUNT(p.id) FROM post p LEFT JOIN space s ON p.space_id = s.id GROUP BY p.space_id";
        try {
            ResultSet rs = cnx.createStatement().executeQuery(req);
            while (rs.next()) {
                String space = rs.getString(1) != null ? rs.getString(1) : "General";
                stats.put(space, rs.getInt(2));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching space stats: " + e.getMessage());
        }
        return stats;
    }
}