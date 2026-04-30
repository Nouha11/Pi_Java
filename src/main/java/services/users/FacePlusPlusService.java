package services.users;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.imageio.ImageIO;

/**
 * FacePlusPlusService — Face++ REST API (faceplusplus.com)
 * Free tier: 1000 API calls/month, no credit card required.
 * Docs: https://console.faceplusplus.com/documents/5679127
 */
public class FacePlusPlusService {

    private static final String DETECT_URL  = "https://api-us.faceplusplus.com/facepp/v3/detect";
    private static final String COMPARE_URL = "https://api-us.faceplusplus.com/facepp/v3/compare";

    private final String apiKey;
    private final String apiSecret;

    public FacePlusPlusService() {
        Properties props = loadConfig();
        this.apiKey    = props.getProperty("FACEPP_API_KEY",    "");
        this.apiSecret = props.getProperty("FACEPP_API_SECRET", "");
    }

    // ── Detect face and return face_token ─────────────────────────────────────

    public String detectFace(BufferedImage image) {
        try {
            byte[] imageBytes = toJpegBytes(image);
            String boundary   = "----FacePPBoundary" + System.currentTimeMillis();
            HttpURLConnection conn = openConnection(DETECT_URL);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = conn.getOutputStream()) {
                writeField(out, boundary, "api_key",    apiKey);
                writeField(out, boundary, "api_secret", apiSecret);
                writeFile(out,  boundary, "image_file", "face.jpg", imageBytes);
                out.write(("--" + boundary + "--\r\n").getBytes());
            }
            String response = readResponse(conn);
            return extractField(response, "face_token");
        } catch (Exception e) {
            System.err.println("[FacePP] detectFace error: " + e.getMessage());
            return null;
        }
    }

    // ── Compare face token against live image ─────────────────────────────────

    public double compareFace(String storedFaceToken, BufferedImage liveImage) {
        try {
            byte[] imageBytes = toJpegBytes(liveImage);
            String boundary   = "----FacePPBoundary" + System.currentTimeMillis();
            HttpURLConnection conn = openConnection(COMPARE_URL);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = conn.getOutputStream()) {
                writeField(out, boundary, "api_key",     apiKey);
                writeField(out, boundary, "api_secret",  apiSecret);
                writeField(out, boundary, "face_token1", storedFaceToken);
                writeFile(out,  boundary, "image_file2", "live.jpg", imageBytes);
                out.write(("--" + boundary + "--\r\n").getBytes());
            }
            String response = readResponse(conn);
            String conf = extractField(response, "confidence");
            if (conf == null) return -1;
            return Double.parseDouble(conf);
        } catch (Exception e) {
            System.err.println("[FacePP] compareFace error: " + e.getMessage());
            return -1;
        }
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.equals("YOUR_FACEPP_API_KEY");
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        return conn;
    }

    private void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes());
    }

    private void writeFile(OutputStream out, String boundary, String name, String filename, byte[] data) throws IOException {
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + filename + "\"\r\nContent-Type: image/jpeg\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes());
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String extractField(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (idx >= json.length()) return null;
        char first = json.charAt(idx);
        if (first == '"') {
            int end = json.indexOf('"', idx + 1);
            return end > idx ? json.substring(idx + 1, end) : null;
        } else {
            int end = idx;
            while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(idx, end).trim();
        }
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private Properties loadConfig() {
        Properties p = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) p.load(is);
        } catch (Exception ignored) {}
        return p;
    }
}
