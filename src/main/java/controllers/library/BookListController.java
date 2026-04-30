package controllers.library;

import controllers.NovaDashboardController;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import models.library.Book;
import services.library.BookService;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BookListController implements Initializable {

    @FXML private FlowPane booksGrid;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private Label statusLabel;
    @FXML private HBox bestSellersRow;
    @FXML private VBox heroSection;
    @FXML private Label heroTitle;
    @FXML private Label heroSubtitle;

    private final BookService bookService = new BookService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        filterType.getItems().addAll("All Types", "physical", "digital");
        filterType.setValue("All Types");
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        filterType.valueProperty().addListener((obs, o, n) -> applyFilters());

        // Animate hero on load
        animateHero();
        loadBestSellers();
        applyFilters();
    }

    private void animateHero() {
        if (heroSection == null) return;
        heroSection.setOpacity(0);
        heroSection.setTranslateY(-20);
        FadeTransition ft = new FadeTransition(Duration.millis(700), heroSection);
        ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(700), heroSection);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(ft, tt).play();
    }

    private void loadBestSellers() {
        List<Book> best = bookService.findBestSellers(5);
        if (bestSellersRow == null || best.isEmpty()) return;
        bestSellersRow.getChildren().clear();
        for (int i = 0; i < best.size(); i++) {
            HBox card = buildBestSellerCard(best.get(i), i + 1);
            card.setOpacity(0);
            bestSellersRow.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(500), card);
            ft.setDelay(Duration.millis(i * 100));
            ft.setToValue(1);
            ft.play();
        }
    }

    private HBox buildBestSellerCard(Book book, int rank) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(220);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 14;" +
            "-fx-border-radius: 14;" +
            "-fx-border-color: #fde68a;" +
            "-fx-border-width: 2;" +
            "-fx-padding: 14 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(251,191,36,0.25), 10, 0, 0, 3);" +
            "-fx-cursor: hand;"
        );

        String[] rankColors = {"#f59e0b","#94a3b8","#cd7c2f","#6366f1","#10b981"};
        String color = rankColors[Math.min(rank-1, rankColors.length-1)];

        Label rankLbl = new Label("#" + rank);
        rankLbl.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label icon = new Label(book.isDigital() ? "💻" : "📖");
        icon.setStyle("-fx-font-size: 26;");

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(book.getTitle());
        title.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        title.setWrapText(true); title.setMaxWidth(120);
        Label price = new Label(book.getPrice() > 0 ? String.format("$%.2f", book.getPrice()) : "Free");
        price.setStyle("-fx-font-size: 13; -fx-text-fill: #059669; -fx-font-weight: bold;");
        info.getChildren().addAll(title, price);

        card.getChildren().addAll(rankLbl, icon, info);
        card.setOnMouseClicked(e -> openBookDetail(book));

        ScaleTransition si = new ScaleTransition(Duration.millis(130), card);
        si.setToX(1.05); si.setToY(1.05);
        ScaleTransition so = new ScaleTransition(Duration.millis(130), card);
        so.setToX(1.0); so.setToY(1.0);
        card.setOnMouseEntered(e -> si.playFromStart());
        card.setOnMouseExited(e -> so.playFromStart());
        return card;
    }

    @FXML
    private void applyFilters() {
        String type   = filterType.getValue();
        String search = searchField.getText();
        List<Book> books = bookService.findByFilters(
                ("All Types".equals(type) || type == null) ? null : type,
                (search == null || search.isEmpty()) ? null : search
        );
        buildCards(books);
        statusLabel.setText(books.size() + " book(s) found");
    }

    private void buildCards(List<Book> books) {
        booksGrid.getChildren().clear();
        if (books.isEmpty()) {
            VBox empty = new VBox(16);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 80;");
            Label icon = new Label("📚");
            icon.setStyle("-fx-font-size: 64;");
            Label msg = new Label("No books found");
            msg.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #475569;");
            Label sub = new Label("Try a different search or filter");
            sub.setStyle("-fx-font-size: 14; -fx-text-fill: #94a3b8;");
            empty.getChildren().addAll(icon, msg, sub);
            booksGrid.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < books.size(); i++) {
            VBox card = buildBookCard(books.get(i));
            card.setOpacity(0);
            card.setTranslateY(30);
            booksGrid.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(400), card);
            ft.setDelay(Duration.millis(i * 60));
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(400), card);
            tt.setDelay(Duration.millis(i * 60));
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        }
    }

    private VBox buildBookCard(Book book) {
        VBox card = new VBox(0);
        card.setPrefWidth(230);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 20;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 20;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.09), 14, 0, 0, 5);" +
            "-fx-cursor: hand;"
        );

        // Large gradient cover
        VBox cover = new VBox();
        cover.setPrefHeight(160);
        cover.setAlignment(Pos.CENTER);
        String gradient = book.isDigital()
                ? "linear-gradient(to bottom right, #4f46e5, #7c3aed, #a855f7)"
                : "linear-gradient(to bottom right, #0284c7, #0ea5e9, #38bdf8)";
        cover.setStyle("-fx-background-color: " + gradient + "; -fx-background-radius: 20 20 0 0;");

        Label coverIcon = new Label(book.isDigital() ? "💻" : "📖");
        coverIcon.setStyle("-fx-font-size: 56;");

        // Subtle shine overlay effect via a lighter strip
        Region shine = new Region();
        shine.setPrefHeight(40);
        shine.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0.15), transparent); -fx-background-radius: 20 20 0 0;");

        StackPane coverStack = new StackPane(cover, shine);
        coverStack.setPrefHeight(160);
        cover.getChildren().add(coverIcon);

        // Info section
        VBox info = new VBox(8);
        info.setStyle("-fx-padding: 16 18 12 18;");

        // Badge row
        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label(book.isDigital() ? "Digital PDF" : "Physical");
        badge.setStyle(book.isDigital()
                ? "-fx-background-color: #ede9fe; -fx-text-fill: #5b21b6; -fx-padding: 3 12; -fx-background-radius: 20; -fx-font-size: 11; -fx-font-weight: bold;"
                : "-fx-background-color: #dcfce7; -fx-text-fill: #14532d; -fx-padding: 3 12; -fx-background-radius: 20; -fx-font-size: 11; -fx-font-weight: bold;");
        badgeRow.getChildren().add(badge);

        Label title = new Label(book.getTitle());
        title.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        title.setWrapText(true); title.setMaxWidth(194);

        Label author = new Label(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        author.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");

        // Price + button row
        HBox priceRow = new HBox();
        priceRow.setAlignment(Pos.CENTER_LEFT);
        Label price = new Label(book.getPrice() > 0 ? String.format("$%.2f", book.getPrice()) : "Free");
        price.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #059669;");
        HBox.setHgrow(price, Priority.ALWAYS);
        priceRow.getChildren().add(price);

        info.getChildren().addAll(badgeRow, title, author, priceRow);

        // Full-width CTA button
        Button btnView = new Button("View Details →");
        btnView.setMaxWidth(Double.MAX_VALUE);
        btnView.setStyle(
            "-fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed);" +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;" +
            "-fx-background-radius: 0 0 20 20; -fx-padding: 13; -fx-cursor: hand;"
        );
        btnView.setOnAction(e -> openBookDetail(book));

        card.getChildren().addAll(coverStack, info, btnView);

        // Hover: scale + shadow boost
        ScaleTransition si = new ScaleTransition(Duration.millis(160), card);
        si.setToX(1.04); si.setToY(1.04);
        ScaleTransition so = new ScaleTransition(Duration.millis(160), card);
        so.setToX(1.0); so.setToY(1.0);
        card.setOnMouseEntered(e -> {
            si.playFromStart();
            card.setStyle(card.getStyle()
                    .replace("-fx-border-color: #e2e8f0", "-fx-border-color: #6366f1")
                    .replace("rgba(0,0,0,0.09)", "rgba(99,102,241,0.22)"));
        });
        card.setOnMouseExited(e -> {
            so.playFromStart();
            card.setStyle(card.getStyle()
                    .replace("-fx-border-color: #6366f1", "-fx-border-color: #e2e8f0")
                    .replace("rgba(99,102,241,0.22)", "rgba(0,0,0,0.09)"));
        });

        return card;
    }

    private void openBookDetail(Book book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            ((BookDetailController) loader.getController()).initData(book);
            NovaDashboardController.setView(root);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).showAndWait();
        }
    }
}
