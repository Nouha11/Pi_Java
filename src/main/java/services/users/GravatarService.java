package services.users;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * GravatarService — Gravatar API (gravatar.com)
 * No API key required. Avatar URL is built from MD5 hash of the user email.
 * Docs: https://docs.gravatar.com/api/avatars/images/
 */
public class GravatarService {

    private static final String BASE_URL = "https://www.gravatar.com/avatar/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Returns the Gravatar image URL for a given email.
     *
     * @param email    user email (trimmed + lowercased before hashing)
     * @param size     pixel size 1-2048
     * @param fallback "identicon" | "mp" | "retro" | "robohash" | "404"
     */
    public String getAvatarUrl(String email, int size, String fallback) {
        String hash = md5(email.trim().toLowerCase());
        size = Math.max(1, Math.min(2048, size));
        return BASE_URL + hash + "?s=" + size + "&d=" + fallback + "&r=g";
    }

    /** Convenience: 150px identicon fallback */
    public String getAvatarUrl(String email) {
        return getAvatarUrl(email, 150, "identicon");
    }

    /**
     * Returns true if the user has a real Gravatar registered.
     * Uses d=404 — Gravatar returns HTTP 404 when no image exists.
     */
    public boolean hasGravatar(String email) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(getAvatarUrl(email, 80, "404")))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** MD5 hex digest — required by Gravatar spec */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 unavailable", e);
        }
    }
}