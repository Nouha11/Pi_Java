package services.library;

import models.library.Book;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookService {

    private Connection getCnx() {
        return MyConnection.getInstance().getCnx();
    }

    // ── VALIDATION ────────────────────────────────────────────────────────────

    public String validate(Book b, boolean forUpdate) {
        if (b.getTitle() == null || b.getTitle().trim().isEmpty())
            return "Title is required.";
        if (b.getTitle().trim().length() < 2)
            return "Title must be at least 2 characters.";
        if (b.getAuthor() == null || b.getAuthor().trim().isEmpty())
            return "Author is required.";
        if (b.getPrice() < 0)
            return "Price cannot be negative.";
        if (b.getIsbn() != null && !b.getIsbn().trim().isEmpty()) {
            String err = checkIsbnUniqueness(b.getIsbn().trim(), forUpdate ? b.getId() : -1);
            if (err != null) return err;
        }
        return null;
    }

    private String checkIsbnUniqueness(String isbn, int excludeId) {
        try {
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

    public void ajouter(Book b) throws SQLException {
        // books schema: title, description, is_digital, price, cover_image, author, isbn,
        //               published_at, uploader_id, created_at, updated_at, user_id, pdf_url
        String sql = "INSERT INTO books (title, author, isbn, price, is_digital, cover_image, pdf_url, created_at) " +
                     "VALUES (?,?,?,?,?,?,?,NOW())";
        PreparedStatement ps = getCnx().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, b.getTitle().trim());
        ps.setString(2, b.getAuthor() != null ? b.getAuthor().trim() : "");
        ps.setString(3, b.getIsbn());
        ps.setDouble(4, b.getPrice());
        ps.setBoolean(5, "digital".equalsIgnoreCase(b.getType()));
        ps.setString(6, b.getCoverImage());
        ps.setString(7, b.getPdfUrl());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) b.setId(keys.getInt(1));
        System.out.println("Book added! ✅");
    }

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

    public void supprimer(int id) throws SQLException {
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

    public List<Book> findByFilters(String type, String search) {
        List<Book> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT * FROM books WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (type != null && !type.isEmpty()) {
            // is_digital: 1 = digital, 0 = physical
            sb.append(" AND is_digital=?");
            params.add("digital".equalsIgnoreCase(type) ? 1 : 0);
        }
        if (search != null && !search.isEmpty()) {
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

    // ── MAPPING ───────────────────────────────────────────────────────────────

    private Book mapRow(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setId(rs.getInt("id"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setIsbn(rs.getString("isbn"));
        b.setPrice(rs.getDouble("price"));
        // convert is_digital (0/1) → type string
        b.setType(rs.getBoolean("is_digital") ? "digital" : "physical");
        b.setCoverImage(rs.getString("cover_image"));
        b.setPdfUrl(rs.getString("pdf_url"));
        return b;
    }
}
