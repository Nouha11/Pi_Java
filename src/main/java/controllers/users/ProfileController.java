package controllers.users;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.users.GravatarService;
import services.users.UserService;
import services.users.ValidationUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ProfileController implements Initializable {

    // ── FXML fields ───────────────────────────────────────────────────────────
    @FXML private Label         lblInitials;
    @FXML private Label         lblFullName;
    @FXML private Label         lblRoleBadge;
    @FXML private Label         lblEmail;
    @FXML private Label         lblStatus;
    @FXML private Label         lblVerified;
    @FXML private Label         lblXp;
    @FXML private Label         lblCreatedAt;
    @FXML private Label         lblGravatarInfo;
    @FXML private Label         lblGravatarStatus;
    @FXML private ImageView     imgAvatar;
    @FXML private StackPane     paneInitials;
    @FXML private Button        btnUploadPic;
    @FXML private Button        btnRemovePic;

    @FXML private PasswordField pfCurrent;
    @FXML private PasswordField pfNew;
    @FXML private PasswordField pfConfirm;
    @FXML private Label         lblPwdMsg;

    // ── Services ──────────────────────────────────────────────────────────────
    private User               currentUser;
    private BorderPane         mainLayout;
    private final UserService     userService     = new UserService();
    private final GravatarService gravatarService = new GravatarService();

    // Folder where uploaded avatars are stored (relative to working dir)
    private static final String UPLOAD_DIR = "uploads/avatars/";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (lblPwdMsg != null) {
            lblPwdMsg.setVisible(false);
            lblPwdMsg.setManaged(false);
        }
        // Ensure upload directory exists
        new File(UPLOAD_DIR).mkdirs();
    }

    public void setCurrentUser(User user, BorderPane layout) {
        this.currentUser = user;
        this.mainLayout  = layout;
        populateProfile();
        loadAvatarAsync();
    }

    // ── Populate static fields ────────────────────────────────────────────────
    private void populateProfile() {
        String username = currentUser.getUsername();
        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.toUpperCase();

        lblInitials.setText(initials);
        lblFullName.setText(username);
        lblEmail.setText(currentUser.getEmail());
        lblRoleBadge.setText(currentUser.getRole().name().replace("ROLE_", ""));
        lblXp.setText(String.valueOf(currentUser.getXp()));
        lblStatus.setText(currentUser.isBanned() ? "Banned"
                        : currentUser.isActive()  ? "Active" : "Inactive");
        lblVerified.setText(currentUser.isVerified() ? "Yes" : "No");

        if (currentUser.getCreatedAt() != null)
            lblCreatedAt.setText(
                currentUser.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        else
            lblCreatedAt.setText("-");

        if (lblGravatarInfo   != null) lblGravatarInfo.setText("Checking...");
        if (lblGravatarStatus != null) lblGravatarStatus.setText("");
    }

    // ── Avatar loading: uploaded pic first, then Gravatar fallback ────────────
    private void loadAvatarAsync() {
        String localPic = currentUser.getProfilePicture();

        if (localPic != null && !localPic.isBlank()) {
            // Load uploaded profile picture
            File f = new File(localPic);
            if (f.exists()) {
                showImageInAvatar(new Image(f.toURI().toString(), 150, 150, true, true));
                if (lblGravatarInfo   != null) lblGravatarInfo.setText("Custom photo");
                if (lblGravatarStatus != null) lblGravatarStatus.setText("Custom photo");
                return;
            }
        }

        // Fallback: load Gravatar asynchronously
        String email = currentUser.getEmail();
        CompletableFuture.supplyAsync(() -> {
            boolean real = gravatarService.hasGravatar(email);
            String  url  = gravatarService.getAvatarUrl(email, 150, "identicon");
            return new Object[]{url, real};
        }).thenAccept(result -> Platform.runLater(() -> {
            String  url  = (String)  result[0];
            boolean real = (Boolean) result[1];
            try {
                Image img = new Image(url, 150, 150, true, true, true);
                img.progressProperty().addListener((obs, old, prog) -> {
                    if (prog.doubleValue() >= 1.0 && !img.isError())
                        showImageInAvatar(img);
                });
                String status = real ? "Gravatar found" : "Generated avatar";
                if (lblGravatarInfo   != null) lblGravatarInfo.setText(status);
                if (lblGravatarStatus != null) lblGravatarStatus.setText(status);
            } catch (Exception ignored) {
                if (lblGravatarInfo != null) lblGravatarInfo.setText("Unavailable");
            }
        }));
    }

    private void showImageInAvatar(Image img) {
        imgAvatar.setImage(img);
        imgAvatar.setVisible(true);
        imgAvatar.setManaged(true);
        paneInitials.setVisible(false);
        paneInitials.setManaged(false);
    }

    // ── Upload profile picture ────────────────────────────────────────────────
    @FXML
    private void onUploadPicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Picture");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        Stage stage = (Stage) btnUploadPic.getScene().getWindow();
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;

        try {
            // Copy file to uploads/avatars/<userId>_<filename>
            String destName = currentUser.getId() + "_" + selected.getName();
            Path dest = Paths.get(UPLOAD_DIR + destName);
            Files.copy(selected.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            // Save path to DB
            String picPath = dest.toString();
            userService.updateProfilePicture(currentUser.getId(), picPath);
            currentUser.setProfilePicture(picPath);

            // Show immediately
            showImageInAvatar(new Image(dest.toUri().toString(), 150, 150, true, true));
            if (lblGravatarInfo   != null) lblGravatarInfo.setText("Custom photo");
            if (lblGravatarStatus != null) lblGravatarStatus.setText("Custom photo");
            showMsg("Profile picture updated!", false);

        } catch (IOException | SQLException e) {
            showMsg("Upload failed: " + e.getMessage(), true);
        }
    }

    // ── Remove profile picture ────────────────────────────────────────────────
    @FXML
    private void onRemovePicture() {
        try {
            // Delete file if it exists
            String pic = currentUser.getProfilePicture();
            if (pic != null) {
                File f = new File(pic);
                if (f.exists()) f.delete();
            }
            userService.updateProfilePicture(currentUser.getId(), null);
            currentUser.setProfilePicture(null);

            // Revert to Gravatar
            imgAvatar.setVisible(false);
            imgAvatar.setManaged(false);
            paneInitials.setVisible(true);
            paneInitials.setManaged(true);
            loadAvatarAsync();
            showMsg("Profile picture removed.", false);
        } catch (SQLException e) {
            showMsg("Error: " + e.getMessage(), true);
        }
    }

    // ── Change password ───────────────────────────────────────────────────────
    @FXML
    private void onChangePassword() {
        hideMsg();
        String current = pfCurrent.getText();
        String newPwd  = pfNew.getText();
        String confirm = pfConfirm.getText();

        if (current.isBlank()) { showMsg("Current password is required.", true); return; }

        String storedHash = currentUser.getPassword();
        boolean currentOk;
        if (storedHash != null && storedHash.startsWith("$2")) {
            String hash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
            try   { currentOk = BCrypt.checkpw(current, hash); }
            catch (Exception e) { currentOk = false; }
        } else {
            currentOk = current.equals(storedHash);
        }

        if (!currentOk) { showMsg("Current password is incorrect.", true); return; }

        List<String> errors = ValidationUtil.validateUser(
            currentUser.getEmail(), currentUser.getUsername(),
            newPwd, currentUser.getRole().name(), true);
        if (!errors.isEmpty()) { showMsg(errors.get(0), true); return; }
        if (!newPwd.equals(confirm)) { showMsg("New passwords do not match.", true); return; }

        try {
            currentUser.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(13)));
            userService.updateUser(currentUser);
            pfCurrent.clear(); pfNew.clear(); pfConfirm.clear();
            showMsg("Password updated successfully.", false);
        } catch (SQLException e) {
            showMsg("Database error: " + e.getMessage(), true);
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    @FXML
    private void onLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 580);
            scene.getStylesheets().add(
                getClass().getResource("/css/login.css").toExternalForm());
            Stage stage = (Stage) lblFullName.getScene().getWindow();
            stage.setTitle("NOVA - Sign In");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            showMsg("Logout error: " + e.getMessage(), true);
        }
    }

    // ── Back navigation ───────────────────────────────────────────────────────
    @FXML
    private void onBackToList() {
        try {
            if (mainLayout != null) {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/users/user-list.fxml"));
                Parent root = loader.load();
                UserListController ctrl = loader.getController();
                ctrl.setCurrentUser(currentUser);
                mainLayout.setCenter(root);
            } else {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/UserStudyDashboard.fxml"));
                controllers.NovaDashboardController.setView(loader.load());
            }
        } catch (Exception e) {
            showMsg("Navigation error: " + e.getMessage(), true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void showMsg(String msg, boolean isError) {
        lblPwdMsg.setText(msg);
        lblPwdMsg.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;-fx-padding:10 14;-fx-background-radius:8;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;-fx-padding:10 14;-fx-background-radius:8;");
        lblPwdMsg.setVisible(true);
        lblPwdMsg.setManaged(true);
    }

    private void hideMsg() {
        lblPwdMsg.setVisible(false);
        lblPwdMsg.setManaged(false);
    }
}
