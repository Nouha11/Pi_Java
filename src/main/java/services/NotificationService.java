package services;

import models.Notification;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {

    private Connection cnx;

    public NotificationService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // Create a new notification (Any module can call this!)
    public void sendNotification(Notification n) {
        String req = "INSERT INTO notifications (type, title, message, metadata, is_read, created_at, action_url, icon, color, user_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setString(1, n.getType());
            ps.setString(2, n.getTitle());
            ps.setString(3, n.getMessage());

            if (n.getMetadata() != null) ps.setString(4, n.getMetadata());
            else ps.setNull(4, Types.VARCHAR);

            ps.setBoolean(5, false); // is_read = false (0)
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            if (n.getActionUrl() != null) ps.setString(7, n.getActionUrl());
            else ps.setNull(7, Types.VARCHAR);

            if (n.getIcon() != null) ps.setString(8, n.getIcon());
            else ps.setNull(8, Types.VARCHAR);

            if (n.getColor() != null) ps.setString(9, n.getColor());
            else ps.setNull(9, Types.VARCHAR);

            ps.setInt(10, n.getUserId());

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("🚨 Error sending notification: " + e.getMessage());
        }
    }

    // Get all notifications for a user, newest first
    public List<Notification> getUserNotifications(int userId) {
        List<Notification> list = new ArrayList<>();
        String req = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 20";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Notification n = new Notification();
                n.setId(rs.getInt("id"));
                n.setType(rs.getString("type"));
                n.setTitle(rs.getString("title"));
                n.setMessage(rs.getString("message"));
                n.setMetadata(rs.getString("metadata"));
                n.setRead(rs.getBoolean("is_read"));
                n.setCreatedAt(rs.getTimestamp("created_at"));
                n.setReadAt(rs.getTimestamp("read_at"));
                n.setActionUrl(rs.getString("action_url"));
                n.setIcon(rs.getString("icon"));
                n.setColor(rs.getString("color"));
                n.setUserId(rs.getInt("user_id"));
                list.add(n);
            }
        } catch (SQLException e) {
            System.err.println("🚨 Error fetching notifications: " + e.getMessage());
        }
        return list;
    }

    // Mark a specific notification as read
    public void markAsRead(int notificationId) {
        String req = "UPDATE notifications SET is_read = 1, read_at = ? WHERE id = ?";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, notificationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("🚨 Error marking notification read: " + e.getMessage());
        }
    }

    // Get the red badge counter
    public int getUnreadCount(int userId) {
        String req = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {}
        return 0;
    }
}