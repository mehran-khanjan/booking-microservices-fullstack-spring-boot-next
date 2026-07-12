package com.example.flightservice.repository;

import com.example.flightservice.entity.Flight;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Flight} entities.
 *
 * <p>Provides complex flight search queries, seat reservation/release operations, and locking
 * support for concurrency control. All queries fetch required associations (airline, airports,
 * aircraft type) to avoid N+1 issues.
 *
 * @see Flight
 */
@Repository
public interface FlightRepository
    extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

  /**
   * Finds available (SCHEDULED with seats) flights for a given route and date. All associations are
   * fetched to avoid lazy loading in the service layer.
   *
   * @param originId ID of the origin airport
   * @param destinationId ID of the destination airport
   * @param departureDate the departure date
   * @return a list of matching flights with available seats
   */
  @Query(
      "SELECT f FROM Flight f "
          + "JOIN FETCH f.airline "
          + "JOIN FETCH f.originAirport "
          + "JOIN FETCH f.destinationAirport "
          + "JOIN FETCH f.aircraftType "
          + "WHERE f.originAirport.id = :originId "
          + "AND f.destinationAirport.id = :destinationId "
          + "AND f.departureDate = :departureDate "
          + "AND f.status = 'SCHEDULED' "
          + "AND f.availableSeats > 0")
  List<Flight> findAvailableFlights(
      @Param("originId") Long originId,
      @Param("destinationId") Long destinationId,
      @Param("departureDate") LocalDate departureDate);

  /**
   * Finds available flights within a date range (e.g., for flexible date searches). Results are
   * ordered by departure date and time.
   *
   * @param originId ID of the origin airport
   * @param destinationId ID of the destination airport
   * @param startDate inclusive start date
   * @param endDate inclusive end date
   * @return a list of flights in chronological order
   */
  @Query(
      "SELECT f FROM Flight f "
          + "JOIN FETCH f.airline "
          + "JOIN FETCH f.originAirport "
          + "JOIN FETCH f.destinationAirport "
          + "JOIN FETCH f.aircraftType "
          + "WHERE f.originAirport.id = :originId "
          + "AND f.destinationAirport.id = :destinationId "
          + "AND f.departureDate BETWEEN :startDate AND :endDate "
          + "AND f.status = 'SCHEDULED' "
          + "AND f.availableSeats > 0 "
          + "ORDER BY f.departureDate, f.departureTime")
  List<Flight> findFlightsInDateRange(
      @Param("originId") Long originId,
      @Param("destinationId") Long destinationId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Retrieves a flight by ID with a pessimistic write lock.
   *
   * <p>Use this method when you need to block other transactions from modifying the flight until
   * the lock is released (e.g., during seat reservation).
   *
   * @param id the flight ID
   * @return an {@link Optional} containing the flight if found
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT f FROM Flight f WHERE f.id = :id")
  Optional<Flight> findByIdWithPessimisticLock(@Param("id") Long id);

  /**
   * Retrieves a flight by ID with optimistic locking.
   *
   * <p>The {@code @Version} field of the entity is used to detect concurrent modifications; a
   * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} will be thrown if the
   * entity has been updated since it was loaded.
   *
   * @param id the flight ID
   * @return an {@link Optional} containing the flight if found
   */
  @Lock(LockModeType.OPTIMISTIC)
  @Query("SELECT f FROM Flight f WHERE f.id = :id")
  Optional<Flight> findByIdWithOptimisticLock(@Param("id") Long id);

  /**
   * Finds a flight by ID with all its associations (airline, airports, aircraft) eagerly fetched.
   *
   * @param id the flight ID
   * @return an {@link Optional} containing the fully loaded flight
   */
  @Query(
      "SELECT f FROM Flight f "
          + "JOIN FETCH f.airline "
          + "JOIN FETCH f.originAirport "
          + "JOIN FETCH f.destinationAirport "
          + "JOIN FETCH f.aircraftType "
          + "WHERE f.id = :id")
  Optional<Flight> findByIdWithDetails(@Param("id") Long id);

  /**
   * Finds a flight by its flight number and departure date.
   *
   * @param flightNumber the airline‑specific flight number
   * @param departureDate the departure date
   * @return an {@link Optional} containing the matching flight
   */
  @Query(
      "SELECT f FROM Flight f "
          + "WHERE f.flightNumber = :flightNumber "
          + "AND f.departureDate = :departureDate")
  Optional<Flight> findByFlightNumberAndDate(
      @Param("flightNumber") String flightNumber, @Param("departureDate") LocalDate departureDate);

  /**
   * Atomically reserves a specified number of seats on a flight.
   *
   * <p>The update is only performed if the total available seats are sufficient. The method returns
   * the number of rows affected (0 or 1), allowing the caller to detect whether the reservation
   * succeeded.
   *
   * @param flightId flight ID
   * @param seats total number of seats to reserve (sum of all cabins)
   * @param economySeats economy seats to reserve
   * @param premiumSeats premium economy seats to reserve
   * @param businessSeats business seats to reserve
   * @param firstSeats first class seats to reserve
   * @return number of rows updated (0 if insufficient seats)
   */
  @Modifying
  @Query(
      "UPDATE Flight f SET f.availableSeats = f.availableSeats - :seats, "
          + "f.economyAvailable = f.economyAvailable - :economySeats, "
          + "f.premiumEconomyAvailable = f.premiumEconomyAvailable - :premiumSeats, "
          + "f.businessAvailable = f.businessAvailable - :businessSeats, "
          + "f.firstClassAvailable = f.firstClassAvailable - :firstSeats "
          + "WHERE f.id = :flightId "
          + "AND f.availableSeats >= :seats")
  int reserveSeats(
      @Param("flightId") Long flightId,
      @Param("seats") Integer seats,
      @Param("economySeats") Integer economySeats,
      @Param("premiumSeats") Integer premiumSeats,
      @Param("businessSeats") Integer businessSeats,
      @Param("firstSeats") Integer firstSeats);

  /**
   * Atomically releases previously reserved seats (e.g., on booking expiry or cancellation).
   *
   * @param flightId flight ID
   * @param seats total number of seats to release
   * @param economySeats economy seats to release
   * @param premiumSeats premium economy seats to release
   * @param businessSeats business seats to release
   * @param firstSeats first class seats to release
   * @return number of rows updated (should be 1 if flight exists)
   */
  @Modifying
  @Query(
      "UPDATE Flight f SET f.availableSeats = f.availableSeats + :seats, "
          + "f.economyAvailable = f.economyAvailable + :economySeats, "
          + "f.premiumEconomyAvailable = f.premiumEconomyAvailable + :premiumSeats, "
          + "f.businessAvailable = f.businessAvailable + :businessSeats, "
          + "f.firstClassAvailable = f.firstClassAvailable + :firstSeats "
          + "WHERE f.id = :flightId")
  int releaseSeats(
      @Param("flightId") Long flightId,
      @Param("seats") Integer seats,
      @Param("economySeats") Integer economySeats,
      @Param("premiumSeats") Integer premiumSeats,
      @Param("businessSeats") Integer businessSeats,
      @Param("firstSeats") Integer firstSeats);

  /**
   * Finds all flights with departure date between the given dates.
   *
   * @param start the inclusive start date
   * @param end the inclusive end date
   * @return a list of flights in that range
   */
  List<Flight> findByDepartureDateBetween(LocalDate start, LocalDate end);

  /**
   * Finds flights for a specific airline with pagination.
   *
   * @param airlineId the airline ID
   * @param pageable pagination information
   * @return a page of flights
   */
  Page<Flight> findByAirlineId(Long airlineId, Pageable pageable);
}
