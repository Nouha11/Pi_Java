package controllers.library;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import models.library.Loan;
import services.library.LoanService;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class LoanController implements Initializable {

    @FXML private TableView<Loan> loanTable;
    @FXML private TableColumn<Loan, Integer>   colId;
    @FXML private TableColumn<Loan, String>    colBook;
    @FXML private TableColumn<Loan, String>    colUser;
    @FXML private TableColumn<Loan, String>    colLibrary;
    @FXML private TableColumn<Loan, String>    colStatus;
    @FXML private TableColumn<Loan, String>    colRequested;

    @FXML private ComboBox<String> filterStatus;
    @FXML private Label statusLabel;
    @FXML private Label statPending;
    @FXML private Label statApproved;
    @FXML private Label statActive;
    @FXML private Label statReturned;
    @FXML private Label statRejected;

    private final LoanService loanService = new LoanService();
    private final ObservableList<Loan> loanData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBook.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colLibrary.setCellValueFactory(new PropertyValueFactory<>("libraryName"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colRequested.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getRequestedAt() != null
                        ? d.getValue().getRequestedAt().toString().substring(0, 10) : ""));

        // Color-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle(switch (status) {
                    case "PENDING"  -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "APPROVED" -> "-fx-text-fill: #3498db; -fx-font-weight: bold;";
                    case "ACTIVE"   -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "RETURNED" -> "-fx-text-fill: #95a5a6; -fx-font-weight: bold;";
                    case "REJECTED" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "OVERDUE"  -> "-fx-text-fill: #c0392b; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        loanTable.setItems(loanData);
    }

    private void setupFilters() {
        filterStatus.getItems().addAll("ALL", "PENDING", "APPROVED", "ACTIVE", "RETURNED", "REJECTED", "OVERDUE");
        filterStatus.setValue("ALL");
        filterStatus.valueProperty().addListener((obs, o, n) -> loadData());
    }

    private void loadData() {
        String status = filterStatus.getValue();
        loanData.setAll("ALL".equals(status)
                ? loanService.afficher()
                : loanService.findByStatus(status));

        // Update stat cards from full list
        List<Loan> all = loanService.afficher();
        if (statPending  != null) statPending.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_PENDING.equals(l.getStatus())).count()));
        if (statApproved != null) statApproved.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_APPROVED.equals(l.getStatus())).count()));
        if (statActive   != null) statActive.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_ACTIVE.equals(l.getStatus())).count()));
        if (statReturned != null) statReturned.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_RETURNED.equals(l.getStatus())).count()));
        if (statRejected != null) statRejected.setText(String.valueOf(all.stream().filter(l -> Loan.STATUS_REJECTED.equals(l.getStatus())).count()));

        setStatus("Showing " + loanData.size() + " loan(s).", false);
    }

    @FXML
    private void handleApprove() {
        Loan selected = loanTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a loan."); return; }
        try {
            loanService.approuver(selected.getId());
            setStatus("✅ Loan #" + selected.getId() + " approved!", false);
            loadData();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleReject() {
        Loan selected = loanTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a loan."); return; }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Loan");
        dialog.setHeaderText("Reason for rejection:");
        dialog.setContentText("Reason:");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                loanService.rejeter(selected.getId(), reason);
                setStatus("❌ Loan #" + selected.getId() + " rejected.", false);
                loadData();
            } catch (SQLException e) {
                showError(e.getMessage());
            }
        });
    }

    @FXML
    private void handleMarkActive() {
        Loan selected = loanTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a loan."); return; }
        try {
            loanService.marquerActif(selected.getId());
            setStatus("📚 Loan #" + selected.getId() + " marked as active (14 days).", false);
            loadData();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleMarkReturned() {
        Loan selected = loanTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Please select a loan."); return; }
        try {
            loanService.marquerRetourne(selected.getId());
            setStatus("📦 Loan #" + selected.getId() + " returned.", false);
            loadData();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadData();
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
