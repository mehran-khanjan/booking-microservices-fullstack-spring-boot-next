package com.example.bookingservice.repository;

import com.example.bookingservice.entity.BookingFlight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for BookingFlight entities. */
@Repository
public interface BookingFlightRepository extends JpaRepository<BookingFlight, Long> {

  List<BookingFlight> findByBookingIdOrderBySegmentOrder(Long bookingId);
}
