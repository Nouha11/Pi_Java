package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import models.library.Book;
import models.library.Library;
import models.library.Loan;
import services.library.LoanService;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

public class LoanFormController {

    @FXML private Label lblBookTitle;
    @FXML private Label lblBookAuthor;
    @FXML private Label lblLibraryName;
    @FXML private Label lblLibraryAddress;
    @FXML private Label sideBookTitle;
    @FXML private Label sideBookAuthor;
    @FXML private DatePicker pickupDate;
    @FXML private DatePicker returnDate;
    @FXML private Label lblError;

    private Book book;
    private Library library;
    private final LoanService loanService = new LoanService();
    private static final int DEMO_USER_ID = 1;

    public void initData(Book book, Library library) {
        this.book = book;
        this.library = library;
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        sideBookTitle.setText(book.getTitle());
        sideBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        lblLibraryName.setText(library.getName());
        lblLibraryAddress.setText(library.getAddress() != null ? library.getAddress() : "");
        pickupDate.setValue(LocalDate.now());
        returnDate.setValue(LocalDate.now().plusDays(14));
    }

    @FXML
    private void handleSubmit() {
        lblError.setVisible(false);
        if (pickupDate.getValue() == null || returnDate.getValue() == null) { showError("Please select both dates."); return; }
        if (returnDate.getValue().isBefore(pickupDate.getValue())) { showError("Return date error."); return; }

        try {
            Loan loan = new Loan();
            loan.setUserId(DEMO_USER_ID);
            loan.setBookId(book.getId());
            loan.setLibraryId(library.getId());
            loan.setBookTitle(book.getTitle());
            loan.setLibraryName(library.getName());
            loanService.ajouter(loan);

            new Alert(Alert.AlertType.INFORMATION, "✅ Loan request submitted!").showAndWait();
            NovaDashboardController.loadPage("/views/library/BookListView.fxml");
        } catch (SQLException e) { showError("Database error: " + e.getMessage()); }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/LibrariesView.fxml"));
            Parent root = loader.load();
            ((LibrariesController)loader.getController()).initData(book);
            // ✅ INTEGRATED
            NovaDashboardController.setView(root);
        } catch (IOException e) { showError("Navigation error: " + e.getMessage()); }
    }

    private void showError(String msg) { lblError.setText("⚠ " + msg); lblError.setVisible(true); }
}