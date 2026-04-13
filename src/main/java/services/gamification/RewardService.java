package services.gamification;

import models.gamification.Reward;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RewardService {
    private final Connection conn = MyConnection.getInstance().getCnx();
    // ===== CREATE =====
    public void addReward(Reward reward) throws SQLException {
        String sql = "INSERT INTO reward (name, description, type, value," +
                " requirement, icon, is_active, required_level)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, reward.getName());
        ps.setString(2, reward.getDescription());
        ps.setString(3, reward.getType());
        ps.setInt(4, reward.getValue());
        ps.setString(5, reward.getRequirement());
        ps.setString(6, reward.getIcon());
        ps.setBoolean(7, reward.isActive());
        if (reward.getRequiredLevel() != null)
            ps.setInt(8, reward.getRequiredLevel());
        else
            ps.setNull(8, Types.INTEGER);
        ps.executeUpdate();
    }

    // ===== READ ALL =====
    public List<Reward> getAllRewards() throws SQLException {
        List<Reward> list = new ArrayList<>();
        String sql = "SELECT * FROM reward ORDER BY id DESC";
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // ===== READ ONE by ID =====
    public Reward getRewardById(int id) throws SQLException {
        String sql = "SELECT * FROM reward WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return mapRow(rs);
        }
        return null;
    }

    // ===== UPDATE =====
    public void updateReward(Reward reward) throws SQLException {
        String sql = "UPDATE reward SET name=?, description=?, type=?, value=?," +
                " requirement=?, icon=?, is_active=?, required_level=?" +
                " WHERE id=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, reward.getName());
        ps.setString(2, reward.getDescription());
        ps.setString(3, reward.getType());
        ps.setInt(4, reward.getValue());
        ps.setString(5, reward.getRequirement());
        ps.setString(6, reward.getIcon());
        ps.setBoolean(7, reward.isActive());
        if (reward.getRequiredLevel() != null)
            ps.setInt(8, reward.getRequiredLevel());
        else
            ps.setNull(8, Types.INTEGER);
        ps.setInt(9, reward.getId());
        ps.executeUpdate();
    }

    // ===== DELETE =====
    public void deleteReward(int id) throws SQLException {
        // game_rewards rows are deleted automatically (CASCADE)
        String sql = "DELETE FROM reward WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    // ===== UNIQUENESS CHECK (required by grading!) =====
    public boolean rewardNameExists(String name, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reward WHERE name = ? AND id != ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, name);
        ps.setInt(2, excludeId);
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt(1) > 0;
    }

    // ===== FILTER BY TYPE (extra feature) =====
    public List<Reward> getRewardsByType(String type) throws SQLException {
        List<Reward> list = new ArrayList<>();
        String sql = "SELECT * FROM reward WHERE type = ? AND is_active = 1 ORDER BY name";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, type);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // ===== SEARCH by name (extra feature — live search) =====
    public List<Reward> searchRewards(String keyword) throws SQLException {
        List<Reward> list = new ArrayList<>();
        String sql = "SELECT * FROM reward WHERE name LIKE ? OR type LIKE ? ORDER BY name";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, "%" + keyword + "%");
        ps.setString(2, "%" + keyword + "%");
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // ===== PRIVATE HELPER — maps a ResultSet row to a Reward object =====
    private Reward mapRow(ResultSet rs) throws SQLException {
        return new Reward(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("type"),
                rs.getInt("value"),
                rs.getString("requirement"),
                rs.getString("icon"),
                rs.getBoolean("is_active"),
                rs.getObject("required_level") != null ? rs.getInt("required_level") : null
        );
    }
}
