package controllers.users;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
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
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;

public class SignupController implements Initializable {

    @FXML private StackPane leftPanel;
    @FXML private VBox      rightPanel;
    @FXML private Pane      animatedSceneContainer;

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

        playEntranceAnimation();
        createBackgroundParticles();
    }

    // Exact copy of LoginController animation
    private void createBackgroundParticles() {
        Random random = new Random();
        int particleCount = 20;
        for (int i = 0; i < particleCount; i++) {
            int size = random.nextInt(15) + 5;
            Rectangle rect = new Rectangle(size, size);
            rect.setFill(Color.web("#ffffff", random.nextDouble() * 0.15 + 0.05));
            rect.setX(random.nextInt(420));
            rect.setY(random.nextInt(620));
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
    private void onSignup() {
        lblError.setStyle("-fx-text-fill: #ef4444;");
        lblError.setText("");

        String email       = tfEmail.getText().trim();
        String username    = tfUsername.getText().trim();
        String password    = pfPassword.getText();
        String confirm     = pfConfirm.getText();
        String roleDisplay = cbRole.getValue();
        String role        = "Student".equals(roleDisplay) ? "ROLE_STUDENT" : "ROLE_TUTOR";

        List<String> errors = ValidationUtil.validateUser(email, username, password, role, true);
        if (!password.equals(confirm)) errors.add("Passwords do not match.");

        if (!errors.isEmpty()) {
            lblError.setText(errors.get(0));
            return;
        }

        try {
            if (userService.emailExists(email)) {
                lblError.setText("This email is already registered.");
                return;
            }
            if (userService.usernameExists(username)) {
                lblError.setText("This username is already taken.");
                return;
            }

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

            lblError.setStyle("-fx-text-fill: #16a34a;");
            lblError.setText("Account created! Redirecting to login...");

            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
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
            stage.setTitle("NOVA - Login");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            lblError.setText("Navigation error: " + e.getMessage());
        }
    }
}