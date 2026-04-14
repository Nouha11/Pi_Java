package services.forum;

import models.forum.Comment;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CommentService {

    private Connection conn;

    public CommentService() {
        // 🔥 UPDATED: Now perfectly matches your MyConnection class using getCnx()
        conn = MyConnection.getInstance().getCnx();
    }

    // --- ADD A NEW COMMENT ---
    public void ajouter(Comment c) {
        // Including parent_id in case you want to add nested replies later!
        String query = "INSERT INTO comment (content, post_id, author_id, parent_id, is_solution, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setString(1, c.getContent());
            pst.setInt(2, c.getPostId());
            pst.setInt(3, c.getAuthorId());

            if (c.getParentId() != null) {
                pst.setInt(4, c.getParentId());
            } else {
                pst.setNull(4, Types.INTEGER);
            }

            pst.setBoolean(5, c.isSolution());
            pst.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            pst.executeUpdate();
            System.out.println("✅ Comment saved to database!");
        } catch (SQLException e) {
            System.err.println("❌ Error adding comment: " + e.getMessage());
        }
    }

    // --- FETCH COMMENTS FOR A SPECIFIC POST ---
    public List<Comment> getCommentsByPost(int postId) {
        List<Comment> comments = new ArrayList<>();

        // We use a JOIN here to grab the actual Username of the person who commented
        String query = "SELECT c.*, u.username as author_name FROM comment c " +
                "LEFT JOIN user u ON c.author_id = u.id " +
                "WHERE c.post_id = ? ORDER BY c.created_at ASC";

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
                if (!rs.wasNull()) {
                    c.setParentId(parentId);
                }

                c.setSolution(rs.getBoolean("is_solution"));
                c.setCreatedAt(rs.getTimestamp("created_at"));
                c.setUpdatedAt(rs.getTimestamp("updated_at"));
                c.setImageName(rs.getString("image_name"));

                // Grab the joined username (Fallback if user table is different)
                try {
                    c.setAuthorName(rs.getString("author_name"));
                } catch (SQLException ex) {
                    c.setAuthorName("Student_" + c.getAuthorId()); // Fallback
                }

                comments.add(c);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching comments: " + e.getMessage());
        }
        return comments;
    }

    // --- DELETE A COMMENT ---
    public void supprimer(int id) {
        String query = "DELETE FROM comment WHERE id = ?";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error deleting comment: " + e.getMessage());
        }
    }

    // --- MARK AS SOLUTION (For the Question/Answer dynamic) ---
    public void markAsSolution(int commentId) {
        String query = "UPDATE comment SET is_solution = true WHERE id = ?";
        try (PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, commentId);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error marking solution: " + e.getMessage());
        }
    }
}