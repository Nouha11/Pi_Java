package services.users;

import models.users.User;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService {

    private final Connection conn;

    public UserService() {
        this.conn = MyConnection.getInstance().getCnx();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────────

    public boolean addUser(User user) throws SQLException {
        String sql = "INSERT INTO user (email, username, password, role, is_active, is_verified, is_banned, ban_reason, xp, profile_picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole().name());
            ps.setBoolean(5, user.isActive());
            ps.setBoolean(6, user.isVerified());
            ps.setBoolean(7, user.isBanned());
            ps.setString(8, user.getBanReason());
            ps.setInt(9, user.getXp());
            ps.setString(10, user.getProfilePicture());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) user.setId(keys.getInt(1));
                return true;
            }
        }
        return false;
    }

    // ── READ ALL ───────────────────────────────────────────────────────────────

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM user ORDER BY created_at DESC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ── READ ONE ───────────────────────────────────────────────────────────────

    public User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM user WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────────

    public boolean updateUser(User user) throws SQLException {
        String sql = "UPDATE user SET email=?, username=?, role=?, is_active=?, is_verified=?, is_banned=?, ban_reason=?, xp=?, profile_picture=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getRole().name());
            ps.setBoolean(4, user.isActive());
            ps.setBoolean(5, user.isVerified());
            ps.setBoolean(6, user.isBanned());
            ps.setString(7, user.getBanReason());
            ps.setInt(8, user.getXp());
            ps.setString(9, user.getProfilePicture());
            ps.setInt(10, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // ── UPDATE PROFILE PICTURE ONLY ───────────────────────────────────────────

    public boolean updateProfilePicture(int userId, String picturePath) throws SQLException {
        String sql = "UPDATE user SET profile_picture=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, picturePath);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── DELETE ─────────────────────────────────────────────────────────────────

    public boolean deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM user WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    public List<User> searchUsers(String keyword) throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM user WHERE username LIKE ? OR email LIKE ? ORDER BY username";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ── FILTER ────────────────────────────────────────────────────────────────

    public List<User> filterByRole(User.Role role) throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = role == null
            ? "SELECT * FROM user ORDER BY username"
            : "SELECT * FROM user WHERE role = ? ORDER BY username";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (role != null) ps.setString(1, role.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ── STATISTICS ────────────────────────────────────────────────────────────

    public Map<String, Integer> getRoleStats() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT role, COUNT(*) as cnt FROM user GROUP BY role";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) stats.put(rs.getString("role"), rs.getInt("cnt"));
        }
        return stats;
    }

    public Map<String, Integer> getSummaryStats() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT COUNT(*) AS total, SUM(is_active) AS active, SUM(is_banned) AS banned, SUM(is_verified) AS verified FROM user";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                stats.put("total",    rs.getInt("total"));
                stats.put("active",   rs.getInt("active"));
                stats.put("banned",   rs.getInt("banned"));
                stats.put("verified", rs.getInt("verified"));
            }
        }
        return stats;
    }

    // ── HELPER ────────────────────────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setEmail(rs.getString("email"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(User.Role.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("is_active"));
        u.setVerified(rs.getBoolean("is_verified"));
        u.setBanned(rs.getBoolean("is_banned"));
        u.setBanReason(rs.getString("ban_reason"));
        u.setXp(rs.getInt("xp"));
        u.setProfilePicture(rs.getString("profile_picture"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) u.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) u.setUpdatedAt(ua.toLocalDateTime());
        return u;
    }

    // ── EXISTENCE CHECKS (used by SignupController) ───────────────────────────

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}