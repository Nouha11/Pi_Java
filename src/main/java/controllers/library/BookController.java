package controllers.library;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.library.Book;
import services.library.BookService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;

public class BookController implements Initializable {

    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, Integer> colId;
    @FXML private TableColumn<Book, String>  colTitle;
    @FXML private TableColumn<Book, String>  colAuthor;
    @FXML private TableColumn<Book, String>  colIsbn;
    @FXML private TableColumn<Book, Double>  colPrice;
    @FXML private TableColumn<Book, String>  colType;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private Label statusLabel;
    @FXML private Label statTotalBooks;
    @FXML private Label statPhysical;
    @FXML private Label statDigital;

    private final BookService bookService = new BookService();
    private final ObservableList<Book> bookData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        // Color-code type column
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("digital".equals(item)
                        ? "-fx-text-fill: #3498db; -fx-font-weight: bold;"
                        : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            }
        });

        bookTable.setItems(bookData);
    }

    private void setupFilters() {
        filterType.getItems().addAll("", "physical", "digital");
        filterType.setValue("");
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterType.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void loadData() {
        applyFilters();
    }

    @FXML
    private void applyFilters() {
        String type   = filterType.getValue();
        String search = searchField.getText();
        bookData.setAll(bookService.findByFilters(
                (type == null || type.isEmpty()) ? null : type,
                (search == null || search.isEmpty()) ? null : search
        ));
        // Update stat cards
        long total    = bookData.size();
        long physical = bookData.stream().filter(b -> "physical".equals(b.getType())).count();
        long digital  = bookData.stream().filter(b -> "digital".equals(b.getType())).count();
        if (statTotalBooks != null) statTotalBooks.setText(String.valueOf(total));
        if (statPhysical   != null) statPhysical.setText(String.valueOf(physical));
        if (statDigital    != null) statDigital.setText(String.valueOf(digital));
        setStatus("Showing " + total + " book(s).", false);
    }

    @FXML
    private void handleNew() {
        openBookForm(null);
    }

    @FXML
    private void handleEdit() {
        Book selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a book to edit."); return; }
        openBookForm(selected);
    }

    @FXML
    private void handleDelete() {
        Book selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a book to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getTitle() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                bookService.supprimer(selected.getId());
                setStatus("Book deleted successfully.", false);
                loadData();
            } catch (SQLException e) {
                showError(e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefresh() {
        loadData();
        setStatus("Refreshed.", false);
    }

    private void openBookForm(Book book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookForm.fxml"));
            Parent root = loader.load();
            BookFormController ctrl = loader.getController();
            ctrl.initData(book, this::loadData);

            Stage stage = new Stage();
            stage.setTitle(book == null ? "New Book" : "Edit Book");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open form: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
    }

    private void showError(String msg) {
        setStatus("⚠ " + msg, true);
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
