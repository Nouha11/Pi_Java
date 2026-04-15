package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import models.library.Book;

import java.io.IOException;

public class BookDetailController {

    @FXML private Label lblTitle;
    @FXML private Label lblBreadcrumb;
    @FXML private Label lblPrice;
    @FXML private Label lblTypeBadge;
    @FXML private Label lblCoverPlaceholder;
    @FXML private Label detailTitle;
    @FXML private Label detailAuthor;
    @FXML private Label detailIsbn;
    @FXML private Label detailPrice;
    @FXML private Label detailType;
    @FXML private Button btnBuyDigital;
    @FXML private Button btnBuyDigital2;
    @FXML private Button btnBorrow;

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
            lblTypeBadge.setStyle("-fx-background-color: #d1e7dd; -fx-text-fill: #0a3622; -fx-padding: 4 12; -fx-background-radius: 20;");
        } else {
            lblTypeBadge.setText("Physical Book");
            lblTypeBadge.setStyle("-fx-background-color: #cfe2ff; -fx-text-fill: #084298; -fx-padding: 4 12; -fx-background-radius: 20;");
            btnBuyDigital.setVisible(false);
            btnBuyDigital.setManaged(false);
            btnBuyDigital2.setVisible(false);
            btnBuyDigital2.setManaged(false);
        }
    }

    @FXML private void handleBuyDigital() { navigate("/views/library/PurchaseView.fxml", loader -> ((PurchaseController)loader.getController()).initData(book)); }
    @FXML private void handleBorrow() { navigate("/views/library/LibrariesView.fxml", loader -> ((LibrariesController)loader.getController()).initData(book)); }
    @FXML private void handleBack() { NovaDashboardController.loadPage("/views/library/BookListView.fxml"); }

    private void navigate(String fxml, ControllerInit init) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            init.init(loader);
            controllers.NovaDashboardController.setView(root);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Navigation error: " + e.getClass().getSimpleName() + ": " + e.getMessage()).showAndWait();
        }
    }

    @FunctionalInterface interface ControllerInit { void init(FXMLLoader loader); }
}