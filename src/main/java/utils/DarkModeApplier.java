package utils;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * DarkModeApplier — safely replaces inline style colors.
 * Uses word-boundary matching to avoid corrupting rgba() values.
 */
public class DarkModeApplier {

    // Only exact color values that appear as standalone tokens
    // Format: { lightValue, darkValue }
    private static final String[][] MAP = {
        // Backgrounds — most common
        { "white",    "#1e1e2e" },
        { "#ffffff",  "#1e1e2e" },
        { "#f8fafc",  "#13131f" },
        { "#f8f9fa",  "#13131f" },
        { "#f1f5f9",  "#13131f" },
        { "#f5f5f5",  "#16162a" },
        { "#f4f6fb",  "#16162a" },
        { "#f0f2f8",  "#16162a" },
        // Card backgrounds
        { "#eff6ff",  "#1e2d4e" },
        { "#dbeafe",  "#1a2540" },
        { "#e0f2fe",  "#0f2030" },
        { "#dcfce7",  "#0f2520" },
        { "#f0fdf4",  "#0f2520" },
        { "#fef2f2",  "#2a1515" },
        { "#fef3c7",  "#2a1a00" },
        { "#fffbeb",  "#2a1a00" },
        { "#f3e8ff",  "#1e1535" },
        { "#ede9fe",  "#1e1535" },
        { "#fff5f5",  "#2a1515" },
        { "#fff8e1",  "#2a1a00" },
        { "#f3e5f5",  "#1e1535" },
        { "#e3f2fd",  "#1a2540" },
        { "#e8f5e9",  "#0f2520" },
        { "#fdf2f8",  "#2a1525" },
        { "#eef2ff",  "#1e2035" },
        { "#e8eaf6",  "#1e2035" },
        { "#e7f1ff",  "#1e2d4e" },
        { "#ffe4e6",  "#2a1515" },
        // Borders
        { "#e2e8f0",  "#2d2d4e" },
        { "#e5e7eb",  "#2d2d4e" },
        { "#cbd5e1",  "#3d3d5c" },
        { "#e4e8f0",  "#2d2d4e" },
        // Text
        { "#0f172a",  "#e2e8f0" },
        { "#0F172A",  "#e2e8f0" },
        { "#1e293b",  "#cbd5e1" },
        { "#374151",  "#cbd5e1" },
        { "#475569",  "#94a3b8" },
        { "#64748b",  "#94a3b8" },
        { "#6c757d",  "#94a3b8" },
        { "#1e2a5e",  "#93c5fd" },
        { "#2d3748",  "#94a3b8" },
        { "#334155",  "#94a3b8" },
        { "#4a5568",  "#94a3b8" },
    };

    public static void apply(Scene scene, boolean dark) {
        if (scene == null || scene.getRoot() == null) return;
        applyToNode(scene.getRoot(), dark);
    }

    public static void applyToNode(Node node, boolean dark) {
        if (node == null) return;
        String style = node.getStyle();
        if (style != null && !style.isBlank()) {
            String transformed = transform(style, dark);
            if (!transformed.equals(style)) {
                node.setStyle(transformed);
            }
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                applyToNode(child, dark);
            }
        }
    }

    private static String transform(String style, boolean dark) {
        String result = style;
        for (String[] pair : MAP) {
            String from = dark ? pair[0] : pair[1];
            String to   = dark ? pair[1] : pair[0];
            // Only replace when the color appears as a standalone value
            // i.e. preceded by ": " or ":  " and followed by ";" or ")"
            result = replaceColor(result, from, to);
        }
        return result;
    }

    /**
     * Replaces a color value only when it appears after ": " (CSS property value)
     * This prevents replacing colors inside rgba() or other functions.
     */
    private static String replaceColor(String style, String from, String to) {
        // Look for pattern: ": <color>" or ":  <color>"
        String search = ": " + from;
        String searchLower = search.toLowerCase();
        String styleLower  = style.toLowerCase();

        if (!styleLower.contains(searchLower)) return style;

        StringBuilder sb = new StringBuilder();
        int start = 0;
        int idx;
        while ((idx = styleLower.indexOf(searchLower, start)) >= 0) {
            // Check what follows the color — must be ; or space or end
            int afterIdx = idx + search.length();
            char nextChar = afterIdx < style.length() ? style.charAt(afterIdx) : ';';
            // Only replace if followed by ; or whitespace (not inside a function)
            if (nextChar == ';' || nextChar == ' ' || nextChar == '\t' || afterIdx >= style.length()) {
                sb.append(style, start, idx + 2); // keep ": "
                sb.append(to);
                start = afterIdx;
            } else {
                sb.append(style, start, idx + 1);
                start = idx + 1;
            }
        }
        sb.append(style, start, style.length());
        return sb.toString();
    }
}
