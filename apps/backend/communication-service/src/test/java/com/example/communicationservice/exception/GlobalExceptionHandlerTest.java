package com.example.communicationservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commonlib.responseenvelope.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleGlobalException_returns500WithGenericErrorBody() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/communication/anything");
    RuntimeException ex = new RuntimeException("boom");

    ResponseEntity<ApiResponse<Void>> response = handler.handleGlobalException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  @Test
  void handleGlobalException_neverLeaksExceptionMessageToClient() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/communication/anything");
    RuntimeException ex = new RuntimeException("sensitive internal detail: db password=hunter2");

    ResponseEntity<ApiResponse<Void>> response = handler.handleGlobalException(ex, request);

    assertThat(response.getBody().toString()).doesNotContain("hunter2");
  }
}
