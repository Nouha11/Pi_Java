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
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class SignupController implements Initializable {

    @FXML private TextField     tfEmail;
    @FXML private TextField     tfUsername;
    @FXML private PasswordField pfPassword;
    @FXML private PasswordField pfConfirm;
    @FXML private ComboBox<String> cbRole;
    @FXML private Label         lblError;
    @FXML private Button        btnSignup;

    private final UserService userService = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbRole.getItems().addAll("Student", "Tutor");
        cbRole.setValue("Student");
        lblError.setText("");
    }

    @FXML
    private void onSignup() {
        lblError.setText("");

        String email    = tfEmail.getText().trim();
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();
        String confirm  = pfConfirm.getText();
        String roleDisplay = cbRole.getValue();
        String role = roleDisplay.equals("Student") ? "ROLE_STUDENT" : "ROLE_TUTOR";

        // Validate fields
        List<String> errors = ValidationUtil.validateUser(email, username, password, role, true);

        if (!password.equals(confirm)) {
            errors.add("Passwords do not match.");
        }

        if (!errors.isEmpty()) {
            lblError.setText(errors.get(0));
            return;
        }

        try {
            // Check uniqueness
            if (userService.emailExists(email)) {
                lblError.setText("This email is already registered.");
                return;
            }
            if (userService.usernameExists(username)) {
                lblError.setText("This username is already taken.");
                return;
            }

            // Create user
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername(username);
            newUser.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(13)));
            newUser.setRole(User.Role.valueOf(role));
            newUser.setActive(true);
            newUser.setVerified(false);
            newUser.setBanned(false);
            newUser.setXp(0);

            userService.addUser(newUser);

            // Show success then go back to login
            lblError.setStyle("-fx-text-fill:#059669;");
            lblError.setText("Account created! Redirecting to login...");

            // Navigate to login after short delay
            javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pause.setOnFinished(e -> goToLogin());
            pause.play();

        } catch (SQLException e) {
            lblError.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void onGoToLogin() {
        goToLogin();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnSignup.getScene().getWindow();
            Scene scene = new Scene(root, 900, 580);
            scene.getStylesheets().add(
                getClass().getResource("/css/login.css").toExternalForm());
            stage.setTitle("NOVA - Sign In");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            lblError.setText("Navigation error: " + e.getMessage());
        }
    }
}
