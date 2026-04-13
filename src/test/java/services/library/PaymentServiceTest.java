package services.library;

import models.library.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    // ─────────────────────────────────────────────
    //  CARD NUMBER (Luhn Algorithm)
    // ─────────────────────────────────────────────

    @Test
    void testValidCardNumber_Visa() {
        assertTrue(paymentService.validateCardNumber("4532015112830366"));
    }

    @Test
    void testValidCardNumber_Mastercard() {
        assertTrue(paymentService.validateCardNumber("5425233430109903"));
    }

    @Test
    void testValidCardNumber_WithSpaces() {
        assertTrue(paymentService.validateCardNumber("4532 0151 1283 0366"));
    }

    @Test
    void testInvalidCardNumber_LuhnFails() {
        assertFalse(paymentService.validateCardNumber("1234567890123456"));
    }

    @Test
    void testInvalidCardNumber_TooShort() {
        assertFalse(paymentService.validateCardNumber("123"));
    }

    @Test
    void testInvalidCardNumber_Letters() {
        assertFalse(paymentService.validateCardNumber("abcd1234efgh5678"));
    }

    // ─────────────────────────────────────────────
    //  EXPIRY DATE
    // ─────────────────────────────────────────────

    @Test
    void testValidExpiryDate() {
        assertTrue(paymentService.validateExpiryDate("12/26"));
        assertTrue(paymentService.validateExpiryDate("01/30"));
    }

    @Test
    void testInvalidExpiryDate_Expired() {
        assertFalse(paymentService.validateExpiryDate("01/20"));
    }

    @Test
    void testInvalidExpiryDate_BadMonth() {
        assertFalse(paymentService.validateExpiryDate("13/26"));
    }

    @Test
    void testInvalidExpiryDate_BadFormat() {
        assertFalse(paymentService.validateExpiryDate("1226"));
    }

    // ─────────────────────────────────────────────
    //  CVC
    // ─────────────────────────────────────────────

    @Test
    void testValidCVC_3digits() {
        assertTrue(paymentService.validateCVC("123"));
    }

    @Test
    void testValidCVC_4digits() {
        assertTrue(paymentService.validateCVC("1234"));
    }

    @Test
    void testInvalidCVC_TooShort() {
        assertFalse(paymentService.validateCVC("12"));
    }

    @Test
    void testInvalidCVC_Letters() {
        assertFalse(paymentService.validateCVC("abc"));
    }

    // ─────────────────────────────────────────────
    //  CARD HOLDER
    // ─────────────────────────────────────────────

    @Test
    void testValidCardHolder() {
        assertTrue(paymentService.validateCardHolder("John Doe"));
        assertTrue(paymentService.validateCardHolder("Marie-Claire"));
    }

    @Test
    void testInvalidCardHolder_TooShort() {
        assertFalse(paymentService.validateCardHolder("Jo"));
    }

    @Test
    void testInvalidCardHolder_Numbers() {
        assertFalse(paymentService.validateCardHolder("John123"));
    }

    // ─────────────────────────────────────────────
    //  CARD TYPE
    // ─────────────────────────────────────────────

    @Test
    void testCardType_Visa() {
        assertEquals("Visa", paymentService.getCardType("4532015112830366"));
    }

    @Test
    void testCardType_Mastercard() {
        assertEquals("Mastercard", paymentService.getCardType("5425233430109903"));
    }

    // ─────────────────────────────────────────────
    //  PAYMENT PROCESSING
    // ─────────────────────────────────────────────

    @Test
    void testProcessPayment_InvalidCard_ReturnError() {
        Payment payment = new Payment();
        payment.setUserId(1);
        payment.setBookId(1);
        payment.setAmount(29.99);

        String error = paymentService.processerCarteBancaire(
                payment, "1234567890123456", "John Doe", "12/26", "123"
        );

        assertNotNull(error);
        assertEquals(Payment.STATUS_FAILED, payment.getStatus());
    }

    @Test
    void testProcessPayment_ValidCard_StatusSet() {
        Payment payment = new Payment();
        payment.setUserId(1);
        payment.setBookId(1);
        payment.setAmount(29.99);

        // Run multiple times — at least one should succeed (95% rate)
        boolean anySuccess = false;
        for (int i = 0; i < 20; i++) {
            Payment p = new Payment();
            p.setUserId(1); p.setBookId(1); p.setAmount(29.99);
            String err = paymentService.processerCarteBancaire(
                    p, "4532015112830366", "John Doe", "12/26", "123"
            );
            if (err == null) { anySuccess = true; break; }
        }
        assertTrue(anySuccess, "Expected at least one success in 20 attempts (95% rate)");
    }
}
