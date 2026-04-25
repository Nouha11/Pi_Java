package services.forum;

import models.forum.Comment;
import models.Notification;
import services.NotificationService;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CommentService {

    private Connection conn;
    private NotificationService notificationService;

    public CommentService() {
        conn = MyConnection.getInstance().getCnx();
        notificationService = new NotificationService();
    }

    public void ajouter(Comment c) {
        String query = "INSERT INTO comment (content, post_id, author_id, parent_id, is_solution, image_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setString(1, c.getContent().trim());
            pst.setInt(2, c.getPostId());
            pst.setInt(3, c.getAuthorId());
            if (c.getParentId() != null) pst.setInt(4, c.getParentId());
            else pst.setNull(4, Types.INTEGER);
            pst.setBoolean(5, c.isSolution());
            pst.setString(6, c.getImageName());
            pst.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

            pst.executeUpdate();
            notifyPostOwner(c.getPostId(), c.getAuthorId());
        } catch (SQLException e) {
            System.err.println("❌ Error adding comment: " + e.getMessage());
        }
    }

    private void notifyPostOwner(int postId, int commenterId) {
        try {
            PreparedStatement psPost = conn.prepareStatement("SELECT author_id, title FROM post WHERE id = ?");
            psPost.setInt(1, postId);
            ResultSet rsPost = psPost.executeQuery();

            if (rsPost.next()) {
                int postOwnerId = rsPost.getInt("author_id");
                String postTitle = rsPost.getString("title");

                if (postOwnerId != commenterId) {
                    String commenterName = "Someone";
                    PreparedStatement psUser = conn.prepareStatement("SELECT username FROM user WHERE id = ?");
                    psUser.setInt(1, commenterId);
                    ResultSet rsUser = psUser.executeQuery();
                    if (rsUser.next()) commenterName = rsUser.getString("username");

                    String message = commenterName + " replied to your discussion: '" + postTitle + "'";

                    Notification n = new Notification(
                            "FORUM_REPLY", "New Reply", message, "/views/forum/student/post_details.fxml", "💬", "#3b82f6", postOwnerId
                    );
                    n.setMetadata(String.valueOf(postId));
                    notificationService.sendNotification(n);
                }
            }
        } catch (SQLException e) {}
    }

    public List<Comment> getCommentsByPost(int postId) {
        List<Comment> comments = new ArrayList<>();
        String query = "SELECT c.*, u.username as author_name FROM comment c LEFT JOIN user u ON c.author_id = u.id WHERE c.post_id = ? ORDER BY c.created_at ASC";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, postId);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                Comment c = new Comment();
                c.setId(rs.getInt("id"));
                c.setContent(rs.getString("content"));
                c.setPostId(rs.getInt("post_id"));
                c.setAuthorId(rs.getInt("author_id"));
                int parentId = rs.getInt("parent_id");
                if (!rs.wasNull()) c.setParentId(parentId);
                c.setSolution(rs.getBoolean("is_solution"));
                c.setCreatedAt(rs.getTimestamp("created_at"));
                c.setUpdatedAt(rs.getTimestamp("updated_at"));
                c.setImageName(rs.getString("image_name"));
                try { c.setAuthorName(rs.getString("author_name")); }
                catch (SQLException ex) { c.setAuthorName("Student_" + c.getAuthorId()); }
                comments.add(c);
            }
        } catch (SQLException e) {}
        return comments;
    }

    public void supprimer(int id) {
        try (PreparedStatement pst = conn.prepareStatement("DELETE FROM comment WHERE id = ?")) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {}
    }

    // ==========================================
    // 🔥 NEW: MODERATOR "SOFT DELETE" 🔥
    // ==========================================
    public void censorByModerator(int commentId) {
        String censorText = "🚫 *[This comment was removed by a moderator for violating community guidelines]*";
        String query = "UPDATE comment SET content = ?, image_name = NULL, updated_at = ? WHERE id = ?";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setString(1, censorText);
            pst.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            pst.setInt(3, commentId);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error censoring comment: " + e.getMessage());
        }
    }

    public void markAsSolution(int commentId) {
        try (PreparedStatement pst = conn.prepareStatement("UPDATE comment SET is_solution = true WHERE id = ?")) {
            pst.setInt(1, commentId);
            pst.executeUpdate();
        } catch (SQLException e) {}
    }

    public boolean isCommentUnique(String content, int postId) {
        try (PreparedStatement pst = conn.prepareStatement("SELECT COUNT(*) FROM comment WHERE LOWER(content) = LOWER(?) AND post_id = ?")) {
            pst.setString(1, content.trim());
            pst.setInt(2, postId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) return rs.getInt(1) == 0;
        } catch (SQLException e) {}
        return false;
    }

    public void modifier(Comment c) {
        try (PreparedStatement pst = conn.prepareStatement("UPDATE comment SET content = ?, updated_at = ? WHERE id = ?")) {
            pst.setString(1, c.getContent().trim());
            pst.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            pst.setInt(3, c.getId());
            pst.executeUpdate();
        } catch (SQLException e) {}
    }
}