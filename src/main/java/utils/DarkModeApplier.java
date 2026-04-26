package utils;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * DarkModeApplier — recursively walks the scene graph and replaces
 * inline style color values with their dark-mode equivalents.
 * Necessary because JavaFX inline styles override CSS stylesheets.
 */
public class DarkModeApplier {

    // { lightValue, darkValue } — order matters: more specific first
    private static final String[][] COLOR_MAP = {
        // ── Page / root backgrounds ──────────────────────────────────────────
        { "white",    "#1e1e2e" },
        { "#ffffff",  "#1e1e2e" },
        { "#f8fafc",  "#13131f" },
        { "#f8f9fa",  "#13131f" },
        { "#f5f5f5",  "#16162a" },
        { "#f4f6fb",  "#16162a" },
        { "#f0f2f8",  "#16162a" },
        { "#f1f5f9",  "#13131f" },
        // ── Card / panel backgrounds ─────────────────────────────────────────
        { "#eff6ff",  "#1e2d4e" },
        { "#dbeafe",  "#1a2540" },
        { "#e0f2fe",  "#0f2030" },
        { "#cfe2ff",  "#1a2540" },
        { "#cff4fc",  "#0f2030" },
        { "#d1e7dd",  "#0f2520" },
        { "#dcfce7",  "#0f2520" },
        { "#ecfdf5",  "#0f2520" },
        { "#f0fdf4",  "#0f2520" },
        { "#e8f5e9",  "#0f2520" },
        { "#fef2f2",  "#2a1515" },
        { "#ffe4e6",  "#2a1515" },
        { "#fff1f2",  "#2a1515" },
        { "#f8d7da",  "#2a1515" },
        { "#fef3c7",  "#2a1a00" },
        { "#fffbeb",  "#2a1a00" },
        { "#fdf2f8",  "#2a1525" },
        { "#f3e8ff",  "#1e1535" },
        { "#ede9fe",  "#1e1535" },
        { "#f5f3ff",  "#1e1535" },
        { "#eef2ff",  "#1e2035" },
        { "#e8eaf6",  "#1e2035" },
        { "#e7f1ff",  "#1e2d4e" },
        // ── Quiz / gamification / forum specific ─────────────────────────────
        { "#1e2a5e",  "#0d1230" },
        { "#2d3748",  "#1a1f2e" },
        { "#334155",  "#1e2535" },
        { "#4a5568",  "#2d3748" },
        { "#a0aec0",  "#64748b" },
        { "#cbd5e1",  "#475569" },
        // ── Borders ──────────────────────────────────────────────────────────
        { "#e2e8f0",  "#2d2d4e" },
        { "#e5e7eb",  "#2d2d4e" },
        // ── Text colors ──────────────────────────────────────────────────────
        { "#0f172a",  "#e2e8f0" },
        { "#0F172A",  "#e2e8f0" },
        { "#1e293b",  "#cbd5e1" },
        { "#2d3748",  "#94a3b8" },
        { "#334155",  "#94a3b8" },
        { "#374151",  "#cbd5e1" },
        { "#475569",  "#94a3b8" },
        { "#4a5568",  "#94a3b8" },
        { "#64748b",  "#94a3b8" },
        { "#6c757d",  "#94a3b8" },
        { "#94a3b8",  "#64748b" },
        { "#a0aec0",  "#64748b" },
    };

    // ── Public API ────────────────────────────────────────────────────────────

    public static void apply(Scene scene, boolean dark) {
        if (scene == null || scene.getRoot() == null) return;
        applyToNode(scene.getRoot(), dark);
    }

    public static void applyToNode(Node node, boolean dark) {
        if (node == null) return;
        String style = node.getStyle();
        if (style != null && !style.isBlank()) {
            node.setStyle(transform(style, dark));
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applyToNode(child, dark);
            }
        }
    }

    // ── Transform a single style string ──────────────────────────────────────

    private static String transform(String style, boolean dark) {
        String result = style;
        for (String[] pair : COLOR_MAP) {
            String light = pair[0];
            String darkVal = pair[1];
            if (dark) {
                result = replaceIgnoreCase(result, light, darkVal);
            } else {
                result = replaceIgnoreCase(result, darkVal, light);
            }
        }
        return result;
    }

    private static String replaceIgnoreCase(String text, String from, String to) {
        if (text == null || from == null || from.isEmpty()) return text;
        String lower = text.toLowerCase();
        String fromLower = from.toLowerCase();
        int idx = lower.indexOf(fromLower);
        if (idx < 0) return text;
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while ((idx = lower.indexOf(fromLower, start)) >= 0) {
            sb.append(text, start, idx);
            sb.append(to);
            start = idx + from.length();
        }
        sb.append(text, start, text.length());
        return sb.toString();
    }
}
