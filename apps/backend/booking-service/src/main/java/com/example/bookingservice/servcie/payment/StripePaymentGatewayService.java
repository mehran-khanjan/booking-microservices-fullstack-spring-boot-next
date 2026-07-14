package com.example.bookingservice.servcie.payment;

import com.example.bookingservice.property.StripeProperties;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Charges cards (Visa/Mastercard/Amex, etc.) through Stripe's PaymentIntents API.
 *
 * <p>{@code paymentToken} is expected to be a Stripe PaymentMethod ID (e.g. {@code "pm_..."})
 * created client-side via Stripe.js/Elements - the raw card PAN never reaches this service, keeping
 * it out of PCI scope.
 *
 * <p>Requires the {@code com.stripe:stripe-java} dependency, e.g. (Maven):
 *
 * <pre>{@code
 * <dependency>
 *   <groupId>com.stripe</groupId>
 *   <artifactId>stripe-java</artifactId>
 *   <version>26.13.0</version>
 * </dependency>
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGatewayService implements PaymentGatewayService {

  private static final String PROVIDER_NAME = "STRIPE";

  // Currencies Stripe treats as having no minor unit (amounts are already "whole").
  // See: https://docs.stripe.com/currencies#zero-decimal
  private static final Set<String> ZERO_DECIMAL_CURRENCIES =
      Set.of(
          "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV",
          "XAF", "XOF", "XPF");

  private final StripeProperties stripeProperties;

  @PostConstruct
  void init() {
    Stripe.apiKey = stripeProperties.getApiKey();
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  /**
   * Charges the customer via Stripe PaymentIntent.
   *
   * @param request the charge request with amount, currency, and payment token
   * @return the gateway result containing success status and transaction ID
   */
  @Override
  public PaymentGatewayResult charge(PaymentChargeRequest request) {
    try {
      long amountInMinorUnits = toMinorUnits(request.getAmount(), request.getCurrency());

      PaymentIntentCreateParams params =
          PaymentIntentCreateParams.builder()
              .setAmount(amountInMinorUnits)
              .setCurrency(request.getCurrency().toLowerCase())
              .setPaymentMethod(request.getPaymentToken())
              .setConfirm(true)
              .setOffSession(true)
              .setDescription("Booking " + request.getBookingReference())
              .putMetadata("bookingReference", request.getBookingReference())
              .setAutomaticPaymentMethods(
                  PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                      .setEnabled(true)
                      .setAllowRedirects(
                          PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                      .build())
              .build();

      RequestOptions options =
          RequestOptions.builder().setIdempotencyKey(request.getIdempotencyKey()).build();

      PaymentIntent intent = PaymentIntent.create(params, options);

      if ("succeeded".equals(intent.getStatus())) {
        log.info(
            "Stripe PaymentIntent {} succeeded for booking {}",
            intent.getId(),
            request.getBookingReference());
        return PaymentGatewayResult.success(intent.getId(), intent.getStatus());
      }

      log.warn(
          "Stripe PaymentIntent {} for booking {} did not succeed, status={}",
          intent.getId(),
          request.getBookingReference(),
          intent.getStatus());
      return PaymentGatewayResult.failure("Stripe payment not completed: " + intent.getStatus());

    } catch (CardException e) {
      log.info(
          "Stripe card declined for booking {}: code={}, message={}",
          request.getBookingReference(),
          e.getCode(),
          e.getMessage());
      return PaymentGatewayResult.failure("Card declined: " + e.getCode());

    } catch (StripeException e) {
      log.error("Stripe error while charging booking {}", request.getBookingReference(), e);
      return PaymentGatewayResult.failure("Payment gateway error, please try again");
    }
  }

  /**
   * Converts a decimal major-currency-unit amount (e.g. 129.99 USD) to Stripe's integer minor
   * units.
   */
  private long toMinorUnits(BigDecimal amount, String currency) {
    int fractionDigits = ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase()) ? 0 : 2;
    return amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }
}
