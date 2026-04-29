package services.users;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * TotpService — Google Authenticator / TOTP (RFC 6238)
 * Uses com.warrenstrange:googleauth for TOTP and ZXing for QR codes.
 * No API key required — fully offline.
 */
public class TotpService {

    private static final String ISSUER = "NOVA Platform";
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    // ── Key generation ────────────────────────────────────────────────────────

    public String generateSecretKey() {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    public String getOtpAuthUrl(String username, String secret) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(ISSUER, username,
            new GoogleAuthenticatorKey.Builder(secret).build());
    }

    // ── QR code generation (no SwingFXUtils — pure pixel copy) ───────────────

    public Image generateQrCodeImage(String otpAuthUrl, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
            return bufferedToFxImage(buffered);
        } catch (WriterException e) {
            System.err.println("[TotpService] QR generation failed: " + e.getMessage());
            return null;
        }
    }

    /** Convert BufferedImage to JavaFX Image without SwingFXUtils */
    private Image bufferedToFxImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        WritableImage fxImg = new WritableImage(w, h);
        PixelWriter pw = fxImg.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setArgb(x, y, img.getRGB(x, y));
            }
        }
        return fxImg;
    }

    // ── Verification ──────────────────────────────────────────────────────────

    public boolean verifyCode(String secret, int code) {
        try {
            return gAuth.authorize(secret, code);
        } catch (Exception e) {
            return false;
        }
    }
}
