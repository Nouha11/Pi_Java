package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.library.Book;
import models.library.Payment;
import services.library.PaymentService;

import java.io.IOException;
import java.sql.SQLException;

public class PaymentFormController {

    @FXML private Label lblHeader;
    @FXML private Label lblMethodTitle;
    @FXML private VBox cardFields;
    @FXML private VBox paypalFields;

    @FXML private TextField txtCardNumber;
    @FXML private TextField txtCardHolder;
    @FXML private TextField txtExpiry;
    @FXML private TextField txtCvc;

    @FXML private Label errCardNumber;
    @FXML private Label errCardHolder;
    @FXML private Label errExpiry;
    @FXML private Label errCvc;
    @FXML private Label lblError;

    @FXML private Label sideBookTitle;
    @FXML private Label sideBookAuthor;
    @FXML private Label sidePrice;
    @FXML private Label sideTotal;
    @FXML private Label sideMethodIcon;
    @FXML private Label sideMethodName;

    private Book book;
    private String paymentMethod;
    private final PaymentService paymentService = new PaymentService();

    private static final int DEMO_USER_ID = 1;

    public void initData(Book book, String paymentMethod) {
        this.book = book;
        this.paymentMethod = paymentMethod;

        sideBookTitle.setText(book.getTitle());
        sideBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        String price = "$" + String.format("%.2f", book.getPrice());
        sidePrice.setText(price);
        sideTotal.setText(price);

        if ("paypal".equals(paymentMethod)) {
            lblHeader.setText("🅿 PayPal Payment");
            lblMethodTitle.setText("🅿 PayPal Payment");
            sideMethodIcon.setText("🅿");
            sideMethodName.setText("PayPal");
            cardFields.setVisible(false);
            cardFields.setManaged(false);
            paypalFields.setVisible(true);
            paypalFields.setManaged(true);
        } else {
            lblHeader.setText("💳 Credit Card Payment");
            lblMethodTitle.setText("💳 Credit Card Payment");
            sideMethodIcon.setText("💳");
            sideMethodName.setText("Credit Card");
        }
    }

    @FXML
    private void handlePay() {
        clearErrors();

        Payment payment = new Payment();
        payment.setUserId(DEMO_USER_ID);
        payment.setBookId(book.getId());
        payment.setAmount(book.getPrice());
        payment.setPaymentMethod(paymentMethod);
        payment.setBookTitle(book.getTitle());

        String error;
        if ("paypal".equals(paymentMethod)) {
            error = paymentService.processerPayPal(payment);
        } else {
            // Validate fields first
            boolean valid = true;
            if (!paymentService.validateCardNumber(txtCardNumber.getText())) {
                errCardNumber.setText("Invalid card number (Luhn check failed).");
                errCardNumber.setVisible(true); valid = false;
            }
            if (!paymentService.validateCardHolder(txtCardHolder.getText())) {
                errCardHolder.setText("Invalid cardholder name.");
                errCardHolder.setVisible(true); valid = false;
            }
            if (!paymentService.validateExpiryDate(txtExpiry.getText())) {
                errExpiry.setText("Invalid or expired date (MM/YY).");
                errExpiry.setVisible(true); valid = false;
            }
            if (!paymentService.validateCVC(txtCvc.getText())) {
                errCvc.setText("Invalid CVC (3-4 digits).");
                errCvc.setVisible(true); valid = false;
            }
            if (!valid) return;

            error = paymentService.processerCarteBancaire(
                    payment,
                    txtCardNumber.getText(),
                    txtCardHolder.getText(),
                    txtExpiry.getText(),
                    txtCvc.getText()
            );
        }

        if (error == null) {
            // Save to DB
            try {
                paymentService.ajouter(payment);
            } catch (SQLException e) {
                System.err.println("Payment save error: " + e.getMessage());
            }

            new Alert(Alert.AlertType.INFORMATION,
                    "✅ Payment Successful!\n\n" +
                    "Book: " + book.getTitle() + "\n" +
                    "Amount: $" + String.format("%.2f", book.getPrice()) + "\n" +
                    "Transaction ID: " + payment.getTransactionId() +
                    (payment.getCardLastFour() != null ? "\nCard: **** **** **** " + payment.getCardLastFour() : ""),
                    ButtonType.OK).showAndWait();

            navigateTo("/views/library/BookListView.fxml");
        } else {
            lblError.setText("❌ " + error);
            lblError.setVisible(true);
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PurchaseView.fxml"));
            Parent root = loader.load();
            PurchaseController ctrl = loader.getController();
            ctrl.initData(book);

            Stage stage = (Stage) sideBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void navigateTo(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) sideBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void clearErrors() {
        lblError.setVisible(false);
        errCardNumber.setVisible(false);
        errCardHolder.setVisible(false);
        errExpiry.setVisible(false);
        errCvc.setVisible(false);
    }
}
