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
import javafx.stage.Stage;
import models.library.Book;
import services.library.BookService;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class BookListController implements Initializable {

    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, String> colTitle;
    @FXML private TableColumn<Book, String> colAuthor;
    @FXML private TableColumn<Book, String> colIsbn;
    @FXML private TableColumn<Book, Double> colPrice;
    @FXML private TableColumn<Book, String> colType;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private Label statusLabel;

    private final BookService bookService = new BookService();
    private final ObservableList<Book> bookData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        // Color-code type
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("digital".equals(item)
                        ? "-fx-text-fill: #0d6efd; -fx-font-weight: bold;"
                        : "-fx-text-fill: #198754; -fx-font-weight: bold;");
            }
        });

        bookTable.setItems(bookData);

        // Double-click to open detail
        bookTable.setRowFactory(tv -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openBookDetail(row.getItem());
            });
            return row;
        });

        filterType.getItems().addAll("All Types", "physical", "digital");
        filterType.setValue("All Types");
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterType.valueProperty().addListener((obs, o, n) -> applyFilters());

        applyFilters();
    }

    @FXML
    private void applyFilters() {
        String type   = filterType.getValue();
        String search = searchField.getText();
        bookData.setAll(bookService.findByFilters(
                ("All Types".equals(type) || type == null) ? null : type,
                (search == null || search.isEmpty()) ? null : search
        ));
        statusLabel.setText("Showing " + bookData.size() + " book(s)");
    }

    @FXML
    private void handleViewBook() {
        Book selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, "Please select a book first.", ButtonType.OK).showAndWait();
            return;
        }
        openBookDetail(selected);
    }

    private void openBookDetail(Book book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            BookDetailController ctrl = loader.getController();
            ctrl.initData(book);

            Stage stage = (Stage) bookTable.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open book: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
