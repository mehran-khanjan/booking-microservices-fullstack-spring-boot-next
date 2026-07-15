package com.example.bookingservice.unit;

import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.entity.*;
import com.example.bookingservice.enums.CabinClass;
import com.example.bookingservice.mapper.BookingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMapperTest {

  private BookingMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new BookingMapper();
  }

  @Test
  void shouldMapBookingToResponse() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setUserId("user-123");
    booking.setStatus(Booking.BookingStatus.CONFIRMED);
    booking.setBookingType(Booking.BookingType.ROUND_TRIP);
    booking.setTotalAmount(BigDecimal.valueOf(500));
    booking.setCurrency("USD");
    booking.setContactEmail("john@example.com");
    booking.setContactPhone("+1234567890");
    booking.setHoldExpiresAt(LocalDateTime.of(2025, 1, 15, 10, 0));
    booking.setConfirmedAt(LocalDateTime.of(2025, 1, 15, 10, 5));
    booking.setPaymentTransactionId("txn-123");
    booking.setCreatedAt(LocalDateTime.of(2025, 1, 15, 9, 0));

    BookingFlight bf = new BookingFlight();
    bf.setFlightId(1L);
    bf.setFlightNumber("AA123");
    bf.setAirlineName("American Airlines");
    bf.setOriginAirportCode("JFK");
    bf.setDestinationAirportCode("LAX");
    bf.setDepartureDate(LocalDate.of(2025, 2, 1));
    bf.setDepartureTime(LocalTime.of(10, 0));
    bf.setArrivalDate(LocalDate.of(2025, 2, 1));
    bf.setArrivalTime(LocalTime.of(13, 0));
    bf.setCabinClass(CabinClass.ECONOMY);
    bf.setBasePrice(BigDecimal.valueOf(200));
    bf.setSegmentOrder(0);
    booking.setBookingFlights(List.of(bf));

    Passenger p = new Passenger();
    p.setId(1L);
    p.setTitle("Mr");
    p.setFirstName("John");
    p.setLastName("Doe");
    p.setDateOfBirth(LocalDate.of(1990, 1, 1));
    p.setNationality("US");
    p.setPassportNumber("P123456");
    p.setMealPreference("Vegetarian");
    booking.setPassengers(List.of(p));

    AdditionalService svc = new AdditionalService();
    svc.setServiceType(AdditionalService.ServiceType.EXTRA_BAGGAGE);
    svc.setDescription("Extra checked bag");
    svc.setPrice(BigDecimal.valueOf(50));
    booking.setAdditionalServices(List.of(svc));

    BookingResponse response = mapper.toResponse(booking);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getBookingReference()).isEqualTo("ABC123");
    assertThat(response.getStatus()).isEqualTo("CONFIRMED");
    assertThat(response.getBookingType()).isEqualTo("ROUND_TRIP");
    assertThat(response.getCurrency()).isEqualTo("USD");
    assertThat(response.getPaymentTransactionId()).isEqualTo("txn-123");

    assertThat(response.getFlights()).hasSize(1);
    assertThat(response.getFlights().get(0).getFlightNumber()).isEqualTo("AA123");
    assertThat(response.getFlights().get(0).getCabinClass()).isEqualTo("ECONOMY");

    assertThat(response.getPassengers()).hasSize(1);
    assertThat(response.getPassengers().get(0).getFirstName()).isEqualTo("John");
    assertThat(response.getPassengers().get(0).getMealPreference()).isEqualTo("Vegetarian");

    assertThat(response.getAdditionalServices()).hasSize(1);
    assertThat(response.getAdditionalServices().get(0).getServiceType()).isEqualTo("EXTRA_BAGGAGE");
  }

  @Test
  void shouldMapBookingWithNullFields() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setUserId("user-123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
    booking.setTotalAmount(BigDecimal.ZERO);
    booking.setContactEmail("test@example.com");
    booking.setBookingFlights(List.of());
    booking.setPassengers(List.of());
    booking.setAdditionalServices(List.of());

    BookingResponse response = mapper.toResponse(booking);

    assertThat(response).isNotNull();
    assertThat(response.getFlights()).isEmpty();
    assertThat(response.getPassengers()).isEmpty();
    assertThat(response.getAdditionalServices()).isEmpty();
    assertThat(response.getCancellationReason()).isNull();
    assertThat(response.getRefundAmount()).isNull();
  }

  @Test
  void shouldMapPassengerDetailsToEntity() {
    CreateBookingRequest.PassengerDetails details = CreateBookingRequest.PassengerDetails.builder()
        .title("Mr")
        .firstName("John")
        .lastName("Doe")
        .middleName("M")
        .dateOfBirth(LocalDate.of(1990, 1, 1))
        .gender("M")
        .nationality("US")
        .passportNumber("P123456")
        .passportExpiryDate(LocalDate.of(2030, 1, 1))
        .frequentFlyerNumber("FF123")
        .mealPreference("Vegetarian")
        .specialAssistance("Wheelchair")
        .build();

    Passenger passenger = mapper.toPassengerEntity(details);

    assertThat(passenger).isNotNull();
    assertThat(passenger.getTitle()).isEqualTo("Mr");
    assertThat(passenger.getFirstName()).isEqualTo("John");
    assertThat(passenger.getLastName()).isEqualTo("Doe");
    assertThat(passenger.getMiddleName()).isEqualTo("M");
    assertThat(passenger.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(passenger.getGender()).isEqualTo("M");
    assertThat(passenger.getNationality()).isEqualTo("US");
    assertThat(passenger.getPassportNumber()).isEqualTo("P123456");
    assertThat(passenger.getPassportExpiryDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    assertThat(passenger.getFrequentFlyerNumber()).isEqualTo("FF123");
    assertThat(passenger.getMealPreference()).isEqualTo("Vegetarian");
    assertThat(passenger.getSpecialAssistance()).isEqualTo("Wheelchair");
  }
}