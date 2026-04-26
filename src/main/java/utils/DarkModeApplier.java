package utils;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * DarkModeApplier — walks the scene graph and overrides inline styles.
 * Called by ThemeManager after adding dark.css.
 */
public class DarkModeApplier {

    // Light palette
    private static final String L_BG_PAGE   = "#f8fafc";
    private static final String L_BG_WHITE  = "white";
    private static final String L_BG_WHITE2 = "#ffffff";
    private static final String L_BORDER    = "#e2e8f0";
    private static final String L_TEXT_DARK = "#0f172a";
    private static final String L_TEXT_MID  = "#374151";
    private static final String L_TEXT_MUTED= "#64748b";
    private static final String L_TEXT_MUTED2="#475569";
    private static final String L_CARD_BG   = "#f8fafc";
    private static final String L_ICON_BG   = "#eff6ff";

    // Dark palette
    private static final String D_BG_PAGE   = "#13131f";
    private static final String D_BG_WHITE  = "#1e1e2e";
    private static final String D_BG_WHITE2 = "#1e1e2e";
    private static final String D_BORDER    = "#2d2d4e";
    private static final String D_TEXT_DARK = "#e2e8f0";
    private static final String D_TEXT_MID  = "#cbd5e1";
    private static final String D_TEXT_MUTED= "#94a3b8";
    private static final String D_TEXT_MUTED2="#94a3b8";
    private static final String D_CARD_BG   = "#16162a";
    private static final String D_ICON_BG   = "#1e2d4e";

    public static void apply(Scene scene, boolean dark) {
        if (scene == null) return;
        applyToNode(scene.getRoot(), dark);
    }

    public static void applyToNode(Node node, boolean dark) {
        if (node == null) return;

        String style = node.getStyle();
        if (style != null && !style.isBlank()) {
            node.setStyle(transformStyle(style, dark));
        }

        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applyToNode(child, dark);
            }
        }
    }

    private static String transformStyle(String style, boolean dark) {
        if (dark) {
            return style
                // Backgrounds
                .replace("-fx-background-color: " + L_BG_PAGE,   "-fx-background-color: " + D_BG_PAGE)
                .replace("-fx-background: "       + L_BG_PAGE,   "-fx-background: "       + D_BG_PAGE)
                .replace("-fx-background-color: " + L_BG_WHITE,  "-fx-background-color: " + D_BG_WHITE)
                .replace("-fx-background-color: " + L_BG_WHITE2, "-fx-background-color: " + D_BG_WHITE2)
                .replace("-fx-background-color: " + L_CARD_BG,   "-fx-background-color: " + D_CARD_BG)
                .replace("-fx-background-color: " + L_ICON_BG,   "-fx-background-color: " + D_ICON_BG)
                // Borders
                .replace("-fx-border-color: " + L_BORDER, "-fx-border-color: " + D_BORDER)
                // Text
                .replace("-fx-text-fill: " + L_TEXT_DARK,  "-fx-text-fill: " + D_TEXT_DARK)
                .replace("-fx-text-fill: " + L_TEXT_MID,   "-fx-text-fill: " + D_TEXT_MID)
                .replace("-fx-text-fill: " + L_TEXT_MUTED, "-fx-text-fill: " + D_TEXT_MUTED)
                .replace("-fx-text-fill: " + L_TEXT_MUTED2,"-fx-text-fill: " + D_TEXT_MUTED2);
        } else {
            return style
                .replace("-fx-background-color: " + D_BG_PAGE,   "-fx-background-color: " + L_BG_PAGE)
                .replace("-fx-background: "       + D_BG_PAGE,   "-fx-background: "       + L_BG_PAGE)
                .replace("-fx-background-color: " + D_BG_WHITE,  "-fx-background-color: " + L_BG_WHITE)
                .replace("-fx-background-color: " + D_BG_WHITE2, "-fx-background-color: " + L_BG_WHITE2)
                .replace("-fx-background-color: " + D_CARD_BG,   "-fx-background-color: " + L_CARD_BG)
                .replace("-fx-background-color: " + D_ICON_BG,   "-fx-background-color: " + L_ICON_BG)
                .replace("-fx-border-color: " + D_BORDER, "-fx-border-color: " + L_BORDER)
                .replace("-fx-text-fill: " + D_TEXT_DARK,  "-fx-text-fill: " + L_TEXT_DARK)
                .replace("-fx-text-fill: " + D_TEXT_MID,   "-fx-text-fill: " + L_TEXT_MID)
                .replace("-fx-text-fill: " + D_TEXT_MUTED, "-fx-text-fill: " + L_TEXT_MUTED)
                .replace("-fx-text-fill: " + D_TEXT_MUTED2,"-fx-text-fill: " + L_TEXT_MUTED2);
        }
    }
}
