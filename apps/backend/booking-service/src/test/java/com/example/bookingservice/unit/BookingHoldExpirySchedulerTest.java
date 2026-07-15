package com.example.bookingservice.unit;

import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.BookingFlight;
import com.example.bookingservice.entity.Passenger;
import com.example.bookingservice.enums.CabinClass;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.BookingHoldExpiryScheduler;
import com.example.bookingservice.servcie.FlightServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingHoldExpirySchedulerTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private FlightServiceClient flightServiceClient;

  private BookingHoldExpiryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new BookingHoldExpiryScheduler(bookingRepository, flightServiceClient);
  }

  @Test
  void shouldReleaseExpiredHolds() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);

    BookingFlight bf = new BookingFlight();
    bf.setFlightId(100L);
    bf.setCabinClass(CabinClass.ECONOMY);
    booking.setBookingFlights(List.of(bf));

    BookingFlight bf2 = new BookingFlight();
    bf2.setFlightId(200L);
    bf2.setCabinClass(CabinClass.BUSINESS);
    booking.setBookingFlights(List.of(bf, bf2));

    Passenger passenger = new Passenger();
    passenger.setBooking(booking);
    booking.setPassengers(List.of(passenger));

    when(bookingRepository.findExpiredBookings(any(LocalDateTime.class)))
        .thenReturn(List.of(booking));
    when(flightServiceClient.releaseSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenReturn(true);

    scheduler.releaseExpiredHolds();

    verify(flightServiceClient).releaseSeats(eq(100L), eq("ABC123"), eq(1), anyMap());
    verify(flightServiceClient).releaseSeats(eq(200L), eq("ABC123"), eq(1), anyMap());
    verify(bookingRepository).save(booking);
    verify(bookingRepository).save(argThat(b -> b.getStatus() == Booking.BookingStatus.EXPIRED));
  }

  @Test
  void shouldDoNothingWhenNoExpiredBookings() {
    when(bookingRepository.findExpiredBookings(any(LocalDateTime.class)))
        .thenReturn(List.of());

    scheduler.releaseExpiredHolds();

    verify(flightServiceClient, never()).releaseSeats(anyLong(), anyString(), anyInt(), anyMap());
    verify(bookingRepository, never()).save(any());
  }

  @Test
  void shouldHandleReleaseFailureGracefully() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);

    BookingFlight bf = new BookingFlight();
    bf.setFlightId(100L);
    bf.setCabinClass(CabinClass.ECONOMY);
    booking.setBookingFlights(List.of(bf));

    Passenger passenger = new Passenger();
    passenger.setBooking(booking);
    booking.setPassengers(List.of(passenger));

    when(bookingRepository.findExpiredBookings(any(LocalDateTime.class)))
        .thenReturn(List.of(booking));
    when(flightServiceClient.releaseSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenReturn(false);

    scheduler.releaseExpiredHolds();

    // Should NOT mark as expired since release failed
    verify(bookingRepository, never()).save(any());
  }

  @Test
  void shouldHandleReleaseExceptionGracefully() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);

    BookingFlight bf = new BookingFlight();
    bf.setFlightId(100L);
    bf.setCabinClass(CabinClass.ECONOMY);
    booking.setBookingFlights(List.of(bf));

    Passenger passenger = new Passenger();
    passenger.setBooking(booking);
    booking.setPassengers(List.of(passenger));

    when(bookingRepository.findExpiredBookings(any(LocalDateTime.class)))
        .thenReturn(List.of(booking));
    when(flightServiceClient.releaseSeats(anyLong(), anyString(), anyInt(), anyMap()))
        .thenThrow(new RuntimeException("Flight service unavailable"));

    scheduler.releaseExpiredHolds();

    // Should NOT mark as expired since release had an exception
    verify(bookingRepository, never()).save(any());
  }
}