package com.example.commonlib.responseenvelope.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.commonlib.responseenvelope.context.RequestContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ApiResponseTest {

  private MockedStatic<RequestContext> requestContextMock;

  @BeforeEach
  void setUp() {
    requestContextMock = mockStatic(RequestContext.class);
    when(RequestContext.getRequestPath()).thenReturn("/test/path");
    when(RequestContext.getTraceId()).thenReturn("trace-123");
  }

  @AfterEach
  void tearDown() {
    requestContextMock.close();
  }

  @Test
  void success_createsResponseWithData() {
    ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "message", "data");
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getMessage()).isEqualTo("message");
    assertThat(response.getData()).isEqualTo("data");
    assertThat(response.getError()).isNull();
    assertThat(response.getPath()).isEqualTo("/test/path");
    assertThat(response.getTraceId()).isEqualTo("trace-123");
  }

  @Test
  void success_defaultMessage() {
    ApiResponse<Integer> response = ApiResponse.success(HttpStatus.CREATED, 42);
    assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
    assertThat(response.getData()).isEqualTo(42);
  }

  @Test
  void created_usesCreatedStatus() {
    ApiResponse<String> response = ApiResponse.created("created", "item");
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMessage()).isEqualTo("created");
  }

  @Test
  void ok_usesOkStatus() {
    ApiResponse<String> response = ApiResponse.ok("ok", "item");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void noContent_returnsNoContent() {
    ApiResponse<Void> response = ApiResponse.noContent("no content");
    assertThat(response.getStatus()).isEqualTo(204);
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isNull();
  }

  @Test
  void successList_returnsList() {
    List<String> list = List.of("a", "b");
    ApiResponse<List<String>> response = ApiResponse.successList("list", list);
    assertThat(response.getData()).isEqualTo(list);
  }

  @Test
  void successPage_returnsPageData() {
    Page<String> page =
        new PageImpl<>(List.of("x", "y"), org.springframework.data.domain.PageRequest.of(1, 2), 10);
    ApiResponse<ApiResponse.PageData<String>> response = ApiResponse.successPage("page", page);
    assertThat(response.getData().getContent()).containsExactly("x", "y");
    assertThat(response.getData().getTotalElements()).isEqualTo(10);
    assertThat(response.getData().getPageNumber()).isEqualTo(1);
    assertThat(response.getData().getPageSize()).isEqualTo(2);
  }

  @Test
  void error_createsErrorResponse() {
    ApiResponse<Void> response =
        ApiResponse.error(HttpStatus.BAD_REQUEST, "error", "ERR", "detail");
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getError().getCode()).isEqualTo("ERR");
    assertThat(response.getError().getDetail()).isEqualTo("detail");
  }

  @Test
  void validationError_createsWithValidationErrors() {
    List<ApiResponse.ValidationError> errors =
        List.of(new ApiResponse.ValidationError("field", "message", "rejected"));
    ApiResponse<Void> response = ApiResponse.validationError("validation", errors);
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getError().getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getError().getValidationErrors()).containsExactlyElementsOf(errors);
  }

  @Test
  void badRequest_createsError() {
    ApiResponse<Void> response = ApiResponse.badRequest("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("BAD_REQUEST");
  }

  @Test
  void unauthorized_createsError() {
    ApiResponse<Void> response = ApiResponse.unauthorized("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void forbidden_createsError() {
    ApiResponse<Void> response = ApiResponse.forbidden("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("FORBIDDEN");
  }

  @Test
  void notFound_createsError() {
    ApiResponse<Void> response = ApiResponse.notFound("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("NOT_FOUND");
  }

  @Test
  void conflict_createsError() {
    ApiResponse<Void> response = ApiResponse.conflict("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("CONFLICT");
  }

  @Test
  void internalError_createsError() {
    ApiResponse<Void> response = ApiResponse.internalError("msg", "detail");
    assertThat(response.getError().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
  }
}
