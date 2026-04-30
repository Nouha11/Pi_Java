package services.api;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * OAuthService — Google & LinkedIn OAuth2 for JavaFX desktop app.
 *
 * Flow:
 *  1. Open browser with authorization URL
 *  2. Local HTTP server on :8080 catches the redirect with auth code
 *  3. Exchange code for access token
 *  4. Fetch user profile (email, name)
 *
 * Uses same credentials as the Symfony project.
 */
public class OAuthService {

    // ── Result model ──────────────────────────────────────────────────────────

    public static class OAuthUser {
        public final String email;
        public final String name;
        public final String provider; // "google" or "linkedin"

        public OAuthUser(String email, String name, String provider) {
            this.email    = email;
            this.name     = name;
            this.provider = provider;
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private final Properties config;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OAuthService() {
        config = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is != null) config.load(is);
        } catch (Exception ignored) {}
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    public void loginWithGoogle(Consumer<OAuthUser> onSuccess, Consumer<String> onError) {
        String clientId    = config.getProperty("GOOGLE_CLIENT_ID", "");
        String redirectUri = config.getProperty("GOOGLE_REDIRECT_URI", "http://localhost:8080/oauth/google/callback");

        String state = UUID.randomUUID().toString();
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri)
            + "&response_type=code"
            + "&scope=" + encode("openid email profile")
            + "&state=" + state
            + "&access_type=offline";

        startCallbackServer("/oauth/google/callback", code -> {
            try {
                String token = exchangeGoogleCode(code, clientId,
                    config.getProperty("GOOGLE_CLIENT_SECRET", ""), redirectUri);
                OAuthUser user = fetchGoogleUser(token);
                onSuccess.accept(user);
            } catch (Exception e) {
                onError.accept("Google login failed: " + e.getMessage());
            }
        }, onError);

        openBrowser(authUrl, onError);
    }

    private String exchangeGoogleCode(String code, String clientId, String secret, String redirectUri) throws Exception {
        String body = "code=" + encode(code)
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(secret)
            + "&redirect_uri=" + encode(redirectUri)
            + "&grant_type=authorization_code";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        String resp = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJson(resp, "access_token");
    }

    private OAuthUser fetchGoogleUser(String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();

        String resp = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        String email = extractJson(resp, "email");
        String name  = extractJson(resp, "name");
        return new OAuthUser(email, name, "google");
    }

    // ── LinkedIn OAuth ────────────────────────────────────────────────────────

    public void loginWithLinkedIn(Consumer<OAuthUser> onSuccess, Consumer<String> onError) {
        String clientId    = config.getProperty("LINKEDIN_CLIENT_ID", "");
        String redirectUri = config.getProperty("LINKEDIN_REDIRECT_URI", "http://localhost:8080/oauth/linkedin/callback");

        String state = UUID.randomUUID().toString();
        String authUrl = "https://www.linkedin.com/oauth/v2/authorization"
            + "?client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri)
            + "&response_type=code"
            + "&scope=" + encode("openid profile email")
            + "&state=" + state;

        startCallbackServer("/oauth/linkedin/callback", code -> {
            try {
                String token = exchangeLinkedInCode(code, clientId,
                    config.getProperty("LINKEDIN_CLIENT_SECRET", ""), redirectUri);
                OAuthUser user = fetchLinkedInUser(token);
                onSuccess.accept(user);
            } catch (Exception e) {
                onError.accept("LinkedIn login failed: " + e.getMessage());
            }
        }, onError);

        openBrowser(authUrl, onError);
    }

    private String exchangeLinkedInCode(String code, String clientId, String secret, String redirectUri) throws Exception {
        String body = "code=" + encode(code)
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(secret)
            + "&redirect_uri=" + encode(redirectUri)
            + "&grant_type=authorization_code";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://www.linkedin.com/oauth/v2/accessToken"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        String resp = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJson(resp, "access_token");
    }

    private OAuthUser fetchLinkedInUser(String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.linkedin.com/v2/userinfo"))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();

        String resp = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        String email = extractJson(resp, "email");
        String name  = extractJson(resp, "name");
        return new OAuthUser(email, name, "linkedin");
    }

    // ── Local callback server ─────────────────────────────────────────────────

    private void startCallbackServer(String path, Consumer<String> onCode, Consumer<String> onError) {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
                server.createContext(path, exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    String code  = extractParam(query, "code");

                    // Send success page to browser
                    String html = "<html><body style='font-family:sans-serif;text-align:center;padding:60px'>"
                        + "<h2 style='color:#22c55e'>Login Successful!</h2>"
                        + "<p>You can close this tab and return to NOVA.</p>"
                        + "</body></html>";
                    byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, resp.length);
                    exchange.getResponseBody().write(resp);
                    exchange.getResponseBody().close();

                    server.stop(0);
                    if (code != null) onCode.accept(code);
                    else onError.accept("No authorization code received.");
                });
                server.start();
            } catch (Exception e) {
                onError.accept("Callback server error: " + e.getMessage());
            }
        }, "OAuthCallbackServer").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void openBrowser(String url, Consumer<String> onError) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                // Fallback for Linux
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            onError.accept("Cannot open browser: " + e.getMessage());
        }
    }

    private String encode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    private String extractJson(String json, String key) {
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
        }
        int end = idx;
        while (end < json.length() && ",}\n".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(idx, end).trim();
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], StandardCharsets.UTF_8); }
                catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }
}
