package com.example.bookingservice.servcie.payment;

import com.example.bookingservice.constant.ErrorCodes;
import com.example.bookingservice.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link PaymentGatewayService} to use for a given {@code paymentMethod} value from
 * {@code PaymentRequest}. Spring injects every {@link PaymentGatewayService} bean (Stripe, PayPal,
 * ...) here automatically; adding a new gateway later is just adding a new
 * {@code @Service}-annotated implementation, no changes needed in this class.
 */
@Component
public class PaymentGatewayFactory {

  private final Map<String, PaymentGatewayService> gatewaysByProvider;

  public PaymentGatewayFactory(List<PaymentGatewayService> gateways) {
    this.gatewaysByProvider =
        gateways.stream().collect(Collectors.toMap(g -> g.getProviderName().toUpperCase(), g -> g));
  }

  /**
   * Returns the appropriate gateway service for the given payment method.
   *
   * @param paymentMethod the client-facing payment method (e.g., "CREDIT_CARD", "PAYPAL")
   * @return the corresponding PaymentGatewayService
   * @throws BusinessException if the method is unsupported or null
   */
  public PaymentGatewayService resolve(String paymentMethod) {
    String provider = normalize(paymentMethod);
    PaymentGatewayService gateway = gatewaysByProvider.get(provider);
    if (gateway == null) {
      throw new BusinessException(
          "Unsupported payment method: " + paymentMethod, ErrorCodes.VALIDATION_ERROR);
    }
    return gateway;
  }

  /** Maps the client-facing payment method values onto a concrete gateway provider name. */
  private String normalize(String paymentMethod) {
    if (paymentMethod == null || paymentMethod.isBlank()) {
      throw new BusinessException("Payment method is required", ErrorCodes.VALIDATION_ERROR);
    }
    return switch (paymentMethod.toUpperCase()) {
      case "CREDIT_CARD", "DEBIT_CARD", "CARD", "STRIPE" -> "STRIPE";
      case "PAYPAL" -> "PAYPAL";
      default -> paymentMethod.toUpperCase();
    };
  }
}
