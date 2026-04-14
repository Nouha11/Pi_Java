package controllers.library;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.library.Book;
import models.library.Library;
import services.library.LibraryService;

import java.io.IOException;
import java.util.List;

public class LibrariesController {

    @FXML private Label lblBookName;
    @FXML private Label lblBookTitle;
    @FXML private Label lblBookAuthor;
    @FXML private Label lblLocationStatus;
    @FXML private VBox librariesContainer;

    private Book book;
    private final LibraryService libraryService = new LibraryService();
    private List<Library> libraries;

    public void initData(Book book) {
        this.book = book;
        lblBookName.setText(book.getTitle());
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown Author");

        libraries = libraryService.findAll();
        buildLibraryCards();
    }

    private void buildLibraryCards() {
        librariesContainer.getChildren().clear();

        if (libraries.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setStyle("-fx-background-color: #cff4fc; -fx-border-color: #9eeaf9; " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 20;");
            empty.setAlignment(javafx.geometry.Pos.CENTER);
            Label icon = new Label("ℹ️");
            icon.setStyle("-fx-font-size: 28;");
            Label msg = new Label("No libraries available for this book.");
            msg.setStyle("-fx-font-size: 14; -fx-text-fill: #055160;");
            empty.getChildren().addAll(icon, msg);
            librariesContainer.getChildren().add(empty);
            return;
        }

        for (Library lib : libraries) {
            librariesContainer.getChildren().add(buildLibraryCard(lib));
        }
    }

    private VBox buildLibraryCard(Library lib) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 2; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16;");

        // Header row
        HBox header = new HBox(12);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label icon = new Label("🏛");
        icon.setStyle("-fx-font-size: 28;");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label(lib.getName());
        name.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #212529;");

        Label address = new Label(lib.getAddress() != null ? "📍 " + lib.getAddress() : "");
        address.setStyle("-fx-font-size: 12; -fx-text-fill: #6c757d;");

        // Show coordinates if available
        if (lib.getLatitude() != 0 && lib.getLongitude() != 0) {
            Label coords = new Label(String.format("🌐 %.4f, %.4f", lib.getLatitude(), lib.getLongitude()));
            coords.setStyle("-fx-font-size: 11; -fx-text-fill: #adb5bd;");
            info.getChildren().addAll(name, address, coords);
        } else {
            info.getChildren().addAll(name, address);
        }

        header.getChildren().addAll(icon, info);

        // Request Loan button
        Button btnLoan = new Button("📚 Request Loan");
        btnLoan.setMaxWidth(Double.MAX_VALUE);
        btnLoan.setStyle("-fx-background-color: #0d6efd; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 6; -fx-padding: 8 16; -fx-cursor: hand;");
        btnLoan.setOnAction(e -> openLoanForm(lib));

        card.getChildren().addAll(header, btnLoan);

        // Hover highlight
        card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle().replace("-fx-border-color: #dee2e6", "-fx-border-color: #0d6efd")));
        card.setOnMouseExited(e ->
                card.setStyle(card.getStyle().replace("-fx-border-color: #0d6efd", "-fx-border-color: #dee2e6")));

        return card;
    }

    @FXML
    private void handleGetLocation() {
        lblLocationStatus.setText("📍 GPS location is not available in desktop mode.");
        lblLocationStatus.setStyle("-fx-text-fill: #6c757d;");
    }

    private void openLoanForm(Library library) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/LoanFormView.fxml"));
            Parent root = loader.load();

            LoanFormController ctrl = loader.getController();
            ctrl.initData(book, library); // Passing the data

            // ✅ THE FIX: Send the pre-loaded screen perfectly to the dashboard!
            controllers.NovaDashboardController.setView(root);

        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();

            BookDetailController ctrl = loader.getController();
            ctrl.initData(book); // Passing the data back

            // ✅ THE FIX: Seamlessly route back without a pop-up!
            controllers.NovaDashboardController.setView(root);

        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}