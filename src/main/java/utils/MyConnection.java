package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class MyConnection {
    private final String URL  = "jdbc:mysql://localhost:3306/nova_db";
    private final String USER = "root";
    private final String PWD  = "";
    private Connection cnx;
    private static MyConnection instance;

    private MyConnection() {
        try {
            cnx = DriverManager.getConnection(URL, USER, PWD);
            System.out.println("Database Connection Successful.");
            runMigrations();
        } catch (SQLException e) {
            System.err.println("Database Connection Failed!");
            System.err.println(e.getMessage());
        }
    }

    public static MyConnection getInstance() {
        if (instance == null) {
            instance = new MyConnection();
        }
        return instance;
    }

    public Connection getCnx() {
        return cnx;
    }

    /**
     * Auto-migration: adds columns that may be missing on teammates' databases.
     * Uses ALTER TABLE ... ADD COLUMN IF NOT EXISTS — safe to run every startup.
     */
    private void runMigrations() {
        if (cnx == null) return;
        String[] migrations = {
            "ALTER TABLE user ADD COLUMN IF NOT EXISTS profile_picture VARCHAR(500) NULL DEFAULT NULL",
            "ALTER TABLE user ADD COLUMN IF NOT EXISTS totp_enabled TINYINT(1) NOT NULL DEFAULT 0",
            "ALTER TABLE user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255) NULL DEFAULT NULL"
        };
        try (Statement st = cnx.createStatement()) {
            for (String sql : migrations) {
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    // Column may already exist on older MySQL — ignore
                    System.out.println("[Migration] Skipped: " + e.getMessage());
                }
            }
            System.out.println("[Migration] Schema up to date.");
        } catch (SQLException e) {
            System.err.println("[Migration] Failed: " + e.getMessage());
        }
    }
}
