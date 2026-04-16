package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import models.library.Book;
import models.library.Payment;
import services.library.PaymentService;

import java.io.IOException;
import java.sql.SQLException;

public class PaymentFormController {

    @FXML private Label lblHeader, lblMethodTitle, lblError, sideBookTitle, sideBookAuthor, sidePrice, sideTotal, sideMethodIcon, sideMethodName;
    @FXML private VBox cardFields, paypalFields;
    @FXML private TextField txtCardNumber, txtCardHolder, txtExpiry, txtCvc;
    @FXML private Label errCardNumber, errCardHolder, errExpiry, errCvc;

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
        sidePrice.setText(price); sideTotal.setText(price);

        if ("paypal".equals(paymentMethod)) {
            lblHeader.setText("🅿 PayPal"); lblMethodTitle.setText("🅿 PayPal");
            sideMethodIcon.setText("🅿"); sideMethodName.setText("PayPal");
            cardFields.setVisible(false); cardFields.setManaged(false);
            paypalFields.setVisible(true); paypalFields.setManaged(true);
        } else {
            lblHeader.setText("💳 Credit Card"); lblMethodTitle.setText("💳 Credit Card");
            sideMethodIcon.setText("💳"); sideMethodName.setText("Credit Card");
        }
    }

    @FXML
    private void handlePay() {
        clearErrors();
        Payment payment = new Payment();
        payment.setUserId(utils.SessionManager.getCurrentUserId()); payment.setBookId(book.getId());
        payment.setAmount(book.getPrice()); payment.setPaymentMethod(paymentMethod); payment.setBookTitle(book.getTitle());

        String error;
        if ("paypal".equals(paymentMethod)) { error = paymentService.processerPayPal(payment); }
        else {
            if (!validate()) return;
            error = paymentService.processerCarteBancaire(payment, txtCardNumber.getText(), txtCardHolder.getText(), txtExpiry.getText(), txtCvc.getText());
        }

        if (error == null) {
            try { paymentService.ajouter(payment); } catch (SQLException e) {}
            new Alert(Alert.AlertType.INFORMATION, "✅ Payment Successful!").showAndWait();
            NovaDashboardController.loadPage("/views/library/BookListView.fxml");
        } else {
            lblError.setText("❌ " + error);
            lblError.setVisible(true);
            lblError.setManaged(true);
        }
    }

    private boolean validate() {
        boolean v = true;
        if (!paymentService.validateCardNumber(txtCardNumber.getText())) {
            errCardNumber.setText("Invalid card number (Luhn check failed).");
            errCardNumber.setVisible(true); errCardNumber.setManaged(true); v = false;
        } else { errCardNumber.setVisible(false); errCardNumber.setManaged(false); }

        if (!paymentService.validateCardHolder(txtCardHolder.getText())) {
            errCardHolder.setText("Invalid cardholder name.");
            errCardHolder.setVisible(true); errCardHolder.setManaged(true); v = false;
        } else { errCardHolder.setVisible(false); errCardHolder.setManaged(false); }

        if (!paymentService.validateExpiryDate(txtExpiry.getText())) {
            errExpiry.setText("Invalid or expired date (MM/YY).");
            errExpiry.setVisible(true); errExpiry.setManaged(true); v = false;
        } else { errExpiry.setVisible(false); errExpiry.setManaged(false); }

        if (!paymentService.validateCVC(txtCvc.getText())) {
            errCvc.setText("Invalid CVC (3-4 digits).");
            errCvc.setVisible(true); errCvc.setManaged(true); v = false;
        } else { errCvc.setVisible(false); errCvc.setManaged(false); }

        return v;
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PurchaseView.fxml"));
            Parent root = loader.load();
            ((PurchaseController)loader.getController()).initData(book);
            // ✅ INTEGRATED
            NovaDashboardController.setView(root);
        } catch (IOException e) {}
    }

    private void clearErrors() {
        lblError.setVisible(false); lblError.setManaged(false);
        errCardNumber.setVisible(false); errCardNumber.setManaged(false);
        errCardHolder.setVisible(false); errCardHolder.setManaged(false);
        errExpiry.setVisible(false);     errExpiry.setManaged(false);
        errCvc.setVisible(false);        errCvc.setManaged(false);
    }
}