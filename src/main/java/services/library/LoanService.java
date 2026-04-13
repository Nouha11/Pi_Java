package services.library;

import models.library.Loan;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LoanService {

    private static final int MAX_ACTIVE_LOANS  = 3;
    private static final int LOAN_DURATION_DAYS = 14;

    private Connection cnx;

    public LoanService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    public String validate(Loan l) {
        if (l.getUserId() <= 0)    return "User is required.";
        if (l.getBookId() <= 0)    return "Book is required.";
        if (l.getLibraryId() <= 0) return "Library is required.";
        return null;
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void ajouter(Loan l) throws SQLException {
        String sql = "INSERT INTO loans (user_id, book_id, library_id, status, requested_at) VALUES (?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, l.getUserId());
        ps.setInt(2, l.getBookId());
        ps.setInt(3, l.getLibraryId());
        ps.setString(4, Loan.STATUS_PENDING);
        ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) l.setId(keys.getInt(1));
        System.out.println("Loan request created! ✅");
    }

    public void supprimer(int id) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement("DELETE FROM loans WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        System.out.println("Loan deleted! 🗑️");
    }

    // ─────────────────────────────────────────────
    //  WORKFLOW ACTIONS
    // ─────────────────────────────────────────────

    /**
     * Approve a loan — enforces max 3 active loans rule
     */
    public void approuver(int loanId) throws SQLException {
        Loan loan = findById(loanId);
        if (loan == null) throw new SQLException("Loan not found.");

        int activeCount = countActiveLoans(loan.getUserId());
        if (activeCount >= MAX_ACTIVE_LOANS)
            throw new SQLException("User already has " + MAX_ACTIVE_LOANS + " active loans.");

        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE loans SET status=?, approved_at=? WHERE id=?");
        ps.setString(1, Loan.STATUS_APPROVED);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setInt(3, loanId);
        ps.executeUpdate();
        System.out.println("Loan approved! ✅");
    }

    /**
     * Reject a loan with a reason
     */
    public void rejeter(int loanId, String reason) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE loans SET status=?, rejection_reason=? WHERE id=?");
        ps.setString(1, Loan.STATUS_REJECTED);
        ps.setString(2, reason);
        ps.setInt(3, loanId);
        ps.executeUpdate();
        System.out.println("Loan rejected! ❌");
    }

    /**
     * Mark as active (book picked up) — sets 14-day return deadline
     */
    public void marquerActif(int loanId) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + (long) LOAN_DURATION_DAYS * 24 * 60 * 60 * 1000);

        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE loans SET status=?, start_at=?, end_at=? WHERE id=?");
        ps.setString(1, Loan.STATUS_ACTIVE);
        ps.setTimestamp(2, now);
        ps.setTimestamp(3, end);
        ps.setInt(4, loanId);
        ps.executeUpdate();
        System.out.println("Loan marked as active! 📚");
    }

    /**
     * Mark as returned
     */
    public void marquerRetourne(int loanId) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE loans SET status=? WHERE id=?");
        ps.setString(1, Loan.STATUS_RETURNED);
        ps.setInt(2, loanId);
        ps.executeUpdate();
        System.out.println("Loan marked as returned! 📦");
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<Loan> afficher() {
        List<Loan> loans = new ArrayList<>();
        String sql = "SELECT l.*, b.title as book_title, u.email as user_name, lib.name as library_name " +
                "FROM loans l " +
                "LEFT JOIN books b ON l.book_id=b.id " +
                "LEFT JOIN users u ON l.user_id=u.id " +
                "LEFT JOIN libraries lib ON l.library_id=lib.id " +
                "ORDER BY l.requested_at DESC";
        try {
            ResultSet rs = cnx.createStatement().executeQuery(sql);
            while (rs.next()) loans.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading loans: " + e.getMessage());
        }
        return loans;
    }

    public List<Loan> findByStatus(String status) {
        List<Loan> loans = new ArrayList<>();
        String sql = "SELECT l.*, b.title as book_title, u.email as user_name, lib.name as library_name " +
                "FROM loans l " +
                "LEFT JOIN books b ON l.book_id=b.id " +
                "LEFT JOIN users u ON l.user_id=u.id " +
                "LEFT JOIN libraries lib ON l.library_id=lib.id " +
                "WHERE l.status=? ORDER BY l.requested_at DESC";
        try {
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) loans.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error filtering loans: " + e.getMessage());
        }
        return loans;
    }

    public Loan findById(int id) {
        String sql = "SELECT l.*, b.title as book_title, u.email as user_name, lib.name as library_name " +
                "FROM loans l " +
                "LEFT JOIN books b ON l.book_id=b.id " +
                "LEFT JOIN users u ON l.user_id=u.id " +
                "LEFT JOIN libraries lib ON l.library_id=lib.id " +
                "WHERE l.id=?";
        try {
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding loan: " + e.getMessage());
        }
        return null;
    }

    private int countActiveLoans(int userId) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE user_id=? AND status=?");
        ps.setInt(1, userId);
        ps.setString(2, Loan.STATUS_ACTIVE);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

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
        try { l.setBookTitle(rs.getString("book_title")); }    catch (SQLException ignored) {}
        try { l.setUserName(rs.getString("user_name")); }      catch (SQLException ignored) {}
        try { l.setLibraryName(rs.getString("library_name")); } catch (SQLException ignored) {}
        return l;
    }
}
