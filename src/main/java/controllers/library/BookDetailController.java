package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.library.Book;

import java.io.IOException;

public class BookDetailController {

    @FXML private Label lblTitle;
    @FXML private Label lblBreadcrumb;
    @FXML private Label lblPrice;
    @FXML private Label lblTypeBadge;
    @FXML private Label detailTitle;
    @FXML private Label detailAuthor;
    @FXML private Label detailIsbn;
    @FXML private Label detailPrice;
    @FXML private Label detailType;
    @FXML private Button btnBuyDigital;
    @FXML private Button btnBuyDigital2;

    private Book book;

    public void initData(Book book) {
        this.book = book;

        lblTitle.setText(book.getTitle());
        lblBreadcrumb.setText(book.getTitle());
        detailTitle.setText(book.getTitle());
        detailAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        detailIsbn.setText(book.getIsbn() != null ? book.getIsbn() : "N/A");
        detailPrice.setText(book.getPrice() > 0 ? "$" + String.format("%.2f", book.getPrice()) : "Free");
        detailType.setText(book.isDigital() ? "Digital (PDF)" : "Physical Book");
        lblPrice.setText(book.getPrice() > 0 ? "$" + String.format("%.2f", book.getPrice()) : "Free");

        if (book.isDigital()) {
            lblTypeBadge.setText("Digital (PDF)");
            lblTypeBadge.setStyle("-fx-background-color: #d1e7dd; -fx-text-fill: #0a3622; " +
                    "-fx-padding: 4 12; -fx-background-radius: 20; -fx-font-size: 12;");
        } else {
            lblTypeBadge.setText("Physical Book");
            lblTypeBadge.setStyle("-fx-background-color: #cfe2ff; -fx-text-fill: #084298; " +
                    "-fx-padding: 4 12; -fx-background-radius: 20; -fx-font-size: 12;");
            // Hide buy digital buttons for physical books
            btnBuyDigital.setVisible(false);
            btnBuyDigital.setManaged(false);
            btnBuyDigital2.setVisible(false);
            btnBuyDigital2.setManaged(false);
        }
    }

    @FXML
    private void handleBuyDigital() {
        navigate("/views/library/PurchaseView.fxml", loader -> {
            PurchaseController ctrl = loader.getController();
            ctrl.initData(book);
        });
    }

    @FXML
    private void handleBorrow() {
        navigate("/views/library/LibrariesView.fxml", loader -> {
            LibrariesController ctrl = loader.getController();
            ctrl.initData(book);
        });
    }

    @FXML
    private void handleBack() {
        navigate("/views/library/BookListView.fxml", loader -> {});
    }

    private void navigate(String fxml, ControllerInit init) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            init.init(loader);
            Stage stage = (Stage) lblTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Navigation error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FunctionalInterface
    interface ControllerInit {
        void init(FXMLLoader loader);
    }
}
