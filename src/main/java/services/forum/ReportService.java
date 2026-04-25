package services.forum;

import models.forum.Report;
import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportService {

    private final Connection cnx;

    public ReportService() {
        this.cnx = MyConnection.getInstance().getCnx();
    }

    public void submitReport(Report r) {
        String req = "INSERT INTO report (reason, created_at, post_id, reporter_id, comment_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setString(1, r.getReason());
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            if (r.getPostId() != null) ps.setInt(3, r.getPostId()); else ps.setNull(3, Types.INTEGER);
            ps.setInt(4, r.getReporterId());
            if (r.getCommentId() != null) ps.setInt(5, r.getCommentId()); else ps.setNull(5, Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    // 🔥 UPDATED: Double JOIN to get both Post Titles and Comment Contents
    public List<Report> getAllReports() {
        List<Report> list = new ArrayList<>();
        String req = "SELECT r.*, u.username as reporter_name, p.title as post_title, c.content as comment_content " +
                "FROM report r " +
                "JOIN user u ON r.reporter_id = u.id " +
                "LEFT JOIN post p ON r.post_id = p.id " +
                "LEFT JOIN comment c ON r.comment_id = c.id " +
                "ORDER BY r.created_at DESC";

        try (Statement st = cnx.createStatement(); ResultSet rs = st.executeQuery(req)) {
            while (rs.next()) {
                Report r = new Report();
                r.setId(rs.getInt("id"));
                r.setReason(rs.getString("reason"));
                r.setCreatedAt(rs.getTimestamp("created_at"));

                // Use getObject so NULL stays NULL in Java!
                r.setPostId((Integer) rs.getObject("post_id"));
                r.setCommentId((Integer) rs.getObject("comment_id"));
                r.setReporterId(rs.getInt("reporter_id"));

                r.setReporterName(rs.getString("reporter_name"));
                r.setPostTitle(rs.getString("post_title"));
                r.setCommentContent(rs.getString("comment_content"));
                r.setStatus("pending");

                list.add(r);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching reports: " + e.getMessage());
        }
        return list;
    }

    public void resolveReport(int reportId) {
        String req = "DELETE FROM report WHERE id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, reportId);
            ps.executeUpdate();
            System.out.println("✅ Report #" + reportId + " resolved and removed.");
        } catch (SQLException e) {
            System.err.println("❌ Error resolving report: " + e.getMessage());
        }
    }

    public boolean hasAlreadyReportedPost(int userId, int postId) {
        String req = "SELECT COUNT(*) FROM report WHERE reporter_id = ? AND post_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId); ps.setInt(2, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) { }
        return false;
    }

    public boolean hasAlreadyReportedComment(int userId, int commentId) {
        String req = "SELECT COUNT(*) FROM report WHERE reporter_id = ? AND comment_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId); ps.setInt(2, commentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) { }
        return false;
    }
}