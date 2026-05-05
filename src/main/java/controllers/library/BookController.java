package controllers.library;

import controllers.NovaDashboardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.library.Book;
import services.library.BookService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class BookController implements Initializable {

    @FXML private VBox booksContainer;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private Label statusLabel;
    @FXML private Label statTotalBooks;
    @FXML private Label statPhysical;
    @FXML private Label statDigital;

    private final BookService bookService = new BookService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        filterType.getItems().addAll("", "physical", "digital");
        filterType.setValue("");
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterType.valueProperty().addListener((obs, o, n) -> applyFilters());
        applyFilters();
    }

    @FXML
    private void applyFilters() {
        String type   = filterType.getValue();
        String search = searchField.getText();
        List<Book> books = bookService.findByFilters(
                (type == null || type.isEmpty()) ? null : type,
                (search == null || search.isEmpty()) ? null : search
        );
        buildCards(books);

        long total    = books.size();
        long physical = books.stream().filter(b -> "physical".equals(b.getType())).count();
        long digital  = books.stream().filter(b -> "digital".equals(b.getType())).count();
        if (statTotalBooks != null) statTotalBooks.setText(String.valueOf(total));
        if (statPhysical   != null) statPhysical.setText(String.valueOf(physical));
        if (statDigital    != null) statDigital.setText(String.valueOf(digital));
        statusLabel.setText("Showing " + total + " book(s)");
    }

    private void buildCards(List<Book> books) {
        booksContainer.getChildren().clear();
        if (books.isEmpty()) {
            Label empty = new Label("No books found. Click '+ Add New Book' to get started.");
            empty.setStyle("-fx-font-size: 14; -fx-text-fill: #6c757d; -fx-padding: 40;");
            booksContainer.getChildren().add(empty);
            return;
        }
        for (Book book : books) {
            booksContainer.getChildren().add(buildBookCard(book));
        }
    }

    /**
     * Builds a horizontal card for each book with:
     * - Type icon + title + author + ISBN + price badge
     * - Edit (✏️) and Delete (🗑️) buttons on the right
     */
    private HBox buildBookCard(Book book) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 10; -fx-padding: 16 20; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");

        // Left: type icon
        Label icon = new Label(book.isDigital() ? "💻" : "📖");
        icon.setStyle("-fx-font-size: 32;");

        // Center: book info
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Title row with type badge
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(book.getTitle());
        title.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #212529;");

        Label badge = new Label(book.isDigital() ? "Digital" : "Physical");
        badge.setStyle(book.isDigital()
                ? "-fx-background-color: #cfe2ff; -fx-text-fill: #084298; -fx-padding: 2 8; -fx-background-radius: 20; -fx-font-size: 11;"
                : "-fx-background-color: #d1e7dd; -fx-text-fill: #0a3622; -fx-padding: 2 8; -fx-background-radius: 20; -fx-font-size: 11;");
        titleRow.getChildren().addAll(title, badge);

        Label author = new Label(book.getAuthor() != null ? book.getAuthor() : "Unknown author");
        author.setStyle("-fx-font-size: 13; -fx-text-fill: #6c757d;");

        Label isbn = new Label(book.getIsbn() != null && !book.getIsbn().isEmpty()
                ? "ISBN: " + book.getIsbn() : "No ISBN");
        isbn.setStyle("-fx-font-size: 12; -fx-text-fill: #adb5bd;");

        info.getChildren().addAll(titleRow, author, isbn);

        // Price badge
        Label price = new Label(book.getPrice() > 0
                ? String.format("$%.2f", book.getPrice()) : "Free");
        price.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #198754; " +
                "-fx-background-color: #d1e7dd; -fx-padding: 4 12; -fx-background-radius: 20;");

        // Action buttons
        Button btnEdit = new Button("✏️ Edit");
        btnEdit.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #664d03; -fx-font-weight: bold; " +
                "-fx-background-radius: 8; -fx-border-color: #ffc107; -fx-border-radius: 8; " +
                "-fx-padding: 6 14; -fx-cursor: hand;");
        btnEdit.setOnAction(e -> openBookForm(book));

        Button btnDelete = new Button("🗑️ Delete");
        btnDelete.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #842029; -fx-font-weight: bold; " +
                "-fx-background-radius: 8; -fx-border-color: #f5c2c7; -fx-border-radius: 8; " +
                "-fx-padding: 6 14; -fx-cursor: hand;");
        btnDelete.setOnAction(e -> handleDelete(book));

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER);
        actions.getChildren().addAll(btnEdit, btnDelete);

        card.getChildren().addAll(icon, info, price, actions);

        // Hover highlight
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #dee2e6", "-fx-border-color: #0d6efd")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #0d6efd", "-fx-border-color: #dee2e6")));

        return card;
    }

    @FXML
    private void handleNew() { openBookForm(null); }

    @FXML
    private void handleRefresh() { applyFilters(); }

    private void handleDelete(Book book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + book.getTitle() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                bookService.supprimer(book.getId());
                statusLabel.setText("Book deleted.");
                applyFilters();
            } catch (SQLException e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK).showAndWait();
            }
        }
    }

    private void openBookForm(Book book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookForm.fxml"));
            Parent root = loader.load();
            ((BookFormController) loader.getController()).initData(book, this::applyFilters);
            Stage stage = new Stage();
            stage.setTitle(book == null ? "New Book" : "Edit Book");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open form: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
