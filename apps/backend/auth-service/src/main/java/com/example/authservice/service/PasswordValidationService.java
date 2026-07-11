package com.example.authservice.service;

import com.example.commonlib.exception.PasswordPolicyException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.passay.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for validating password strength and enforcing password policies.
 *
 * <p>This service uses the Passay library to apply a configurable set of rules:
 *
 * <ul>
 *   <li><strong>Length:</strong> between {@code password.min-length} (default 8) and 128 characters
 *   <li><strong>Character classes:</strong> at least one uppercase letter, one lowercase letter,
 *       one digit, and one special character (e.g., !@#$%)
 *   <li><strong>Whitespace:</strong> no whitespace characters are allowed
 * </ul>
 *
 * <p>If the password fails validation, a {@link PasswordPolicyException} is thrown with a
 * user-friendly message indicating the requirements.
 *
 * <p>The service also provides a placeholder for password history checks ({@link
 * #checkPasswordHistory(String, String)}), which is currently not implemented but is intended to
 * prevent reuse of recent passwords.
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>{@code password.min-length} – minimum allowed password length (default: 8)
 * </ul>
 *
 * @author Your Team
 * @see PasswordPolicyException
 * @since 1.0
 */
@Service
@Slf4j
public class PasswordValidationService {

  /** Minimum password length, configurable via {@code password.min-length}. */
  @Value("${password.min-length:8}")
  private int minLength;

  /**
   * Validates a password against the configured policy rules.
   *
   * <p>The method constructs a {@link PasswordValidator} with the following rules:
   *
   * <ul>
   *   <li>{@link LengthRule} – enforces the configured minimum and a maximum of 128
   *   <li>{@link CharacterRule} for uppercase, lowercase, digit, and special characters, each
   *       requiring at least one occurrence
   *   <li>{@link WhitespaceRule} – disallows any whitespace
   * </ul>
   *
   * <p>If the password fails any rule, the validation messages are collected, logged at WARN level,
   * and a {@link PasswordPolicyException} is thrown with a descriptive message.
   *
   * @param password the password to validate (must not be {@code null})
   * @throws PasswordPolicyException if the password does not meet the policy requirements
   */
  public void validatePassword(String password) {
    PasswordValidator validator =
        new PasswordValidator(
            Arrays.asList(
                new LengthRule(minLength, 128),
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                new CharacterRule(EnglishCharacterData.Digit, 1),
                new CharacterRule(EnglishCharacterData.Special, 1),
                new WhitespaceRule()));

    RuleResult result = validator.validate(new PasswordData(password));

    if (!result.isValid()) {
      String messages = String.join(", ", validator.getMessages(result));
      log.warn("Password validation failed: {}", messages);
      throw new PasswordPolicyException(
          "Password must be at least "
              + minLength
              + " characters with uppercase, lowercase, digit, and special character");
    }
  }

  /**
   * Checks whether a new password has been used recently by the user.
   *
   * <p><strong>Note:</strong> This method is currently a placeholder and always returns {@code
   * true} (i.e., allows the password). The implementation is pending and should be completed to
   * query the last N password hashes from Keycloak or a dedicated password history store. In
   * production, this would typically reject passwords that match any of the last 5 or 10 previously
   * used passwords.
   *
   * @param userId the ID of the user changing the password
   * @param newPassword the proposed new password
   * @return {@code true} if the password is not in the recent history (allowed), {@code false} if
   *     it should be rejected (not currently enforced)
   */
  public boolean checkPasswordHistory(String userId, String newPassword) {
    // TODO: Implement password history check against Keycloak or separate DB
    // For now, return true (allowed)
    // In production: query last 5 password hashes and compare
    return true;
  }
}
