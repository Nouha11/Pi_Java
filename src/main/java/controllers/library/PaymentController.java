package controllers.library;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import models.library.Payment;
import services.library.PaymentService;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class PaymentController implements Initializable {

    @FXML private VBox paymentsContainer;
    @FXML private Label statusLabel;
    @FXML private Label statTotal, statCompleted, statFailed, statRevenue;

    private final PaymentService paymentService = new PaymentService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadHistory();
    }

    private void loadHistory() {
        List<Payment> payments = paymentService.afficher();
        buildCards(payments);

        long completed = payments.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long failed    = payments.stream().filter(p -> "FAILED".equals(p.getStatus())).count();
        double revenue = payments.stream().filter(p -> "COMPLETED".equals(p.getStatus()))
                .mapToDouble(Payment::getAmount).sum();

        if (statTotal     != null) statTotal.setText(String.valueOf(payments.size()));
        if (statCompleted != null) statCompleted.setText(String.valueOf(completed));
        if (statFailed    != null) statFailed.setText(String.valueOf(failed));
        if (statRevenue   != null) statRevenue.setText(String.format("$%.0f", revenue));
        statusLabel.setText(payments.size() + " payment(s)");
    }

    private void buildCards(List<Payment> payments) {
        paymentsContainer.getChildren().clear();
        if (payments.isEmpty()) {
            VBox empty = new VBox(12); empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 60;");
            Label icon = new Label("💳"); icon.setStyle("-fx-font-size: 48;");
            Label msg = new Label("No payments yet");
            msg.setStyle("-fx-font-size: 16; -fx-text-fill: #64748b;");
            empty.getChildren().addAll(icon, msg);
            paymentsContainer.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < payments.size(); i++) {
            HBox card = buildPaymentCard(payments.get(i));
            card.setOpacity(0); card.setTranslateX(-16);
            paymentsContainer.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(300), card);
            ft.setDelay(Duration.millis(i * 40)); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), card);
            tt.setDelay(Duration.millis(i * 40)); tt.setToX(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        }
    }

    private HBox buildPaymentCard(Payment payment) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; " +
            "-fx-padding: 16 20; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);"
        );

        boolean completed = "COMPLETED".equals(payment.getStatus());
        boolean failed    = "FAILED".equals(payment.getStatus());
        String color = completed ? "#10b981" : failed ? "#ef4444" : "#f59e0b";
        String statusIcon = completed ? "✅" : failed ? "❌" : "⏳";

        // Left bar
        Region bar = new Region();
        bar.setPrefWidth(4); bar.setPrefHeight(56);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");

        // Method icon
        boolean isCard = "credit_card".equals(payment.getPaymentMethod());
        Label methodIcon = new Label(isCard ? "💳" : "🅿");
        methodIcon.setStyle("-fx-font-size: 28;");

        // Info
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label bookTitle = new Label(payment.getBookTitle() != null ? payment.getBookTitle() : "Book #" + payment.getBookId());
        bookTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        HBox meta = new HBox(16); meta.setAlignment(Pos.CENTER_LEFT);
        Label user = new Label("👤 " + (payment.getUserName() != null ? payment.getUserName() : "User #" + payment.getUserId()));
        user.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
        Label method = new Label(isCard ? "Credit Card" : "PayPal");
        method.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
        meta.getChildren().addAll(user, method);

        Label txId = new Label("TX: " + (payment.getTransactionId() != null ? payment.getTransactionId() : "—"));
        txId.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");

        info.getChildren().addAll(bookTitle, meta, txId);

        // Amount
        Label amount = new Label(String.format("$%.2f", payment.getAmount()));
        amount.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        // Status badge
        Label badge = new Label(statusIcon + " " + payment.getStatus());
        badge.setStyle("-fx-background-color: " + color + "22; -fx-text-fill: " + color + "; " +
                "-fx-font-weight: bold; -fx-padding: 5 14; -fx-background-radius: 20; -fx-font-size: 12;");

        // Date
        Label date = new Label(payment.getCreatedAt() != null
                ? payment.getCreatedAt().toString().substring(0, 10) : "");
        date.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");

        VBox right = new VBox(6); right.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().addAll(amount, badge, date);

        card.getChildren().addAll(bar, methodIcon, info, right);

        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #e2e8f0", "-fx-border-color: " + color)));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: " + color, "-fx-border-color: #e2e8f0")));

        return card;
    }

    @FXML private void handleRefresh() { loadHistory(); }
}
