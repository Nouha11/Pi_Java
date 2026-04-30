package services.forum;

import models.forum.Post;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public class PostService {

    private Connection cnx;
    private ModerationPipeline moderationPipeline;

    public PostService() {
        cnx = MyConnection.getInstance().getCnx();
        this.moderationPipeline = new ModerationPipeline();
    }

    public void ajouter(Post p) {
        String originalTitle = p.getTitle();
        String sanitizedTitle = moderationPipeline.sanitize(originalTitle);
        p.setTitle(sanitizedTitle);

        String originalContent = p.getContent();
        String sanitizedContent = moderationPipeline.sanitize(originalContent);
        p.setContent(sanitizedContent);

        boolean wasFlagged = !originalTitle.equals(sanitizedTitle) || !originalContent.equals(sanitizedContent);

        String req = "INSERT INTO post (title, content, author_id, space_id, upvotes, is_locked, hot_score, created_at, image_name, link) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS);
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

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newPostId = rs.getInt(1);
                p.setId(newPostId);
                syncTags(newPostId, p.getTags());

                if (wasFlagged) {
                    String fullOriginal = "Title: " + originalTitle + " | Content: " + originalContent;
                    moderationPipeline.triggerAutoReport(p.getAuthorId(), fullOriginal, "POST", newPostId);
                }
            }
            System.out.println("Post ajouté avec succès ! ✅");
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du post : " + e.getMessage());
        }
    }

    // 🔥 NEW METHOD: Returns the generated ID so we can link the poll to it
    public int ajouterAndGetId(Post p) {
        String originalTitle = p.getTitle();
        String sanitizedTitle = moderationPipeline.sanitize(originalTitle);
        p.setTitle(sanitizedTitle);

        String originalContent = p.getContent();
        String sanitizedContent = moderationPipeline.sanitize(originalContent);
        p.setContent(sanitizedContent);

        boolean wasFlagged = !originalTitle.equals(sanitizedTitle) || !originalContent.equals(sanitizedContent);

        String req = "INSERT INTO post (title, content, author_id, space_id, upvotes, is_locked, hot_score, created_at, image_name, link) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int newPostId = -1;
        try {
            PreparedStatement ps = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS);
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

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                newPostId = rs.getInt(1);
                p.setId(newPostId);
                syncTags(newPostId, p.getTags());

                if (wasFlagged) {
                    String fullOriginal = "Title: " + originalTitle + " | Content: " + originalContent;
                    moderationPipeline.triggerAutoReport(p.getAuthorId(), fullOriginal, "POST", newPostId);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout du post : " + e.getMessage());
        }
        return newPostId;
    }

    public List<Post> afficher() {
        List<Post> posts = new ArrayList<>();
        String req = "SELECT p.*, u.username AS author_name, s.name AS space_name, " +
                "(SELECT GROUP_CONCAT(t.name SEPARATOR ',') FROM post_tags pt JOIN tag t ON pt.tag_id = t.id WHERE pt.post_id = p.id) AS tags_string " +
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
                    p.setTags(rs.getString("tags_string"));
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

    public void modifier(Post p) {
        String originalTitle = p.getTitle();
        String sanitizedTitle = moderationPipeline.sanitize(originalTitle);
        p.setTitle(sanitizedTitle);

        String originalContent = p.getContent();
        String sanitizedContent = moderationPipeline.sanitize(originalContent);
        p.setContent(sanitizedContent);

        boolean wasFlagged = !originalTitle.equals(sanitizedTitle) || !originalContent.equals(sanitizedContent);

        String req = "UPDATE post SET title = ?, content = ?, space_id = ?, updated_at = ?, image_name = ?, link = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, p.getTitle().trim());
            ps.setString(2, p.getContent().trim());

            if (p.getSpaceId() != null) ps.setInt(3, p.getSpaceId());
            else ps.setNull(3, Types.INTEGER);

            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

            if (p.getImageName() != null) ps.setString(5, p.getImageName());
            else ps.setNull(5, Types.VARCHAR);

            if (p.getLink() != null) ps.setString(6, p.getLink());
            else ps.setNull(6, Types.VARCHAR);

            ps.setInt(7, p.getId());

            ps.executeUpdate();
            syncTags(p.getId(), p.getTags());

            if (wasFlagged) {
                String fullOriginal = "Title: " + originalTitle + " | Content: " + originalContent;
                moderationPipeline.triggerAutoReport(p.getAuthorId(), fullOriginal, "POST", p.getId());
            }

            System.out.println("✅ Post updated successfully in DB!");
        } catch (SQLException e) {
            System.err.println("Erreur: " + e.getMessage());
        }
    }

    public void supprimer(int id) {
        try { cnx.prepareStatement("DELETE FROM comment WHERE post_id = " + id).executeUpdate(); } catch (SQLException e) {}
        try { cnx.prepareStatement("DELETE FROM saved_posts WHERE post_id = " + id).executeUpdate(); } catch (SQLException e) {}
        try { cnx.prepareStatement("DELETE FROM post_tags WHERE post_id = " + id).executeUpdate(); } catch (SQLException e) {}

        try {
            PreparedStatement ps2 = cnx.prepareStatement("DELETE FROM post WHERE id = ?");
            ps2.setInt(1, id);
            ps2.executeUpdate();
        } catch (SQLException e) { }
    }

    private void syncTags(int postId, String tagsString) {
        try {
            PreparedStatement deletePs = cnx.prepareStatement("DELETE FROM post_tags WHERE post_id = ?");
            deletePs.setInt(1, postId);
            deletePs.executeUpdate();
        } catch (SQLException e) {}

        if (tagsString == null || tagsString.trim().isEmpty()) return;

        String[] tags = tagsString.split(",");
        for (String t : tags) {
            String tagName = t.trim().toLowerCase();
            if (tagName.isEmpty()) continue;

            int tagId = -1;
            try {
                PreparedStatement checkPs = cnx.prepareStatement("SELECT id FROM tag WHERE LOWER(name) = ?");
                checkPs.setString(1, tagName);
                ResultSet rs = checkPs.executeQuery();

                if (rs.next()) {
                    tagId = rs.getInt(1);
                } else {
                    PreparedStatement insertTag = cnx.prepareStatement("INSERT INTO tag (name, created_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
                    insertTag.setString(1, tagName);
                    insertTag.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    insertTag.executeUpdate();

                    ResultSet rsNew = insertTag.getGeneratedKeys();
                    if (rsNew.next()) tagId = rsNew.getInt(1);
                }

                if (tagId != -1) {
                    PreparedStatement linkPs = cnx.prepareStatement("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)");
                    linkPs.setInt(1, postId);
                    linkPs.setInt(2, tagId);
                    linkPs.executeUpdate();
                }
            } catch (SQLException e) {}
        }
    }

    public Set<Integer> getSavedPostsForUser(int userId) {
        Set<Integer> saved = new HashSet<>();
        try {
            PreparedStatement ps = cnx.prepareStatement("SELECT post_id FROM saved_posts WHERE user_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                saved.add(rs.getInt(1));
            }
        } catch (SQLException e) {}
        return saved;
    }

    public void savePost(int userId, int postId) {
        try {
            PreparedStatement ps = cnx.prepareStatement("INSERT IGNORE INTO saved_posts (user_id, post_id) VALUES (?, ?)");
            ps.setInt(1, userId);
            ps.setInt(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) { }
    }

    public void unsavePost(int userId, int postId) {
        try {
            PreparedStatement ps = cnx.prepareStatement("DELETE FROM saved_posts WHERE user_id = ? AND post_id = ?");
            ps.setInt(1, userId);
            ps.setInt(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) { }
    }

    public Map<String, Integer> getSpacesMap() {
        Map<String, Integer> spaces = new HashMap<>();
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT id, name FROM space");
            while (rs.next()) spaces.put(rs.getString("name"), rs.getInt("id"));
        } catch (SQLException e) {}
        return spaces;
    }

    public void updateUpvotes(int postId, int changeAmount) {
        String req = "UPDATE post SET upvotes = upvotes + ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, changeAmount);
            ps.setInt(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) { }
    }

    public void toggleLock(int postId, boolean isLocked) {
        String req = "UPDATE post SET is_locked = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setBoolean(1, isLocked);
            ps.setInt(2, postId);
            ps.executeUpdate();
        } catch (SQLException e) { }
    }

    public boolean isTitleUnique(String title) {
        String req = "SELECT COUNT(*) FROM post WHERE LOWER(title) = LOWER(?)";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, title.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) { }
        return false;
    }

    public int getTotalPostsCount() {
        int count = 0;
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT COUNT(*) FROM post");
            if (rs.next()) count = rs.getInt(1);
        } catch (SQLException e) {}
        return count;
    }

    public Map<String, Integer> getPostsPerSpace() {
        Map<String, Integer> stats = new HashMap<>();
        String req = "SELECT s.name, COUNT(p.id) FROM post p LEFT JOIN space s ON p.space_id = s.id GROUP BY p.space_id";
        try {
            ResultSet rs = cnx.createStatement().executeQuery(req);
            while (rs.next()) {
                String space = rs.getString(1) != null ? rs.getString(1) : "General";
                stats.put(space, rs.getInt(2));
            }
        } catch (SQLException e) {}
        return stats;
    }

    public Map<String, Integer> getUserStats(int userId) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("posts", 0);
        stats.put("upvotes", 0);
        stats.put("comments", 0);
        try {
            PreparedStatement ps = cnx.prepareStatement("SELECT COUNT(*), SUM(upvotes) FROM post WHERE author_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                stats.put("posts", rs.getInt(1));
                stats.put("upvotes", rs.getObject(2) != null ? rs.getInt(2) : 0);
            }

            PreparedStatement ps2 = cnx.prepareStatement("SELECT COUNT(*) FROM comment WHERE author_id = ?");
            ps2.setInt(1, userId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                stats.put("comments", rs2.getInt(1));
            }
        } catch (SQLException e) {}
        return stats;
    }

    public List<Post> getPostsByUserId(int userId) {
        List<Post> posts = new ArrayList<>();
        String req = "SELECT p.*, s.name AS space_name FROM post p LEFT JOIN space s ON p.space_id = s.id WHERE p.author_id = ? ORDER BY p.created_at DESC LIMIT 5";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Post p = new Post();
                p.setId(rs.getInt("id"));
                p.setTitle(rs.getString("title"));
                p.setSpaceName(rs.getString("space_name"));
                posts.add(p);
            }
        } catch (SQLException e) { }
        return posts;
    }

    public List<Post> searchPosts(String keyword) {
        List<Post> posts = new ArrayList<>();

        String req = "SELECT p.*, u.username AS author_name, s.name AS space_name, " +
                "(SELECT GROUP_CONCAT(t.name SEPARATOR ',') FROM post_tags pt JOIN tag t ON pt.tag_id = t.id WHERE pt.post_id = p.id) AS tags_string " +
                "FROM post p " +
                "LEFT JOIN user u ON p.author_id = u.id " +
                "LEFT JOIN space s ON p.space_id = s.id " +
                "WHERE p.title LIKE ? OR p.content LIKE ? " +
                "ORDER BY p.created_at DESC";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            String searchPattern = "%" + keyword + "%";
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Post p = new Post();
                p.setId(rs.getInt("id"));
                p.setTitle(rs.getString("title"));
                p.setContent(rs.getString("content"));
                p.setAuthorId(rs.getInt("author_id"));
                p.setAuthorName(rs.getString("author_name"));
                if (rs.getObject("space_id") != null) p.setSpaceId(rs.getInt("space_id"));
                p.setSpaceName(rs.getString("space_name"));
                p.setUpvotes(rs.getInt("upvotes"));
                p.setCreatedAt(rs.getTimestamp("created_at"));
                try {
                    p.setTags(rs.getString("tags_string"));
                    p.setImageName(rs.getString("image_name"));
                } catch (SQLException ignore) {}

                p.setLocked(rs.getBoolean("is_locked"));

                long hoursOld = java.time.Duration.between(p.getCreatedAt().toLocalDateTime(), java.time.LocalDateTime.now()).toHours();
                double score = p.getUpvotes() / Math.pow((hoursOld + 2), 1.5);
                p.setHotScore(score);

                posts.add(p);
            }
        } catch (SQLException e) {}
        return posts;
    }

    public Post getPostById(int id) {
        String req = "SELECT p.*, u.username AS author_name, s.name AS space_name, " +
                "(SELECT GROUP_CONCAT(t.name SEPARATOR ',') FROM post_tags pt JOIN tag t ON pt.tag_id = t.id WHERE pt.post_id = p.id) AS tags_string " +
                "FROM post p " +
                "LEFT JOIN user u ON p.author_id = u.id " +
                "LEFT JOIN space s ON p.space_id = s.id " +
                "WHERE p.id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
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
                    p.setTags(rs.getString("tags_string"));
                    p.setImageName(rs.getString("image_name"));
                    p.setLink(rs.getString("link"));
                } catch (SQLException ignore) {}
                return p;
            }
        } catch (SQLException e) {}
        return null;
    }
}