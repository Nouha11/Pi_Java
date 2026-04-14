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
        sideBookAuthor.setText(book.getAuthor());
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
        payment.setUserId(DEMO_USER_ID); payment.setBookId(book.getId());
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
        } else { lblError.setText("❌ " + error); lblError.setVisible(true); }
    }

    private boolean validate() {
        boolean v = true;
        if (!paymentService.validateCardNumber(txtCardNumber.getText())) { errCardNumber.setVisible(true); v = false; }
        if (!paymentService.validateCardHolder(txtCardHolder.getText())) { errCardHolder.setVisible(true); v = false; }
        if (!paymentService.validateExpiryDate(txtExpiry.getText())) { errExpiry.setVisible(true); v = false; }
        if (!paymentService.validateCVC(txtCvc.getText())) { errCvc.setVisible(true); v = false; }
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

    private void clearErrors() { lblError.setVisible(false); errCardNumber.setVisible(false); errCardHolder.setVisible(false); errExpiry.setVisible(false); errCvc.setVisible(false); }
}