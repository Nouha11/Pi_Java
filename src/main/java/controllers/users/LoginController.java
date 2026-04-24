package controllers.users;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import utils.MyConnection;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private StackPane leftPanel;
    @FXML private VBox rightPanel;
    @FXML private Pane animatedSceneContainer;

    @FXML private TextField      tfUsername;
    @FXML private PasswordField  pfPassword;
    @FXML private Label          lblError;
    @FXML private Button         btnLogin;
    @FXML private ProgressIndicator spinner;

    private final Connection conn = MyConnection.getInstance().getCnx();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblError.setText("");
        spinner.setVisible(false);
        spinner.setManaged(false);
        tfUsername.setOnAction(e -> onLogin());
        pfPassword.setOnAction(e -> onLogin());

        playEntranceAnimation();
        createBackgroundParticles();
    }

    private void createBackgroundParticles() {
        Random random = new Random();
        int particleCount = 20;

        for (int i = 0; i < particleCount; i++) {
            int size = random.nextInt(15) + 5;
            Rectangle rect = new Rectangle(size, size);

            rect.setFill(Color.web("#ffffff", random.nextDouble() * 0.15 + 0.05));

            rect.setX(random.nextInt(420));
            rect.setY(random.nextInt(580));
            rect.setRotate(random.nextInt(360));

            animatedSceneContainer.getChildren().add(rect);

            TranslateTransition tt = new TranslateTransition(Duration.seconds(random.nextInt(15) + 15), rect);
            tt.setByY(-150 - random.nextInt(200));
            tt.setByX((random.nextDouble() - 0.5) * 100);
            tt.setCycleCount(TranslateTransition.INDEFINITE);
            tt.setAutoReverse(true);
            tt.play();

            RotateTransition rt = new RotateTransition(Duration.seconds(random.nextInt(10) + 10), rect);
            rt.setByAngle(360);
            rt.setCycleCount(RotateTransition.INDEFINITE);
            rt.setAutoReverse(false);
            rt.play();

            FadeTransition ft = new FadeTransition(Duration.seconds(random.nextInt(8) + 5), rect);
            ft.setFromValue(0.1);
            ft.setToValue(0.6);
            ft.setCycleCount(FadeTransition.INDEFINITE);
            ft.setAutoReverse(true);
            ft.play();
        }
    }

    private void playEntranceAnimation() {
        leftPanel.setOpacity(0);
        rightPanel.setOpacity(0);

        TranslateTransition slideRight = new TranslateTransition(Duration.millis(600), rightPanel);
        slideRight.setFromY(30);
        slideRight.setToY(0);

        FadeTransition fadeRight = new FadeTransition(Duration.millis(600), rightPanel);
        fadeRight.setFromValue(0);
        fadeRight.setToValue(1);

        FadeTransition fadeLeft = new FadeTransition(Duration.millis(800), leftPanel);
        fadeLeft.setFromValue(0);
        fadeLeft.setToValue(1);

        slideRight.play();
        fadeRight.play();
        fadeLeft.play();
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
            if (user == null) { showError("Invalid username or password."); pfPassword.clear(); pfPassword.requestFocus(); return; }
            if (user.isBanned()) { showError("Account banned. Reason: " + (user.getBanReason() != null ? user.getBanReason() : "N/A")); return; }
            if (!user.isActive()) { showError("Account inactive. Contact an administrator."); return; }

            // 🔥 NEW: Save the user globally in the session
            utils.UserSession.getInstance().setLoggedInUser(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());

            routeUserBasedOnRole(user);
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
                try { passwordMatches = BCrypt.checkpw(password, jbcryptHash); }
                catch (Exception e) { passwordMatches = false; }
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

    private void routeUserBasedOnRole(User loggedInUser) {
        try {
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            FXMLLoader loader;
            Parent root;
            Scene scene;

            if (loggedInUser.getRole() == User.Role.ROLE_ADMIN) {
                loader = new FXMLLoader(getClass().getResource("/views/admin/AdminDashboard.fxml"));
                root = loader.load();
                controllers.admin.AdminDashboardController adminCtrl = loader.getController();
                adminCtrl.setCurrentUser(loggedInUser);
                scene = new Scene(root, 1280, 800);
                scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
                stage.setTitle("NOVA - Admin Dashboard");
            } else {
                loader = new FXMLLoader(getClass().getResource("/views/NovaDashboard.fxml"));
                root = loader.load();
                controllers.NovaDashboardController dashCtrl = loader.getController();
                dashCtrl.setCurrentUser(loggedInUser);
                scene = new Scene(root, 1300, 800);
                stage.setTitle("NOVA - Student Hub");
            }

            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Cannot load dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void onGoToSignup() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/users/signup.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) btnLogin.getScene().getWindow();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 620);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setTitle("NOVA - Create Account");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Cannot open signup: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
    }
}