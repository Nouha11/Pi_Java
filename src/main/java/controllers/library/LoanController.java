package controllers.library;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import models.library.Loan;
import services.library.LoanService;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class LoanController implements Initializable {

    @FXML private VBox loansContainer;
    @FXML private ComboBox<String> filterStatus;
    @FXML private Label statusLabel;
    @FXML private Label statPending, statApproved, statActive, statReturned, statRejected;

    private final LoanService loanService = new LoanService();
    private List<Loan> currentLoans;
    private Loan selectedLoan = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        filterStatus.getItems().addAll("ALL","PENDING","APPROVED","ACTIVE","RETURNED","REJECTED","OVERDUE");
        filterStatus.setValue("ALL");
        filterStatus.valueProperty().addListener((obs, o, n) -> loadData());
        loadData();
    }

    private void loadData() {
        String status = filterStatus.getValue();
        currentLoans = "ALL".equals(status) ? loanService.afficher() : loanService.findByStatus(status);
        selectedLoan = null;
        buildCards(currentLoans);

        List<Loan> all = loanService.afficher();
        if (statPending  != null) statPending.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_PENDING.equals(l.getStatus())).count()));
        if (statApproved != null) statApproved.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_APPROVED.equals(l.getStatus())).count()));
        if (statActive   != null) statActive.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_ACTIVE.equals(l.getStatus())).count()));
        if (statReturned != null) statReturned.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_RETURNED.equals(l.getStatus())).count()));
        if (statRejected != null) statRejected.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_REJECTED.equals(l.getStatus())).count()));
        statusLabel.setText(currentLoans.size() + " loan(s)");
    }

    private void buildCards(List<Loan> loans) {
        loansContainer.getChildren().clear();
        if (loans.isEmpty()) {
            VBox empty = new VBox(12); empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 60;");
            Label icon = new Label("📋"); icon.setStyle("-fx-font-size: 48;");
            Label msg = new Label("No loans found");
            msg.setStyle("-fx-font-size: 16; -fx-text-fill: #64748b;");
            empty.getChildren().addAll(icon, msg);
            loansContainer.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < loans.size(); i++) {
            HBox card = buildLoanCard(loans.get(i));
            card.setOpacity(0); card.setTranslateX(-16);
            loansContainer.getChildren().add(card);
            FadeTransition ft = new FadeTransition(Duration.millis(300), card);
            ft.setDelay(Duration.millis(i * 40)); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), card);
            tt.setDelay(Duration.millis(i * 40)); tt.setToX(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        }
    }

    private HBox buildLoanCard(Loan loan) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; " +
            "-fx-padding: 16 20; -fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);"
        );

        String[] cfg = statusConfig(loan.getStatus());
        String color = cfg[0], icon = cfg[1], label = cfg[2];

        // Left color bar
        Region bar = new Region();
        bar.setPrefWidth(4); bar.setPrefHeight(56);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");

        // Book icon
        Label bookIcon = new Label("📖");
        bookIcon.setStyle("-fx-font-size: 28;");

        // Info
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label bookTitle = new Label(loan.getBookTitle() != null ? loan.getBookTitle() : "Book #" + loan.getBookId());
        bookTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox meta = new HBox(16);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (loan.getUserName() != null) {
            Label user = new Label("👤 " + loan.getUserName());
            user.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
            meta.getChildren().add(user);
        }
        if (loan.getLibraryName() != null) {
            Label lib = new Label("🏛 " + loan.getLibraryName());
            lib.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
            meta.getChildren().add(lib);
        }
        Label date = new Label("📅 " + (loan.getRequestedAt() != null
                ? loan.getRequestedAt().toString().substring(0, 10) : ""));
        date.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        info.getChildren().addAll(bookTitle, meta, date);

        // Status badge
        Label badge = new Label(icon + " " + label);
        badge.setStyle("-fx-background-color: " + color + "22; -fx-text-fill: " + color + "; " +
                "-fx-font-weight: bold; -fx-padding: 5 14; -fx-background-radius: 20; -fx-font-size: 12;");

        // Action buttons (shown on hover via selection)
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);

        if (Loan.STATUS_PENDING.equals(loan.getStatus())) {
            Button btnApprove = actionBtn("✅", "#059669", "#dcfce7");
            btnApprove.setOnAction(e -> { e.consume(); approveLoan(loan); });
            Button btnReject = actionBtn("❌", "#dc2626", "#fee2e2");
            btnReject.setOnAction(e -> { e.consume(); rejectLoan(loan); });
            actions.getChildren().addAll(btnApprove, btnReject);
        } else if (Loan.STATUS_APPROVED.equals(loan.getStatus())) {
            Button btnActive = actionBtn("▶", "#0284c7", "#e0f2fe");
            btnActive.setOnAction(e -> { e.consume(); markActive(loan); });
            actions.getChildren().add(btnActive);
        } else if (Loan.STATUS_ACTIVE.equals(loan.getStatus())) {
            Button btnReturn = actionBtn("↩", "#6366f1", "#ede9fe");
            btnReturn.setOnAction(e -> { e.consume(); markReturned(loan); });
            actions.getChildren().add(btnReturn);
        }

        card.getChildren().addAll(bar, bookIcon, info, badge, actions);

        // Hover
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #e2e8f0", "-fx-border-color: " + color)
                .replace("rgba(0,0,0,0.06)", "rgba(0,0,0,0.1)")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: " + color, "-fx-border-color: #e2e8f0")
                .replace("rgba(0,0,0,0.1)", "rgba(0,0,0,0.06)")));

        return card;
    }

    private Button actionBtn(String text, String textColor, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; " +
                "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 14;");
        return btn;
    }

    private void approveLoan(Loan loan) {
        try { loanService.approuver(loan.getId()); loadData(); }
        catch (SQLException e) { showError(e.getMessage()); }
    }

    private void rejectLoan(Loan loan) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Reject Loan"); d.setHeaderText("Reason for rejection:"); d.setContentText("Reason:");
        d.showAndWait().ifPresent(reason -> {
            try { loanService.rejeter(loan.getId(), reason); loadData(); }
            catch (SQLException e) { showError(e.getMessage()); }
        });
    }

    private void markActive(Loan loan) {
        try { loanService.marquerActif(loan.getId()); loadData(); }
        catch (SQLException e) { showError(e.getMessage()); }
    }

    private void markReturned(Loan loan) {
        try { loanService.marquerRetourne(loan.getId()); loadData(); }
        catch (SQLException e) { showError(e.getMessage()); }
    }

    @FXML private void handleRefresh() { loadData(); }

    private String[] statusConfig(String status) {
        return switch (status) {
            case "PENDING"  -> new String[]{"#f59e0b", "⏳", "Pending"};
            case "APPROVED" -> new String[]{"#3b82f6", "✅", "Approved"};
            case "ACTIVE"   -> new String[]{"#10b981", "📗", "Active"};
            case "RETURNED" -> new String[]{"#6366f1", "↩", "Returned"};
            case "REJECTED" -> new String[]{"#ef4444", "❌", "Rejected"};
            case "OVERDUE"  -> new String[]{"#dc2626", "🔴", "Overdue"};
            default         -> new String[]{"#94a3b8", "❓", status};
        };
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
