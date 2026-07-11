package com.example.communicationservice.controller;

import static com.example.communicationservice.util.Util.mask;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.route.ApiRoutes;
import com.example.communicationservice.service.DlqReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for replaying messages from dead‑letter queues (DLQs).
 *
 * <p>All endpoints are restricted to users with the {@code ADMIN} role.
 *
 * @see DlqReplayService
 * @see ApiRoutes.Communication
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole(@role.admin())")
@Slf4j
public class DlqAdminController {

  private final DlqReplayService dlqReplayService;

  /**
   * Replays all messages from the OTP email DLQ back to the main OTP email queue.
   *
   * <p>The authenticated user's email is logged (masked) for audit purposes.
   *
   * @param authentication the current authentication object containing the JWT
   * @return a success response with the number of replayed messages
   */
  @PostMapping(
      path = {ApiRoutes.Communication.ADMIN_DQL_EMAIL, ApiRoutes.Communication.ADMIN_DQL_EMAIL_TS})
  public ResponseEntity<ApiResponse<String>> replayEmailDlq(Authentication authentication) {

    Jwt jwt = (Jwt) authentication.getPrincipal();

    log.info("Request by user: {}", mask(jwt.getClaimAsString("email")));

    int replayed =
        dlqReplayService.replay(
            CommunicationRoutingKeys.DLQ_OTP_EMAIL, CommunicationRoutingKeys.QUEUE_OTP_EMAIL);

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, replayed + " message(s) replayed from email DLQ"));
  }

  /**
   * Replays all messages from the OTP SMS DLQ back to the main OTP SMS queue.
   *
   * <p>(The endpoint does not log the requesting user, unlike the email counterpart.)
   *
   * @return a success response with the number of replayed messages
   */
  @PostMapping(
      path = {ApiRoutes.Communication.ADMIN_DQL_SMS, ApiRoutes.Communication.ADMIN_DQL_SMS_TS})
  public ResponseEntity<ApiResponse<String>> replaySmsDlq() {

    int replayed =
        dlqReplayService.replay(
            CommunicationRoutingKeys.DLQ_OTP_SMS, CommunicationRoutingKeys.QUEUE_OTP_SMS);

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, replayed + " message(s) replayed from SMS DLQ"));
  }
}
