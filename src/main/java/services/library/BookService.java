package services.library;

import models.library.Book;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for all book-related database operations.
 * Handles CRUD operations, validation, and search/filter queries.
 */
public class BookService {

    // Uses the singleton DB connection instead of opening a new one each time
    private Connection getCnx() {
        return MyConnection.getInstance().getCnx();
    }

    // ── VALIDATION ────────────────────────────────────────────────────────────

    /**
     * Validates a Book object before inserting or updating.
     * @param forUpdate true = we're editing an existing book (ISBN check excludes current book)
     * @return error message string, or null if valid
     */
    public String validate(Book b, boolean forUpdate) {
        if (b.getTitle() == null || b.getTitle().trim().isEmpty())
            return "Title is required.";
        if (b.getTitle().trim().length() < 2)
            return "Title must be at least 2 characters.";
        if (b.getAuthor() == null || b.getAuthor().trim().isEmpty())
            return "Author is required.";
        if (b.getPrice() < 0)
            return "Price cannot be negative.";
        // Only check ISBN uniqueness if one was provided
        if (b.getIsbn() != null && !b.getIsbn().trim().isEmpty()) {
            String err = checkIsbnUniqueness(b.getIsbn().trim(), forUpdate ? b.getId() : -1);
            if (err != null) return err;
        }
        return null; // null means no errors
    }

    /**
     * Checks that no other book in the DB has the same ISBN.
     * When updating, we exclude the current book's own ID from the check.
     */
    private String checkIsbnUniqueness(String isbn, int excludeId) {
        try {
            // If excludeId == -1 we're inserting, so check all rows
            // If excludeId != -1 we're updating, so skip the current book's row
            String sql = excludeId == -1
                    ? "SELECT COUNT(*) FROM books WHERE isbn=?"
                    : "SELECT COUNT(*) FROM books WHERE isbn=? AND id!=?";
            PreparedStatement ps = getCnx().prepareStatement(sql);
            ps.setString(1, isbn);
            if (excludeId != -1) ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                return "A book with this ISBN already exists.";
        } catch (SQLException e) {
            System.err.println("ISBN check error: " + e.getMessage());
        }
        return null;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Inserts a new book into the database.
     * Uses RETURN_GENERATED_KEYS to get the auto-incremented ID back and set it on the object.
     */
    public void ajouter(Book b) throws SQLException {
        String sql = "INSERT INTO books (title, author, isbn, price, is_digital, cover_image, pdf_url, created_at) " +
                     "VALUES (?,?,?,?,?,?,?,NOW())";
        // RETURN_GENERATED_KEYS tells JDBC to give us back the new row's ID
        PreparedStatement ps = getCnx().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, b.getTitle().trim());
        ps.setString(2, b.getAuthor() != null ? b.getAuthor().trim() : "");
        ps.setString(3, b.getIsbn());
        ps.setDouble(4, b.getPrice());
        // Store book type as boolean: digital=true, physical=false
        ps.setBoolean(5, "digital".equalsIgnoreCase(b.getType()));
        ps.setString(6, b.getCoverImage());
        ps.setString(7, b.getPdfUrl());
        ps.executeUpdate();
        // Retrieve and set the generated ID on the book object
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) b.setId(keys.getInt(1));
        System.out.println("Book added! ✅");
    }

    /**
     * Updates an existing book record in the database.
     * Identified by book.getId().
     */
    public void modifier(Book b) throws SQLException {
        String sql = "UPDATE books SET title=?, author=?, isbn=?, price=?, is_digital=?, " +
                     "cover_image=?, pdf_url=?, updated_at=NOW() WHERE id=?";
        PreparedStatement ps = getCnx().prepareStatement(sql);
        ps.setString(1, b.getTitle().trim());
        ps.setString(2, b.getAuthor() != null ? b.getAuthor().trim() : "");
        ps.setString(3, b.getIsbn());
        ps.setDouble(4, b.getPrice());
        ps.setBoolean(5, "digital".equalsIgnoreCase(b.getType()));
        ps.setString(6, b.getCoverImage());
        ps.setString(7, b.getPdfUrl());
        ps.setInt(8, b.getId());
        ps.executeUpdate();
        System.out.println("Book updated! ✏️");
    }

    /**
     * Deletes a book by ID.
     * First checks if the book has any active/pending loans — if so, deletion is blocked
     * to maintain data integrity (can't delete a book that's currently borrowed).
     */
    public void supprimer(int id) throws SQLException {
        // Safety check: prevent deleting a book with ongoing loans
        PreparedStatement check = getCnx().prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE book_id=? AND status IN ('PENDING','APPROVED','ACTIVE')");
        check.setInt(1, id);
        ResultSet rs = check.executeQuery();
        if (rs.next() && rs.getInt(1) > 0)
            throw new SQLException("Cannot delete: book has active loans.");
        PreparedStatement ps = getCnx().prepareStatement("DELETE FROM books WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        System.out.println("Book deleted! 🗑️");
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    /**
     * Returns all books ordered alphabetically by title.
     */
    public List<Book> afficher() {
        List<Book> list = new ArrayList<>();
        try {
            ResultSet rs = getCnx().createStatement()
                    .executeQuery("SELECT * FROM books ORDER BY title");
            while (rs.next()) list.add(mapRow(rs));
            System.out.println("BookService.afficher() returned " + list.size() + " books");
        } catch (SQLException e) {
            System.err.println("Error loading books: " + e.getMessage());
        }
        return list;
    }

    /**
     * Filters books by type (digital/physical) and/or a search keyword.
     * Builds the SQL query dynamically based on which filters are provided.
     * Uses LIKE for partial matching on title, author, and ISBN.
     */
    public List<Book> findByFilters(String type, String search) {
        List<Book> list = new ArrayList<>();
        // Start with a base query that always returns true, then append conditions
        StringBuilder sb = new StringBuilder("SELECT * FROM books WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (type != null && !type.isEmpty()) {
            // is_digital column: 1 = digital, 0 = physical
            sb.append(" AND is_digital=?");
            params.add("digital".equalsIgnoreCase(type) ? 1 : 0);
        }
        if (search != null && !search.isEmpty()) {
            // Search across title, author, and ISBN with partial matching
            sb.append(" AND (title LIKE ? OR author LIKE ? OR isbn LIKE ?)");
            String like = "%" + search + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sb.append(" ORDER BY title");

        try {
            PreparedStatement ps = getCnx().prepareStatement(sb.toString());
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            System.out.println("BookService.findByFilters() returned " + list.size() + " books");
        } catch (SQLException e) {
            System.err.println("Error filtering books: " + e.getMessage());
        }
        return list;
    }

    /**
     * Finds a single book by its primary key. Returns null if not found.
     */
    public Book findById(int id) {
        try {
            PreparedStatement ps = getCnx().prepareStatement("SELECT * FROM books WHERE id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding book: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the top N best-selling books based on completed payment count.
     */
    public List<Book> findBestSellers(int limit) {
        List<Book> list = new ArrayList<>();
        String sql = "SELECT b.*, COUNT(p.id) as sales_count " +
                "FROM books b " +
                "JOIN payments p ON b.id = p.book_id AND p.status = 'COMPLETED' " +
                "GROUP BY b.id " +
                "ORDER BY sales_count DESC " +
                "LIMIT ?";
        try {
            PreparedStatement ps = getCnx().prepareStatement(sql);
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading best sellers: " + e.getMessage());
        }
        return list;
    }

    /**
     * Maps a single ResultSet row to a Book object.
     * Converts the DB boolean is_digital back to the "digital"/"physical" string type.
     */
    private Book mapRow(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setId(rs.getInt("id"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setIsbn(rs.getString("isbn"));
        b.setPrice(rs.getDouble("price"));
        // Convert is_digital (0/1) back to a readable type string
        b.setType(rs.getBoolean("is_digital") ? "digital" : "physical");
        b.setCoverImage(rs.getString("cover_image"));
        b.setPdfUrl(rs.getString("pdf_url"));
        return b;
    }
}
