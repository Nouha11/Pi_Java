package controllers.library;

import controllers.NovaDashboardController;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import models.library.Book;
import models.library.Payment;
import netscape.javascript.JSObject;
import services.library.PaymentService;
import services.library.StripeService;

import java.sql.SQLException;

/**
 * Controller for the embedded Stripe payment form.
 *
 * Flow:
 *   1. Java calls StripeService to create a PaymentIntent → gets client_secret
 *   2. Loads stripe_payment.html into a WebView
 *   3. Injects client_secret + publishable key + book info as JS variables
 *   4. Stripe.js renders the card form inside the WebView (no external redirect)
 *   5. On success, JS calls JavaBridge.onPaymentSuccess() back into Java
 *   6. Java saves the payment record to DB and navigates to book list
 */
public class PaymentFormController {

    @FXML private Label lblHeader;
    @FXML private Label sideBookTitle, sideBookAuthor, sidePrice, sideTotal;
    @FXML private Label sideMethodIcon, sideMethodName;
    @FXML private VBox cardFields, paypalFields;
    @FXML private WebView stripeWebView;
    @FXML private Label lblStatus;

    private Book book;
    private String paymentMethod;
    private final PaymentService paymentService = new PaymentService();
    private final StripeService stripeService = new StripeService();

    // Your Stripe publishable key (safe to expose in frontend)
    // Get it from https://dashboard.stripe.com/apikeys
    private static final String PUBLISHABLE_KEY = "pk_test_51TRh71HZXM1bT6hwrtCiY9MhTsflcn1Ur2G4R8oVOYblzW9y8GNgDrPWszlbMm88pXBqOW8Da67EWeiAYUvuO0aO00UUtVO3y3";
    private static final int DEMO_USER_ID = 2; // overridden at runtime by SessionManager

    public void initData(Book book, String paymentMethod) {
        this.book = book;
        this.paymentMethod = paymentMethod;

        sideBookTitle.setText(book.getTitle());
        sideBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        String price = String.format("$%.2f", book.getPrice());
        sidePrice.setText(price);
        sideTotal.setText(price);

        if ("paypal".equals(paymentMethod)) {
            // PayPal: keep old simulation for now
            lblHeader.setText("PayPal Payment");
            sideMethodIcon.setText("P");
            sideMethodName.setText("PayPal");
            stripeWebView.setVisible(false);
            stripeWebView.setManaged(false);
            paypalFields.setVisible(true);
            paypalFields.setManaged(true);
        } else {
            lblHeader.setText("Credit Card — Stripe");
            sideMethodIcon.setText("💳");
            sideMethodName.setText("Credit Card");
            loadStripeForm();
        }
    }

    /**
     * Creates a PaymentIntent via Stripe API, then loads the embedded HTML form
     * into the WebView with the client_secret injected as a JS variable.
     * Runs the API call on a background thread to avoid blocking the UI.
     */
    private void loadStripeForm() {
        lblStatus.setText("Connecting to Stripe...");
        lblStatus.setVisible(true);

        // Run Stripe API call off the JavaFX thread
        new Thread(() -> {
            try {
                long amountCents = (long)(book.getPrice() * 100);
                String clientSecret = stripeService.createPaymentIntent(
                        amountCents, "usd", "Purchase: " + book.getTitle()
                );

                Platform.runLater(() -> {
                    lblStatus.setVisible(false);
                    injectAndLoadStripeForm(clientSecret);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblStatus.setText("Could not connect to Stripe: " + e.getMessage());
                    lblStatus.setStyle("-fx-text-fill: #dc3545;");
                });
            }
        }).start();
    }

    /**
     * Generates the full HTML page with all values baked in as string literals,
     * then loads it directly into the WebView via loadContent().
     */
    private void injectAndLoadStripeForm(String clientSecret) {
        WebEngine engine = stripeWebView.getEngine();
        stripeWebView.setContextMenuEnabled(false);
        engine.setJavaScriptEnabled(true);

        // Keep a strong reference to the bridge so GC doesn't collect it
        JavaBridge bridge = new JavaBridge();

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // Inject bridge immediately after page load
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("JavaBridge", bridge);
                // Also set it as a global var so Stripe callbacks can find it
                engine.executeScript("window._javaBridge = window.JavaBridge;");
            }
        });

        // Escape values for safe embedding in JS string literals
        String safeTitle  = book.getTitle().replace("'", "\\'").replace("\n", " ");
        String safeAmount = String.format("$%.2f", book.getPrice());

        // Generate HTML with values baked in — no runtime injection needed
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'/>" +
            "<script src='https://js.stripe.com/v3/'></script>" +
            "<style>" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif;" +
            "  background: #f8f9fa; display: flex; align-items: center;" +
            "  justify-content: center; min-height: 100vh; padding: 20px; }" +
            ".card { background: white; border-radius: 12px; padding: 32px;" +
            "  width: 100%; max-width: 460px;" +
            "  box-shadow: 0 4px 24px rgba(0,0,0,0.08); }" +
            "h2 { font-size: 20px; font-weight: 700; color: #212529; margin-bottom: 6px; }" +
            ".subtitle { font-size: 13px; color: #6c757d; margin-bottom: 20px; }" +
            ".amount { display: inline-block; background: #d1e7dd; color: #0a3622;" +
            "  font-weight: 700; font-size: 20px; padding: 6px 18px;" +
            "  border-radius: 20px; margin-bottom: 22px; }" +
            "label { display: block; font-size: 13px; font-weight: 600;" +
            "  color: #495057; margin-bottom: 6px; }" +
            "#card-element { border: 1.5px solid #dee2e6; border-radius: 8px;" +
            "  padding: 14px; background: white; min-height: 46px; }" +
            "#card-element.StripeElement--focus { border-color: #0d6efd;" +
            "  box-shadow: 0 0 0 3px rgba(13,110,253,0.15); }" +
            "#card-errors { color: #dc3545; font-size: 12px; margin-top: 8px; min-height: 18px; }" +
            "#submit-btn { width: 100%; padding: 14px; background: #0d6efd; color: white;" +
            "  border: none; border-radius: 8px; font-size: 15px; font-weight: 700;" +
            "  cursor: pointer; margin-top: 22px; }" +
            "#submit-btn:disabled { background: #6c757d; cursor: not-allowed; }" +
            ".lock { display: flex; align-items: center; gap: 8px; background: #cff4fc;" +
            "  border: 1px solid #9eeaf9; border-radius: 8px; padding: 10px 14px;" +
            "  margin-top: 14px; font-size: 12px; color: #055160; }" +
            "</style></head><body>" +
            "<div class='card'>" +
            "  <h2>" + safeTitle + "</h2>" +
            "  <p class='subtitle'>Secure payment powered by Stripe</p>" +
            "  <div class='amount'>" + safeAmount + "</div>" +
            "  <form id='payment-form'>" +
            "    <label>Card Details</label>" +
            "    <div id='card-element'></div>" +
            "    <div id='card-errors'></div>" +
            "    <button id='submit-btn' type='submit'>Pay Now</button>" +
            "  </form>" +
            "  <div class='lock'>Your payment is encrypted. We never store your card number.</div>" +
            "</div>" +
            "<script>" +
            "var stripe = Stripe('" + PUBLISHABLE_KEY + "');" +
            "var elements = stripe.elements();" +
            "var card = elements.create('card', {" +
            "  style: { base: { fontSize: '15px', color: '#212529'," +
            "    fontFamily: 'system-ui, sans-serif'," +
            "    '::placeholder': { color: '#adb5bd' } }," +
            "    invalid: { color: '#dc3545' } }" +
            "});" +
            "card.mount('#card-element');" +
            "card.on('change', function(e) {" +
            "  document.getElementById('card-errors').textContent = e.error ? e.error.message : '';" +
            "});" +
            "document.getElementById('payment-form').addEventListener('submit', async function(e) {" +
            "  e.preventDefault();" +
            "  var btn = document.getElementById('submit-btn');" +
            "  btn.disabled = true; btn.textContent = 'Processing...';" +
            "  try {" +
            "    var result = await stripe.confirmCardPayment('" + clientSecret + "', {" +
            "      payment_method: { card: card }" +
            "    });" +
            "    if (result.error) {" +
            "      document.getElementById('card-errors').textContent = result.error.message;" +
            "      btn.disabled = false; btn.textContent = 'Pay Now';" +
            "    } else if (result.paymentIntent.status === 'succeeded') {" +
            "      var bridge = window.JavaBridge || window._javaBridge;" +
            "      if (bridge) { bridge.onPaymentSuccess(result.paymentIntent.id); }" +
            "      else { document.getElementById('card-errors').textContent = 'Bridge error - please restart.'; btn.disabled = false; btn.textContent = 'Pay Now'; }" +
            "    }" +
            "  } catch(err) {" +
            "    document.getElementById('card-errors').textContent = 'Error: ' + err.message;" +
            "    btn.disabled = false; btn.textContent = 'Pay Now';" +
            "  }" +
            "});" +
            "</script></body></html>";

        engine.loadContent(html);
    }

    /**
     * Bridge object exposed to JavaScript as window.JavaBridge.
     * JS calls onPaymentSuccess() when Stripe confirms the payment.
     * Must be public with public methods for JSObject.setMember to work.
     */
    public class JavaBridge {
        /**
         * Called by Stripe.js after successful payment confirmation.
         * @param paymentIntentId the Stripe PaymentIntent ID (e.g. pi_xxx)
         */
        public void onPaymentSuccess(String paymentIntentId) {
            Platform.runLater(() -> {
                try {
                    Payment payment = new Payment();
                    payment.setUserId(utils.SessionManager.getCurrentUserId());
                    payment.setBookId(book.getId());
                    payment.setAmount(book.getPrice());
                    payment.setPaymentMethod("credit_card");
                    payment.setTransactionId(paymentIntentId); // store Stripe's ID
                    payment.setStatus(Payment.STATUS_COMPLETED);
                    payment.setBookTitle(book.getTitle());
                    paymentService.ajouter(payment);

                    utils.SuccessDialog.show(
                            utils.SuccessDialog.Type.SUCCESS,
                            "OK",
                            "Payment Successful!",
                            "Your purchase of \"" + book.getTitle() + "\" is confirmed.\nTransaction ID: " + paymentIntentId,
                            "Go to My Library"
                    );
                    NovaDashboardController.loadPage("/views/library/BookListView.fxml");

                } catch (SQLException e) {
                    new Alert(Alert.AlertType.ERROR, "Payment recorded but DB error: " + e.getMessage())
                            .showAndWait();
                }
            });
        }
    }

    // PayPal fallback (simulation)
    @FXML
    private void handlePayPal() {
        Payment payment = new Payment();
        payment.setUserId(DEMO_USER_ID);
        payment.setBookId(book.getId());
        payment.setAmount(book.getPrice());
        payment.setPaymentMethod("paypal");
        payment.setBookTitle(book.getTitle());

        String error = paymentService.processerPayPal(payment);
        if (error == null) {
            try { paymentService.ajouter(payment); } catch (SQLException ignored) {}
            utils.SuccessDialog.show(
                    utils.SuccessDialog.Type.SUCCESS,
                    "OK",
                    "PayPal Payment Successful!",
                    "Your purchase of \"" + book.getTitle() + "\" is confirmed.",
                    "Go to My Library"
            );
            NovaDashboardController.loadPage("/views/library/BookListView.fxml");
        } else {
            new Alert(Alert.AlertType.ERROR, "PayPal failed: " + error).showAndWait();
        }
    }

    @FXML
    private void handleBack() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/views/library/PurchaseView.fxml"));
            javafx.scene.Parent root = loader.load();
            ((PurchaseController) loader.getController()).initData(book);
            NovaDashboardController.setView(root);
        } catch (Exception ignored) {}
    }
}
