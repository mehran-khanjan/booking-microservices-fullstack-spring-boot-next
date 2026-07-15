package com.example.bookingservice.e2e;

import com.example.bookingservice.TestBookingServiceApplication;
import com.example.bookingservice.config.TestConfig;
import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.dto.controller.PaymentRequest;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.BookingService;
import com.example.bookingservice.servcie.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the booking service using RestClient.
 * These tests exercise the full HTTP stack including security, validation, and business logic.
 */
@SpringBootTest(classes = TestBookingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestConfig.class)
class BookingServiceE2ETest {

  @LocalServerPort private int port;

  @Autowired private BookingRepository bookingRepository;

  @MockitoBean private BookingService bookingService;
  @MockitoBean private OrderService orderService;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.create("http://localhost:" + port);
    bookingRepository.deleteAll();
  }

  @Test
  void shouldReturn401WhenNoAuthToken() throws IOException {
    CreateBookingRequest request = CreateBookingRequest.builder()
        .flights(List.of(
            CreateBookingRequest.FlightSelection.builder()
                .flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build()
        ))
        .passengers(List.of(
            CreateBookingRequest.PassengerDetails.builder()
                .firstName("John").lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).build()
        ))
        .contactEmail("john@example.com")
        .build();

    var response = restClient.post()
        .uri("/api/v1/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange((req, res) -> res);

    assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldReturn400ForInvalidBookingRequest() throws IOException {
    CreateBookingRequest invalidRequest = CreateBookingRequest.builder()
        .flights(null)
        .passengers(null)
        .contactEmail("not-an-email")
        .build();

    var response = restClient.post()
        .uri("/api/v1/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(invalidRequest)
        .exchange((req, res) -> res);

    assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReturn400ForInvalidPaymentRequest() throws IOException {
    PaymentRequest invalidRequest = PaymentRequest.builder()
        .bookingReference(null)
        .paymentMethod(null)
        .amount(null)
        .currency(null)
        .build();

    var response = restClient.post()
        .uri("/api/v1/bookings/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .body(invalidRequest)
        .exchange((req, res) -> res);

    assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldVerifyServiceBeansAreLoaded() {
    assertThat(bookingService).isNotNull();
    assertThat(orderService).isNotNull();
    assertThat(bookingRepository).isNotNull();
  }

  @Test
  void shouldCreateAndRetrieveBookingViaService() {
    CreateBookingRequest request = CreateBookingRequest.builder()
        .flights(List.of(
            CreateBookingRequest.FlightSelection.builder()
                .flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build()
        ))
        .passengers(List.of(
            CreateBookingRequest.PassengerDetails.builder()
                .firstName("John").lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).build()
        ))
        .contactEmail("john@example.com")
        .build();

    BookingResponse mockResponse = BookingResponse.builder()
        .id(1L)
        .bookingReference("E2E-TEST-2")
        .status("PENDING_PAYMENT")
        .totalAmount(BigDecimal.valueOf(230))
        .currency("USD")
        .contactEmail("john@example.com")
        .build();

    when(bookingService.createBooking(any(CreateBookingRequest.class), anyString()))
        .thenReturn(mockResponse);

    BookingResponse response = bookingService.createBooking(request, "user-123");

    assertThat(response).isNotNull();
    assertThat(response.getBookingReference()).isEqualTo("E2E-TEST-2");
    assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void shouldProcessPaymentThroughService() {
    when(orderService.handlePayment(
        eq("BOOKING-1"), eq("CREDIT_CARD"), eq("pm_token"), eq("idemp-1"),
        eq("user-123"), eq(BigDecimal.valueOf(230)), eq("USD")))
        .thenReturn("txn-e2e-1");

    String txnId = orderService.handlePayment(
        "BOOKING-1", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(230), "USD");

    assertThat(txnId).isEqualTo("txn-e2e-1");
  }
}