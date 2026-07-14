package com.example.bookingservice.repository;

import com.example.bookingservice.entity.Passenger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for Passenger entities. */
@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {

  List<Passenger> findByBookingId(Long bookingId);
}
