package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import models.library.Book;

import java.io.IOException;

public class PurchaseController {

    @FXML private Label lblBookTitle, lblBookAuthor, lblPrice, lblTotal;
    @FXML private VBox cardCreditCard, cardPayPal;
    @FXML private Button btnProceed;

    private Book book;
    private String selectedMethod = "credit_card";

    public void initData(Book book) {
        this.book = book;
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        String price = "$" + String.format("%.2f", book.getPrice());
        lblPrice.setText(price); lblTotal.setText(price);
        selectCreditCard();
    }

    private static final String CARD_SELECTED   = "-fx-background-color: #e7f1ff; -fx-border-color: #0d6efd; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 24; -fx-cursor: hand;";
    private static final String CARD_UNSELECTED = "-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 24; -fx-cursor: hand;";

    @FXML private void selectCreditCard() {
        selectedMethod = "credit_card";
        cardCreditCard.setStyle(CARD_SELECTED);
        cardPayPal.setStyle(CARD_UNSELECTED);
    }

    @FXML private void selectPayPal() {
        selectedMethod = "paypal";
        cardPayPal.setStyle(CARD_SELECTED);
        cardCreditCard.setStyle(CARD_UNSELECTED);
    }

    @FXML
    private void handleProceed() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PaymentFormView.fxml"));
            Parent root = loader.load();
            ((PaymentFormController)loader.getController()).initData(book, selectedMethod);
            // ✅ INTEGRATED
            NovaDashboardController.setView(root);
        } catch (IOException e) { new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).showAndWait(); }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            ((BookDetailController)loader.getController()).initData(book);
            // ✅ INTEGRATED
            NovaDashboardController.setView(root);
        } catch (IOException e) {}
    }
}