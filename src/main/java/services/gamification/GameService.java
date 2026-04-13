package services.gamification;

import models.gamification.Game;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GameService {
    private final Connection conn = MyConnection.getInstance().getCnx();

    // ===== CREATE =====
    public void addGame(Game game) throws SQLException {
        String sql = "INSERT INTO game (name, description, type, difficulty, category," +
                " token_cost, reward_tokens, reward_xp, energy_points, is_active, created_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setString(1, game.getName());
        ps.setString(2, game.getDescription());
        ps.setString(3, game.getType());
        ps.setString(4, game.getDifficulty());
        ps.setString(5, game.getCategory());
        ps.setInt(6, game.getTokenCost());
        ps.setInt(7, game.getRewardTokens());
        ps.setInt(8, game.getRewardXP());

        if (game.getEnergyPoints() != null) ps.setInt(9, game.getEnergyPoints());
        else ps.setNull(9, Types.INTEGER);

        ps.setBoolean(10, game.isActive());
        ps.executeUpdate();
    }

    // ===== READ ALL =====
    public List<Game> getAllGames() throws SQLException {
        List<Game> list = new ArrayList<>();
        String sql = "SELECT * FROM game ORDER BY id DESC";
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(sql);
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
        return list;
    }

    // ===== READ ONE by ID =====
    public Game getGameById(int id) throws SQLException {
        String sql = "SELECT * FROM game WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return new Game(rs.getInt("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("type"),
                    rs.getString("difficulty"), rs.getString("category"),
                    rs.getInt("token_cost"), rs.getInt("reward_tokens"),
                    rs.getInt("reward_xp"),
                    rs.getObject("energy_points") != null ? rs.getInt("energy_points") : null,
                    rs.getBoolean("is_active"));
        }
        return null;
    }

    // ===== UPDATE =====
    public void updateGame(Game game) throws SQLException {
        String sql = "UPDATE game SET name=?, description=?, type=?, difficulty=?, category=?," +
                " token_cost=?, reward_tokens=?, reward_xp=?, energy_points=?, is_active=?" +
                " WHERE id=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, game.getName());
        ps.setString(2, game.getDescription());
        ps.setString(3, game.getType());
        ps.setString(4, game.getDifficulty());
        ps.setString(5, game.getCategory());
        ps.setInt(6, game.getTokenCost());
        ps.setInt(7, game.getRewardTokens());
        ps.setInt(8, game.getRewardXP());
        if (game.getEnergyPoints() != null) ps.setInt(9, game.getEnergyPoints());
        else ps.setNull(9, Types.INTEGER);
        ps.setBoolean(10, game.isActive());
        ps.setInt(11, game.getId());
        ps.executeUpdate();
    }

    // ===== DELETE =====
    public void deleteGame(int id) throws SQLException {
        // game_rewards rows are deleted automatically (CASCADE)
        String sql = "DELETE FROM game WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    // ===== SEARCH  =====
    public List<Game> searchGames(String keyword) throws SQLException {
        List<Game> list = new ArrayList<>();
        String sql = "SELECT * FROM game WHERE name LIKE ? OR type LIKE ? ORDER BY name";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, "%" + keyword + "%");
        ps.setString(2, "%" + keyword + "%");
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(new Game(rs.getInt("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("type"),
                    rs.getString("difficulty"), rs.getString("category"),
                    rs.getInt("token_cost"), rs.getInt("reward_tokens"),
                    rs.getInt("reward_xp"),
                    rs.getObject("energy_points") != null ? rs.getInt("energy_points") : null,
                    rs.getBoolean("is_active")));
        }
        return list;
    }

    // ===== UNIQUENESS CHECK =====
    public boolean gameNameExists(String name, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM game WHERE name = ? AND id != ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, name); ps.setInt(2, excludeId);
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt(1) > 0;
    }

    // ===== GAME ↔ REWARD RELATION METHODS =====
    public void addRewardToGame(int gameId, int rewardId) throws SQLException {
        String sql = "INSERT IGNORE INTO game_rewards (game_id, reward_id) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, gameId); ps.setInt(2, rewardId);
        ps.executeUpdate();
    }
    public void removeRewardFromGame(int gameId, int rewardId) throws SQLException {
        String sql = "DELETE FROM game_rewards WHERE game_id=? AND reward_id=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, gameId); ps.setInt(2, rewardId);
        ps.executeUpdate();
    }
    public List<models.gamification.Reward> getRewardsForGame(int gameId) throws SQLException {
        List<models.gamification.Reward> list = new ArrayList<>();
        String sql = "SELECT r.* FROM reward r" +
                " JOIN game_rewards gr ON r.id = gr.reward_id" +
                " WHERE gr.game_id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, gameId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(new models.gamification.Reward(rs.getInt("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("type"),
                    rs.getInt("value"), rs.getString("requirement"),
                    rs.getString("icon"), rs.getBoolean("is_active"),
                    rs.getObject("required_level") != null ? rs.getInt("required_level") : null));
        }
        return list;
    }


    }
