package com.example.authservice.controller;

import com.example.authservice.dto.EmailCreateUserResponseDto;
import com.example.authservice.dto.EmailReadUserRequestDto;
import com.example.authservice.service.AuthService;
import com.example.commonlib.exception.UserAlreadyExistsException;
import com.example.commonlib.idempotency.Idempotent;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.route.ApiRoutes;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for authentication‑related endpoints (sign‑up, etc.). */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Idempotent(ttlSeconds = 3600)
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_UP, ApiRoutes.Auth.SIGN_UP_TS})
  public ResponseEntity<ApiResponse<EmailCreateUserResponseDto>> signUp(
      @Valid @RequestBody EmailReadUserRequestDto request) {
    try {
      String userId = authService.registerUser(request.email(), request.password());

      EmailCreateUserResponseDto res =
          EmailCreateUserResponseDto.builder().userId(UUID.fromString(userId)).build();

      return ResponseEntity.status(HttpStatus.CREATED)
          .header("Location", "/api/v1/users/" + userId)
          .body(
              ApiResponse.success(
                  HttpStatus.CREATED, "Registered. Please verify your email.", res));
    } catch (UserAlreadyExistsException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.conflict("User already exists", e.getMessage()));
    }
  }
}
