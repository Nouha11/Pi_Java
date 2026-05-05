package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import models.library.Book;
import services.library.BookService;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class BookFormController implements Initializable {

    @FXML private Label    formTitle;
    @FXML private TextField txtTitle;
    @FXML private TextField txtAuthor;
    @FXML private TextField txtIsbn;
    @FXML private TextField txtPrice;
    @FXML private ComboBox<String> cbType;
    @FXML private TextField txtCoverImage;
    @FXML private TextField txtPdfUrl;
    @FXML private Label     lblError;
    @FXML private Button    btnSave;
    @FXML private Button    btnClearPdf;

    // Per-field error labels
    @FXML private Label errTitle;
    @FXML private Label errAuthor;
    @FXML private Label errPrice;
    @FXML private Label errType;

    private final BookService bookService = new BookService();
    private Book book;
    private boolean isEdit;
    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbType.getItems().addAll("physical", "digital");

        // Live validation on focus loss
        txtTitle.focusedProperty().addListener((obs, o, n)  -> { if (!n) validateTitle(); });
        txtAuthor.focusedProperty().addListener((obs, o, n) -> { if (!n) validateAuthor(); });
        txtPrice.focusedProperty().addListener((obs, o, n)  -> { if (!n) validatePrice(); });
    }

    public void initData(Book b, Runnable onSave) {
        this.onSaveCallback = onSave;
        if (b == null) {
            isEdit = false;
            book = new Book();
            formTitle.setText("➕ New Book");
            cbType.setValue("physical");
        } else {
            isEdit = true;
            book = b;
            formTitle.setText("✏ Edit Book");
            txtTitle.setText(b.getTitle());
            txtAuthor.setText(b.getAuthor());
            txtIsbn.setText(b.getIsbn());
            txtPrice.setText(String.valueOf(b.getPrice()));
            cbType.setValue(b.getType());
            txtCoverImage.setText(b.getCoverImage());
            txtPdfUrl.setText(b.getPdfUrl());
            if (b.getPdfUrl() != null && !b.getPdfUrl().isBlank()) {
                btnClearPdf.setVisible(true);
                btnClearPdf.setManaged(true);
            }
        }
    }

    @FXML
    private void handleSave() {
        clearErrors();
        if (!validateAll()) return;

        populateBook();
        String validationError = bookService.validate(book, isEdit);
        if (validationError != null) {
            lblError.setText("⚠ " + validationError);
            lblError.setVisible(true);
            return;
        }

        try {
            if (isEdit) bookService.modifier(book);
            else        bookService.ajouter(book);

            if (onSaveCallback != null) onSaveCallback.run();
            closeWindow();
        } catch (SQLException e) {
            lblError.setText("⚠ Database error: " + e.getMessage());
            lblError.setVisible(true);
        }
    }

    @FXML
    private void handleBrowsePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PDF File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        File file = chooser.showOpenDialog(btnSave.getScene().getWindow());
        if (file != null) {
            txtPdfUrl.setText(file.getAbsolutePath());
            btnClearPdf.setVisible(true);
            btnClearPdf.setManaged(true);
        }
    }

    @FXML
    private void handleClearPdf() {
        txtPdfUrl.clear();
        btnClearPdf.setVisible(false);
        btnClearPdf.setManaged(false);
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    private boolean validateAll() {
        boolean ok = true;
        if (!validateTitle())  ok = false;
        if (!validateAuthor()) ok = false;
        if (!validatePrice())  ok = false;
        if (cbType.getValue() == null) {
            errType.setText("Type is required."); errType.setVisible(true); ok = false;
        }
        return ok;
    }

    private boolean validateTitle() {
        String v = txtTitle.getText();
        if (v == null || v.trim().isEmpty()) {
            errTitle.setText("Title is required."); errTitle.setVisible(true); return false;
        }
        if (v.trim().length() < 2) {
            errTitle.setText("Minimum 2 characters."); errTitle.setVisible(true); return false;
        }
        errTitle.setVisible(false); return true;
    }

    private boolean validateAuthor() {
        String v = txtAuthor.getText();
        if (v == null || v.trim().isEmpty()) {
            errAuthor.setText("Author is required."); errAuthor.setVisible(true); return false;
        }
        errAuthor.setVisible(false); return true;
    }

    private boolean validatePrice() {
        try {
            double val = Double.parseDouble(txtPrice.getText().trim());
            if (val < 0) throw new NumberFormatException();
            errPrice.setVisible(false); return true;
        } catch (NumberFormatException e) {
            errPrice.setText("Must be a positive number."); errPrice.setVisible(true); return false;
        }
    }

    private void populateBook() {
        book.setTitle(txtTitle.getText().trim());
        book.setAuthor(txtAuthor.getText().trim());
        book.setIsbn(txtIsbn.getText());
        book.setPrice(Double.parseDouble(txtPrice.getText().trim()));
        book.setType(cbType.getValue());
        book.setCoverImage(txtCoverImage.getText());
        book.setPdfUrl(txtPdfUrl.getText());
    }

    private void clearErrors() {
        lblError.setVisible(false);
        errTitle.setVisible(false);
        errAuthor.setVisible(false);
        errPrice.setVisible(false);
        errType.setVisible(false);
    }

    private void closeWindow() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
