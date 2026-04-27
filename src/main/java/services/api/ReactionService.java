package services.api;

import utils.MyConnection;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ReactionService {

    private final Connection cnx;

    public ReactionService() {
        this.cnx = MyConnection.getInstance().getCnx();
    }

    /**
     * Toggles a reaction.
     * - If the user clicks the exact SAME emoji, it removes it.
     * - If the user clicks a DIFFERENT emoji, it updates their current reaction.
     * - If the user hasn't reacted yet, it inserts a new reaction.
     */
    public void reactToPost(int userId, int postId, String emoji) {
        String checkReq = "SELECT reaction_type FROM post_reaction WHERE user_id = ? AND post_id = ?";

        try (PreparedStatement checkPs = cnx.prepareStatement(checkReq)) {
            checkPs.setInt(1, userId);
            checkPs.setInt(2, postId);
            ResultSet rs = checkPs.executeQuery();

            if (rs.next()) {
                String existingReaction = rs.getString("reaction_type");

                if (existingReaction.equals(emoji)) {
                    deleteReaction(userId, postId);
                } else {
                    updateReaction(userId, postId, emoji);
                }
            } else {
                insertReaction(userId, postId, emoji);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error processing reaction: " + e.getMessage());
        }
    }

    private void insertReaction(int userId, int postId, String emoji) throws SQLException {
        String req = "INSERT INTO post_reaction (user_id, post_id, reaction_type) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId);
            ps.setInt(2, postId);
            ps.setString(3, emoji);
            ps.executeUpdate();
        }
    }

    private void updateReaction(int userId, int postId, String emoji) throws SQLException {
        String req = "UPDATE post_reaction SET reaction_type = ? WHERE user_id = ? AND post_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setString(1, emoji);
            ps.setInt(2, userId);
            ps.setInt(3, postId);
            ps.executeUpdate();
        }
    }

    private void deleteReaction(int userId, int postId) throws SQLException {
        String req = "DELETE FROM post_reaction WHERE user_id = ? AND post_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId);
            ps.setInt(2, postId);
            ps.executeUpdate();
        }
    }

    /**
     * Gets a map of all emojis and their total counts for a specific post.
     */
    public Map<String, Integer> getReactionsForPost(int postId) {
        Map<String, Integer> counts = new HashMap<>();
        String req = "SELECT reaction_type, COUNT(*) as total FROM post_reaction WHERE post_id = ? GROUP BY reaction_type";

        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, postId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                counts.put(rs.getString("reaction_type"), rs.getInt("total"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching reactions: " + e.getMessage());
        }
        return counts;
    }

    /**
     * Checks if a specific user has reacted to a post, and returns the emoji they used.
     */
    public String getUserReactionForPost(int userId, int postId) {
        String req = "SELECT reaction_type FROM post_reaction WHERE user_id = ? AND post_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId);
            ps.setInt(2, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("reaction_type");
            }
        } catch (SQLException e) {}
        return null;
    }
}