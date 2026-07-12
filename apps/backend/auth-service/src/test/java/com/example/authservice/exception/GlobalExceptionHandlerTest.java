package com.example.authservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.commonlib.responseenvelope.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Each handler method is invoked directly (no
 * Spring context required) with a real or lightly-mocked exception to verify the resulting HTTP
 * status and that the response body carries a sensible message.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
    }

    @Test
    void handleMethodArgumentNotValid_returns400_withFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        MethodParameter methodParameter =
                new MethodParameter(this.getClass().getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void handleBindException_returns400_withFieldErrors() {
        BindException ex = new BindException(new Object(), "request");
        ex.addError(new FieldError("request", "password", "must not be blank"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void handleConstraintViolation_returns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("must not be null");
        when(violation.getPropertyPath()).thenReturn(new PathStub());
        when(violation.getInvalidValue()).thenReturn(null);

        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleHttpMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGlobalException_returns500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGlobalException(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Minimal {@link jakarta.validation.Path} stub used only when no validator-produced path is available. */
    private static class PathStub implements jakarta.validation.Path {
        @Override
        public java.util.Iterator<Node> iterator() {
            return java.util.Collections.<Node>emptyList().iterator();
        }

        @Override
        public String toString() {
            return "field";
        }
    }
}
