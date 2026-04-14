package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import utils.MyConnection;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField     tfUsername;
    @FXML private PasswordField pfPassword;
    @FXML private Label         lblError;
    @FXML private Button        btnLogin;
    @FXML private ProgressIndicator spinner;

    private final Connection conn = MyConnection.getInstance().getCnx();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblError.setText("");
        spinner.setVisible(false);
        spinner.setManaged(false);
        tfUsername.setOnAction(e -> onLogin());
        pfPassword.setOnAction(e -> onLogin());
    }

    @FXML
    private void onLogin() {
        lblError.setText("");
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();

        if (username.isBlank()) { showError("Username is required."); tfUsername.requestFocus(); return; }
        if (password.isBlank()) { showError("Password is required."); pfPassword.requestFocus(); return; }

        spinner.setVisible(true);
        spinner.setManaged(true);
        btnLogin.setDisable(true);

        try {
            User user = authenticate(username, password);
            if (user == null) {
                showError("Invalid username or password.");
                pfPassword.clear();
                pfPassword.requestFocus();
                return;
            }
            if (user.isBanned()) {
                showError("Account banned. Reason: " + (user.getBanReason() != null ? user.getBanReason() : "N/A"));
                return;
            }
            if (!user.isActive()) {
                showError("Account inactive. Contact an administrator.");
                return;
            }
            openUserManagement(user);
        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
        } finally {
            spinner.setVisible(false);
            spinner.setManaged(false);
            btnLogin.setDisable(false);
        }
    }

    private User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT * FROM user WHERE username = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;

            String storedHash = rs.getString("password");
            boolean passwordMatches;

            if (storedHash != null && storedHash.startsWith("$2")) {
                String jbcryptHash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
                try {
                    passwordMatches = BCrypt.checkpw(password, jbcryptHash);
                } catch (Exception e) {
                    passwordMatches = false;
                }
            } else {
                passwordMatches = password.equals(storedHash);
            }

            if (!passwordMatches) return null;

            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setEmail(rs.getString("email"));
            u.setPassword(storedHash);
            u.setRole(User.Role.valueOf(rs.getString("role")));
            u.setActive(rs.getBoolean("is_active"));
            u.setVerified(rs.getBoolean("is_verified"));
            u.setBanned(rs.getBoolean("is_banned"));
            u.setBanReason(rs.getString("ban_reason"));
            u.setXp(rs.getInt("xp"));
            return u;
        }
    }

    private void openUserManagement(User loggedInUser) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/user-list.fxml"));
            Parent root = loader.load();
            UserListController listCtrl = loader.getController();
            listCtrl.setCurrentUser(loggedInUser);
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 740);
            scene.getStylesheets().add(
                getClass().getResource("/css/users.css").toExternalForm());
            stage.setTitle("NOVA - User Management");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Cannot open User Management: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
    }
}