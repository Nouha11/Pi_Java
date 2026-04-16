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

/**
 * Controller for the Loan Request form.
 * The user selects pickup and return dates for borrowing a physical book
 * from a specific library. Submitting creates a PENDING loan request.
 */
public class LoanFormController {

    @FXML private Label lblBookTitle;
    @FXML private Label lblBookAuthor;
    @FXML private Label lblLibraryName;
    @FXML private Label lblLibraryAddress;
    @FXML private Label sideBookTitle;
    @FXML private Label sideBookAuthor;
    @FXML private DatePicker pickupDate;  // user selects when they'll pick up the book
    @FXML private DatePicker returnDate;  // user selects when they'll return it
    @FXML private Label lblError;

    private Book book;
    private Library library;
    private final LoanService loanService = new LoanService();

    // Hardcoded for demo — in production this comes from the session
    private static final int DEMO_USER_ID = 1; // fallback, overridden by SessionManager

    /**
     * Populates the form with the selected book and library details.
     * Sets default dates: pickup = today, return = today + 14 days.
     */
    public void initData(Book book, Library library) {
        this.book = book;
        this.library = library;

        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        sideBookTitle.setText(book.getTitle());
        sideBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        lblLibraryName.setText(library.getName());
        lblLibraryAddress.setText(library.getAddress() != null ? library.getAddress() : "");

        // Default date range: today to 2 weeks from now
        pickupDate.setValue(LocalDate.now());
        returnDate.setValue(LocalDate.now().plusDays(14));
    }

    /**
     * Handles the loan request submission.
     * Validates that dates are selected and return is after pickup,
     * then creates a PENDING loan in the database.
     */
    @FXML
    private void handleSubmit() {
        lblError.setVisible(false);

        // Basic date validation
        if (pickupDate.getValue() == null || returnDate.getValue() == null) {
            showError("Please select both dates.");
            return;
        }
        if (returnDate.getValue().isBefore(pickupDate.getValue())) {
            showError("Return date must be after pickup date.");
            return;
        }
        if (returnDate.getValue().isAfter(pickupDate.getValue().plusDays(14))) {
            showError("Loan period cannot exceed 14 days.");
            return;
        }

        try {
            Loan loan = new Loan();
            loan.setUserId(DEMO_USER_ID);
            loan.setBookId(book.getId());
            loan.setLibraryId(library.getId());
            loan.setBookTitle(book.getTitle());
            loan.setLibraryName(library.getName());
            // Map the user-selected dates to start_at and end_at
            loan.setStartAt(java.sql.Timestamp.valueOf(pickupDate.getValue().atStartOfDay()));
            loan.setEndAt(java.sql.Timestamp.valueOf(returnDate.getValue().atStartOfDay()));
            loanService.ajouter(loan);

            new Alert(Alert.AlertType.INFORMATION, "Loan request submitted! Awaiting admin approval.").showAndWait();
            NovaDashboardController.loadPage("/views/library/BookListView.fxml");
        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
        }
    }

    /** Goes back to the library selection screen. */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/LibrariesView.fxml"));
            Parent root = loader.load();
            ((LibrariesController) loader.getController()).initData(book);
            NovaDashboardController.setView(root);
        } catch (IOException e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    /** Displays an error message in the error label. */
    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
