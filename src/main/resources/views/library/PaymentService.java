package services.library;

import models.library.Payment;
import utils.MyConnection;

import java.sql.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PaymentService {

    private Connection cnx;

    public PaymentService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // ─────────────────────────────────────────────
    //  CARD VALIDATION (Luhn Algorithm)
    // ─────────────────────────────────────────────

    public boolean validateCardNumber(String cardNumber) {
        cardNumber = cardNumber.replaceAll("[\\s\\-]", "");
        if (!cardNumber.matches("\\d+")) return false;
        if (cardNumber.length() < 13 || cardNumber.length() > 19) return false;
        return isValidLuhn(cardNumber);
    }

    /**
     * Luhn algorithm — same algorithm used by real banks
     */
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean isSecond = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (isSecond) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            isSecond = !isSecond;
        }
        return (sum % 10) == 0;
    }

    public boolean validateExpiryDate(String expiry) {
        if (!expiry.matches("^(0[1-9]|1[0-2])/([0-9]{2})$")) return false;
        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year  = Integer.parseInt(parts[1]) + 2000;
        YearMonth cardExpiry = YearMonth.of(year, month);
        return !cardExpiry.isBefore(YearMonth.now());
    }

    public boolean validateCVC(String cvc) {
        return cvc != null && cvc.matches("\\d{3,4}");
    }

    public boolean validateCardHolder(String name) {
        return name != null && name.trim().length() >= 3 && name.matches("[a-zA-Z\\s\\-']+");
    }

    public String getCardType(String cardNumber) {
        cardNumber = cardNumber.replaceAll("[\\s\\-]", "");
        if (cardNumber.startsWith("4"))          return "Visa";
        if (cardNumber.matches("^5[1-5].*"))     return "Mastercard";
        if (cardNumber.matches("^3[47].*"))      return "American Express";
        if (cardNumber.matches("^6(?:011|5).*")) return "Discover";
        return "Unknown";
    }

    // ─────────────────────────────────────────────
    //  PAYMENT PROCESSING
    // ─────────────────────────────────────────────

    /**
     * Process a credit card payment — validates then simulates (95% success)
     */
    public String processerCarteBancaire(Payment payment, String cardNumber,
                                         String cardHolder, String expiry, String cvc) {
        List<String> errors = new ArrayList<>();

        if (!validateCardNumber(cardNumber))  errors.add("Invalid card number");
        if (!validateCardHolder(cardHolder))  errors.add("Invalid cardholder name");
        if (!validateExpiryDate(expiry))      errors.add("Card expired or invalid expiry date");
        if (!validateCVC(cvc))               errors.add("Invalid CVC code");

        if (!errors.isEmpty()) {
            payment.setStatus(Payment.STATUS_FAILED);
            payment.setFailureReason(String.join(", ", errors));
            return String.join(", ", errors);
        }

        // Simulation: 95% success rate
        boolean success = new Random().nextInt(100) < 95;
        if (success) {
            payment.setStatus(Payment.STATUS_COMPLETED);
            payment.setCompletedAt(new Timestamp(System.currentTimeMillis()));
            payment.setCardLastFour(cardNumber.replaceAll("[\\s\\-]", "")
                    .substring(cardNumber.replaceAll("[\\s\\-]", "").length() - 4));
            payment.setCardHolderName(cardHolder);
            System.out.println("Payment successful! 💳");
            return null; // null = success
        } else {
            payment.setStatus(Payment.STATUS_FAILED);
            payment.setFailureReason("Payment declined by bank");
            return "Payment declined by bank";
        }
    }

    /**
     * Process a PayPal payment — simulation (98% success)
     */
    public String processerPayPal(Payment payment) {
        boolean success = new Random().nextInt(100) < 98;
        if (success) {
            payment.setStatus(Payment.STATUS_COMPLETED);
            payment.setCompletedAt(new Timestamp(System.currentTimeMillis()));
            System.out.println("PayPal payment successful! 💳");
            return null;
        } else {
            payment.setStatus(Payment.STATUS_FAILED);
            payment.setFailureReason("PayPal payment cancelled");
            return "PayPal payment cancelled";
        }
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void ajouter(Payment p) throws SQLException {
        String sql = "INSERT INTO payment (user_id, book_id, amount, status, payment_method, " +
                "transaction_id, card_last_four, card_holder_name, failure_reason, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, p.getUserId());
        ps.setInt(2, p.getBookId());
        ps.setDouble(3, p.getAmount());
        ps.setString(4, p.getStatus());
        ps.setString(5, p.getPaymentMethod());
        ps.setString(6, p.getTransactionId());
        ps.setString(7, p.getCardLastFour());
        ps.setString(8, p.getCardHolderName());
        ps.setString(9, p.getFailureReason());
        ps.setTimestamp(10, p.getCreatedAt());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) p.setId(keys.getInt(1));
        System.out.println("Payment saved! ✅");
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<Payment> afficher() {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, b.title as book_title, u.email as user_name " +
                "FROM payment p " +
                "LEFT JOIN book b ON p.book_id=b.id " +
                "LEFT JOIN users u ON p.user_id=u.id " +
                "ORDER BY p.created_at DESC";
        try {
            ResultSet rs = cnx.createStatement().executeQuery(sql);
            while (rs.next()) payments.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading payments: " + e.getMessage());
        }
        return payments;
    }

    public List<Payment> findByUser(int userId) {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, b.title as book_title, u.email as user_name " +
                "FROM payment p " +
                "LEFT JOIN book b ON p.book_id=b.id " +
                "LEFT JOIN users u ON p.user_id=u.id " +
                "WHERE p.user_id=? ORDER BY p.created_at DESC";
        try {
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) payments.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading user payments: " + e.getMessage());
        }
        return payments;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getInt("id"));
        p.setUserId(rs.getInt("user_id"));
        p.setBookId(rs.getInt("book_id"));
        p.setAmount(rs.getDouble("amount"));
        p.setStatus(rs.getString("status"));
        p.setPaymentMethod(rs.getString("payment_method"));
        p.setTransactionId(rs.getString("transaction_id"));
        p.setCardLastFour(rs.getString("card_last_four"));
        p.setCardHolderName(rs.getString("card_holder_name"));
        p.setFailureReason(rs.getString("failure_reason"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setCompletedAt(rs.getTimestamp("completed_at"));
        try { p.setBookTitle(rs.getString("book_title")); } catch (SQLException ignored) {}
        try { p.setUserName(rs.getString("user_name")); }  catch (SQLException ignored) {}
        return p;
    }
}
