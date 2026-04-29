package controllers.users;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
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
    @FXML private VBox      rightPanel;
    @FXML private Pane      animatedSceneContainer;

    @FXML private TextField         tfUsername;
    @FXML private PasswordField     pfPassword;
    @FXML private Label             lblError;
    @FXML private Button            btnLogin;
    @FXML private ProgressIndicator spinner;

    // ── reCAPTCHA-style checkbox ──────────────────────────────────────────────
    @FXML private HBox      captchaBox;
    @FXML private StackPane captchaCheckPane;
    @FXML private Label     lblCaptchaCheck;
    @FXML private Label     lblCaptchaStatus;

    // Behavioral analysis
    private boolean captchaVerified  = false;
    private boolean captchaChecking  = false;
    private int     mouseMoveCount   = 0;
    private long    pageLoadTime     = 0;

    private final Connection conn = MyConnection.getInstance().getCnx();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblError.setText("");
        spinner.setVisible(false);
        spinner.setManaged(false);
        pageLoadTime = System.currentTimeMillis();

        tfUsername.setOnAction(e -> onLogin());
        pfPassword.setOnAction(e -> onLogin());

        playEntranceAnimation();
        createBackgroundParticles();
    }

    // ── reCAPTCHA checkbox behavior ───────────────────────────────────────────

    @FXML
    private void onCaptchaMouseMoved(MouseEvent e) {
        if (!captchaVerified) mouseMoveCount++;
    }

    @FXML
    private void onCaptchaClick(MouseEvent e) {
        if (captchaVerified || captchaChecking) return;
        captchaChecking = true;

        // Show spinner animation inside checkbox
        lblCaptchaCheck.setText("⟳");
        captchaCheckPane.setStyle(
            "-fx-background-color: white; -fx-border-color: #1a73e8; " +
            "-fx-border-radius: 3; -fx-border-width: 2; -fx-cursor: hand; -fx-background-radius: 3;");

        // Spin animation
        Timeline spin = new Timeline(
            new KeyFrame(Duration.ZERO,       new KeyValue(lblCaptchaCheck.rotateProperty(), 0)),
            new KeyFrame(Duration.millis(600), new KeyValue(lblCaptchaCheck.rotateProperty(), 360))
        );
        spin.setCycleCount(2);

        long timeSinceLoad = System.currentTimeMillis() - pageLoadTime;
        boolean looksHuman = mouseMoveCount >= 3 && timeSinceLoad > 1500;

        spin.setOnFinished(ev -> {
            if (looksHuman) {
                // ✓ Verified
                captchaVerified = true;
                captchaChecking = false;
                lblCaptchaCheck.setRotate(0);
                lblCaptchaCheck.setText("✓");
                lblCaptchaCheck.setStyle("-fx-font-size: 16px; -fx-text-fill: #1a73e8; -fx-font-weight: bold;");
                captchaCheckPane.setStyle(
                    "-fx-background-color: white; -fx-border-color: #1a73e8; " +
                    "-fx-border-radius: 3; -fx-border-width: 2; -fx-background-radius: 3;");
                if (lblCaptchaStatus != null)
                    lblCaptchaStatus.setText("Verified");
            } else {
                // Not enough human signals — reset and ask to try again
                captchaChecking = false;
                lblCaptchaCheck.setRotate(0);
                lblCaptchaCheck.setText("");
                captchaCheckPane.setStyle(
                    "-fx-background-color: white; -fx-border-color: #9ca3af; " +
                    "-fx-border-radius: 3; -fx-border-width: 2; -fx-cursor: hand; -fx-background-radius: 3;");
                if (lblCaptchaStatus != null)
                    lblCaptchaStatus.setText("Please move your mouse first");
                mouseMoveCount = 0;
            }
        });
        spin.play();
    }

    private boolean validateCaptcha() {
        if (captchaVerified) return true;
        showError("Please complete the CAPTCHA.");
        // Highlight the checkbox
        if (captchaCheckPane != null) {
            captchaCheckPane.setStyle(
                "-fx-background-color: #fff8f8; -fx-border-color: #ef4444; " +
                "-fx-border-radius: 3; -fx-border-width: 2; -fx-cursor: hand; -fx-background-radius: 3;");
        }
        return false;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @FXML
    private void onLogin() {
        lblError.setText("");
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();

        if (username.isBlank()) { showError("Username is required."); tfUsername.requestFocus(); return; }
        if (password.isBlank()) { showError("Password is required."); pfPassword.requestFocus(); return; }
        if (!validateCaptcha()) return;

        spinner.setVisible(true);
        spinner.setManaged(true);
        btnLogin.setDisable(true);

        try {
            User user = authenticate(username, password);
            if (user == null) { showError("Invalid username or password."); pfPassword.clear(); pfPassword.requestFocus(); return; }
            if (user.isBanned()) { showError("Account banned. Reason: " + (user.getBanReason() != null ? user.getBanReason() : "N/A")); return; }
            if (!user.isActive()) { showError("Account inactive. Contact an administrator."); return; }

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

    // ── Auth ──────────────────────────────────────────────────────────────────

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
                try   { passwordMatches = BCrypt.checkpw(password, jbcryptHash); }
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
            try { u.setProfilePicture(rs.getString("profile_picture")); } catch (Exception ignored) {}
            try { u.setTotpEnabled(rs.getBoolean("totp_enabled")); }     catch (Exception ignored) {}
            try { u.setTotpSecret(rs.getString("totp_secret")); }        catch (Exception ignored) {}
            return u;
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

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
                stage.setTitle(loggedInUser.getRole() == User.Role.ROLE_TUTOR
                    ? "NOVA - Tutor Hub" : "NOVA - Student Hub");
            }
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Cannot load dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void createBackgroundParticles() {
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
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
        slideRight.setFromY(30); slideRight.setToY(0);
        FadeTransition fadeRight = new FadeTransition(Duration.millis(600), rightPanel);
        fadeRight.setFromValue(0); fadeRight.setToValue(1);
        FadeTransition fadeLeft = new FadeTransition(Duration.millis(800), leftPanel);
        fadeLeft.setFromValue(0); fadeLeft.setToValue(1);
        slideRight.play(); fadeRight.play(); fadeLeft.play();
    }

    // ── Signup navigation ─────────────────────────────────────────────────────

    @FXML
    private void onGoToSignup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/signup.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setTitle("NOVA - Create Account");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Cannot open signup: " + e.getMessage());
        }
    }

    private void showError(String msg) { lblError.setText(msg); }

    private void showTwoFactorVerify(User user) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/users/2fa-verify.fxml"));
            javafx.scene.Parent root = loader.load();
            TwoFactorVerifyController ctrl = loader.getController();
            ctrl.setup(user, this::routeUserBasedOnRole);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 420, 380);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            javafx.stage.Stage stage = (javafx.stage.Stage) btnLogin.getScene().getWindow();
            stage.setTitle("NOVA - Two-Factor Authentication");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("2FA error: " + e.getMessage());
        }
    }

    @FXML
    private void onFaceLogin() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) btnLogin.getScene().getWindow();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/users/face-login.fxml"));
            javafx.scene.Parent root = loader.load();
            FaceLoginController ctrl = loader.getController();
            // Pass the stage so routing works after scene switch
            ctrl.setStageAndSuccess(stage, user -> routeUserBasedOnRole(user, stage));
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 640, 560);
            stage.setTitle("NOVA - Face Login");
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Cannot open face login: " + e.getMessage());
        }
    }

    /** Overload used by face login — accepts an explicit stage */
    private void routeUserBasedOnRole(User loggedInUser, javafx.stage.Stage stage) {
        try {
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
                stage.setTitle(loggedInUser.getRole() == User.Role.ROLE_TUTOR
                    ? "NOVA - Tutor Hub" : "NOVA - Student Hub");
            }
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (java.io.IOException e) {
            showError("Cannot load dashboard: " + e.getMessage());
        }
    }
}