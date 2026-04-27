package utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for consistent emoji rendering across all platforms using Twemoji.
 * 
 * <p>This ensures emojis appear the same way for all users, regardless of their
 * operating system or font support. Uses Twitter's Twemoji CDN for emoji images.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>
 * // Get emoji as ImageView for JavaFX
 * ImageView emojiView = EmojiUtil.getEmojiImage("📚", 16);
 * 
 * // Get emoji as text (fallback to Unicode)
 * String emoji = EmojiUtil.getEmoji("📚");
 * </pre>
 */
public class EmojiUtil {

    /** Twemoji CDN base URL */
    private static final String TWEMOJI_CDN = "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/";
    
    /** Cache for loaded emoji images */
    private static final Map<String, Image> imageCache = new HashMap<>();
    
    /** Mapping of common emojis to their Unicode codepoints */
    private static final Map<String, String> EMOJI_MAP = new HashMap<>();
    
    static {
        // Study/Education emojis
        EMOJI_MAP.put("📚", "1f4da");  // Books
        EMOJI_MAP.put("📖", "1f4d6");  // Open book
        EMOJI_MAP.put("📝", "1f4dd");  // Memo
        EMOJI_MAP.put("📘", "1f4d8");  // Blue book
        EMOJI_MAP.put("🎓", "1f393");  // Graduation cap
        EMOJI_MAP.put("✏️", "270f");   // Pencil
        EMOJI_MAP.put("📅", "1f4c5");  // Calendar
        
        // Programming emojis
        EMOJI_MAP.put("💻", "1f4bb");  // Laptop
        EMOJI_MAP.put("🖥️", "1f5a5");  // Desktop computer
        EMOJI_MAP.put("⌨️", "2328");   // Keyboard
        EMOJI_MAP.put("🔍", "1f50d");  // Magnifying glass
        
        // Database emojis
        EMOJI_MAP.put("🗄️", "1f5c4");  // File cabinet
        EMOJI_MAP.put("💾", "1f4be");  // Floppy disk
        EMOJI_MAP.put("📊", "1f4ca");  // Bar chart
        
        // Math emojis
        EMOJI_MAP.put("🧮", "1f9ee");  // Abacus
        EMOJI_MAP.put("📐", "1f4d0");  // Triangular ruler
        EMOJI_MAP.put("📏", "1f4cf");  // Straight ruler
        EMOJI_MAP.put("➕", "2795");   // Plus
        EMOJI_MAP.put("➖", "2796");   // Minus
        EMOJI_MAP.put("✖️", "2716");   // Multiply
        EMOJI_MAP.put("➗", "2797");   // Divide
        
        // Status emojis
        EMOJI_MAP.put("✅", "2705");   // Check mark
        EMOJI_MAP.put("❌", "274c");   // Cross mark
        EMOJI_MAP.put("⚠️", "26a0");   // Warning
        EMOJI_MAP.put("ℹ️", "2139");   // Information
        EMOJI_MAP.put("🔔", "1f514");  // Bell
        EMOJI_MAP.put("📢", "1f4e2");  // Loudspeaker
        
        // Action emojis
        EMOJI_MAP.put("🎯", "1f3af");  // Direct hit
        EMOJI_MAP.put("💡", "1f4a1");  // Light bulb
        EMOJI_MAP.put("🚀", "1f680");  // Rocket
        EMOJI_MAP.put("⭐", "2b50");   // Star
        EMOJI_MAP.put("🌟", "1f31f");  // Glowing star
        EMOJI_MAP.put("💪", "1f4aa");  // Flexed biceps
        
        // Time emojis
        EMOJI_MAP.put("⏰", "23f0");   // Alarm clock
        EMOJI_MAP.put("⏱️", "23f1");   // Stopwatch
        EMOJI_MAP.put("⏳", "23f3");   // Hourglass
        EMOJI_MAP.put("🍅", "1f345");  // Tomato (Pomodoro)
        
        // Misc emojis
        EMOJI_MAP.put("🤖", "1f916");  // Robot
        EMOJI_MAP.put("❓", "2753");   // Question mark
        EMOJI_MAP.put("💬", "1f4ac");  // Speech balloon
        EMOJI_MAP.put("🗑️", "1f5d1");  // Wastebasket
    }
    
    /**
     * Gets an emoji as a JavaFX ImageView using Twemoji.
     * Falls back to text emoji if image loading fails.
     * 
     * @param emoji the emoji character (e.g., "📚")
     * @param size the desired size in pixels
     * @return ImageView with the emoji image, or null if not found
     */
    public static ImageView getEmojiImage(String emoji, int size) {
        String codepoint = EMOJI_MAP.get(emoji);
        if (codepoint == null) {
            // Emoji not in map, return null
            return null;
        }
        
        // Check cache first
        String cacheKey = codepoint + "_" + size;
        if (imageCache.containsKey(cacheKey)) {
            ImageView view = new ImageView(imageCache.get(cacheKey));
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            return view;
        }
        
        try {
            // Load from Twemoji CDN
            String imageUrl = TWEMOJI_CDN + codepoint + ".png";
            Image image = new Image(imageUrl, size, size, true, true, true);
            
            // Cache the image
            imageCache.put(cacheKey, image);
            
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            
            return view;
        } catch (Exception e) {
            System.err.println("[EmojiUtil] Failed to load emoji image for: " + emoji);
            return null;
        }
    }
    
    /**
     * Gets an emoji as text. This is a simple passthrough that returns the emoji character.
     * Use this when you want to keep using text emojis but ensure they're from the supported set.
     * 
     * @param emoji the emoji character
     * @return the emoji character, or empty string if not supported
     */
    public static String getEmoji(String emoji) {
        return EMOJI_MAP.containsKey(emoji) ? emoji : "";
    }
    
    /**
     * Checks if an emoji is supported by this utility.
     * 
     * @param emoji the emoji character to check
     * @return true if the emoji is supported
     */
    public static boolean isSupported(String emoji) {
        return EMOJI_MAP.containsKey(emoji);
    }
    
    /**
     * Gets a Label-friendly emoji string with a fallback icon if the emoji isn't supported.
     * 
     * @param emoji the emoji character
     * @param fallback the fallback text if emoji isn't supported
     * @return the emoji or fallback text
     */
    public static String getEmojiOrFallback(String emoji, String fallback) {
        return EMOJI_MAP.containsKey(emoji) ? emoji : fallback;
    }
}
