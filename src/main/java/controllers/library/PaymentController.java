package controllers.library;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import models.library.Payment;
import services.library.PaymentService;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class PaymentController implements Initializable {

    // ── Payment Form ──
    @FXML private TextField txtCardNumber;
    @FXML private TextField txtCardHolder;
    @FXML private TextField txtExpiry;
    @FXML private TextField txtCvc;
    @FXML private TextField txtAmount;
    @FXML private ComboBox<String> cbMethod;
    @FXML private Label lblResult;

    // Per-field error labels
    @FXML private Label errCardNumber;
    @FXML private Label errCardHolder;
    @FXML private Label errExpiry;
    @FXML private Label errCvc;
    @FXML private Label errAmount;

    // ── Payment History Table ──
    @FXML private TableView<Payment> paymentTable;
    @FXML private TableColumn<Payment, Integer> colId;
    @FXML private TableColumn<Payment, String>  colBook;
    @FXML private TableColumn<Payment, Double>  colAmount;
    @FXML private TableColumn<Payment, String>  colStatus;
    @FXML private TableColumn<Payment, String>  colMethod;
    @FXML private TableColumn<Payment, String>  colDate;

    @FXML private Label statusLabel;

    private final PaymentService paymentService = new PaymentService();
    private final ObservableList<Payment> paymentData = FXCollections.observableArrayList();

    // Demo user ID — in a real app this comes from the session
    private static final int DEMO_USER_ID = 1;
    private static final int DEMO_BOOK_ID = 1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbMethod.getItems().addAll("credit_card", "paypal");
        cbMethod.setValue("credit_card");
        cbMethod.valueProperty().addListener((obs, o, n) -> toggleCardFields());

        setupTable();
        loadHistory();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBook.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colMethod.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        colDate.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getCreatedAt() != null
                        ? d.getValue().getCreatedAt().toString().substring(0, 10) : ""));

        // Color-code status
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle(switch (status) {
                    case "COMPLETED" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "FAILED"    -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "PENDING"   -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        paymentTable.setItems(paymentData);
    }

    private void loadHistory() {
        paymentData.setAll(paymentService.afficher());
        setStatus("Showing " + paymentData.size() + " payment(s).", false);
    }

    @FXML
    private void handlePay() {
        clearErrors();
        if (!validateForm()) return;

        Payment payment = new Payment();
        payment.setUserId(DEMO_USER_ID);
        payment.setBookId(DEMO_BOOK_ID);
        payment.setAmount(Double.parseDouble(txtAmount.getText().trim()));
        payment.setPaymentMethod(cbMethod.getValue());

        String error;
        if ("credit_card".equals(cbMethod.getValue())) {
            error = paymentService.processerCarteBancaire(
                    payment,
                    txtCardNumber.getText(),
                    txtCardHolder.getText(),
                    txtExpiry.getText(),
                    txtCvc.getText()
            );
        } else {
            error = paymentService.processerPayPal(payment);
        }

        if (error == null) {
            try {
                paymentService.ajouter(payment);
                lblResult.setText("✅ Payment successful! Transaction: " + payment.getTransactionId());
                lblResult.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                loadHistory();
            } catch (SQLException e) {
                lblResult.setText("⚠ DB error: " + e.getMessage());
                lblResult.setStyle("-fx-text-fill: #e74c3c;");
            }
        } else {
            lblResult.setText("❌ " + error);
            lblResult.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void handleRefresh() {
        loadHistory();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    private boolean validateForm() {
        boolean ok = true;

        try {
            double val = Double.parseDouble(txtAmount.getText().trim());
            if (val <= 0) throw new NumberFormatException();
            errAmount.setVisible(false);
        } catch (NumberFormatException e) {
            errAmount.setText("Must be a positive number."); errAmount.setVisible(true); ok = false;
        }

        if ("credit_card".equals(cbMethod.getValue())) {
            if (!paymentService.validateCardNumber(txtCardNumber.getText())) {
                errCardNumber.setText("Invalid card number (Luhn check failed).");
                errCardNumber.setVisible(true); ok = false;
            } else errCardNumber.setVisible(false);

            if (!paymentService.validateCardHolder(txtCardHolder.getText())) {
                errCardHolder.setText("Invalid cardholder name."); errCardHolder.setVisible(true); ok = false;
            } else errCardHolder.setVisible(false);

            if (!paymentService.validateExpiryDate(txtExpiry.getText())) {
                errExpiry.setText("Invalid or expired date (MM/YY)."); errExpiry.setVisible(true); ok = false;
            } else errExpiry.setVisible(false);

            if (!paymentService.validateCVC(txtCvc.getText())) {
                errCvc.setText("Invalid CVC (3-4 digits)."); errCvc.setVisible(true); ok = false;
            } else errCvc.setVisible(false);
        }

        return ok;
    }

    private void toggleCardFields() {
        boolean isCard = "credit_card".equals(cbMethod.getValue());
        txtCardNumber.setDisable(!isCard);
        txtCardHolder.setDisable(!isCard);
        txtExpiry.setDisable(!isCard);
        txtCvc.setDisable(!isCard);
    }

    private void clearErrors() {
        errCardNumber.setVisible(false); errCardHolder.setVisible(false);
        errExpiry.setVisible(false);     errCvc.setVisible(false);
        errAmount.setVisible(false);
        lblResult.setText("");
    }

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
    }
}
