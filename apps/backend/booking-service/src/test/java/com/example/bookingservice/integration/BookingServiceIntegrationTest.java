package com.example.bookingservice.integration;

import com.example.bookingservice.TestBookingServiceApplication;
import com.example.bookingservice.config.LockingStrategyConfig;
import com.example.bookingservice.config.TestConfig;
import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.Flight;
import com.example.bookingservice.mapper.BookingMapper;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.BookingLockingService;
import com.example.bookingservice.servcie.BookingService;
import com.example.bookingservice.servcie.FlightServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestBookingServiceApplication.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class BookingServiceIntegrationTest {

  @Autowired private BookingRepository bookingRepository;
  @Autowired private BookingMapper bookingMapper;
  @Autowired private LockingStrategyConfig lockingConfig;

  @MockitoBean private FlightServiceClient flightServiceClient;
  @MockitoBean private BookingLockingService lockingService;

  private BookingService bookingService;

  @BeforeEach
  void setUp() {
    lockingConfig.setStrategy("OPTIMISTIC");
    bookingService = new BookingService(lockingConfig, lockingService, bookingRepository, bookingMapper, flightServiceClient);
    bookingRepository.deleteAll();
  }

  @Test
  void shouldPersistBookingInDatabase() {
    Flight flight = createTestFlight(1L, "AA123", "JFK", "LAX");
    when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
    when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenReturn(true);

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
        .contactPhone("+1234567890")
        .currency("USD")
        .build();

    BookingResponse response = bookingService.createBooking(request, "user-123");

    assertThat(response).isNotNull();
    assertThat(response.getBookingReference()).isNotNull();
    assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");

    Booking savedBooking = bookingRepository.findByBookingReference(response.getBookingReference())
        .orElse(null);
    assertThat(savedBooking).isNotNull();
    assertThat(savedBooking.getUserId()).isEqualTo("user-123");
    assertThat(savedBooking.getContactEmail()).isEqualTo("john@example.com");
  }

  @Test
  void shouldCreateBookingWithMultiplePassengers() {
    Flight flight = createTestFlight(1L, "AA123", "JFK", "LAX");
    when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
    when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenReturn(true);

    CreateBookingRequest request = CreateBookingRequest.builder()
        .flights(List.of(
            CreateBookingRequest.FlightSelection.builder()
                .flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build()
        ))
        .passengers(List.of(
            CreateBookingRequest.PassengerDetails.builder()
                .firstName("John").lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).build(),
            CreateBookingRequest.PassengerDetails.builder()
                .firstName("Jane").lastName("Doe")
                .dateOfBirth(LocalDate.of(1992, 5, 15)).build()
        ))
        .contactEmail("family@example.com")
        .currency("USD")
        .build();

    BookingResponse response = bookingService.createBooking(request, "user-123");

    assertThat(response).isNotNull();
    assertThat(response.getBookingReference()).isNotNull();

    Booking savedBooking = bookingRepository.findByBookingReference(response.getBookingReference())
        .orElse(null);
    assertThat(savedBooking).isNotNull();
    assertThat(savedBooking.getPassengers()).hasSize(2);
    assertThat(savedBooking.getBookingFlights()).hasSize(1);
  }

  @Test
  void shouldCreateBookingWithAdditionalServices() {
    Flight flight = createTestFlight(1L, "AA123", "JFK", "LAX");
    when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
    when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenReturn(true);

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
        .additionalServices(List.of(
            CreateBookingRequest.AdditionalServiceRequest.builder()
                .serviceType("EXTRA_BAGGAGE").description("Extra bag").build()
        ))
        .contactEmail("john@example.com")
        .currency("USD")
        .build();

    BookingResponse response = bookingService.createBooking(request, "user-123");

    assertThat(response).isNotNull();
    assertThat(response.getBookingReference()).isNotNull();
    assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(280));
  }

  private Flight createTestFlight(Long id, String flightNumber, String origin, String destination) {
    Flight flight = new Flight();
    flight.setId(id);
    flight.setFlightNumber(flightNumber);
    flight.setStatus(Flight.FlightStatus.SCHEDULED);
    flight.setEconomyAvailable(50);
    flight.setPremiumEconomyAvailable(10);
    flight.setBusinessAvailable(5);
    flight.setFirstClassAvailable(2);
    flight.setAvailableSeats(67);
    flight.setBasePriceEconomy(BigDecimal.valueOf(200));
    flight.setBasePricePremiumEconomy(BigDecimal.valueOf(350));
    flight.setBasePriceBusiness(BigDecimal.valueOf(600));
    flight.setBasePriceFirstClass(BigDecimal.valueOf(1000));

    com.example.bookingservice.entity.Airline airline = new com.example.bookingservice.entity.Airline();
    airline.setName("Test Airlines");
    flight.setAirline(airline);

    com.example.bookingservice.entity.Airport orig = new com.example.bookingservice.entity.Airport();
    orig.setIataCode(origin);
    flight.setOriginAirport(orig);

    com.example.bookingservice.entity.Airport dest = new com.example.bookingservice.entity.Airport();
    dest.setIataCode(destination);
    flight.setDestinationAirport(dest);

    flight.setDepartureDate(LocalDate.now().plusDays(7));
    flight.setDepartureTime(LocalTime.of(10, 0));
    flight.setArrivalDate(LocalDate.now().plusDays(7));
    flight.setArrivalTime(LocalTime.of(13, 0));

    return flight;
  }
}