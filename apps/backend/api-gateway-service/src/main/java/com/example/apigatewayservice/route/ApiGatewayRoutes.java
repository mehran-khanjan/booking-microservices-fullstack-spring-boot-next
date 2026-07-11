package com.example.apigatewayservice.route;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** Defines API Gateway routing rules using Spring Cloud Gateway's functional DSL. */
@Configuration
public class ApiGatewayRoutes {

  @Bean
  public RouterFunction<ServerResponse> customRoutes() {

    var authRoutes =
        route("auth-service")
            .route(path("/api/v1/auth/**"), http())
            .filter(lb("auth-service"))
            .build();

    var communicationRoutes =
        route("communication-service")
            .route(path("/api/v1/communication/**"), http())
            .filter(lb("communication-service"))
            .build();

    return authRoutes.and(communicationRoutes);
  }
}
