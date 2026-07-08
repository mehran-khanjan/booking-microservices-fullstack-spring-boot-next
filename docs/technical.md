# Technical Notes

## Acceptance Criteria 1.1.1

**Note 1:** I chose Nx for the monorepo to handle the Next.js frontend application and the Spring Boot backend application.

**Note 2:** Create 4 docker compose files:
- base
- dev
- test
- prod

**Prerequisites:**
1. Use the `env` file to fill variables in docker compose files.
2. Fill the `realm_config.json` to config Keycloak

```bash
# Use this script to run docker compose
./docker-compose-run-script.sh
```

**Note 3:** The system will follow a Microservices architecture, comprising:

1. API Gateway (Spring Cloud Gateway)
2. Service Discovery (Eureka)
3. Auth Service (+ Keycloak)
4. Common Library

**Note 4:** Using Keycloak config file to create the default values

**Note 5:** API Gateway new files:

Here is a one-line explanation for each file:

- **`RequestTracingContext.java`** – Manages per-request distributed tracing (Correlation, Request, Transaction IDs), injects them into MDC, wraps the request to propagate headers, echoes them on the response, and auto-clears MDC.

- **`GatewayEdgeFilter.java`** – The highest-priority servlet filter that orchestrates the layered validation pipeline (tracing → forwarded headers → locale → idempotency) and handles error responses for validation failures.

- **`ApiGatewayRoutes.java`** – Defines the gateway's routing rules, using functional endpoints to forward `/api/v1/auth/**` requests to the `auth-service` with load balancing.

- **`LocaleProperties.java`** – Holds configuration properties (`apigw.locale`) for locale validation, including supported languages/locales, defaults, and enabling/disabling.

- **`LocaleValidator.java`** – Provides the core logic to validate the `Accept-Language` header format, extract the primary language tag, and check it against configured supported lists.

- **`LocaleContext.java`** – An immutable per‑request context that validates the `Accept-Language` header, injects the locale into MDC, wraps the request, echoes `Content-Language` on the response, and auto-clears MDC.

- **`IdempotencyValidator.java`** – Determines which HTTP methods require idempotency checks (all except GET) and strictly validates the `Idempotency-Key` header as a proper UUID.

- **`IdempotencyContext.java`** – An immutable per‑request context that validates the `Idempotency-Key` for mutating operations, injects it into MDC, wraps the request, and auto-clears MDC.

- **`IdempotencyProperties.java`** – Holds configuration properties (`apigw.idempotency`) to enable/disable idempotency and set constraints like the max header length.

- **`ForwardedHeaderRequestWrapper.java`** – Synthesizes missing standard forwarded headers (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, and `Forwarded`) based on the actual request details, without overwriting existing ones.

**Note 6:** Auth service new files:

Here is a one-line explanation for each file:

- **`Util.java`** – Utility class that masks sensitive strings (like emails or passwords) for safe logging without exposing full values.

- **`KeycloakUserAdminService.java`** – A resilient, fault‑tolerant service that encapsulates all Keycloak Admin API calls to create users, assign realm roles, and handle responses.

- **`AuthService.java`** – Orchestrates the user registration flow by calling the Keycloak admin service with predefined attributes (signup method, phone verification).

- **`CorsProperties.java`** – Configuration property holder that binds `app.cors.allowed-origins` for cross-origin request handling.

- **`SecurityConfig.java`** – Defines the security filter chain with stateless session, OAuth2 resource server, custom JWT authentication converter (extracting realm roles), and CORS configuration.

- **`KeycloakConfig.java`** – Creates the Keycloak admin client bean using credentials and server URL from properties.

- **`EmailCreateUserResponseDto.java`** – Response DTO containing the newly created user's UUID after successful sign-up.

- **`EmailReadUserRequestDto.java`** – Request DTO for sign-up, holding email and password fields with validation annotations.

- **`AuthController.java`** – REST controller exposing the `/signup` endpoint with idempotency and rate-limiting, handling user registration and returning appropriate HTTP statuses.

- **`AuthServiceApplication.java`** – Spring Boot main class that bootstraps the service and enables service discovery via `@EnableDiscoveryClient`.