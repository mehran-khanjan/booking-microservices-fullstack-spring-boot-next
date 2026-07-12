package com.example.flightservice.service;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import com.example.commonlib.responseenvelope.response.PageResponse;
import com.example.flightservice.constant.ErrorCodes;
import com.example.flightservice.dto.controller.flightsearch.FlightResponseDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto;
import com.example.flightservice.entity.Airport;
import com.example.flightservice.entity.Flight;
import com.example.flightservice.exception.BusinessException;
import com.example.flightservice.mapper.FlightMapper;
import com.example.flightservice.repository.AirportRepository;
import com.example.flightservice.repository.FlightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that performs the actual flight search for a single leg (one‑way).
 *
 * <p>This service handles the core search logic, including:
 *
 * <ul>
 *   <li>Airport validation
 *   <li>Building JPA {@link Specification} filters based on search criteria
 *   <li>Executing paginated queries against the database
 *   <li>Caching search results in Redis (using a JSON serialization)
 *   <li>Mapping {@link Flight} entities to {@link FlightResponseDto}
 * </ul>
 *
 * <p>All methods are {@code @Transactional(readOnly = true)} to optimise database access and keep
 * the persistence context open for lazy loading.
 *
 * @see FlightSearchService
 * @see FlightSpecification
 * @see FlightMapper
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightSearchLegService {

  private final FlightRepository flightRepository;
  private final AirportRepository airportRepository;
  private final FlightMapper flightMapper;
  private final StringRedisTemplate redisTemplate; // Using StringRedisTemplate
  private final ObjectMapper objectMapper; // For JSON serialization

  private static final String SEARCH_CACHE_PREFIX = "flight:search:";
  private static final long CACHE_TTL_MINUTES = 60;

  private static final String CACHE_VERSION = "v1"; // bump if response schema changes

  /**
   * Searches a single leg (outbound or return) with caching and a read‑only transaction.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Validates origin and destination airports (throws {@link BusinessException} if not found)
   *   <li>Builds a request object for cache key generation
   *   <li>Attempts to retrieve a cached result from Redis (JSON)
   *   <li>If cached, returns it immediately (with {@code fromCache=true})
   *   <li>If not cached, builds a {@link Specification} and {@link Sort} from the request
   *   <li>Executes the paginated query
   *   <li>Maps results to DTOs (lazy loading works inside the transaction)
   *   <li>Populates metadata and pagination info
   *   <li>Caches the response as JSON for future requests
   * </ol>
   *
   * @param originCode IATA code of the departure airport
   * @param destinationCode IATA code of the arrival airport
   * @param departureDate the date of travel
   * @param originalRequest the full search request (used for passenger count, cabin class, etc.)
   * @param page page number (zero‑based)
   * @param size page size
   * @param sortBy sorting criterion (e.g., "PRICE_ASC")
   * @return a fully populated {@link FlightSearchResponseDto} for this leg
   * @throws BusinessException if an airport is not found or search parameters are invalid
   */
  @Transactional(readOnly = true)
  public FlightSearchResponseDto searchLeg(
      String originCode,
      String destinationCode,
      LocalDate departureDate,
      FlightSearchRequestDto originalRequest,
      int page,
      int size,
      String sortBy) {

    log.debug(
        "searchLeg: origin={}, dest={}, date={}, page={}, size={}",
        originCode,
        destinationCode,
        departureDate,
        page,
        size);

    // 1. Validate airports
    Airport origin =
        airportRepository
            .findByIataCodeIgnoreCase(originCode)
            .orElseThrow(
                () -> {
                  log.warn("Airport not found: origin={}", originCode);
                  return new BusinessException(
                      "Invalid origin: " + originCode, ErrorCodes.INVALID_SEARCH_PARAMS);
                });
    Airport destination =
        airportRepository
            .findByIataCodeIgnoreCase(destinationCode)
            .orElseThrow(
                () -> {
                  log.warn("Airport not found: destination={}", destinationCode);
                  return new BusinessException(
                      "Invalid destination: " + destinationCode, ErrorCodes.INVALID_SEARCH_PARAMS);
                });

    // 2. Build a request copy for cache key and filters
    FlightSearchRequestDto legRequest =
        FlightSearchRequestDto.builder()
            .origin(originCode)
            .destination(destinationCode)
            .departureDate(departureDate)
            .passengers(originalRequest.getPassengers())
            .cabinClass(originalRequest.getCabinClass())
            .minPrice(originalRequest.getMinPrice())
            .maxPrice(originalRequest.getMaxPrice())
            .airline(originalRequest.getAirline())
            .maxStops(originalRequest.getMaxStops())
            .departureTimeRange(originalRequest.getDepartureTimeRange())
            .arrivalTimeRange(originalRequest.getArrivalTimeRange())
            .aircraftType(originalRequest.getAircraftType())
            .sortBy(sortBy)
            .page(page)
            .size(size)
            .build();

    // 3. Build cache key
    String cacheKey = buildCacheKey(legRequest);
    log.debug("Cache key: {}", cacheKey);

    // 4. Try to get cached result
    FlightSearchResponseDto cached = getCachedSearch(cacheKey);
    if (cached != null) {
      log.info("Cache HIT for key: {}", cacheKey);
      cached.getMetadata().setFromCache(true);
      return cached;
    }
    log.debug("Cache MISS for key: {}", cacheKey);

    // 5. Build specification and sorting
    Specification<Flight> spec =
        buildSpecification(legRequest, origin.getId(), destination.getId());
    Sort sort = buildSort(legRequest.getSortBy());
    Pageable pageable = PageRequest.of(legRequest.getPage(), legRequest.getSize(), sort);

    // 6. Execute database query (inside transaction)
    log.debug("Executing database query...");
    Page<Flight> flightPage = flightRepository.findAll(spec, pageable);

    // 7. Map entities to responses – lazy loading is possible here because
    //    the transaction is still open.
    List<FlightResponseDto> flightResponses =
        flightPage.getContent().stream().map(flightMapper::toResponse).collect(Collectors.toList());

    // 8. Build metadata
    FlightSearchResponseDto.SearchMetadata meta =
        FlightSearchResponseDto.SearchMetadata.builder()
            .origin(originCode)
            .destination(destinationCode)
            .departureDate(departureDate.toString())
            .passengers(legRequest.getPassengers())
            .cabinClass(legRequest.getCabinClass())
            .totalResults(flightPage.getTotalElements())
            .searchId(UUID.randomUUID().toString())
            .fromCache(false)
            .build();

    // 9. Build the final response
    FlightSearchResponseDto response =
        FlightSearchResponseDto.builder()
            .flights(flightResponses)
            .metadata(meta)
            .pageInfo(
                PageResponse.PageInfo.builder()
                    .page(flightPage.getNumber())
                    .size(flightPage.getSize())
                    .totalElements(flightPage.getTotalElements())
                    .totalPages(flightPage.getTotalPages())
                    .first(flightPage.isFirst())
                    .last(flightPage.isLast())
                    .build())
            .build();

    // 10. Cache the result as JSON
    cacheSearchResult(cacheKey, response);

    log.debug(
        "searchLeg completed for {} → {}, found {} flights",
        originCode,
        destinationCode,
        flightResponses.size());
    return response;
  }

  // ------------------- CACHE HELPERS (using StringRedisTemplate) -------------------

  /**
   * Stores a search response in Redis as a JSON string.
   *
   * <p>The TTL is fixed (default 60 minutes). Any serialisation exception is logged but does not
   * break the request – the caller will simply have a cache miss next time.
   *
   * @param cacheKey the Redis key
   * @param response the response DTO to cache
   */
  private void cacheSearchResult(String cacheKey, FlightSearchResponseDto response) {
    try {
      String json = objectMapper.writeValueAsString(response);
      redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
      log.debug("Cached search result (JSON) with key: {}", cacheKey);
    } catch (Exception e) {
      log.error("Failed to cache search result for key {}: {}", cacheKey, e.getMessage(), e);
    }
  }

  /**
   * Retrieves a cached search response from Redis.
   *
   * <p>If the key does not exist or deserialisation fails, {@code null} is returned.
   *
   * @param cacheKey the Redis key
   * @return the cached {@link FlightSearchResponseDto}, or {@code null} if not found or invalid
   */
  private FlightSearchResponseDto getCachedSearch(String cacheKey) {
    try {
      String json = redisTemplate.opsForValue().get(cacheKey);
      if (json != null) {
        log.debug("Cache entry found for key: {}", cacheKey);
        return objectMapper.readValue(json, FlightSearchResponseDto.class);
      }
    } catch (Exception e) {
      log.warn("Failed to retrieve cached search for key {}: {}", cacheKey, e.getMessage());
    }
    return null;
  }

  /**
   * Builds a deterministic cache key for a given search request.
   *
   * <p>The key is composed of:
   *
   * <ul>
   *   <li>A fixed prefix and version
   *   <li>The origin, destination, and departure date (for human readability)
   *   <li>A SHA‑256 hash of all request parameters (to ensure uniqueness)
   * </ul>
   *
   * <p>This approach balances debuggability (prefix shows route and date) with collision avoidance
   * (hash ensures all parameters are considered).
   *
   * @param request the leg search request
   * @return a Redis‑compatible cache key
   */
  private String buildCacheKey(FlightSearchRequestDto request) {
    String rawKey =
        String.join(
            "|",
            "origin=" + upperOrAny(request.getOrigin()),
            "dest=" + upperOrAny(request.getDestination()),
            "depDate=" + request.getDepartureDate(),
            "retDate=" + request.getReturnDate(),
            "pax=" + request.getPassengers(),
            "cabin=" + upperOrAny(request.getCabinClass()),
            "minPrice=" + request.getMinPrice(),
            "maxPrice=" + request.getMaxPrice(),
            "airline=" + upperOrAny(request.getAirline()),
            "maxStops=" + request.getMaxStops(),
            "depTimeRange=" + request.getDepartureTimeRange(),
            "arrTimeRange=" + request.getArrivalTimeRange(),
            "aircraft=" + upperOrAny(request.getAircraftType()),
            "sortBy=" + upperOrAny(request.getSortBy()),
            "page=" + request.getPage(),
            "size=" + request.getSize());

    // Keep the key length bounded and Redis-friendly, but retain a
    // human-readable prefix (route + date) for debugging/observability
    // in Redis CLI / monitoring tools.
    return SEARCH_CACHE_PREFIX
        + CACHE_VERSION
        + ":"
        + upperOrAny(request.getOrigin())
        + ":"
        + upperOrAny(request.getDestination())
        + ":"
        + request.getDepartureDate()
        + ":"
        + sha256Hex(rawKey);
  }

  /**
   * Returns the uppercase version of a string, or {@code "ANY"} if the input is {@code null}.
   *
   * @param value the string to normalise
   * @return uppercase value or "ANY"
   */
  private String upperOrAny(String value) {
    return value == null ? "ANY" : value.toUpperCase();
  }

  /**
   * Computes the SHA‑256 hash of a given string.
   *
   * <p>This method uses {@link MessageDigest} directly and is guaranteed to be available on every
   * Java platform.
   *
   * @param input the input string
   * @return the hex representation of the SHA‑256 hash
   * @throws IllegalStateException if SHA‑256 is not available (should never happen)
   */
  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM — this should never happen.
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  // ------------------- SPECIFICATION & SORT BUILDERS -------------------

  /**
   * Builds a JPA {@link Specification} from the search request.
   *
   * <p>All criteria are AND‑ed together. The base conditions are:
   *
   * <ul>
   *   <li>origin airport
   *   <li>destination airport
   *   <li>departure date
   *   <li>availability (SCHEDULED and availableSeats > 0)
   * </ul>
   *
   * Additional filters (price range, airline, aircraft type, cabin class) are added if present in
   * the request.
   *
   * @param request the leg search request
   * @param originId ID of the origin airport
   * @param destinationId ID of the destination airport
   * @return a {@link Specification} that can be passed to the repository
   */
  private Specification<Flight> buildSpecification(
      FlightSearchRequestDto request, Long originId, Long destinationId) {
    Specification<Flight> spec =
        Specification.where(FlightSpecification.hasOrigin(originId))
            .and(FlightSpecification.hasDestination(destinationId))
            .and(FlightSpecification.hasDepartureDate(request.getDepartureDate()))
            .and(FlightSpecification.isAvailable());

    if (request.getMinPrice() != null) {
      spec =
          spec.and(
              FlightSpecification.hasPriceGreaterThan(BigDecimal.valueOf(request.getMinPrice())));
    }
    if (request.getMaxPrice() != null) {
      spec =
          spec.and(FlightSpecification.hasPriceLessThan(BigDecimal.valueOf(request.getMaxPrice())));
    }
    if (request.getAirline() != null && !request.getAirline().isBlank()) {
      spec = spec.and(FlightSpecification.hasAirlineCode(request.getAirline()));
    }
    if (request.getAircraftType() != null && !request.getAircraftType().isBlank()) {
      spec = spec.and(FlightSpecification.hasAircraftType(request.getAircraftType()));
    }
    if (request.getCabinClass() != null) {
      spec =
          spec.and(
              FlightSpecification.hasCabinClassAvailable(
                  request.getCabinClass(), request.getPassengers()));
    }
    return spec;
  }

  /**
   * Translates a {@code sortBy} string into a Spring Data {@link Sort} object.
   *
   * <p>Supported values:
   *
   * <ul>
   *   <li>{@code PRICE_ASC} – ascending by economy price (default)
   *   <li>{@code PRICE_DESC} – descending by economy price
   *   <li>{@code DURATION} – ascending by duration in minutes
   *   <li>{@code DEPARTURE_TIME} – ascending by departure date and time
   *   <li>{@code ARRIVAL_TIME} – ascending by arrival date and time
   * </ul>
   *
   * Any other value falls back to {@code PRICE_ASC}.
   *
   * @param sortBy the sort criterion (case‑insensitive)
   * @return a {@link Sort} instance
   */
  private Sort buildSort(String sortBy) {
    String effectiveSort =
        (sortBy == null || sortBy.isBlank()) ? "PRICE_ASC" : sortBy.trim().toUpperCase();

    return switch (effectiveSort) {
      case "PRICE_ASC" -> Sort.by(Sort.Direction.ASC, "basePriceEconomy");
      case "PRICE_DESC" -> Sort.by(Sort.Direction.DESC, "basePriceEconomy");
      case "DURATION" -> Sort.by(Sort.Direction.ASC, "durationMinutes");
      case "DEPARTURE_TIME" -> Sort.by(Sort.Direction.ASC, "departureDate", "departureTime");
      case "ARRIVAL_TIME" -> Sort.by(Sort.Direction.ASC, "arrivalDate", "arrivalTime");
      default -> Sort.by(Sort.Direction.ASC, "basePriceEconomy");
    };
  }
}
