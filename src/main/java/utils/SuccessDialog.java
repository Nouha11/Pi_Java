package utils;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Fancy animated success/info/warning dialog.
 * Uses a standard JavaFX Dialog so content is always visible.
 */
public class SuccessDialog {

    public enum Type { SUCCESS, INFO, WARNING }

    public static void show(Type type, String ignored, String title, String message, String btnLabel) {

        // Colors per type
        String headerGradient, accentColor, btnColor;
        switch (type) {
            case SUCCESS -> {
                headerGradient = "linear-gradient(to right, #064e3b, #059669)";
                accentColor    = "#10b981";
                btnColor       = "linear-gradient(to right, #059669, #10b981)";
            }
            case WARNING -> {
                headerGradient = "linear-gradient(to right, #78350f, #d97706)";
                accentColor    = "#f59e0b";
                btnColor       = "linear-gradient(to right, #d97706, #f59e0b)";
            }
            default -> { // INFO
                headerGradient = "linear-gradient(to right, #1e1b4b, #4338ca)";
                accentColor    = "#6366f1";
                btnColor       = "linear-gradient(to right, #4338ca, #6366f1)";
            }
        }

        // Build the dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("");

        // Remove default header
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        // Custom content
        VBox content = new VBox(18);
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(380);
        content.setStyle(
            "-fx-background-color: #0f172a;" +
            "-fx-padding: 36 40 32 40;" +
            "-fx-border-color: " + accentColor + ";" +
            "-fx-border-width: 0 0 0 4;"
        );

        // Colored top bar
        Region topBar = new Region();
        topBar.setPrefHeight(6);
        topBar.setStyle("-fx-background-color: " + headerGradient + ";");

        // Icon label (text-based, no emoji)
        String iconText = type == Type.SUCCESS ? "OK" : type == Type.WARNING ? "!" : "i";
        Label iconLbl = new Label(iconText);
        iconLbl.setStyle(
            "-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: white;" +
            "-fx-background-color: " + accentColor + ";" +
            "-fx-background-radius: 50; -fx-min-width: 56; -fx-min-height: 56;" +
            "-fx-alignment: center;"
        );
        iconLbl.setMinSize(56, 56);
        iconLbl.setAlignment(Pos.CENTER);

        // Title
        Label titleLbl = new Label(title);
        titleLbl.setStyle(
            "-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: white;" +
            "-fx-text-alignment: center;"
        );
        titleLbl.setWrapText(true);
        titleLbl.setAlignment(Pos.CENTER);

        // Message
        Label msgLbl = new Label(message);
        msgLbl.setStyle(
            "-fx-font-size: 13; -fx-text-fill: rgba(255,255,255,0.7);" +
            "-fx-text-alignment: center;"
        );
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(300);
        msgLbl.setAlignment(Pos.CENTER);

        // Button
        Button btn = new Button(btnLabel);
        btn.setStyle(
            "-fx-background-color: " + btnColor + ";" +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;" +
            "-fx-background-radius: 10; -fx-padding: 12 32; -fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 3);"
        );
        btn.setOnAction(e -> dialog.close());

        content.getChildren().addAll(iconLbl, titleLbl, msgLbl, btn);

        // Wrap in a VBox with the top bar
        VBox root = new VBox(topBar, content);
        root.setStyle("-fx-background-color: #0f172a;");

        // Set dialog content
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setStyle(
            "-fx-background-color: #0f172a; -fx-padding: 0;" +
            "-fx-border-color: " + accentColor + "; -fx-border-width: 1;" +
            "-fx-border-radius: 12; -fx-background-radius: 12;"
        );

        // Remove default buttons
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeButton = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setVisible(false);
        closeButton.setManaged(false);

        // Entrance animation on the content
        content.setOpacity(0);
        content.setTranslateY(20);
        dialog.setOnShown(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(350), content);
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), content);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition st = new ScaleTransition(Duration.millis(400), iconLbl);
            st.setFromX(0.4); st.setFromY(0.4);
            st.setToX(1.0);   st.setToY(1.0);
            st.setInterpolator(Interpolator.SPLINE(0.34, 1.56, 0.64, 1));

            new ParallelTransition(ft, tt, st).play();
        });

        dialog.showAndWait();
    }
}
