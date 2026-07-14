package com.example.bookingservice.servcie;

import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.BookingFlight;
import com.example.bookingservice.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task that releases seats for expired booking holds.
 *
 * <p>BUG FIX (#5): the 15-minute booking hold never actually released seats back to inventory.
 * {@code BookingRepository#findExpiredBookings} existed but nothing called it, and the only place
 * expiry was even checked was lazily, when a user attempted to pay after the hold had already
 * lapsed - and even then it only flipped the booking to {@code EXPIRED} without releasing the seats
 * it was holding in flight-service.
 *
 * <p>This scheduled sweep periodically finds {@code PENDING_PAYMENT} bookings whose hold has
 * expired, releases every seat they're holding via {@code FlightServiceClient}, and only then marks
 * the booking {@code EXPIRED}. If releasing seats fails for a given booking, that booking is
 * deliberately left in {@code PENDING_PAYMENT} so the next sweep retries it, rather than marking it
 * expired while seats remain stuck as reserved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingHoldExpiryScheduler {

  private final BookingRepository bookingRepository;
  private final FlightServiceClient flightServiceClient;

  /**
   * Runs periodically to release expired holds. The interval is controlled by {@code
   * booking.hold-expiry.sweep-interval-ms} (default 60s).
   */
  @Scheduled(fixedDelayString = "${booking.hold-expiry.sweep-interval-ms:60000}")
  @Transactional
  public void releaseExpiredHolds() {
    List<Booking> expiredBookings = bookingRepository.findExpiredBookings(LocalDateTime.now());

    if (expiredBookings.isEmpty()) {
      return;
    }

    log.info("Found {} expired booking hold(s) to release", expiredBookings.size());

    for (Booking booking : expiredBookings) {
      try {
        releaseAllSeatsFor(booking);
        booking.setStatus(Booking.BookingStatus.EXPIRED);
        bookingRepository.save(booking);
        log.info("Released expired hold and expired booking {}", booking.getBookingReference());
      } catch (Exception e) {
        log.error(
            "Failed to release expired hold for booking {}, will retry next sweep",
            booking.getBookingReference(),
            e);
      }
    }
  }

  /**
   * Releases all seats held by a booking across all its flight segments.
   *
   * @param booking the booking whose seats to release
   * @throws IllegalStateException if flight-service reports failure for any segment
   */
  private void releaseAllSeatsFor(Booking booking) {
    int passengerCount = booking.getPassengers().size();

    for (BookingFlight bookingFlight : booking.getBookingFlights()) {
      Map<String, Integer> cabinSeats =
          Map.of(bookingFlight.getCabinClass().name(), passengerCount);

      boolean released =
          flightServiceClient.releaseSeats(
              bookingFlight.getFlightId(),
              booking.getBookingReference(),
              passengerCount,
              cabinSeats);

      if (!released) {
        throw new IllegalStateException(
            "flight-service reported release failure for flight "
                + bookingFlight.getFlightId()
                + " on booking "
                + booking.getBookingReference());
      }
    }
  }
}
