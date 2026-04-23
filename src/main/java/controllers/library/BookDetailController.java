package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import models.library.Book;

import java.io.IOException;

/**
 * Controller for the Book Detail view.
 * Displays full information about a single book and provides
 * navigation to Purchase (digital books) or Borrow (physical/digital).
 */
public class BookDetailController {

    // All @FXML fields are automatically injected by JavaFX when the FXML loads.
    // The field name must exactly match the fx:id in the FXML file.
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

    // The book being displayed — passed in from the previous screen
    private Book book;

    /**
     * Populates the view with the selected book's data.
     * Called by the previous controller after loading this FXML.
     * Also hides the "Buy Digital" buttons if the book is physical.
     */
    public void initData(Book book) {
        this.book = book;

        // Populate header and breadcrumb
        lblTitle.setText(book.getTitle());
        lblBreadcrumb.setText(book.getTitle());

        // Populate detail grid
        detailTitle.setText(book.getTitle());
        detailAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        detailIsbn.setText(book.getIsbn() != null ? book.getIsbn() : "N/A");
        detailPrice.setText(book.getPrice() > 0 ? "$" + String.format("%.2f", book.getPrice()) : "Free");
        detailType.setText(book.isDigital() ? "Digital (PDF)" : "Physical Book");
        lblPrice.setText(book.getPrice() > 0 ? "$" + String.format("%.2f", book.getPrice()) : "Free");

        // Style the type badge differently for digital vs physical
        if (book.isDigital()) {
            lblTypeBadge.setText("Digital (PDF)");
            lblTypeBadge.setStyle("-fx-background-color: #d1e7dd; -fx-text-fill: #0a3622; -fx-padding: 4 12; -fx-background-radius: 20;");
        } else {
            lblTypeBadge.setText("Physical Book");
            lblTypeBadge.setStyle("-fx-background-color: #cfe2ff; -fx-text-fill: #084298; -fx-padding: 4 12; -fx-background-radius: 20;");
            // Hide purchase buttons for physical books — they can only be borrowed
            btnBuyDigital.setVisible(false);
            btnBuyDigital.setManaged(false); // setManaged(false) removes it from layout flow too
            btnBuyDigital2.setVisible(false);
            btnBuyDigital2.setManaged(false);
        }
    }

    // ── NAVIGATION HANDLERS ───────────────────────────────────────────────────

    /** Navigates to the Purchase screen for buying a digital copy. */
    @FXML private void handleBuyDigital() {
        navigate("/views/library/PurchaseView.fxml",
                loader -> ((PurchaseController) loader.getController()).initData(book));
    }

    /** Navigates to the Libraries screen to select a library for borrowing. */
    @FXML private void handleBorrow() {
        navigate("/views/library/LibrariesView.fxml",
                loader -> ((LibrariesController) loader.getController()).initData(book));
    }

    /** Goes back to the book list. */
    @FXML private void handleBack() {
        NovaDashboardController.loadPage("/views/library/BookListView.fxml");
    }

    /**
     * Generic navigation helper.
     * Loads an FXML, calls the ControllerInit lambda to pass data to the new controller,
     * then swaps the main content area to the new view.
     *
     * Using a functional interface (ControllerInit) here lets us pass different
     * initData() calls as lambdas without duplicating the try/catch boilerplate.
     */
    private void navigate(String fxml, ControllerInit init) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            init.init(loader); // pass data to the new controller
            controllers.NovaDashboardController.setView(root); // swap the view
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Navigation error: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .showAndWait();
        }
    }

    /** Functional interface used to pass controller initialization as a lambda. */
    @FunctionalInterface
    interface ControllerInit {
        void init(FXMLLoader loader);
    }
}
