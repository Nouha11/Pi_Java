package services.gamification;

import utils.MyConnection;
import java.sql.*;

/**
 * Manages the game_rating table.
 * One rating per user per game — upsert on re-rate.
 * Schema: game_rating(id, game_id, user_id, rating, created_at, updated_at)
 */
public class GameRatingService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    /** Submit or update a rating (1–5 stars). */
    public void rate(int userId, int gameId, int stars) throws SQLException {
        String sql = "INSERT INTO game_rating (game_id, user_id, rating, created_at, updated_at) " +
                     "VALUES (?, ?, ?, NOW(), NOW()) " +
                     "ON DUPLICATE KEY UPDATE rating = VALUES(rating), updated_at = NOW()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ps.setInt(2, userId);
            ps.setInt(3, Math.max(1, Math.min(5, stars)));
            ps.executeUpdate();
        }
    }

    /** Get the user's existing rating for a game, or 0 if none. */
    public int getUserRating(int userId, int gameId) throws SQLException {
        String sql = "SELECT rating FROM game_rating WHERE user_id = ? AND game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, gameId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("rating") : 0;
        }
    }

    /** Get average rating for a game (0.0 if no ratings). */
    public double getAverageRating(int gameId) throws SQLException {
        String sql = "SELECT AVG(rating) as avg, COUNT(*) as cnt FROM game_rating WHERE game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt("cnt") > 0)
                return rs.getDouble("avg");
        }
        return 0.0;
    }

    /** Get total number of ratings for a game. */
    public int getRatingCount(int gameId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM game_rating WHERE game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
