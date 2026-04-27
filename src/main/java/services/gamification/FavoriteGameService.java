package services.gamification;

import models.gamification.Game;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FavoriteGameService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    public List<Game> getFavorites(int userId) throws SQLException {
        List<Game> list = new ArrayList<>();
        String sql = "SELECT g.* FROM game g " +
                     "JOIN user_favorite_games f ON g.id = f.game_id " +
                     "WHERE f.user_id = ? AND g.is_active = 1 ORDER BY g.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Game g = new Game(rs.getInt("id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("type"),
                        rs.getString("difficulty"), rs.getString("category"),
                        rs.getInt("token_cost"), rs.getInt("reward_tokens"),
                        rs.getInt("reward_xp"),
                        rs.getObject("energy_points") != null ? rs.getInt("energy_points") : null,
                        rs.getBoolean("is_active"));
                list.add(g);
            }
        }
        return list;
    }

    public boolean isFavorite(int userId, int gameId) throws SQLException {
        String sql = "SELECT 1 FROM user_favorite_games WHERE user_id = ? AND game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, gameId);
            return ps.executeQuery().next();
        }
    }

    public void addFavorite(int userId, int gameId) throws SQLException {
        String sql = "INSERT IGNORE INTO user_favorite_games (user_id, game_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, gameId); ps.executeUpdate();
        }
    }

    public void removeFavorite(int userId, int gameId) throws SQLException {
        String sql = "DELETE FROM user_favorite_games WHERE user_id = ? AND game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, gameId); ps.executeUpdate();
        }
    }

    public boolean toggle(int userId, int gameId) throws SQLException {
        if (isFavorite(userId, gameId)) { removeFavorite(userId, gameId); return false; }
        else { addFavorite(userId, gameId); return true; }
    }
}
