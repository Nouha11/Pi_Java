package services.library;

import models.library.Book;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookService {

    private Connection cnx;

    public BookService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    public String validate(Book b, boolean forUpdate) {
        if (b.getTitle() == null || b.getTitle().trim().isEmpty())
            return "Title is required.";
        if (b.getTitle().trim().length() < 2)
            return "Title must be at least 2 characters.";

        if (b.getAuthor() == null || b.getAuthor().trim().isEmpty())
            return "Author is required.";

        if (b.getPrice() < 0)
            return "Price cannot be negative.";

        if (b.getType() == null || (!b.getType().equals("physical") && !b.getType().equals("digital")))
            return "Type must be 'physical' or 'digital'.";

        if (b.getIsbn() != null && !b.getIsbn().trim().isEmpty()) {
            String uniqueErr = checkIsbnUniqueness(b.getIsbn().trim(), forUpdate ? b.getId() : -1);
            if (uniqueErr != null) return uniqueErr;
        }

        return null;
    }

    private String checkIsbnUniqueness(String isbn, int excludeId) {
        try {
            String sql = excludeId == -1
                    ? "SELECT COUNT(*) FROM book WHERE isbn=?"
                    : "SELECT COUNT(*) FROM book WHERE isbn=? AND id!=?";
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setString(1, isbn);
            if (excludeId != -1) ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                return "A book with this ISBN already exists.";
        } catch (SQLException e) {
            System.err.println("ISBN uniqueness check failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void ajouter(Book b) throws SQLException {
        String sql = "INSERT INTO book (title, author, isbn, price, type, cover_image, pdf_url) VALUES (?,?,?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, b.getTitle().trim());
        ps.setString(2, b.getAuthor().trim());
        ps.setString(3, b.getIsbn());
        ps.setDouble(4, b.getPrice());
        ps.setString(5, b.getType());
        ps.setString(6, b.getCoverImage());
        ps.setString(7, b.getPdfUrl());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) b.setId(keys.getInt(1));
        System.out.println("Book added successfully! ✅");
    }

    public void modifier(Book b) throws SQLException {
        String sql = "UPDATE book SET title=?, author=?, isbn=?, price=?, type=?, cover_image=?, pdf_url=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, b.getTitle().trim());
        ps.setString(2, b.getAuthor().trim());
        ps.setString(3, b.getIsbn());
        ps.setDouble(4, b.getPrice());
        ps.setString(5, b.getType());
        ps.setString(6, b.getCoverImage());
        ps.setString(7, b.getPdfUrl());
        ps.setInt(8, b.getId());
        ps.executeUpdate();
        System.out.println("Book updated successfully! ✏️");
    }

    public void supprimer(int id) throws SQLException {
        // Check for active loans first
        PreparedStatement check = cnx.prepareStatement(
                "SELECT COUNT(*) FROM loan WHERE book_id=? AND status IN ('PENDING','APPROVED','ACTIVE')");
        check.setInt(1, id);
        ResultSet rs = check.executeQuery();
        if (rs.next() && rs.getInt(1) > 0)
            throw new SQLException("Cannot delete book: it has active loans.");

        PreparedStatement ps = cnx.prepareStatement("DELETE FROM book WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        System.out.println("Book deleted successfully! 🗑️");
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<Book> afficher() {
        List<Book> books = new ArrayList<>();
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT * FROM book ORDER BY title");
            while (rs.next()) books.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading books: " + e.getMessage());
        }
        return books;
    }

    public List<Book> findByFilters(String type, String search) {
        List<Book> books = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT * FROM book WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (type != null && !type.isEmpty()) {
            sb.append(" AND type=?"); params.add(type);
        }
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (title LIKE ? OR author LIKE ? OR isbn LIKE ?)");
            String like = "%" + search + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sb.append(" ORDER BY title");

        try {
            PreparedStatement ps = cnx.prepareStatement(sb.toString());
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) books.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error filtering books: " + e.getMessage());
        }
        return books;
    }

    public Book findById(int id) {
        try {
            PreparedStatement ps = cnx.prepareStatement("SELECT * FROM book WHERE id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding book: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    private Book mapRow(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setId(rs.getInt("id"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setIsbn(rs.getString("isbn"));
        b.setPrice(rs.getDouble("price"));
        b.setType(rs.getString("type"));
        b.setCoverImage(rs.getString("cover_image"));
        b.setPdfUrl(rs.getString("pdf_url"));
        return b;
    }
}
