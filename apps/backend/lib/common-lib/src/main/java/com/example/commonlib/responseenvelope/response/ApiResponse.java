package com.example.commonlib.responseenvelope.response;

import com.example.commonlib.responseenvelope.context.RequestContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

/**
 * Unified API response envelope for both success and error responses.
 *
 * <p>Example success:
 *
 * <pre>
 * {
 *   "status": 201,
 *   "success": true,
 *   "timestamp": "...",
 *   "path": "/api/users",
 *   "message": "Task retrieved successfully",
 *   "traceId": "...",
 *   "data": { ... },
 *   "error": null
 * }
 * </pre>
 *
 * <p>Example error:
 *
 * <pre>
 * {
 *   "status": 404,
 *   "success": false,
 *   "timestamp": "...",
 *   "path": "/admin1",
 *   "message": "Not found",
 *   "traceId": "...",
 *   "data": null,
 *   "error": { "code": "NOT_FOUND", "detail": "..." }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
  "status",
  "success",
  "timestamp",
  "path",
  "message",
  "traceId",
  "data",
  "error"
})
public class ApiResponse<T> {

  private LocalDateTime timestamp;
  private int status;
  private boolean success;
  //  request URL path
  private String path;
  private String message;
  private String traceId;
  private T data;
  private ErrorDetail error;

  // ========================================
  // Nested Classes
  // ========================================

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ErrorDetail {
    private String code;
    private String detail;
    private List<ValidationError> validationErrors;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class ValidationError {
    private String field;
    private String message;
    private Object rejectedValue;
  }

  /** Wrapper for paginated data */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PageData<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;
    private boolean hasContent;
    private int numberOfElements;
    private boolean empty;
  }

  // ========================================
  // SUCCESS Factory Methods
  // ========================================

  /** Success response with data */
  public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
    return ApiResponse.<T>builder()
        .timestamp(LocalDateTime.now())
        .status(status.value())
        .success(true)
        .path(RequestContext.getRequestPath())
        .message(message)
        .traceId(RequestContext.getTraceId())
        .data(data)
        .error(null)
        .build();
  }

  /** Success response with default message */
  public static <T> ApiResponse<T> success(HttpStatus status, T data) {
    return success(status, "Operation completed successfully", data);
  }

  /** 201 Created response */
  public static <T> ApiResponse<T> created(String message, T data) {
    return success(HttpStatus.CREATED, message, data);
  }

  /** 200 OK response */
  public static <T> ApiResponse<T> ok(String message, T data) {
    return success(HttpStatus.OK, message, data);
  }

  /** 204 No Content response */
  public static <Void> ApiResponse<Void> noContent(String message) {
    return ApiResponse.<Void>builder()
        .timestamp(LocalDateTime.now())
        .status(HttpStatus.NO_CONTENT.value())
        .success(true)
        .path(RequestContext.getRequestPath())
        .message(message)
        .traceId(RequestContext.getTraceId())
        .data(null)
        .error(null)
        .build();
  }

  /** Success response with list data */
  public static <T> ApiResponse<List<T>> successList(String message, List<T> data) {
    return success(HttpStatus.OK, message, data);
  }

  /** Success response with paginated data */
  public static <T> ApiResponse<PageData<T>> successPage(String message, Page<T> page) {
    PageData<T> pageData =
        PageData.<T>builder()
            .content(page.getContent())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .last(page.isLast())
            .first(page.isFirst())
            .hasContent(page.hasContent())
            .numberOfElements(page.getNumberOfElements())
            .empty(page.isEmpty())
            .build();

    return success(HttpStatus.OK, message, pageData);
  }

  // ========================================
  // ERROR Factory Methods
  // ========================================

  /** Error response with error detail */
  public static <T> ApiResponse<T> error(
      HttpStatus status, String message, String errorCode, String errorDetail) {
    return ApiResponse.<T>builder()
        .timestamp(LocalDateTime.now())
        .status(status.value())
        .success(false)
        .path(RequestContext.getRequestPath())
        .message(message)
        .traceId(RequestContext.getTraceId())
        .data(null)
        .error(ErrorDetail.builder().code(errorCode).detail(errorDetail).build())
        .build();
  }

  /** Error response with validation errors */
  public static <T> ApiResponse<T> validationError(
      String message, List<ValidationError> validationErrors) {
    return ApiResponse.<T>builder()
        .timestamp(LocalDateTime.now())
        .status(HttpStatus.BAD_REQUEST.value())
        .success(false)
        .path(RequestContext.getRequestPath())
        .message(message)
        .traceId(RequestContext.getTraceId())
        .data(null)
        .error(
            ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .detail(message)
                .validationErrors(validationErrors)
                .build())
        .build();
  }

  /** 400 Bad Request */
  public static <T> ApiResponse<T> badRequest(String message, String detail) {
    return error(HttpStatus.BAD_REQUEST, message, "BAD_REQUEST", detail);
  }

  /** 401 Unauthorized */
  public static <T> ApiResponse<T> unauthorized(String message, String detail) {
    return error(HttpStatus.UNAUTHORIZED, message, "UNAUTHORIZED", detail);
  }

  /** 403 Forbidden */
  public static <T> ApiResponse<T> forbidden(String message, String detail) {
    return error(HttpStatus.FORBIDDEN, message, "FORBIDDEN", detail);
  }

  /** 404 Not Found */
  public static <T> ApiResponse<T> notFound(String message, String detail) {
    return error(HttpStatus.NOT_FOUND, message, "NOT_FOUND", detail);
  }

  /** 409 Conflict */
  public static <T> ApiResponse<T> conflict(String message, String detail) {
    return error(HttpStatus.CONFLICT, message, "CONFLICT", detail);
  }

  /** 500 Internal Server Error */
  public static <T> ApiResponse<T> internalError(String message, String detail) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, message, "INTERNAL_SERVER_ERROR", detail);
  }
}
