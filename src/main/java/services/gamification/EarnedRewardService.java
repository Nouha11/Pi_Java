package services.gamification;

import models.gamification.Reward;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages student_earned_rewards table.
 * Links: student_profile_id ↔ reward_id
 */
public class EarnedRewardService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    /** Get all rewards earned by a user (via student_profile_id). */
    public List<Reward> getEarnedRewards(int userId) throws SQLException {
        List<Reward> list = new ArrayList<>();
        String sql = "SELECT r.* FROM reward r " +
                     "JOIN student_earned_rewards ser ON r.id = ser.reward_id " +
                     "JOIN user u ON u.student_profile_id = ser.student_profile_id " +
                     "WHERE u.id = ? AND r.is_active = 1 " +
                     "ORDER BY r.type, r.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Reward(
                    rs.getInt("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("type"),
                    rs.getInt("value"), rs.getString("requirement"),
                    rs.getString("icon"), rs.getBoolean("is_active"),
                    rs.getObject("required_level") != null ? rs.getInt("required_level") : null
                ));
            }
        }
        return list;
    }

    /** Check if a user has earned a specific reward. */
    public boolean hasEarned(int userId, int rewardId) throws SQLException {
        // First check if user has a student_profile
        String checkProfile = "SELECT student_profile_id FROM user WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkProfile)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || rs.getObject("student_profile_id") == null) return false;
        }
        String sql = "SELECT 1 FROM student_earned_rewards ser " +
                     "JOIN user u ON u.student_profile_id = ser.student_profile_id " +
                     "WHERE u.id = ? AND ser.reward_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, rewardId);
            return ps.executeQuery().next();
        }
    }

    /** Award a reward to a user. Creates student_profile if missing. */
    public void awardReward(int userId, int rewardId) throws SQLException {
        int profileId = getOrCreateStudentProfile(userId);
        if (profileId <= 0) {
            System.err.println("[EarnedReward] Cannot get/create student_profile for userId=" + userId);
            return;
        }

        String sql = "INSERT IGNORE INTO student_earned_rewards (student_profile_id, reward_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profileId); ps.setInt(2, rewardId);
            int rows = ps.executeUpdate();
            System.out.println("[EarnedReward] Awarded reward " + rewardId + " to profile " + profileId + " (rows=" + rows + ")");
        }
    }

    /** Get student_profile_id for a user, creating one if it doesn't exist. */
    private int getOrCreateStudentProfile(int userId) throws SQLException {
        // Try to get existing profile
        String getProfile = "SELECT student_profile_id FROM user WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(getProfile)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int pid = rs.getInt("student_profile_id");
                if (!rs.wasNull() && pid > 0) return pid;
            }
        }

        // No profile — create one
        System.out.println("[EarnedReward] Creating student_profile for userId=" + userId);
        String getUser = "SELECT username, email FROM user WHERE id = ?";
        String username = "Student", email = null;
        try (PreparedStatement ps = conn.prepareStatement(getUser)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { username = rs.getString("username"); email = rs.getString("email"); }
        }

        String createProfile = "INSERT INTO student_profile (first_name, last_name, email, total_xp, total_tokens, level, energy) " +
                               "VALUES (?, '', ?, 0, 0, 1, 100)";
        try (PreparedStatement ps = conn.prepareStatement(createProfile, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int newProfileId = keys.getInt(1);
                // Link to user
                String link = "UPDATE user SET student_profile_id = ? WHERE id = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(link)) {
                    ps2.setInt(1, newProfileId); ps2.setInt(2, userId);
                    ps2.executeUpdate();
                }
                System.out.println("[EarnedReward] Created student_profile " + newProfileId + " for userId=" + userId);
                return newProfileId;
            }
        }
        return -1;
    }

    /** Count total earned rewards for a user. */
    public int countEarned(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM student_earned_rewards ser " +
                     "JOIN user u ON u.student_profile_id = ser.student_profile_id " +
                     "WHERE u.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
