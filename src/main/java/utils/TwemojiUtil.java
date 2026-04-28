package utils;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.concurrent.CompletableFuture;

/**
 * Utility for loading Twemoji (Twitter Emoji) images from the CDN.
 *
 * CDN pattern:
 *   https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/{codepoint}.png
 *
 * Usage:
 *   StackPane icon = TwemojiUtil.circle("🧠", 56, "#f3e5f5");
 */
public class TwemojiUtil {

    private static final String CDN =
        "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/";

    /**
     * Convert an emoji string to its Twemoji CDN URL.
     * Handles single codepoint and ZWJ sequences.
     */
    public static String toUrl(String emoji) {
        if (emoji == null || emoji.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int[] codePoints = emoji.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int cp = codePoints[i];
            // Skip variation selector U+FE0F
            if (cp == 0xFE0F) continue;
            if (sb.length() > 0) sb.append("-");
            sb.append(Integer.toHexString(cp));
        }
        return CDN + sb.toString() + ".png";
    }

    /**
     * Create a circular StackPane with a Twemoji image loaded asynchronously.
     * Shows a colored background immediately; image appears when loaded.
     *
     * @param emoji      the emoji character(s)
     * @param size       diameter of the circle in pixels
     * @param bgColor    CSS background color (e.g. "#f3e5f5" or gradient)
     * @param imageSize  size of the emoji image inside the circle
     */
    public static StackPane circle(String emoji, double size, String bgColor, double imageSize) {
        StackPane pane = new StackPane();
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        pane.setStyle("-fx-background-color:" + bgColor + ";-fx-background-radius:50;");

        // Show emoji as text immediately (instant, no network needed)
        javafx.scene.control.Label fallback = new javafx.scene.control.Label(emoji);
        fallback.setStyle("-fx-font-size:" + (int)(imageSize * 0.75) + "px;");
        pane.getChildren().add(fallback);

        // Try to load Twemoji image — replace text if successful
        String url = toUrl(emoji);
        if (url != null) {
            ImageView iv = new ImageView();
            iv.setFitWidth(imageSize);
            iv.setFitHeight(imageSize);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);

            CompletableFuture.supplyAsync(() -> {
                try {
                    Image img = new Image(url, imageSize, imageSize, true, true, false);
                    // Wait for image to finish loading
                    long start = System.currentTimeMillis();
                    while (img.getProgress() < 1.0 && !img.isError() && System.currentTimeMillis() - start < 5000) {
                        Thread.sleep(50);
                    }
                    return img.isError() ? null : img;
                } catch (Exception e) {
                    return null;
                }
            }).thenAccept(img -> Platform.runLater(() -> {
                if (img != null && !img.isError()) {
                    iv.setImage(img);
                    pane.getChildren().setAll(iv); // replace text with image
                }
                // else: keep the text fallback
            }));
        }

        return pane;
    }

    /** Convenience overload — image fills 60% of the circle. */
    public static StackPane circle(String emoji, double size, String bgColor) {
        return circle(emoji, size, bgColor, size * 0.60);
    }

    // ── Pre-defined emoji constants for gamification ──────────────────────────

    // Game types
    public static final String PUZZLE  = "\uD83E\uDDE9"; // 🧩
    public static final String MEMORY  = "\uD83E\uDDE0"; // 🧠
    public static final String TRIVIA  = "\u2753";        // ❓
    public static final String ARCADE  = "\uD83D\uDD79";  // 🕹

    // Rewards
    public static final String TROPHY      = "\uD83C\uDFC6"; // 🏆
    public static final String MEDAL       = "\uD83C\uDFC5"; // 🏅
    public static final String STAR        = "\u2B50";        // ⭐
    public static final String COIN        = "\uD83E\uDE99"; // 🪙
    public static final String GIFT        = "\uD83C\uDF81"; // 🎁

    // Mini games / wellness
    public static final String WIND        = "\uD83D\uDCA8"; // 💨
    public static final String DUMBBELL    = "\uD83C\uDFCB"; // 🏋
    public static final String EYE         = "\uD83D\uDC41"; // 👁
    public static final String WATER       = "\uD83D\uDCA7"; // 💧

    // Results
    public static final String WIN         = "\uD83C\uDFC6"; // 🏆
    public static final String LOSE        = "\uD83D\uDE14"; // 😔

    // Stats / misc
    public static final String BOLT        = "\u26A1";        // ⚡
    public static final String HEART       = "\u2764";        // ❤
    public static final String HEART_EMPTY = "\uD83E\uDD0D"; // 🤍
    public static final String PLAY        = "\u25B6";        // ▶
    public static final String GAMEPAD     = "\uD83C\uDFAE"; // 🎮
}
