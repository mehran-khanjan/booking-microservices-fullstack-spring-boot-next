package com.example.apigatewayservice.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class ApiGatewayRoutesTest {

  private final ApiGatewayRoutes routes = new ApiGatewayRoutes();

  @Test
  @DisplayName("customRoutes bean is created successfully")
  void customRoutesBeanIsCreated() {
    RouterFunction<ServerResponse> routerFunction = routes.customRoutes();
    assertThat(routerFunction).isNotNull();
  }

  @Test
  @DisplayName("matches requests under /api/v1/auth/**")
  void matchesAuthPath() {
    RouterFunction<ServerResponse> routerFunction = routes.customRoutes();

    MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/api/v1/auth/login");
    ServerRequest serverRequest = ServerRequest.create(mockRequest, Collections.emptyList());

    assertThat(routerFunction.route(serverRequest)).isPresent();
  }

  @Test
  @DisplayName("does not match unrelated paths")
  void doesNotMatchUnrelatedPath() {
    RouterFunction<ServerResponse> routerFunction = routes.customRoutes();

    MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/api/v1/bookings/123");
    ServerRequest serverRequest = ServerRequest.create(mockRequest, Collections.emptyList());

    assertThat(routerFunction.route(serverRequest)).isNotPresent();
  }
}
