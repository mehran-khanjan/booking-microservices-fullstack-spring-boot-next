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

## Acceptance Criteria 1.1.2 to 1.1.10

**Note 1:** The system will follow a Microservices architecture, comprising:

1. API Gateway (Spring Cloud Gateway)
2. Service Discovery (Eureka)
3. Auth Service (+ Keycloak)
4. Common Library
5. Outbox Library (Inbox-outbox Pattern with RabbitMQ)
6. Communication Service (Email, SMS, Notification Handler)

**Note 2:** API Gateway new files:

Here is a one-line explanation for each file:

- **`GlobalExceptionHandler.java`** – Catches **all unhandled exceptions**, logs them with a trace ID, and returns a standardized `ApiResponse` with HTTP 500.

**Note 3:** Auth service new files:

Here is a one-line explanation for each file:

- **GlobalExceptionHandler.java** – Catches all unhandled exceptions, logs them with a trace ID, and returns a standardized 500 `ApiResponse`.

- **CommunicationRabbitConfig.java** – Defines RabbitMQ exchange, object mapper, custom message converter, and `RabbitTemplate` for outbox publishing.

- **CustomMessageConverter.java** – Custom JSON message converter for RabbitMQ; `fromMessage` is stubbed (returns null) and needs implementation.

- **GoogleIdTokenVerifierConfig.java** – Provides a `GoogleIdTokenVerifier` bean for validating Google OAuth2 ID tokens.

- **SchedulingConfig.java** – Enables Spring scheduling (`@EnableScheduling`) for outbox and background jobs.

- **otp/SmsService.java** – Queues OTP SMS events via the outbox for the notification service to send via Twilio.

- **otp/EmailService.java** – Queues OTP email events via the outbox for the notification service to send via SendGrid.

- **otp/OtpService.java** – Generates, stores (in Redis), and verifies OTPs with attempt limits, dispatching via email/SMS channels.

- **keycloak/KeycloakUserAdminService.java** – Resilient Keycloak admin client for user creation, role assignment, attribute updates, and federated identity linking.

- **keycloak/KeycloakTokenExchangeService.java** – Exchanges a service account token for a user token using Keycloak's token exchange grant, with caching.

- **AccountLockoutService.java** – Tracks failed login attempts in Redis and temporarily locks accounts after a configurable threshold.

- **PasswordValidationService.java** – Validates password strength using Passay rules (length, upper, lower, digit, special).

- **TokenRevocationService.java** – Stores revoked tokens in Redis for blacklisting during logout or refresh.

**Note 3:** Outbox library new files:

Here is a one-line explanation for each file:

- **inbox/repository/InboxRepository.java** – JPA repository for `InboxEvent` with methods to find by `eventId` and check existence for idempotency.

- **inbox/converter/EpochMillisConverter.java** – JPA converter to map `LocalDateTime` to/from epoch milliseconds (stored as `Long`) in the database.

- **inbox/entity/InboxEvent.java** – Entity representing a consumed message with status tracking (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`) and an idempotency key.

- **outbox/repository/OutboxRepository.java** – JPA repository for `OutboxEvent` with custom queries to fetch events by status, retry count, and timestamps.

- **outbox/converter/EpochMillisConverter.java** – Same epoch-millis converter for outbox date fields (consistent with inbox).

- **outbox/entity/OutboxEvent.java** – Entity for an outbox message with status (`PENDING`, `PROCESSING`, `PUBLISHED`, `FAILED`), routing keys, and exchange.

- **outbox/service/OutboxService.java** – Service to save an event to the outbox table inside the caller’s transaction (`@Transactional(propagation = MANDATORY)`).

- **outbox/service/OutboxPublisher.java** – Scheduled publisher that polls pending outbox rows, sends them to RabbitMQ, and updates statuses with recovery for stuck events.

**Note 4:** Communication service new files:

Here is a one-line explanation for each file:

- **DeathCountExtractor.java** – Extracts cumulative dead‑letter count from RabbitMQ’s `x‑death` header for a specific queue.

- **Util.java** – Provides utility methods for masking emails and phone numbers in logs.

- **CommunicationServiceApplication.java** – Spring Boot entry point with component scanning and JPA configuration for the inbox library.

- **SmsService.java** – Sends OTP SMS via Twilio with circuit‑breaker and retry resilience.

- **DlqReplayService.java** – Moves messages from a dead‑letter queue back to its original live queue for manual replay.

- **IdempotencyGuard.java** – Uses Redis SETNX to prevent duplicate processing of the same eventId (24h window).

- **DlqMonitorService.java** – Periodically checks DLQ depths and exposes them as Prometheus gauges, logging errors when non‑empty.

- **EmailService.java** – Sends transactional emails (OTP, password reset) via SendGrid with resilience patterns.

- **RoleBean.java** – Exposes role names (`ADMIN`, `USER`) as Spring beans for use in `@PreAuthorize` expressions.

- **CorsProperties.java** – Binds `app.cors.allowed‑origins` for CORS configuration.

- **SecurityConfig.java** – Configures stateless JWT resource server with Keycloak, extracting realm roles and admin‑only endpoints.

- **CommunicationRabbitConfig.java** – Declares exchanges, queues (with DLX), bindings, and a listener container factory for RabbitMQ.

- **CustomMessageConverter.java** – Converts incoming RabbitMQ messages to `OtpEmailEvent` or `OtpSmsEvent` based on the `eventType` header.

- **DlqAdminController.java** – Exposes admin endpoints (secured with `ROLE_ADMIN`) to trigger DLQ replays for email and SMS queues.

- **CommunicationConsumer.java** – Implements the inbox‑pattern consumer: claims inbox records, processes events, and updates status (idempotent, failure tracking).

- **GlobalExceptionHandler.java** – Handles all uncaught exceptions, logs them with trace ID, and returns a standardized 500 error response.

## Acceptance Criteria 2.1.1 to 2.1.3

**Note 1:** Flight service new files:

Here is a one-line explanation for each file:

- **FlightSearchLegService.java** – Handles a single leg search with Redis caching, JPA Specifications, and pagination, returning a fully populated response.

- **FlightSearchService.java** – Orchestrates one‑way or round‑trip searches; executes outbound and return leg searches in parallel with timeout handling and context propagation.

- **FlightSpecification.java** – Provides static JPA `Specification` methods for building dynamic flight filters (origin, destination, price, airline, aircraft type, cabin availability).

- **AircraftTypeRepository.java** – Spring Data JPA repository for `AircraftType` entities (aircraft models with seat configurations).

- **AirportRepository.java** – JPA repository for `Airport` with methods to find by IATA code (case‑sensitive/insensitive), by city, and a full‑text search across name, city, and code.

- **AirlineRepository.java** – JPA repository for `Airline` entities, providing basic CRUD operations.

- **FlightRepository.java** – Advanced JPA repository for `Flight` with custom fetch queries, pessimistic/optimistic locking, and atomic seat reservation/release updates.

- **Flight.java** – Core entity representing a scheduled flight with all operational fields, status enum, and optimistic locking (`@Version`).

- **Airport.java** – Entity storing IATA/ICAO codes, location, timezone, and geographic coordinates for airports.

- **AircraftType.java** – Entity defining aircraft model, manufacturer, and seat distribution across economy, premium economy, business, and first class.

- **Airline.java** – Entity for airline with IATA code, name, country, logo URL, and an active flag.

- **DataFakerLoader.java** – Populates the database with realistic test data (500+ flights, airports, airlines, aircraft types) when running under the `dev` profile.

- **ContextPropagatingSupplier.java** – Utility that wraps a `Supplier` to propagate MDC and SecurityContext to a different thread (used for async leg searches).

- **CorsProperties.java** – Configuration properties (`app.cors.allowed-origins`) for CORS allowed origins.

- **ErrorCodes.java** – Central registry of error codes (e.g., `FLIGHT_001`, `SEARCH_TIMEOUT_001`) for consistent error handling.

- **SecurityConfig.java** – Spring Security configuration with OAuth2 resource server, JWT authentication converter (extracts Keycloak realm roles), and CORS.

- **AsyncExecutorConfig.java** – Provides a dedicated `ThreadPoolTaskExecutor` bean for async flight searches, with back‑pressure using `CallerRunsPolicy`.

- **FlightMapper.java** – Maps `Flight` entities to `FlightResponseDto`, flattening nested relationships and formatting duration.

- **FlightSearchResponseDto.java** – Search response DTO containing outbound flights, optional return flights, metadata, and pagination info.

- **FlightSearchRequestDto.java** – Search request DTO with validation constraints and all optional filters (price, airline, cabin, sorting, pagination).

- **FlightResponseDto.java** – Detailed flight response DTO with nested objects for airline, airports, pricing, and seat availability.

- **FlightServiceApplication.java** – Spring Boot main class with `@EnableDiscoveryClient` for service registration.

- **FlightSearchController.java** – REST controller exposing flight search endpoints with `@PreAuthorize`, `@RateLimiter`, and `@Idempotent` annotations.

- **GlobalExceptionHandler.java** – Central `@RestControllerAdvice` that catches validation errors, malformed JSON, and all other exceptions, returning structured `ApiResponse` errors.

- **BusinessException.java** – Custom runtime exception for domain‑specific errors, carrying an error code from `ErrorCodes`.

## Acceptance Criteria 3.1.1 to 3.1.7

**Note 1:** Booking service new files:

Here is a one-line explanation for each file:

- **`bookingservice/repository/BookingRepository.java`** – Repository for `Booking` entities with custom query methods and pessimistic locking.

- **`bookingservice/repository/AdditionalServiceRepository.java`** – Repository for `AdditionalService` entities, supporting lookup by booking.

- **`bookingservice/repository/PassengerRepository.java`** – Repository for `Passenger` entities, supporting lookup by booking.

- **`bookingservice/repository/BookingFlightRepository.java`** – Repository for `BookingFlight` entities, providing ordered flight segments per booking.

- **`bookingservice/entity/BookingFlight.java`** – Entity representing a single flight segment within a booking, with denormalised flight data.

- **`bookingservice/entity/Passenger.java`** – Entity representing a passenger associated with a booking.

- **`bookingservice/entity/Flight.java`** – Core entity representing a scheduled flight with operational details, seat availability, and pricing.

- **`bookingservice/entity/Booking.java`** – Core booking entity holding flights, passengers, services, and lifecycle status.

- **`bookingservice/entity/Airport.java`** – Entity representing an airport with IATA/ICAO codes and location metadata.

- **`bookingservice/entity/AircraftType.java`** – Entity representing an aircraft model with seat capacities per cabin.

- **`bookingservice/entity/Airline.java`** – Entity representing an airline with IATA code, name, and logo.

- **`bookingservice/entity/AdditionalService.java`** – Entity for optional services (e.g., extra baggage, meals) attached to a booking.

- **`bookingservice/enums/TimeOfDayRange.java`** – Enum representing time‑of‑day ranges for flight schedule filtering.

- **`bookingservice/enums/CabinClass.java`** – Enum for cabin classes (economy, business, etc.).

- **`bookingservice/BookingServiceApplication.java`** – Spring Boot main class that starts the Booking Service.

- **`bookingservice/property/StripeProperties.java`** – Configuration properties for Stripe payment gateway (API key, webhook secret).

- **`bookingservice/property/CorsProperties.java`** – Configuration properties for CORS allowed origins.

- **`bookingservice/property/PayPalProperties.java`** – Configuration properties for PayPal payment gateway (client ID, secret, base URL).

- **`bookingservice/constant/ErrorCodes.java`** – Central registry of unique error codes grouped by domain.

- **`bookingservice/config/SecurityConfig.java`** – Spring Security configuration with OAuth2 resource server, JWT, and role‑based authorization.

- **`bookingservice/config/GrpcClientConfig.java`** – gRPC client configuration providing a blocking stub for the Flight Service.

- **`bookingservice/config/LockingStrategyConfig.java`** – Configuration for the booking locking strategy (distributed or pessimistic).

- **`bookingservice/config/RedisConfig.java`** – Redis configuration using Redisson for distributed locking.

- **`bookingservice/config/SchedulingConfig.java`** – Enables Spring’s scheduled task execution for hold‑expiry sweeps.

- **`bookingservice/mapper/BookingMapper.java`** – Mapper between `Booking` entities and `BookingResponse` DTOs, including passenger conversion.

- **`bookingservice/servcie/payment/StripePaymentGatewayService.java`** – Stripe integration implementing `PaymentGatewayService` via PaymentIntents.

- **`bookingservice/servcie/payment/PayPalPaymentGatewayService.java`** – PayPal integration capturing orders via REST API.

- **`bookingservice/servcie/payment/PaymentRequest.java`** – Internal DTO for triggering a payment charge.

- **`bookingservice/servcie/payment/PaymentGatewayResult.java`** – Gateway‑agnostic outcome of a charge attempt.

- **`bookingservice/servcie/payment/PaymentGatewayFactory.java`** – Factory that resolves the appropriate payment gateway bean by method name.

- **`bookingservice/servcie/payment/PaymentChargeRequest.java`** – Gateway‑agnostic request object passed to payment gateways.

- **`bookingservice/servcie/payment/PaymentGatewayService.java`** – Interface for all payment gateway implementations.

- **`bookingservice/servcie/BookingHoldExpiryScheduler.java`** – Scheduled task that releases seats for expired booking holds.

- **`bookingservice/servcie/OrderService.java`** – Handles payment processing, validation, and booking confirmation lifecycle.

- **`bookingservice/servcie/FlightServiceClient.java`** – Client for the Flight gRPC service to fetch flights, reserve, and release seats.

- **`bookingservice/servcie/BookingService.java`** – Core service for creating bookings with distributed/optimistic locking and compensation logic.

- **`bookingservice/servcie/BookingLockingService.java`** – Distributed locking service using Redisson for single and multi‑lock acquisitions.

- **`bookingservice/dto/controller/BookingResponse.java`** – Response DTO containing complete booking details.

- **`bookingservice/dto/controller/CreateBookingRequest.java`** – Request DTO for creating a new booking with flights, passengers, and services.

- **`bookingservice/dto/controller/PaymentRequest.java`** – Request DTO for processing a payment.

- **`bookingservice/dto/controller/CancelBookingRequest.java`** – Request DTO for cancelling a booking (full or partial).

- **`bookingservice/dto/controller/PaymentResponse.java`** – Response DTO for payment processing results.

- **`bookingservice/controller/FlightBookingController.java`** – REST controller exposing booking creation and payment endpoints with circuit‑breaker fallbacks.

- **`bookingservice/exception/GlobalExceptionHandler.java`** – Global exception handler providing structured `ApiResponse` errors for validation and unexpected failures.

- **`bookingservice/exception/BusinessException.java`** – Custom runtime exception for business logic errors with an error code.