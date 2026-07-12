package com.example.flightservice.repository;

import com.example.flightservice.entity.Airport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Airport} entities.
 *
 * <p>Provides methods to find airports by IATA code (case‑sensitive or insensitive), by city, and a
 * flexible search across multiple fields (city, name, IATA code).
 *
 * @see Airport
 */
@Repository
public interface AirportRepository extends JpaRepository<Airport, Long> {

  /**
   * Finds an airport by its exact IATA code.
   *
   * @param iataCode the 3‑letter IATA code
   * @return an {@link Optional} containing the airport if found
   */
  Optional<Airport> findByIataCode(String iataCode);

  /**
   * Finds an airport by IATA code, ignoring case.
   *
   * @param iataCode the IATA code (case‑insensitive)
   * @return an {@link Optional} containing the airport if found
   */
  Optional<Airport> findByIataCodeIgnoreCase(String iataCode);

  /**
   * Finds all airports in a given city (exact match, case‑sensitive).
   *
   * @param city the city name
   * @return a list of airports in that city
   */
  List<Airport> findByCity(String city);

  /**
   * Finds all airports in a given city, ignoring case.
   *
   * @param city the city name
   * @return a list of airports in that city
   */
  List<Airport> findByCityIgnoreCase(String city);

  /**
   * Searches airports by a text fragment, matching against city, name, or IATA code.
   *
   * <p>For IATA codes, the match must be exact (case‑insensitive); for other fields, the search is
   * case‑insensitive and partial.
   *
   * @param search the search string
   * @return a list of airports that match the criteria
   */
  @Query(
      "SELECT a FROM Airport a WHERE LOWER(a.city) LIKE LOWER(CONCAT('%', :search, '%')) "
          + "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) "
          + "OR LOWER(a.iataCode) = LOWER(:search)")
  List<Airport> searchAirports(@Param("search") String search);

  /**
   * Checks whether an airport with the given IATA code exists.
   *
   * @param iataCode the IATA code
   * @return {@code true} if an airport with that code exists, {@code false} otherwise
   */
  boolean existsByIataCode(String iataCode);
}
