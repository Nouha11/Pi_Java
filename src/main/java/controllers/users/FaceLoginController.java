package controllers.users;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.users.User;
import services.users.FacePlusPlusService;
import services.users.UserService;
import utils.UserSession;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * FaceLoginController — webcam face recognition login.
 * Uses Face++ API to compare live webcam frame against stored face_token.
 */
public class FaceLoginController implements Initializable {

    @FXML private ImageView     imgWebcam;
    @FXML private Label         lblStatus;
    @FXML private Button        btnScan;
    @FXML private Button        btnCancel;
    @FXML private ProgressIndicator spinner;
    @FXML private VBox          paneNotConfigured;

    private Webcam                   webcam;
    private ScheduledExecutorService previewExecutor;
    private final FacePlusPlusService faceService = new FacePlusPlusService();
    private final UserService         userService  = new UserService();

    // Callback: called with the authenticated user on success
    private Consumer<User> onSuccess;
    private javafx.stage.Stage ownerStage;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
        if (lblStatus != null) lblStatus.setText("");

        if (!faceService.isConfigured()) {
            if (paneNotConfigured != null) { paneNotConfigured.setVisible(true); paneNotConfigured.setManaged(true); }
            if (btnScan != null) btnScan.setDisable(true);
            return;
        }

        startWebcamPreview();
    }

    public void setOnSuccess(Consumer<User> callback) {
        this.onSuccess = callback;
    }

    public void setStageAndSuccess(javafx.stage.Stage stage, Consumer<User> callback) {
        this.ownerStage = stage;
        this.onSuccess  = callback;
    }

    // ── Webcam preview ────────────────────────────────────────────────────────

    private void startWebcamPreview() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                setStatus("No webcam detected.", true);
                if (btnScan != null) btnScan.setDisable(true);
                return;
            }
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();

            previewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WebcamPreview");
                t.setDaemon(true);
                return t;
            });
            previewExecutor.scheduleAtFixedRate(() -> {
                if (webcam.isOpen()) {
                    BufferedImage frame = webcam.getImage();
                    if (frame != null) {
                        Image fxImg = bufferedToFxImage(frame);
                        Platform.runLater(() -> { if (imgWebcam != null) imgWebcam.setImage(fxImg); });
                    }
                }
            }, 0, 66, TimeUnit.MILLISECONDS); // ~15fps

        } catch (Exception e) {
            setStatus("Webcam error: " + e.getMessage(), true);
        }
    }

    // ── Scan face ─────────────────────────────────────────────────────────────

    @FXML
    private void onScan() {
        if (webcam == null || !webcam.isOpen()) {
            setStatus("Webcam not available.", true);
            return;
        }

        btnScan.setDisable(true);
        spinner.setVisible(true);
        spinner.setManaged(true);
        setStatus("Scanning face...", false);

        BufferedImage frame = webcam.getImage();
        if (frame == null) {
            setStatus("Could not capture image.", true);
            btnScan.setDisable(false);
            spinner.setVisible(false);
            return;
        }

        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                // Detect face in live frame
                String liveFaceToken = faceService.detectFace(frame);
                if (liveFaceToken == null) {
                    updateMessage("No face detected. Please look at the camera.");
                    return null;
                }

                // Find matching user by comparing against all stored face tokens
                List<User> users = userService.getAllUsers();
                for (User u : users) {
                    if (u.getFaceToken() == null || u.getFaceToken().isBlank()) continue;
                    double confidence = faceService.compareFace(u.getFaceToken(), frame);
                    if (confidence >= 76.0) {
                        return u;
                    }
                }
                updateMessage("Face not recognized. Please try again or use password login.");
                return null;
            }
        };

        task.messageProperty().addListener((obs, old, msg) ->
            Platform.runLater(() -> setStatus(msg, true)));

        task.setOnSucceeded(e -> {
            User user = task.getValue();
            spinner.setVisible(false);
            spinner.setManaged(false);
            btnScan.setDisable(false);

            if (user != null) {
                if (user.isBanned()) { setStatus("Account banned.", true); return; }
                if (!user.isActive()) { setStatus("Account inactive.", true); return; }
                setStatus("Face recognized! Welcome, " + user.getUsername() + "!", false);
                UserSession.getInstance().setLoggedInUser(
                    user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
                stopWebcam();
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(user));
                }
            }
        });

        task.setOnFailed(e -> {
            spinner.setVisible(false);
            spinner.setManaged(false);
            btnScan.setDisable(false);
            setStatus("Error: " + task.getException().getMessage(), true);
        });

        new Thread(task, "FaceScan").start();
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @FXML
    private void onCancel() {
        stopWebcam();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            Stage stage = ownerStage != null ? ownerStage : (Stage) btnCancel.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void stopWebcam() {
        if (previewExecutor != null) previewExecutor.shutdownNow();
        if (webcam != null && webcam.isOpen()) webcam.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
        Platform.runLater(() -> {
            if (lblStatus == null) return;
            lblStatus.setText(msg);
            lblStatus.setStyle(isError
                ? "-fx-text-fill:#ef4444;-fx-font-weight:bold;"
                : "-fx-text-fill:#22c55e;-fx-font-weight:bold;");
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
