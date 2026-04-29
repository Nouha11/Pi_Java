package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import models.users.User;
import services.users.TotpService;
import services.users.UserService;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * 2FA Setup screen — shown from profile page.
 * Generates a QR code the user scans with Google Authenticator,
 * then asks them to enter a code to confirm setup.
 */
public class TwoFactorSetupController implements Initializable {

    @FXML private ImageView imgQrCode;
    @FXML private Label     lblSecretKey;
    @FXML private TextField tfVerifyCode;
    @FXML private Label     lblStatus;
    @FXML private Button    btnEnable;
    @FXML private Button    btnDisable;
    @FXML private Button    btnClose;
    @FXML private Label     lblCurrentStatus;

    private User        currentUser;
    private String      pendingSecret;
    private final TotpService  totpService  = new TotpService();
    private final UserService  userService  = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (lblStatus != null) lblStatus.setText("");
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        updateUI();
        if (!user.isTotpEnabled()) {
            generateNewQr();
        }
    }

    private void updateUI() {
        boolean enabled = currentUser.isTotpEnabled();
        if (lblCurrentStatus != null) {
            lblCurrentStatus.setText(enabled ? "2FA is ENABLED" : "2FA is DISABLED");
            lblCurrentStatus.setStyle(enabled
                ? "-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 13px;"
                : "-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 13px;");
        }
        if (btnEnable  != null) btnEnable.setDisable(enabled);
        if (btnDisable != null) btnDisable.setDisable(!enabled);
        if (imgQrCode  != null) imgQrCode.setVisible(!enabled);
        if (lblSecretKey != null) lblSecretKey.setVisible(!enabled);
        if (tfVerifyCode != null) {
            tfVerifyCode.setVisible(!enabled);
            tfVerifyCode.setManaged(!enabled);
        }
    }

    private void generateNewQr() {
        pendingSecret = totpService.generateSecretKey();
        String otpUrl = totpService.getOtpAuthUrl(currentUser.getUsername(), pendingSecret);

        javafx.scene.image.Image qr = totpService.generateQrCodeImage(otpUrl, 200);
        if (qr != null && imgQrCode != null) imgQrCode.setImage(qr);
        if (lblSecretKey != null)
            lblSecretKey.setText("Manual key: " + pendingSecret);
    }

    @FXML
    private void onEnable() {
        if (pendingSecret == null) { setStatus("Generate QR first.", true); return; }
        String codeText = tfVerifyCode.getText().trim();
        if (codeText.isBlank()) { setStatus("Enter the 6-digit code from your app.", true); return; }

        int code;
        try { code = Integer.parseInt(codeText); }
        catch (NumberFormatException e) { setStatus("Code must be 6 digits.", true); return; }

        if (totpService.verifyCode(pendingSecret, code)) {
            try {
                userService.enableTotp(currentUser.getId(), pendingSecret);
                currentUser.setTotpEnabled(true);
                currentUser.setTotpSecret(pendingSecret);
                setStatus("2FA enabled successfully!", false);
                updateUI();
            } catch (SQLException e) {
                setStatus("Database error: " + e.getMessage(), true);
            }
        } else {
            setStatus("Wrong code. Check your authenticator app and try again.", true);
            tfVerifyCode.clear();
        }
    }

    @FXML
    private void onDisable() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Disable 2FA? Your account will be less secure.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Disable 2FA");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    userService.disableTotp(currentUser.getId());
                    currentUser.setTotpEnabled(false);
                    currentUser.setTotpSecret(null);
                    pendingSecret = null;
                    setStatus("2FA disabled.", false);
                    updateUI();
                    generateNewQr();
                } catch (SQLException e) {
                    setStatus("Error: " + e.getMessage(), true);
                }
            }
        });
    }

    @FXML
    private void onClose() {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    private void setStatus(String msg, boolean error) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(error
            ? "-fx-text-fill: #ef4444; -fx-font-weight: bold;"
            : "-fx-text-fill: #22c55e; -fx-font-weight: bold;");
    }
}
