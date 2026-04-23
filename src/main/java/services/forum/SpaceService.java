package services.forum;

import models.forum.Space;
import utils.MyConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SpaceService {
    private final Connection conn;

    public SpaceService() {
        conn = MyConnection.getInstance().getCnx();
    }

    public List<Space> afficher() {
        List<Space> spaces = new ArrayList<>();
        String req = "SELECT * FROM space ORDER BY name ASC";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Space s = new Space();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setDescription(rs.getString("description"));
                spaces.add(s);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching spaces: " + e.getMessage());
        }
        return spaces;
    }
}