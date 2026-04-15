package services.library;

import models.library.Payment;
import utils.MyConnection;

import java.sql.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service class handling payment processing and persistence.
 * Supports two payment methods: Credit Card (with Luhn validation) and PayPal.
 * Payment processing is simulated — no real bank API is called.
 */
public class PaymentService {

    private Connection getCnx() {
        return MyConnection.getInstance().getCnx();
    }

    // ─────────────────────────────────────────────
    //  CARD VALIDATION (Luhn Algorithm)
    // ─────────────────────────────────────────────

    /**
     * Validates a credit card number format and checksum.
     * Strips spaces/dashes, checks it's all digits, checks length (13-19),
     * then runs the Luhn algorithm.
     */
    public boolean validateCardNumber(String cardNumber) {
        cardNumber = cardNumber.replaceAll("[\\s\\-]", ""); // remove spaces and dashes
        if (!cardNumber.matches("\\d+")) return false;     // must be digits only
        if (cardNumber.length() < 13 || cardNumber.length() > 19) return false;
        return isValidLuhn(cardNumber);
    }

    /**
     * Luhn algorithm — the industry-standard checksum used by all major card networks.
     * How it works:
     *   1. Starting from the rightmost digit, double every second digit.
     *   2. If doubling gives a number > 9, subtract 9.
     *   3. Sum all digits. A valid card number gives a sum divisible by 10.
     */
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean isSecond = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (isSecond) {
                digit *= 2;
                if (digit > 9) digit -= 9; // same as summing the two digits of the doubled value
            }
            sum += digit;
            isSecond = !isSecond;
        }
        return (sum % 10) == 0;
    }

    /**
     * Validates expiry date in MM/YY format.
     * Checks format with regex, then checks the card hasn't expired yet.
     */
    public boolean validateExpiryDate(String expiry) {
        if (!expiry.matches("^(0[1-9]|1[0-2])/([0-9]{2})$")) return false;
        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year  = Integer.parseInt(parts[1]) + 2000; // convert YY to YYYY
        YearMonth cardExpiry = YearMonth.of(year, month);
        return !cardExpiry.isBefore(YearMonth.now()); // card must not be expired
    }

    /**
     * Validates CVC — must be 3 or 4 digits (Amex uses 4).
     */
    public boolean validateCVC(String cvc) {
        return cvc != null && cvc.matches("\\d{3,4}");
    }

    /**
     * Validates cardholder name — at least 3 chars, letters/spaces/hyphens only.
     */
    public boolean validateCardHolder(String name) {
        return name != null && name.trim().length() >= 3 && name.matches("[a-zA-Z\\s\\-']+");
    }

    /**
     * Detects card network from the card number prefix (BIN range).
     * Visa starts with 4, Mastercard with 51-55, Amex with 34/37, Discover with 6011/65.
     */
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
     * Processes a credit card payment.
     * Steps:
     *   1. Validate all card fields (number, holder, expiry, CVC).
     *   2. If validation fails, mark payment as FAILED and return the error.
     *   3. If valid, simulate bank authorization (95% success rate).
     *   4. On success, store the last 4 digits (never the full number — security).
     *
     * @return null on success, or an error message string on failure
     */
    public String processerCarteBancaire(Payment payment, String cardNumber,
                                         String cardHolder, String expiry, String cvc) {
        List<String> errors = new ArrayList<>();

        // Run all validations and collect errors
        if (!validateCardNumber(cardNumber))  errors.add("Invalid card number");
        if (!validateCardHolder(cardHolder))  errors.add("Invalid cardholder name");
        if (!validateExpiryDate(expiry))      errors.add("Card expired or invalid expiry date");
        if (!validateCVC(cvc))                errors.add("Invalid CVC code");

        if (!errors.isEmpty()) {
            payment.setStatus(Payment.STATUS_FAILED);
            payment.setFailureReason(String.join(", ", errors));
            return String.join(", ", errors);
        }

        // Simulate bank response: 95% approval rate
        boolean success = new Random().nextInt(100) < 95;
        if (success) {
            payment.setStatus(Payment.STATUS_COMPLETED);
            payment.setCompletedAt(new Timestamp(System.currentTimeMillis()));
            // Store only last 4 digits for display — never store the full card number
            String stripped = cardNumber.replaceAll("[\\s\\-]", "");
            payment.setCardLastFour(stripped.substring(stripped.length() - 4));
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
     * Processes a PayPal payment (simulated).
     * PayPal has a higher simulated success rate (98%) since there's no card validation step.
     *
     * @return null on success, or an error message string on failure
     */
    public String processerPayPal(Payment payment) {
        // Simulate PayPal authorization: 98% success rate
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

    /**
     * Persists a payment record to the database.
     * Called after a successful (or failed) payment attempt to keep a full audit trail.
     */
    public void ajouter(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (user_id, book_id, amount, status, payment_method, " +
                "transaction_id, card_last_four, card_holder_name, failure_reason, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,NOW())";
        PreparedStatement ps = getCnx().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, p.getUserId());
        ps.setInt(2, p.getBookId());
        ps.setDouble(3, p.getAmount());
        ps.setString(4, p.getStatus());
        ps.setString(5, p.getPaymentMethod());
        ps.setString(6, p.getTransactionId()); // auto-generated UUID in Payment constructor
        ps.setString(7, p.getCardLastFour());
        ps.setString(8, p.getCardHolderName());
        ps.setString(9, p.getFailureReason());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) p.setId(keys.getInt(1));
        System.out.println("Payment saved! ✅");
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    /**
     * Returns all payments with book title and user email joined in.
     * Ordered by most recent first.
     */
    public List<Payment> afficher() {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, b.title as book_title, u.email as user_name " +
                "FROM payments p " +
                "LEFT JOIN books b ON p.book_id=b.id " +
                "LEFT JOIN user u ON p.user_id=u.id " +
                "ORDER BY p.created_at DESC";
        try {
            ResultSet rs = getCnx().createStatement().executeQuery(sql);
            while (rs.next()) payments.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error loading payments: " + e.getMessage());
        }
        return payments;
    }

    /**
     * Returns all payments made by a specific user.
     * Used to show a user their purchase history.
     */
    public List<Payment> findByUser(int userId) {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, b.title as book_title, u.email as user_name " +
                "FROM payments p " +
                "LEFT JOIN books b ON p.book_id=b.id " +
                "LEFT JOIN user u ON p.user_id=u.id " +
                "WHERE p.user_id=? ORDER BY p.created_at DESC";
        try {
            PreparedStatement ps = getCnx().prepareStatement(sql);
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

    /**
     * Maps a ResultSet row to a Payment object.
     * Joined fields (book_title, user_name) are wrapped in try/catch
     * in case the JOIN returned no match.
     */
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
