package services.library;

import models.library.Library;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryService {

    private Connection getCnx() {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            if (cnx == null || cnx.isClosed()) {
                return DriverManager.getConnection("jdbc:mysql://localhost:3306/nova_db", "root", "");
            }
            return cnx;
        } catch (SQLException e) {
            System.err.println("LibraryService connection error: " + e.getMessage());
            return null;
        }
    }

    /** All libraries (used by admin) */
    public List<Library> findAll() {
        List<Library> list = new ArrayList<>();
        try {
            ResultSet rs = getCnx().createStatement()
                    .executeQuery("SELECT * FROM libraries ORDER BY name");
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading libraries: " + e.getMessage());
        }
        return list;
    }

    /** Libraries linked to a specific book via book_library join table */
    public List<Library> findByBook(int bookId) {
        List<Library> list = new ArrayList<>();
        String sql = "SELECT l.* FROM libraries l " +
                     "INNER JOIN book_library bl ON l.id = bl.library_id " +
                     "WHERE bl.book_id = ? ORDER BY l.name";
        try {
            PreparedStatement ps = getCnx().prepareStatement(sql);
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            System.out.println("Libraries for book " + bookId + ": " + list.size());
        } catch (SQLException e) {
            System.err.println("Error loading libraries for book: " + e.getMessage());
        }
        return list;
    }

    public Library findById(int id) {
        try {
            PreparedStatement ps = getCnx().prepareStatement("SELECT * FROM libraries WHERE id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding library: " + e.getMessage());
        }
        return null;
    }

    private Library mapRow(ResultSet rs) throws SQLException {
        Library l = new Library();
        l.setId(rs.getInt("id"));
        l.setName(rs.getString("name"));
        try { l.setAddress(rs.getString("address")); }   catch (SQLException ignored) {}
        try { l.setPhone(rs.getString("phone")); }        catch (SQLException ignored) {}
        try { l.setLatitude(rs.getDouble("latitude")); }  catch (SQLException ignored) {}
        try { l.setLongitude(rs.getDouble("longitude")); } catch (SQLException ignored) {}
        return l;
    }
}
