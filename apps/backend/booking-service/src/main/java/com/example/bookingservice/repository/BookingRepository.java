package com.example.bookingservice.repository;

import com.example.bookingservice.entity.Booking;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Booking entities with custom query methods. */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

  Optional<Booking> findByBookingReference(String bookingReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Booking b WHERE b.bookingReference = :reference")
  Optional<Booking> findByBookingReferenceWithLock(@Param("reference") String reference);

  @Query(
      "SELECT b FROM Booking b "
          + "LEFT JOIN FETCH b.bookingFlights bf "
          + "LEFT JOIN FETCH b.passengers "
          + "LEFT JOIN FETCH b.additionalServices "
          + "WHERE b.bookingReference = :reference")
  Optional<Booking> findByBookingReferenceWithDetails(@Param("reference") String reference);

  List<Booking> findByUserId(String userId);

  Page<Booking> findByUserId(String userId, Pageable pageable);

  @Query("SELECT b FROM Booking b WHERE b.contactEmail = :email")
  List<Booking> findByContactEmail(@Param("email") String email);

  @Query(
      "SELECT b FROM Booking b "
          + "WHERE b.bookingReference = :reference "
          + "AND (b.contactEmail = :email OR b.userId = :userId)")
  Optional<Booking> findByReferenceAndEmailOrUserId(
      @Param("reference") String reference,
      @Param("email") String email,
      @Param("userId") String userId);

  @Query(
      "SELECT b FROM Booking b "
          + "WHERE b.status = 'PENDING_PAYMENT' "
          + "AND b.holdExpiresAt < :now")
  List<Booking> findExpiredBookings(@Param("now") LocalDateTime now);

  List<Booking> findByStatus(Booking.BookingStatus status);

  @Query("SELECT COUNT(b) FROM Booking b WHERE b.userId = :userId AND b.status = 'CONFIRMED'")
  long countConfirmedBookingsByUser(@Param("userId") String userId);
}
