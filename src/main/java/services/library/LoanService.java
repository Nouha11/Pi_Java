package services.library;

import models.library.Loan;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class managing the full loan lifecycle:
 * PENDING → APPROVED → ACTIVE → RETURNED (or REJECTED / OVERDUE)
 */
public class LoanService {

    // Business rules enforced at the service level
    private static final int MAX_ACTIVE_LOANS   = 3;  // a user can't have more than 3 active loans at once
    private static final int LOAN_DURATION_DAYS = 14; // default loan period is 2 weeks

    private Connection getCnx() {
        return MyConnection.getInstance().getCnx();
    }

    // ── VALIDATION ────────────────────────────────────────────────────────────

    /**
     * Basic validation before creating a loan request.
     * Returns an error message or null if valid.
     */
    public String validate(Loan l) {
        if (l.getUserId() <= 0) return "User is required.";
        if (l.getBookId() <= 0) return "Book is required.";
        return null;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new loan request with status PENDING.
     * The loan starts in PENDING state — an admin must approve it before it becomes ACTIVE.
     * library_id is optional (null if no specific library was chosen).
     */
    public void ajouter(Loan l) throws SQLException {
        String sql = "INSERT INTO loans (user_id, book_id, library_id, status, requested_at, start_at, end_at) " +
                     "VALUES (?,?,?,?,NOW(),?,?)";
        PreparedStatement ps = getCnx().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, l.getUserId());
        ps.setInt(2, l.getBookId());
        if (l.getLibraryId() > 0) ps.setInt(3, l.getLibraryId());
        else ps.setNull(3, Types.INTEGER);
        ps.setString(4, Loan.STATUS_PENDING);
        // Use provided start_at or default to now
        ps.setTimestamp(5, l.getStartAt() != null ? l.getStartAt()
                : new Timestamp(System.currentTimeMillis()));
        // Use provided end_at (return date chosen by user) or default to 14 days from now
        ps.setTimestamp(6, l.getEndAt() != null ? l.getEndAt()
                : new Timestamp(System.currentTimeMillis() + (long) LOAN_DURATION_DAYS * 24 * 60 * 60 * 1000));
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) l.setId(keys.getInt(1));
        System.out.println("Loan created! ✅");
    }

    /**
     * Updates the start and end dates of a PENDING loan.
     * Only allowed while the loan is still pending (not yet approved).
     */
    public void updateDates(int loanId, java.sql.Timestamp startAt, java.sql.Timestamp endAt) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET start_at=?, end_at=? WHERE id=? AND status=?");
        ps.setTimestamp(1, startAt);
        ps.setTimestamp(2, endAt);
        ps.setInt(3, loanId);
        ps.setString(4, Loan.STATUS_PENDING);
        ps.executeUpdate();
    }

    /**
     * Deletes a loan record permanently.
     */
    public void supprimer(int id) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement("DELETE FROM loans WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    // ── WORKFLOW ──────────────────────────────────────────────────────────────

    /**
     * Admin approves a loan request.
     * Enforces the MAX_ACTIVE_LOANS rule — a user cannot have more than 3 active loans.
     */
    public void approuver(int loanId) throws SQLException {
        Loan loan = findById(loanId);
        if (loan == null) throw new SQLException("Loan not found.");
        // Check business rule: max 3 active loans per user
        if (countActiveLoans(loan.getUserId()) >= MAX_ACTIVE_LOANS)
            throw new SQLException("User already has " + MAX_ACTIVE_LOANS + " active loans.");

        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, approved_at=NOW() WHERE id=?");
        ps.setString(1, Loan.STATUS_APPROVED);
        ps.setInt(2, loanId);
        ps.executeUpdate();
    }

    /**
     * Admin rejects a loan request with a reason.
     * The rejection reason is stored so the user can see why it was denied.
     */
    public void rejeter(int loanId, String reason) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, rejection_reason=? WHERE id=?");
        ps.setString(1, Loan.STATUS_REJECTED);
        ps.setString(2, reason);
        ps.setInt(3, loanId);
        ps.executeUpdate();
    }

    /**
     * Marks a loan as ACTIVE (book has been physically picked up).
     * Calculates the end date by adding LOAN_DURATION_DAYS to the current time.
     */
    public void marquerActif(int loanId) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        // end date = now + 14 days (converted to milliseconds)
        Timestamp end = new Timestamp(now.getTime() + (long) LOAN_DURATION_DAYS * 24 * 60 * 60 * 1000);
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=?, start_at=?, end_at=? WHERE id=?");
        ps.setString(1, Loan.STATUS_ACTIVE);
        ps.setTimestamp(2, now);
        ps.setTimestamp(3, end);
        ps.setInt(4, loanId);
        ps.executeUpdate();
    }

    /**
     * Marks a loan as RETURNED (book has been brought back).
     */
    public void marquerRetourne(int loanId) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "UPDATE loans SET status=? WHERE id=?");
        ps.setString(1, Loan.STATUS_RETURNED);
        ps.setInt(2, loanId);
        ps.executeUpdate();
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    /**
     * Base SELECT query with JOINs to enrich loan data with book title,
     * user email, and library name — avoids extra queries in the UI layer.
     */
    private static final String SELECT_BASE =
            "SELECT l.*, b.title AS book_title, u.email AS user_name, lib.name AS library_name " +
            "FROM loans l " +
            "LEFT JOIN books b ON l.book_id = b.id " +
            "LEFT JOIN user u ON l.user_id = u.id " +
            "LEFT JOIN libraries lib ON l.library_id = lib.id ";

    /**
     * Returns all loans ordered by most recent request first.
     */
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

    /**
     * Returns all loans for a specific user, ordered by most recent first.
     */
    public List<Loan> findByUser(int userId) {
        List<Loan> list = new ArrayList<>();
        try {
            PreparedStatement ps = getCnx().prepareStatement(
                    SELECT_BASE + "WHERE l.user_id=? ORDER BY l.requested_at DESC");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading user loans: " + e.getMessage());
        }
        return list;
    }

    /**
     * Returns loans filtered by a specific status (e.g. "PENDING", "ACTIVE").
     */
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

    /**
     * Finds a single loan by ID. Returns null if not found.
     */
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

    /**
     * Counts how many loans a user currently has in ACTIVE status.
     * Used to enforce the MAX_ACTIVE_LOANS business rule.
     */
    private int countActiveLoans(int userId) throws SQLException {
        PreparedStatement ps = getCnx().prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE user_id=? AND status=?");
        ps.setInt(1, userId);
        ps.setString(2, Loan.STATUS_ACTIVE);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    // ── MAPPING ───────────────────────────────────────────────────────────────

    /**
     * Maps a ResultSet row to a Loan object.
     * The try/catch blocks for joined fields handle cases where the JOIN
     * returned no match (e.g. book was deleted), avoiding a crash.
     */
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
        // These come from JOINs — may be null if related record doesn't exist
        try { l.setBookTitle(rs.getString("book_title")); }     catch (SQLException ignored) {}
        try { l.setUserName(rs.getString("user_name")); }       catch (SQLException ignored) {}
        try { l.setLibraryName(rs.getString("library_name")); } catch (SQLException ignored) {}
        return l;
    }
}
