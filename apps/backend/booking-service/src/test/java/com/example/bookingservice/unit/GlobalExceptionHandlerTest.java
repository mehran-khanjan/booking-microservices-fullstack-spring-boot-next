package com.example.bookingservice.unit;

import com.example.bookingservice.exception.GlobalExceptionHandler;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @Mock private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(request.getRequestURI()).thenReturn("/api/test");
  }

  @Test
  void shouldHandleMethodArgumentNotValid() {
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);

    FieldError fieldError = new FieldError("object", "email", "test@", false, null, null, "must be a well-formed email");
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
    when(ex.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(response.getBody().getError().getValidationErrors()).hasSize(1);
    assertThat(response.getBody().getError().getValidationErrors().get(0).getField()).isEqualTo("email");
  }

  @Test
  void shouldHandleBindException() {
    BindException ex = mock(BindException.class);
    org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);

    FieldError fieldError = new FieldError("object", "name", null, false, null, null, "name is required");
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
    when(ex.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getError().getValidationErrors()).hasSize(1);
  }

  @Test
  void shouldHandleConstraintViolation() {
    @SuppressWarnings("rawtypes")
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    Path path = mock(Path.class);

    when(path.toString()).thenReturn("email");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must be a valid email");
    when(violation.getInvalidValue()).thenReturn("invalid");

    @SuppressWarnings("unchecked")
    Set<ConstraintViolation<?>> violations = Set.of(violation);
    ConstraintViolationException ex = new ConstraintViolationException("validation failed", violations);

    ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getError().getValidationErrors()).hasSize(1);
  }

  @Test
  void shouldHandleHttpMessageNotReadable() {
    HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
    when(ex.getMessage()).thenReturn("Malformed JSON");

    ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadable(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("Malformed");
  }

  @Test
  void shouldHandleGenericException() {
    Exception ex = new RuntimeException("Unexpected error");

    ResponseEntity<ApiResponse<Void>> response = handler.handleGlobalException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).contains("unexpected error");
  }
}