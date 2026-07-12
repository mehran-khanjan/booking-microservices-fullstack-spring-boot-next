package com.example.flightservice.service;

import com.example.flightservice.entity.Flight;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Utility class that provides JPA {@link Specification} methods for filtering {@link Flight}
 * entities.
 *
 * <p>Each method returns a {@link Specification} that can be combined using {@link
 * Specification#and(Specification)} and {@link Specification#or(Specification)}. This allows
 * building dynamic, type‑safe queries without writing JPQL for every filter combination.
 *
 * <p>All methods are static and designed to be used together in the {@link FlightSearchLegService}.
 */
public class FlightSpecification {

  /**
   * Filters flights by origin airport ID.
   *
   * @param originId the ID of the origin airport
   * @return a specification that matches flights with that origin
   */
  public static Specification<Flight> hasOrigin(Long originId) {
    return (root, query, cb) -> cb.equal(root.get("originAirport").get("id"), originId);
  }

  /**
   * Filters flights by destination airport ID.
   *
   * @param destinationId the ID of the destination airport
   * @return a specification that matches flights with that destination
   */
  public static Specification<Flight> hasDestination(Long destinationId) {
    return (root, query, cb) -> cb.equal(root.get("destinationAirport").get("id"), destinationId);
  }

  /**
   * Filters flights by exact departure date.
   *
   * @param date the departure date
   * @return a specification that matches flights departing on that date
   */
  public static Specification<Flight> hasDepartureDate(LocalDate date) {
    return (root, query, cb) -> cb.equal(root.get("departureDate"), date);
  }

  /**
   * Filters flights that are currently available for booking.
   *
   * <p>A flight is available if:
   *
   * <ul>
   *   <li>Its status is {@link Flight.FlightStatus#SCHEDULED}
   *   <li>It has at least one available seat ({@code availableSeats > 0})
   * </ul>
   *
   * @return a specification that checks both conditions
   */
  public static Specification<Flight> isAvailable() {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("status"), Flight.FlightStatus.SCHEDULED),
            cb.greaterThan(root.get("availableSeats"), 0));
  }

  /**
   * Filters flights with an economy base price greater than or equal to the given minimum.
   *
   * <p>Note: This uses the economy price as the basis for all price comparisons.
   *
   * @param minPrice the minimum price (inclusive)
   * @return a specification for price ≥ minPrice
   */
  public static Specification<Flight> hasPriceGreaterThan(BigDecimal minPrice) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("basePriceEconomy"), minPrice);
  }

  /**
   * Filters flights with an economy base price less than or equal to the given maximum.
   *
   * @param maxPrice the maximum price (inclusive)
   * @return a specification for price ≤ maxPrice
   */
  public static Specification<Flight> hasPriceLessThan(BigDecimal maxPrice) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("basePriceEconomy"), maxPrice);
  }

  /**
   * Filters flights by airline code (case‑insensitive).
   *
   * <p>The code is compared with {@link String#toUpperCase()} on both sides.
   *
   * @param airlineCode the IATA airline code (e.g., "AA")
   * @return a specification that matches the airline code
   */
  public static Specification<Flight> hasAirlineCode(String airlineCode) {
    return (root, query, cb) ->
        cb.equal(cb.upper(root.get("airline").get("code")), airlineCode.toUpperCase());
  }

  /**
   * Filters flights by aircraft model (partial, case‑insensitive match).
   *
   * <p>The search is performed using a {@code LIKE} query on the aircraft type's model name, with
   * the provided string wrapped in {@code '%'}.
   *
   * @param aircraftType the aircraft model or partial name (e.g., "Boeing")
   * @return a specification that matches the model containing the given text
   */
  public static Specification<Flight> hasAircraftType(String aircraftType) {
    return (root, query, cb) ->
        cb.like(
            cb.upper(root.get("aircraftType").get("model")),
            "%" + aircraftType.toUpperCase() + "%");
  }

  /**
   * Filters flights that have at least the requested number of seats available in the specified
   * cabin class.
   *
   * <p>Supported cabin classes (case‑insensitive):
   *
   * <ul>
   *   <li>{@code ECONOMY} – checks {@code economyAvailable}
   *   <li>{@code PREMIUM_ECONOMY} – checks {@code premiumEconomyAvailable}
   *   <li>{@code BUSINESS} – checks {@code businessAvailable}
   *   <li>{@code FIRST_CLASS} – checks {@code firstClassAvailable}
   *   <li>Any other value – falls back to total {@code availableSeats} (all cabins)
   * </ul>
   *
   * @param cabinClass the requested cabin (e.g., "BUSINESS")
   * @param passengers the number of passengers
   * @return a specification that ensures enough seats in that cabin
   */
  public static Specification<Flight> hasCabinClassAvailable(
      String cabinClass, Integer passengers) {
    return (root, query, cb) -> {
      return switch (cabinClass.toUpperCase()) {
        case "ECONOMY" -> cb.greaterThanOrEqualTo(root.get("economyAvailable"), passengers);
        case "PREMIUM_ECONOMY" ->
            cb.greaterThanOrEqualTo(root.get("premiumEconomyAvailable"), passengers);
        case "BUSINESS" -> cb.greaterThanOrEqualTo(root.get("businessAvailable"), passengers);
        case "FIRST_CLASS" -> cb.greaterThanOrEqualTo(root.get("firstClassAvailable"), passengers);
        default -> cb.greaterThanOrEqualTo(root.get("availableSeats"), passengers);
      };
    };
  }
}
