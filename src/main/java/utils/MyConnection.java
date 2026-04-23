package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyConnection {
    private final String URL = "jdbc:mysql://localhost:3306/nova_db";
    private final String USER = "root";
    private final String PWD = "";
    private Connection cnx;
    private static MyConnection instance;

    private MyConnection() {
        try {
            cnx = DriverManager.getConnection(URL, USER, PWD); // [cite: 69]
            System.out.println("Database Connection Successful.");
        } catch (SQLException e) {
            System.err.println("Database Connection Failed! ");
            System.err.println(e.getMessage()); // [cite: 71]
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
}