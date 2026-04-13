package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
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

    // Demo user — in a real app this comes from session
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

        // Default dates: today + 14 days
        pickupDate.setValue(LocalDate.now());
        returnDate.setValue(LocalDate.now().plusDays(14));
    }

    @FXML
    private void handleSubmit() {
        lblError.setVisible(false);

        if (pickupDate.getValue() == null || returnDate.getValue() == null) {
            showError("Please select both pickup and return dates.");
            return;
        }
        if (returnDate.getValue().isBefore(pickupDate.getValue())) {
            showError("Return date must be after pickup date.");
            return;
        }
        if (returnDate.getValue().isAfter(pickupDate.getValue().plusDays(14))) {
            showError("Maximum loan period is 14 days.");
            return;
        }

        try {
            Loan loan = new Loan();
            loan.setUserId(DEMO_USER_ID);
            loan.setBookId(book.getId());
            loan.setLibraryId(library.getId());
            loan.setBookTitle(book.getTitle());
            loan.setLibraryName(library.getName());

            loanService.ajouter(loan);

            new Alert(Alert.AlertType.INFORMATION,
                    "✅ Loan request submitted!\n\nBook: " + book.getTitle() +
                    "\nLibrary: " + library.getName() +
                    "\nPickup: " + pickupDate.getValue() +
                    "\nReturn by: " + returnDate.getValue() +
                    "\n\nStatus: PENDING — waiting for admin approval.",
                    ButtonType.OK).showAndWait();

            // Go back to book list
            navigateTo("/views/library/BookListView.fxml");

        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/LibrariesView.fxml"));
            Parent root = loader.load();
            LibrariesController ctrl = loader.getController();
            ctrl.initData(book);
            Stage stage = (Stage) lblBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    private void navigateTo(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) lblBookTitle.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText("⚠ " + msg);
        lblError.setVisible(true);
    }
}
