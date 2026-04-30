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
import models.library.Loan;
import services.library.LoanService;
import services.library.PaymentService;
import utils.SessionManager;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MyLibraryController implements Initializable {

    @FXML private FlowPane booksGrid;
    @FXML private VBox loansContainer;
    @FXML private Label lblCount;
    @FXML private TextField searchField;
    @FXML private ScrollPane scrollBooks, scrollLoans;
    @FXML private Button tabBooks, tabLoans;

    private final PaymentService paymentService = new PaymentService();
    private final LoanService loanService = new LoanService();
    private List<Book> allBooks;
    private List<Loan> allLoans;
    private boolean showingBooks = true;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        allBooks = paymentService.findPurchasedBooks(SessionManager.getCurrentUserId());
        allLoans = loanService.findByUser(SessionManager.getCurrentUserId());
        searchField.textProperty().addListener((obs, o, n) -> {
            if (showingBooks) applySearch(n);
        });
        buildBookCards(allBooks);
        updateCount();
    }

    private void updateCount() {
        lblCount.setText(allBooks.size() + " purchased book" + (allBooks.size() != 1 ? "s" : "") +
                " · " + allLoans.size() + " loan" + (allLoans.size() != 1 ? "s" : ""));
    }

    // ── TAB SWITCHING ─────────────────────────────────────────────────────────

    @FXML private void showBooks() {
        showingBooks = true;
        scrollBooks.setVisible(true); scrollBooks.setManaged(true);
        scrollLoans.setVisible(false); scrollLoans.setManaged(false);
        tabBooks.setStyle(tabBooks.getStyle()
                .replace("-fx-background-color: transparent", "-fx-background-color: rgba(255,255,255,0.15)")
                .replace("-fx-text-fill: rgba(255,255,255,0.6)", "-fx-text-fill: white"));
        tabLoans.setStyle(tabLoans.getStyle()
                .replace("-fx-background-color: rgba(255,255,255,0.15)", "-fx-background-color: transparent")
                .replace("-fx-text-fill: white;", "-fx-text-fill: rgba(255,255,255,0.6);"));
    }

    @FXML private void showLoans() {
        showingBooks = false;
        scrollLoans.setVisible(true); scrollLoans.setManaged(true);
        scrollBooks.setVisible(false); scrollBooks.setManaged(false);
        tabLoans.setStyle(tabLoans.getStyle()
                .replace("-fx-background-color: transparent", "-fx-background-color: rgba(255,255,255,0.15)")
                .replace("-fx-text-fill: rgba(255,255,255,0.6)", "-fx-text-fill: white"));
        tabBooks.setStyle(tabBooks.getStyle()
                .replace("-fx-background-color: rgba(255,255,255,0.15)", "-fx-background-color: transparent")
                .replace("-fx-text-fill: white;", "-fx-text-fill: rgba(255,255,255,0.6);"));
        buildLoanCards(allLoans);
    }

    // ── BOOKS SECTION ─────────────────────────────────────────────────────────

    private void applySearch(String query) {
        if (query == null || query.isBlank()) { buildBookCards(allBooks); return; }
        String q = query.toLowerCase();
        buildBookCards(allBooks.stream()
                .filter(b -> b.getTitle().toLowerCase().contains(q)
                        || (b.getAuthor() != null && b.getAuthor().toLowerCase().contains(q)))
                .toList());
    }

    private void buildBookCards(List<Book> books) {
        booksGrid.getChildren().clear();
        if (books.isEmpty()) {
            VBox empty = new VBox(16);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 60;");
            Label icon = new Label("📚"); icon.setStyle("-fx-font-size: 60;");
            Label msg = new Label("Your library is empty");
            msg.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #475569;");
            Label sub = new Label("Purchase a digital book to start reading");
            sub.setStyle("-fx-font-size: 13; -fx-text-fill: #94a3b8;");
            Button btn = new Button("Browse Books");
            btn.setStyle("-fx-background-color: linear-gradient(to right, #6366f1, #8b5cf6); " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; " +
                    "-fx-padding: 12 28; -fx-cursor: hand;");
            btn.setOnAction(e -> NovaDashboardController.loadPage("/views/library/BookListView.fxml"));
            empty.getChildren().addAll(icon, msg, sub, btn);
            booksGrid.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < books.size(); i++) {
            VBox card = buildBookCard(books.get(i));
            card.setOpacity(0); card.setTranslateY(20);
            booksGrid.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(350), card);
            ft.setDelay(Duration.millis(i * 60)); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), card);
            tt.setDelay(Duration.millis(i * 60)); tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        }
    }

    private VBox buildBookCard(Book book) {
        VBox card = new VBox(0);
        card.setPrefWidth(210);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; " +
                "-fx-border-color: #e2e8f0; -fx-border-radius: 16; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 4); -fx-cursor: hand;");

        VBox cover = new VBox();
        cover.setPrefHeight(140); cover.setAlignment(Pos.CENTER);
        cover.setStyle("-fx-background-color: linear-gradient(to bottom right, #7c3aed, #6366f1, #818cf8); " +
                "-fx-background-radius: 16 16 0 0;");
        Label coverIcon = new Label(book.isDigital() ? "📄" : "📖");
        coverIcon.setStyle("-fx-font-size: 52;");
        cover.getChildren().add(coverIcon);

        VBox info = new VBox(6); info.setStyle("-fx-padding: 14 14 10 14;");
        Label title = new Label(book.getTitle());
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        title.setWrapText(true); title.setMaxWidth(182);
        Label author = new Label(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        author.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
        boolean hasPdf = book.getPdfUrl() != null && !book.getPdfUrl().isBlank();
        Label badge = new Label(hasPdf ? "PDF Available" : "No PDF");
        badge.setStyle(hasPdf
                ? "-fx-background-color: #dcfce7; -fx-text-fill: #14532d; -fx-padding: 3 10; -fx-background-radius: 20; -fx-font-size: 11; -fx-font-weight: bold;"
                : "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 3 10; -fx-background-radius: 20; -fx-font-size: 11; -fx-font-weight: bold;");
        info.getChildren().addAll(title, author, badge);

        Button btnRead = new Button(hasPdf ? "📖 Read Now" : "No PDF");
        btnRead.setMaxWidth(Double.MAX_VALUE); btnRead.setDisable(!hasPdf);
        btnRead.setStyle(hasPdf
                ? "-fx-background-color: linear-gradient(to right, #7c3aed, #6366f1); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 0 0 16 16; -fx-padding: 11; -fx-cursor: hand; -fx-font-size: 13;"
                : "-fx-background-color: #f1f5f9; -fx-text-fill: #94a3b8; -fx-background-radius: 0 0 16 16; -fx-padding: 11; -fx-font-size: 13;");
        btnRead.setOnAction(e -> openPdfViewer(book));
        card.getChildren().addAll(cover, info, btnRead);

        ScaleTransition si = new ScaleTransition(Duration.millis(150), card);
        si.setToX(1.04); si.setToY(1.04);
        ScaleTransition so = new ScaleTransition(Duration.millis(150), card);
        so.setToX(1.0); so.setToY(1.0);
        card.setOnMouseEntered(e -> { si.playFromStart();
            card.setStyle(card.getStyle().replace("-fx-border-color: #e2e8f0", "-fx-border-color: #7c3aed")); });
        card.setOnMouseExited(e -> { so.playFromStart();
            card.setStyle(card.getStyle().replace("-fx-border-color: #7c3aed", "-fx-border-color: #e2e8f0")); });
        return card;
    }

    // ── LOANS SECTION ─────────────────────────────────────────────────────────

    private void buildLoanCards(List<Loan> loans) {
        loansContainer.getChildren().clear();
        if (loans.isEmpty()) {
            VBox empty = new VBox(12); empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 60;");
            Label icon = new Label("🔖"); icon.setStyle("-fx-font-size: 52;");
            Label msg = new Label("No loans yet");
            msg.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #475569;");
            empty.getChildren().addAll(icon, msg);
            loansContainer.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < loans.size(); i++) {
            HBox card = buildLoanCard(loans.get(i));
            card.setOpacity(0); card.setTranslateX(-20);
            loansContainer.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(350), card);
            ft.setDelay(Duration.millis(i * 60)); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), card);
            tt.setDelay(Duration.millis(i * 60)); tt.setToX(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        }
    }

    private HBox buildLoanCard(Loan loan) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; " +
                "-fx-border-radius: 14; -fx-border-color: #e2e8f0; -fx-padding: 18 22; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 3);");

        // Status icon + color
        String[] statusConfig = getStatusConfig(loan.getStatus());
        String statusColor = statusConfig[0];
        String statusIcon  = statusConfig[1];
        String statusLabel = statusConfig[2];

        // Left: status indicator bar
        Region bar = new Region();
        bar.setPrefWidth(5); bar.setPrefHeight(60);
        bar.setStyle("-fx-background-color: " + statusColor + "; -fx-background-radius: 3;");

        // Book icon
        Label bookIcon = new Label("📖");
        bookIcon.setStyle("-fx-font-size: 32;");

        // Info
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label bookTitle = new Label(loan.getBookTitle() != null ? loan.getBookTitle() : "Book #" + loan.getBookId());
        bookTitle.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label libName = new Label(loan.getLibraryName() != null ? "🏛 " + loan.getLibraryName() : "");
        libName.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
        Label dates = new Label(formatDates(loan));
        dates.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        info.getChildren().addAll(bookTitle, libName, dates);

        // Status badge
        VBox statusBox = new VBox(6);
        statusBox.setAlignment(Pos.CENTER);
        Label statusBadge = new Label(statusIcon + " " + statusLabel);
        statusBadge.setStyle("-fx-background-color: " + statusColor + "22; " +
                "-fx-text-fill: " + statusColor + "; -fx-font-weight: bold; " +
                "-fx-padding: 6 14; -fx-background-radius: 20; -fx-font-size: 12;");
        statusBox.getChildren().add(statusBadge);

        // Overdue warning
        if (loan.isOverdue()) {
            Label overdue = new Label("⚠ Overdue");
            overdue.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; " +
                    "-fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 20; -fx-font-size: 11;");
            statusBox.getChildren().add(overdue);
        }

        card.getChildren().addAll(bar, bookIcon, info, statusBox);

        // Edit dates button — only for PENDING loans
        if (Loan.STATUS_PENDING.equals(loan.getStatus())) {
            Button btnEdit = new Button("✏ Edit Dates");
            btnEdit.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; " +
                    "-fx-background-radius: 8; -fx-border-color: #fbbf24; -fx-border-radius: 8; " +
                    "-fx-padding: 6 14; -fx-cursor: hand; -fx-font-size: 12;");
            btnEdit.setOnAction(e -> showEditDatesDialog(loan));
            card.getChildren().add(btnEdit);
        }

        // Hover
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #e2e8f0", "-fx-border-color: " + statusColor)));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: " + statusColor, "-fx-border-color: #e2e8f0")));
        return card;
    }

    private String[] getStatusConfig(String status) {
        return switch (status) {
            case Loan.STATUS_PENDING  -> new String[]{"#f59e0b", "⏳", "Pending"};
            case Loan.STATUS_APPROVED -> new String[]{"#3b82f6", "✅", "Approved"};
            case Loan.STATUS_ACTIVE   -> new String[]{"#10b981", "📗", "Active"};
            case Loan.STATUS_RETURNED -> new String[]{"#6366f1", "↩", "Returned"};
            case Loan.STATUS_REJECTED -> new String[]{"#ef4444", "❌", "Rejected"};
            case Loan.STATUS_OVERDUE  -> new String[]{"#dc2626", "🔴", "Overdue"};
            default                   -> new String[]{"#94a3b8", "❓", status};
        };
    }

    private String formatDates(Loan loan) {
        StringBuilder sb = new StringBuilder();
        if (loan.getRequestedAt() != null)
            sb.append("Requested: ").append(loan.getRequestedAt().toLocalDateTime().toLocalDate());
        if (loan.getEndAt() != null)
            sb.append("  ·  Due: ").append(loan.getEndAt().toLocalDateTime().toLocalDate());
        return sb.toString();
    }

    private void showEditDatesDialog(Loan loan) {
        // Build a custom styled stage instead of the plain Dialog
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("Edit Loan Dates");
        stage.setResizable(false);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0f172a;");

        // Header
        VBox header = new VBox(4);
        header.setStyle("-fx-background-color: linear-gradient(to right, #1e1b4b, #312e81); -fx-padding: 20 24;");
        Label title = new Label("✏ Edit Loan Dates");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: white;");
        Label subtitle = new Label("📖 " + (loan.getBookTitle() != null ? loan.getBookTitle() : "Loan #" + loan.getId()));
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: rgba(255,255,255,0.65);");
        header.getChildren().addAll(title, subtitle);

        // Body
        VBox body = new VBox(20);
        body.setStyle("-fx-background-color: #1e293b; -fx-padding: 28 28 20 28;");

        // Info note
        HBox note = new HBox(10);
        note.setAlignment(Pos.CENTER_LEFT);
        note.setStyle("-fx-background-color: rgba(99,102,241,0.15); -fx-border-color: #6366f1; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14;");
        Label noteIcon = new Label("ℹ");
        noteIcon.setStyle("-fx-font-size: 16; -fx-text-fill: #a5b4fc;");
        Label noteText = new Label("Maximum loan period is 14 days.");
        noteText.setStyle("-fx-font-size: 12; -fx-text-fill: #a5b4fc;");
        note.getChildren().addAll(noteIcon, noteText);

        // Date pickers
        DatePicker pickupPicker = new DatePicker();
        DatePicker returnPicker = new DatePicker();
        pickupPicker.setMaxWidth(Double.MAX_VALUE);
        returnPicker.setMaxWidth(Double.MAX_VALUE);

        if (loan.getStartAt() != null)
            pickupPicker.setValue(loan.getStartAt().toLocalDateTime().toLocalDate());
        if (loan.getEndAt() != null)
            returnPicker.setValue(loan.getEndAt().toLocalDateTime().toLocalDate());
        else if (pickupPicker.getValue() != null)
            returnPicker.setValue(pickupPicker.getValue().plusDays(14));

        // Auto-set return date when pickup changes
        pickupPicker.valueProperty().addListener((obs, o, n) -> {
            if (n != null) returnPicker.setValue(n.plusDays(14));
        });

        VBox pickupBox = new VBox(6);
        Label pickupLbl = new Label("Pickup Date");
        pickupLbl.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        pickupBox.getChildren().addAll(pickupLbl, pickupPicker);

        VBox returnBox = new VBox(6);
        Label returnLbl = new Label("Return Date (max 14 days from pickup)");
        returnLbl.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        returnBox.getChildren().addAll(returnLbl, returnPicker);

        // Error label
        Label errLabel = new Label("");
        errLabel.setStyle("-fx-text-fill: #f87171; -fx-font-size: 12; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        body.getChildren().addAll(note, pickupBox, returnBox, errLabel);

        // Footer buttons
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color: #0f172a; -fx-padding: 16 24;");

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-font-weight: bold; " +
                "-fx-background-radius: 8; -fx-padding: 10 22; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> stage.close());

        Button btnSave = new Button("💾 Save Changes");
        btnSave.setStyle("-fx-background-color: linear-gradient(to right, #6366f1, #8b5cf6); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; " +
                "-fx-padding: 10 22; -fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.4), 8, 0, 0, 2);");
        btnSave.setOnAction(e -> {
            errLabel.setText("");
            if (pickupPicker.getValue() == null || returnPicker.getValue() == null) {
                errLabel.setText("Please select both dates."); return;
            }
            if (returnPicker.getValue().isBefore(pickupPicker.getValue())) {
                errLabel.setText("Return date must be after pickup date."); return;
            }
            if (returnPicker.getValue().isAfter(pickupPicker.getValue().plusDays(14))) {
                errLabel.setText("Loan period cannot exceed 14 days."); return;
            }
            try {
                loanService.updateDates(
                        loan.getId(),
                        java.sql.Timestamp.valueOf(pickupPicker.getValue().atStartOfDay()),
                        java.sql.Timestamp.valueOf(returnPicker.getValue().atStartOfDay())
                );
                allLoans = loanService.findByUser(SessionManager.getCurrentUserId());
                buildLoanCards(allLoans);
                stage.close();
            } catch (Exception ex) {
                errLabel.setText("Error: " + ex.getMessage());
            }
        });

        footer.getChildren().addAll(btnCancel, btnSave);
        root.getChildren().addAll(header, body, footer);

        stage.setScene(new javafx.scene.Scene(root, 420, 420));
        stage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.show();
    }

    private void openPdfViewer(Book book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/PdfViewer.fxml"));
            Parent root = loader.load();
            ((PdfViewerController) loader.getController()).initData(book);
            NovaDashboardController.setView(root);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            new Alert(Alert.AlertType.ERROR, "Cannot open PDF viewer:\n" +
                    cause.getClass().getSimpleName() + ": " + cause.getMessage()).showAndWait();
        }
    }

    @FXML private void handleBrowse() {
        NovaDashboardController.loadPage("/views/library/BookListView.fxml");
    }
}
