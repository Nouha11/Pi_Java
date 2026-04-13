package services.library;

import models.library.Library;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryService {

    private Connection cnx;

    public LibraryService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    public List<Library> findAll() {
        List<Library> list = new ArrayList<>();
        try {
            ResultSet rs = cnx.createStatement().executeQuery("SELECT * FROM libraries ORDER BY name");
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading libraries: " + e.getMessage());
        }
        return list;
    }

    public Library findById(int id) {
        try {
            PreparedStatement ps = cnx.prepareStatement("SELECT * FROM libraries WHERE id=?");
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
        l.setAddress(rs.getString("address"));
        l.setPhone(rs.getString("phone"));
        try { l.setLatitude(rs.getDouble("latitude")); }  catch (SQLException ignored) {}
        try { l.setLongitude(rs.getDouble("longitude")); } catch (SQLException ignored) {}
        return l;
    }
}
