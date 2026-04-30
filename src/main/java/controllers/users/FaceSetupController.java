package controllers.users;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import models.users.User;
import services.users.FacePlusPlusService;
import services.users.UserService;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FaceSetupController — register/remove face for face login.
 * Opened as a modal dialog from Edit Profile.
 */
public class FaceSetupController implements Initializable {

    @FXML private ImageView         imgWebcam;
    @FXML private Label             lblStatus;
    @FXML private Label             lblCurrentStatus;
    @FXML private Button            btnRegister;
    @FXML private Button            btnRemove;
    @FXML private Button            btnClose;
    @FXML private ProgressIndicator spinner;

    private Webcam                   webcam;
    private ScheduledExecutorService previewExecutor;
    private User                     currentUser;
    private final FacePlusPlusService faceService = new FacePlusPlusService();
    private final UserService         userService  = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
        if (lblStatus != null) lblStatus.setText("");
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        updateStatusLabel();
        if (faceService.isConfigured()) startWebcamPreview();
        else setStatus("Face++ API not configured. Add keys to config.properties.", true);
    }

    private void updateStatusLabel() {
        if (lblCurrentStatus == null) return;
        boolean hasFace = currentUser.getFaceToken() != null && !currentUser.getFaceToken().isBlank();
        lblCurrentStatus.setText(hasFace ? "Face login: ENABLED" : "Face login: NOT SET UP");
        lblCurrentStatus.setStyle(hasFace
            ? "-fx-text-fill:#22c55e;-fx-font-weight:bold;-fx-font-size:13px;"
            : "-fx-text-fill:#ef4444;-fx-font-weight:bold;-fx-font-size:13px;");
        if (btnRemove != null) btnRemove.setDisable(!hasFace);
    }

    private void startWebcamPreview() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) { setStatus("No webcam detected.", true); return; }
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();
            previewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FaceSetupPreview"); t.setDaemon(true); return t;
            });
            previewExecutor.scheduleAtFixedRate(() -> {
                if (webcam.isOpen()) {
                    BufferedImage frame = webcam.getImage();
                    if (frame != null) {
                        Image fxImg = bufferedToFxImage(frame);
                        Platform.runLater(() -> { if (imgWebcam != null) imgWebcam.setImage(fxImg); });
                    }
                }
            }, 0, 66, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            setStatus("Webcam error: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onRegister() {
        if (webcam == null || !webcam.isOpen()) { setStatus("Webcam not available.", true); return; }
        BufferedImage frame = webcam.getImage();
        if (frame == null) { setStatus("Could not capture image.", true); return; }

        btnRegister.setDisable(true);
        spinner.setVisible(true); spinner.setManaged(true);
        setStatus("Detecting face...", false);

        Task<String> task = new Task<>() {
            @Override protected String call() { return faceService.detectFace(frame); }
        };
        task.setOnSucceeded(e -> {
            spinner.setVisible(false); spinner.setManaged(false); btnRegister.setDisable(false);
            String token = task.getValue();
            if (token == null) { setStatus("No face detected. Look directly at the camera.", true); return; }
            try {
                userService.updateFaceToken(currentUser.getId(), token);
                currentUser.setFaceToken(token);
                setStatus("Face registered successfully! You can now use face login.", false);
                updateStatusLabel();
            } catch (Exception ex) { setStatus("DB error: " + ex.getMessage(), true); }
        });
        task.setOnFailed(e -> {
            spinner.setVisible(false); spinner.setManaged(false); btnRegister.setDisable(false);
            setStatus("Error: " + task.getException().getMessage(), true);
        });
        new Thread(task, "FaceRegister").start();
    }

    @FXML
    private void onRemove() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Remove face login? You will need to re-register to use it again.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove Face Login"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    userService.updateFaceToken(currentUser.getId(), null);
                    currentUser.setFaceToken(null);
                    setStatus("Face login removed.", false);
                    updateStatusLabel();
                } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
            }
        });
    }

    @FXML
    private void onClose() {
        stopWebcam();
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    private void stopWebcam() {
        if (previewExecutor != null) previewExecutor.shutdownNow();
        if (webcam != null && webcam.isOpen()) webcam.close();
    }

    private void setStatus(String msg, boolean isError) {
        Platform.runLater(() -> {
            if (lblStatus == null) return;
            lblStatus.setText(msg);
            lblStatus.setStyle(isError ? "-fx-text-fill:#ef4444;-fx-font-weight:bold;" : "-fx-text-fill:#22c55e;-fx-font-weight:bold;");
        });
    }

    private Image bufferedToFxImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        WritableImage fxImg = new WritableImage(w, h);
        PixelWriter pw = fxImg.getPixelWriter();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                pw.setArgb(x, y, img.getRGB(x, y));
        return fxImg;
    }
}
