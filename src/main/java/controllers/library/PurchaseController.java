package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import models.library.Book;

import java.io.IOException;

/**
 * Controller for the Purchase screen.
 * Lets the user choose between Credit Card and PayPal before
 * proceeding to the actual payment form.
 */
public class PurchaseController {

    @FXML private Label lblBookTitle, lblBookAuthor, lblPrice, lblTotal;
    @FXML private VBox cardCreditCard, cardPayPal; // the two clickable payment method cards
    @FXML private Button btnProceed;

    private Book book;
    private String selectedMethod = "credit_card"; // default selection

    // CSS styles for selected/unselected payment method cards
    private static final String CARD_SELECTED   = "-fx-background-color: #e7f1ff; -fx-border-color: #0d6efd; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 24; -fx-cursor: hand;";
    private static final String CARD_UNSELECTED = "-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 24; -fx-cursor: hand;";

    /**
     * Populates the order summary with the selected book's info.
     * Defaults to credit card selection on load.
     */
    public void initData(Book book) {
        this.book = book;
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        String price = "$" + String.format("%.2f", book.getPrice());
        lblPrice.setText(price);
        lblTotal.setText(price);
        selectCreditCard(); // highlight credit card as default
    }

    /**
     * Selects Credit Card as the payment method.
     * Updates the visual state of both cards (selected/unselected styles).
     */
    @FXML private void selectCreditCard() {
        selectedMethod = "credit_card";
        cardCreditCard.setStyle(CARD_SELECTED);
        cardPayPal.setStyle(CARD_UNSELECTED);
    }

    /**
     * Selects PayPal as the payment method.
     */
    @FXML private void selectPayPal() {
        selectedMethod = "paypal";
        cardPayPal.setStyle(CARD_SELECTED);
        cardCreditCard.setStyle(CARD_UNSELECTED);
    }

    /**
     * Proceeds to the payment form, passing the book and chosen payment method.
     */
    @FXML
    private void handleProceed() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PaymentFormView.fxml"));
            Parent root = loader.load();
            // Pass both the book and the selected payment method to the next screen
            ((PaymentFormController) loader.getController()).initData(book, selectedMethod);
            NovaDashboardController.setView(root);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).showAndWait();
        }
    }

    /**
     * Goes back to the book detail screen.
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            ((BookDetailController) loader.getController()).initData(book);
            NovaDashboardController.setView(root);
        } catch (IOException e) {
            // silently ignore — user stays on current screen
        }
    }
}
