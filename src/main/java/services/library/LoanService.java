package services.library;

import models.library.Loan;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LoanService {

    private static final int MAX_ACTIVE_LOANS   = 3;
    private static final int LOAN_DURATION_DAYS = 14;

    private Connection getCnx() {
        return MyConnection.getInstance().getCnx();
    }

    // ── VALIDATION ────────────────────────────────────────────────────────────

    public String validate(Loan l) {
        if (l.getUserId() <= 0) return "User is required.";
        if (l.getBookId() <= 0) return "Book is required.";
        return null;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    // loans schema: id, start_at(NOT NULL), end_at, book_id, user_id, status,
    //               requested_at, approved_at, rejection_reason, library_id
    public void ajouter(Loan l) throws SQLException {
        String sql = "INSERT INTO loans (user_id, book_id, library_id, status, requested_at, start_at) " +
                     "VALUES (?,?,?,?,NOW(),NOW())";
        PreparedStatement ps = getCnx().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, l.getUserId());
        ps.setInt(2, l.getBookId());
        if (l.getLibraryId() > 0) ps.setInt(3, l.getLibraryId());
        else ps.setNull(3, Types.INTEGER);
        ps.setString(4, Loan.STATUS_PENDING);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) l.setId(keys.getInt(1));
        System.out.println("Loan created! ✅");
    }

    public void supprimer(int id) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement("DELETE FROM loans WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    // ── WORKFLOW ──────────────────────────────────────────────────────────────

    public void approuver(int loanId) throws SQLException {
        Loan loan = findById(loanId);
        if (loan == null) throw new SQLException("Loan not found.");
        if (countActiveLoans(loan.getUserId()) >= MAX_ACTIVE_LOANS)
            throw new SQLException("User already has " + MAX_ACTIVE_LOANS + " active loans.");

        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, approved_at=NOW() WHERE id=?");
        ps.setString(1, Loan.STATUS_APPROVED);
        ps.setInt(2, loanId);
        ps.executeUpdate();
    }

    public void rejeter(int loanId, String reason) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, rejection_reason=? WHERE id=?");
        ps.setString(1, Loan.STATUS_REJECTED);
        ps.setString(2, reason);
        ps.setInt(3, loanId);
        ps.executeUpdate();
    }

    public void marquerActif(int loanId) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + (long) LOAN_DURATION_DAYS * 24 * 60 * 60 * 1000);
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, start_at=?, end_at=? WHERE id=?");
        ps.setString(1, Loan.STATUS_ACTIVE);
        ps.setTimestamp(2, now);
        ps.setTimestamp(3, end);
        ps.setInt(4, loanId);
        ps.executeUpdate();
    }

    public void marquerRetourne(int loanId) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=? WHERE id=?");
        ps.setString(1, Loan.STATUS_RETURNED);
        ps.setInt(2, loanId);
        ps.executeUpdate();
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    private static final String SELECT_BASE =
            "SELECT l.*, b.title AS book_title, u.email AS user_name, lib.name AS library_name " +
            "FROM loans l " +
            "LEFT JOIN books b ON l.book_id = b.id " +
            "LEFT JOIN user u ON l.user_id = u.id " +
            "LEFT JOIN libraries lib ON l.library_id = lib.id ";

    public List<Loan> afficher() {
        List<Loan> list = new ArrayList<>();
        try {
            ResultSet rs = getCnx().createStatement()
                    .executeQuery(SELECT_BASE + "ORDER BY l.requested_at DESC");
            while (rs.next()) list.add(mapRow(rs));
            System.out.println("LoanService.afficher() returned " + list.size() + " loans");
        } catch (SQLException e) {
            System.err.println("Error loading loans: " + e.getMessage());
        }
        return list;
    }

    public List<Loan> findByStatus(String status) {
        List<Loan> list = new ArrayList<>();
        try {
            PreparedStatement ps = getCnx().prepareStatement(
                    SELECT_BASE + "WHERE l.status=? ORDER BY l.requested_at DESC");
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error filtering loans: " + e.getMessage());
        }
        return list;
    }

    public Loan findById(int id) {
        try {
            PreparedStatement ps = getCnx().prepareStatement(SELECT_BASE + "WHERE l.id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding loan: " + e.getMessage());
        }
        return null;
    }

    private int countActiveLoans(int userId) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE user_id=? AND status=?");
        ps.setInt(1, userId);
        ps.setString(2, Loan.STATUS_ACTIVE);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    // ── MAPPING ───────────────────────────────────────────────────────────────

    private Loan mapRow(ResultSet rs) throws SQLException {
        Loan l = new Loan();
        l.setId(rs.getInt("id"));
        l.setUserId(rs.getInt("user_id"));
        l.setBookId(rs.getInt("book_id"));
        l.setLibraryId(rs.getInt("library_id"));
        l.setStatus(rs.getString("status"));
        l.setRequestedAt(rs.getTimestamp("requested_at"));
        l.setApprovedAt(rs.getTimestamp("approved_at"));
        l.setStartAt(rs.getTimestamp("start_at"));
        l.setEndAt(rs.getTimestamp("end_at"));
        l.setRejectionReason(rs.getString("rejection_reason"));
        try { l.setBookTitle(rs.getString("book_title")); }     catch (SQLException ignored) {}
        try { l.setUserName(rs.getString("user_name")); }       catch (SQLException ignored) {}
        try { l.setLibraryName(rs.getString("library_name")); } catch (SQLException ignored) {}
        return l;
    }
}
