package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.shape.Rectangle;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class EditProfileController implements Initializable {

    // ── Account info ──────────────────────────────────────────────────────────
    @FXML private TextField tfUsername;
    @FXML private TextField tfEmail;
    @FXML private Label     lblRoleDisplay;
    @FXML private Label     lblMessage;
    @FXML private Button    btnSave;
    @FXML private javafx.scene.control.Label lblFaceStatus;

    // ── Password change ───────────────────────────────────────────────────────
    @FXML private PasswordField pfCurrent;
    @FXML private PasswordField pfNew;
    @FXML private PasswordField pfConfirm;
    @FXML private Label         lblPwdMsg;
    @FXML private TextField  tfNewVisible;
    @FXML private Button     btnShowNew;
    @FXML private Rectangle  pwBar1, pwBar2, pwBar3, pwBar4;
    @FXML private Label      lblPwStrength;
    private boolean showingNew = false;

    private User              currentUser;
    private Runnable          onSavedCallback;
    private final UserService userService = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (lblMessage != null) { lblMessage.setVisible(false); lblMessage.setManaged(false); }
        if (lblPwdMsg  != null) { lblPwdMsg.setVisible(false);  lblPwdMsg.setManaged(false); }
        if (pfNew != null) pfNew.textProperty().addListener((obs, old, val) -> updateStrengthMeter(val));
        if (tfNewVisible != null) tfNewVisible.textProperty().addListener((obs, old, val) -> updateStrengthMeter(val));
    }

    public void setCurrentUser(User user, Runnable onSaved) {
        this.currentUser     = user;
        this.onSavedCallback = onSaved;
        populateFields();
    }

    private void populateFields() {
        if (currentUser == null) return;
        tfUsername.setText(currentUser.getUsername());
        tfEmail.setText(currentUser.getEmail());
        lblRoleDisplay.setText(currentUser.getRole().name().replace("ROLE_", ""));
    }

    // ── Save account info ─────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        hideMessage();
        String username = tfUsername.getText().trim();
        String email    = tfEmail.getText().trim();

        List<String> errors = ValidationUtil.validateUser(
            email, username, "placeholder123", currentUser.getRole().name(), false);
        if (!errors.isEmpty()) { showMessage(errors.get(0), true); return; }

        try {
            if (!username.equals(currentUser.getUsername()) && userService.usernameExists(username)) {
                showMessage("Username is already taken.", true); return;
            }
            if (!email.equals(currentUser.getEmail()) && userService.emailExists(email)) {
                showMessage("Email is already registered.", true); return;
            }
            currentUser.setUsername(username);
            currentUser.setEmail(email);
            userService.updateUser(currentUser);
            showMessage("Profile updated successfully!", false);
            if (onSavedCallback != null) onSavedCallback.run();
        } catch (SQLException e) {
            showMessage("Database error: " + e.getMessage(), true);
        }
    }

    // ── Change password ───────────────────────────────────────────────────────

    @FXML
    private void onChangePassword() {
        hidePwdMsg();
        String current = pfCurrent.getText();
        String newPwd  = pfNew.getText();
        String confirm = pfConfirm.getText();

        if (current.isBlank()) { showPwdMsg("Current password is required.", true); return; }

        String storedHash = currentUser.getPassword();
        boolean currentOk;
        if (storedHash != null && storedHash.startsWith("$2")) {
            String hash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
            try   { currentOk = BCrypt.checkpw(current, hash); }
            catch (Exception e) { currentOk = false; }
        } else {
            currentOk = current.equals(storedHash);
        }

        if (!currentOk) { showPwdMsg("Current password is incorrect.", true); return; }

        List<String> errors = ValidationUtil.validateUser(
            currentUser.getEmail(), currentUser.getUsername(),
            newPwd, currentUser.getRole().name(), true);
        if (!errors.isEmpty()) { showPwdMsg(errors.get(0), true); return; }
        if (!newPwd.equals(confirm)) { showPwdMsg("Passwords do not match.", true); return; }

        try {
            currentUser.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(13)));
            userService.updateUser(currentUser);
            pfCurrent.clear(); pfNew.clear(); pfConfirm.clear();
            showPwdMsg("Password updated successfully!", false);
        } catch (SQLException e) {
            showPwdMsg("Database error: " + e.getMessage(), true);
        }
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @FXML
    private void onCancel() {
        navigateBack();
    }

    // ── Delete account ────────────────────────────────────────────────────────

    @FXML
    private void onDeleteAccount() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Are you sure you want to permanently delete your account?\n\nThis action CANNOT be undone.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Account");
        confirm.setHeaderText("Delete Account");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                userService.deleteUser(currentUser.getId());
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/users/login.fxml"));
                Parent root = loader.load();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 1100, 720);
                scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
                javafx.stage.Stage stage = (javafx.stage.Stage) btnSave.getScene().getWindow();
                stage.setTitle("NOVA - Sign In");
                stage.setScene(scene);
                stage.setResizable(false);
                stage.centerOnScreen();
            } catch (Exception e) {
                showMessage("Error deleting account: " + e.getMessage(), true);
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/profile.fxml"));
            Parent root = loader.load();
            ProfileController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser, null, null);
            controllers.NovaDashboardController.setView(root);
        } catch (Exception e) {
            showMessage("Navigation error: " + e.getMessage(), true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showMessage(String msg, boolean isError) {
        lblMessage.setText(msg);
        lblMessage.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;-fx-padding:10 14;-fx-background-radius:8;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;-fx-padding:10 14;-fx-background-radius:8;");
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void hideMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
    }

    private void showPwdMsg(String msg, boolean isError) {
        lblPwdMsg.setText(msg);
        lblPwdMsg.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;-fx-padding:10 14;-fx-background-radius:8;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;-fx-padding:10 14;-fx-background-radius:8;");
        lblPwdMsg.setVisible(true);
        lblPwdMsg.setManaged(true);
    }

    private void hidePwdMsg() {
        lblPwdMsg.setVisible(false);
        lblPwdMsg.setManaged(false);
    }

    @FXML
    private void onSetupFaceLogin() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/users/face-setup.fxml"));
            javafx.scene.Parent root = loader.load();
            controllers.users.FaceSetupController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 560, 520);
            scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Face Login Setup");
            dialog.setScene(scene);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setResizable(false);
            dialog.showAndWait();
            // Refresh face status label after dialog closes
            if (lblFaceStatus != null) {
                boolean hasFace = currentUser.getFaceToken() != null && !currentUser.getFaceToken().isBlank();
                lblFaceStatus.setText(hasFace ? "Enabled" : "Not set up");
                lblFaceStatus.setStyle(hasFace
                    ? "-fx-text-fill:#22c55e;-fx-font-weight:bold;-fx-font-size:12px;"
                    : "-fx-text-fill:#ef4444;-fx-font-weight:bold;-fx-font-size:12px;");
            }
        } catch (Exception e) {
            showMessage("Cannot open face setup: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onToggleNewPassword() {
        showingNew = !showingNew;
        if (showingNew) {
            tfNewVisible.setText(pfNew.getText());
            tfNewVisible.setVisible(true);  tfNewVisible.setManaged(true);
            pfNew.setVisible(false);         pfNew.setManaged(false);
            btnShowNew.setText("Hide");
        } else {
            pfNew.setText(tfNewVisible.getText());
            pfNew.setVisible(true);          pfNew.setManaged(true);
            tfNewVisible.setVisible(false);  tfNewVisible.setManaged(false);
            btnShowNew.setText("Show");
        }
    }

    private void updateStrengthMeter(String password) {
        if (pwBar1 == null) return;
        int score = 0;
        if (password.length() >= 8)                              score++;
        if (password.matches(".*[A-Z].*"))                       score++;
        if (password.matches(".*[0-9].*"))                       score++;
        if (password.matches(".*[!@#%^&*()_+=\\[\\]-].*"))  score++;

        String[] colors = {"#e5e7eb","#e5e7eb","#e5e7eb","#e5e7eb"};
        String label = ""; String labelColor = "#9ca3af";
        switch (score) {
            case 1 -> { colors[0] = "#ef4444"; label = "Weak";   labelColor = "#ef4444"; }
            case 2 -> { colors[0] = "#f59e0b"; colors[1] = "#f59e0b"; label = "Fair";   labelColor = "#f59e0b"; }
            case 3 -> { colors[0] = "#3b82f6"; colors[1] = "#3b82f6"; colors[2] = "#3b82f6"; label = "Good"; labelColor = "#3b82f6"; }
            case 4 -> { colors[0] = "#22c55e"; colors[1] = "#22c55e"; colors[2] = "#22c55e"; colors[3] = "#22c55e"; label = "Strong"; labelColor = "#22c55e"; }
        }
        Rectangle[] bars = {pwBar1, pwBar2, pwBar3, pwBar4};
        for (int i = 0; i < 4; i++) bars[i].setFill(javafx.scene.paint.Color.web(colors[i]));
        if (lblPwStrength != null) {
            lblPwStrength.setText(password.isEmpty() ? "" : label);
            lblPwStrength.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" + labelColor + ";");
        }
    }
}