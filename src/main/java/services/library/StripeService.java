package services.library;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * Creates a Stripe PaymentIntent and returns the client_secret.
 * The client_secret is passed to the embedded Stripe.js form in a WebView,
 * so the card form renders INSIDE our app — no external redirect.
 */
public class StripeService {

    // Replace with your real test key from https://dashboard.stripe.com/apikeys
    private static final String SECRET_KEY = "sk_test_51TRh71HZXM1bT6hw3KSs7y314e1jA8YQkoRKT9onmpmcbneQhGrCAnsxRGPwasnq7egMk8OUvxs32MMJWTg7gTQA00wV4ulEeI";

    /**
     * Creates a PaymentIntent on Stripe's servers.
     * Returns the client_secret which Stripe.js uses to confirm the payment
     * directly inside the embedded WebView form.
     *
     * @param amountCents price in smallest currency unit (e.g. $9.99 = 999)
     * @param currency    e.g. "usd", "eur"
     * @param description shown on Stripe dashboard (e.g. book title)
     * @return client_secret string
     */
    public String createPaymentIntent(long amountCents, String currency, String description) throws StripeException {
        Stripe.apiKey = SECRET_KEY;

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                .setDescription(description)
                .addPaymentMethodType("card")
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        return intent.getClientSecret();
    }
}
