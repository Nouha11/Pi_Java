package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.users.User;
import services.users.TotpService;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * 2FA verification screen — shown after successful password login
 * when the user has 2FA enabled.
 */
public class TwoFactorVerifyController implements Initializable {

    @FXML private TextField tfCode;
    @FXML private Label     lblError;
    @FXML private Button    btnVerify;
    @FXML private Label     lblUsername;

    private User             currentUser;
    private Consumer<User>   onSuccess;
    private final TotpService totpService = new TotpService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (lblError != null) lblError.setText("");
        if (tfCode   != null) tfCode.setOnAction(e -> onVerify());
    }

    public void setup(User user, Consumer<User> onSuccessCallback) {
        this.currentUser = user;
        this.onSuccess   = onSuccessCallback;
        if (lblUsername != null)
            lblUsername.setText("Welcome, " + user.getUsername() + "!");
    }

    @FXML
    private void onVerify() {
        String codeText = tfCode.getText().trim();
        if (codeText.isBlank()) { showError("Enter the 6-digit code."); return; }

        int code;
        try { code = Integer.parseInt(codeText); }
        catch (NumberFormatException e) { showError("Code must be 6 digits."); return; }

        if (totpService.verifyCode(currentUser.getTotpSecret(), code)) {
            // Success — call the routing callback
            if (onSuccess != null) onSuccess.accept(currentUser);
        } else {
            showError("Invalid code. Try again.");
            tfCode.clear();
            tfCode.requestFocus();
        }
    }

    private void showError(String msg) {
        if (lblError != null) lblError.setText(msg);
    }
}
