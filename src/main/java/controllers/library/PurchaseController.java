package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.library.Book;

import java.io.IOException;

public class PurchaseController {

    @FXML private Label lblBookTitle;
    @FXML private Label lblBookAuthor;
    @FXML private Label lblPrice;
    @FXML private Label lblTotal;
    @FXML private VBox cardCreditCard;
    @FXML private VBox cardPayPal;
    @FXML private Button btnProceed;

    private Book book;
    private String selectedMethod = "credit_card"; // default

    public void initData(Book book) {
        this.book = book;
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        String price = "$" + String.format("%.2f", book.getPrice());
        lblPrice.setText(price);
        lblTotal.setText(price);

        // Select credit card by default
        selectCreditCard();
    }

    @FXML
    private void selectCreditCard() {
        selectedMethod = "credit_card";
        cardCreditCard.setStyle(cardCreditCard.getStyle()
                .replace("-fx-border-color: #dee2e6", "-fx-border-color: #0d6efd")
                .replace("-fx-background-color: #f8f9fa", "-fx-background-color: #e7f1ff"));
        cardPayPal.setStyle(cardPayPal.getStyle()
                .replace("-fx-border-color: #0d6efd", "-fx-border-color: #dee2e6")
                .replace("-fx-background-color: #e7f1ff", "-fx-background-color: #f8f9fa"));
    }

    @FXML
    private void selectPayPal() {
        selectedMethod = "paypal";
        cardPayPal.setStyle(cardPayPal.getStyle()
                .replace("-fx-border-color: #dee2e6", "-fx-border-color: #0d6efd")
                .replace("-fx-background-color: #f8f9fa", "-fx-background-color: #e7f1ff"));
        cardCreditCard.setStyle(cardCreditCard.getStyle()
                .replace("-fx-border-color: #0d6efd", "-fx-border-color: #dee2e6")
                .replace("-fx-background-color: #e7f1ff", "-fx-background-color: #f8f9fa"));
    }

    @FXML
    private void handleProceed() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PaymentFormView.fxml"));
            Parent root = loader.load();
            PaymentFormController ctrl = loader.getController();
            ctrl.initData(book, selectedMethod);

            Stage stage = (Stage) lblBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            BookDetailController ctrl = loader.getController();
            ctrl.initData(book);

            Stage stage = (Stage) lblBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
