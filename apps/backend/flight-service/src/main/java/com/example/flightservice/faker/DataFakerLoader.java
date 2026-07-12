package com.example.flightservice.faker;

import com.example.flightservice.entity.*;
import com.example.flightservice.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database initialisation component that populates the Flight Service with realistic test data when
 * running under the {@code dev} profile.
 *
 * <p>This loader runs once at application startup (after the context is ready) and inserts:
 *
 * <ul>
 *   <li>20 airlines (including some well‑known carriers)
 *   <li>50 airports (including major hubs like JFK, LAX, LHR, DXB)
 *   <li>6 aircraft types with realistic seat configurations
 *   <li>500+ flights, both random and predictable (for consistent testing)
 * </ul>
 *
 * <p>If the flight table already contains more than 100 records, the loader skips execution to
 * avoid duplicate data.
 *
 * @see Faker
 * @see Flight
 * @see Airport
 * @see Airline
 * @see AircraftType
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataFakerLoader {

  private final AirportRepository airportRepository;
  private final AirlineRepository airlineRepository;
  private final AircraftTypeRepository aircraftTypeRepository;
  private final FlightRepository flightRepository;

  private final Faker faker = new Faker();
  private final Random random = new Random();

  /**
   * Loads all test data after the application has started.
   *
   * <p>This method is transactional to ensure consistency and to avoid partial inserts in case of
   * failure.
   *
   * @throws IllegalStateException if a required aircraft type for predictable flights is missing
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void loadData() {
    // Check if DB is already populated to avoid duplicate runs
    if (flightRepository.count() > 100) {
      log.info("Database already populated. Skipping data faker load.");
      return;
    }

    log.info("Starting database population with Datafaker...");

    // 1. Create Aircraft Types
    List<AircraftType> aircraftTypes = createAircraftTypes();
    aircraftTypeRepository.saveAll(aircraftTypes);

    // 2. Create Airlines
    List<Airline> airlines = createAirlines();
    airlineRepository.saveAll(airlines);

    // 3. Create Airports
    List<Airport> airports = createAirports();
    airportRepository.saveAll(airports);

    // 4. Create Flights (with all the complex relationships)
    List<Flight> flights = createFlights(airlines, airports, aircraftTypes);
    flightRepository.saveAll(flights);

    log.info(
        "Successfully populated {} flights, {} airports, {} airlines, and {} aircraft types.",
        flights.size(),
        airports.size(),
        airlines.size(),
        aircraftTypes.size());
  }

  /**
   * Builds a list of realistic aircraft types with seat distribution.
   *
   * @return a list of {@link AircraftType} entities
   */
  private List<AircraftType> createAircraftTypes() {
    // Realistic aircraft models with seat configurations
    List<AircraftType> list = new ArrayList<>();
    list.add(
        AircraftType.builder()
            .model("Boeing 737-800")
            .manufacturer("Boeing")
            .totalSeats(189)
            .economySeats(162)
            .premiumEconomySeats(0)
            .businessSeats(27)
            .firstClassSeats(0)
            .build());
    list.add(
        AircraftType.builder()
            .model("Airbus A320neo")
            .manufacturer("Airbus")
            .totalSeats(180)
            .economySeats(156)
            .premiumEconomySeats(0)
            .businessSeats(24)
            .firstClassSeats(0)
            .build());
    list.add(
        AircraftType.builder()
            .model("Boeing 777-300ER")
            .manufacturer("Boeing")
            .totalSeats(396)
            .economySeats(304)
            .premiumEconomySeats(28)
            .businessSeats(40)
            .firstClassSeats(24)
            .build());
    list.add(
        AircraftType.builder()
            .model("Airbus A380")
            .manufacturer("Airbus")
            .totalSeats(497)
            .economySeats(399)
            .premiumEconomySeats(38)
            .businessSeats(36)
            .firstClassSeats(24)
            .build());
    list.add(
        AircraftType.builder()
            .model("Embraer E190")
            .manufacturer("Embraer")
            .totalSeats(98)
            .economySeats(92)
            .premiumEconomySeats(6)
            .businessSeats(0)
            .firstClassSeats(0)
            .build());
    list.add(
        AircraftType.builder()
            .model("Boeing 787-9 Dreamliner")
            .manufacturer("Boeing")
            .totalSeats(290)
            .economySeats(216)
            .premiumEconomySeats(21)
            .businessSeats(28)
            .firstClassSeats(25)
            .build());
    return list;
  }

  /**
   * Generates a list of 20 random airlines plus 5 known ones (AA, DL, UA, EK, LH).
   *
   * @return a list of {@link Airline} entities with unique IATA codes
   */
  private List<Airline> createAirlines() {
    List<Airline> list = new ArrayList<>();
    Set<String> usedCodes = new HashSet<>();

    // Generate 20 random airlines
    for (int i = 0; i < 20; i++) {
      String code = faker.lorem().characters(2, 3, true, true).toUpperCase();
      while (usedCodes.contains(code)) {
        code = faker.lorem().characters(2, 3, true, true).toUpperCase();
      }
      usedCodes.add(code);

      list.add(
          Airline.builder()
              .code(code)
              .name(faker.company().name() + " Airlines")
              .country(faker.address().country())
              .logoUrl("https://placehold.co/100x100?text=" + code)
              .active(true)
              .build());
    }
    // Add known real airlines
    list.add(
        Airline.builder().code("AA").name("American Airlines").country("USA").active(true).build());
    list.add(
        Airline.builder().code("DL").name("Delta Air Lines").country("USA").active(true).build());
    list.add(
        Airline.builder().code("UA").name("United Airlines").country("USA").active(true).build());
    list.add(Airline.builder().code("EK").name("Emirates").country("UAE").active(true).build());
    list.add(
        Airline.builder().code("LH").name("Lufthansa").country("Germany").active(true).build());
    return list;
  }

  /**
   * Generates a list of 50 random airports plus 4 well‑known hubs.
   *
   * @return a list of {@link Airport} entities with unique IATA codes
   */
  private List<Airport> createAirports() {
    List<Airport> list = new ArrayList<>();
    Set<String> usedIatas = new HashSet<>();

    for (int i = 0; i < 50; i++) {
      String iata = randomIata();
      while (usedIatas.contains(iata)) {
        iata = randomIata();
      }
      usedIatas.add(iata);

      list.add(
          Airport.builder()
              .iataCode(iata)
              .icaoCode(randomIcao())
              .name(faker.address().cityName() + " International Airport")
              .city(faker.address().city())
              .country(faker.address().country())
              .timezone(faker.address().timeZone())
              .latitude(new BigDecimal(faker.address().latitude()))
              .longitude(new BigDecimal(faker.address().longitude()))
              .build());
    }
    // Add well-known hubs (hardcoded for realism)
    list.add(
        Airport.builder()
            .iataCode("JFK")
            .icaoCode("KJFK")
            .name("John F. Kennedy International")
            .city("New York")
            .country("USA")
            .timezone("America/New_York")
            .build());
    list.add(
        Airport.builder()
            .iataCode("LAX")
            .icaoCode("KLAX")
            .name("Los Angeles International")
            .city("Los Angeles")
            .country("USA")
            .timezone("America/Los_Angeles")
            .build());
    list.add(
        Airport.builder()
            .iataCode("LHR")
            .icaoCode("EGLL")
            .name("London Heathrow")
            .city("London")
            .country("UK")
            .timezone("Europe/London")
            .build());
    list.add(
        Airport.builder()
            .iataCode("DXB")
            .icaoCode("OMDB")
            .name("Dubai International")
            .city("Dubai")
            .country("UAE")
            .timezone("Asia/Dubai")
            .build());
    return list;
  }

  /**
   * Orchestrates the creation of all flights: 500 random flights plus a fixed set of predictable
   * flights for consistent testing.
   *
   * @param airlines the list of persisted airlines
   * @param airports the list of persisted airports
   * @param aircraftTypes the list of persisted aircraft types
   * @return a list of {@link Flight} entities
   */
  private List<Flight> createFlights(
      List<Airline> airlines, List<Airport> airports, List<AircraftType> aircraftTypes) {
    List<Flight> flights = new ArrayList<>();

    // 1. Generate 500 random flights (as before)
    flights.addAll(createRandomFlights(airlines, airports, aircraftTypes));

    // 2. Add predictable flights for consistent testing
    flights.addAll(createPredictableFlights(airlines, airports, aircraftTypes));

    return flights;
  }

  /**
   * Generates 500 random flights with arbitrary origins, destinations, dates, prices, and seat
   * occupation. Some flights are marked as delayed or full.
   *
   * @param airlines list of airlines
   * @param airports list of airports
   * @param aircraftTypes list of aircraft types
   * @return a list of random {@link Flight} entities
   */
  private List<Flight> createRandomFlights(
      List<Airline> airlines, List<Airport> airports, List<AircraftType> aircraftTypes) {
    List<Flight> flights = new ArrayList<>();
    LocalDate today = LocalDate.now();

    for (int i = 0; i < 500; i++) {
      // Pick random entities
      Airline airline = airlines.get(random.nextInt(airlines.size()));
      Airport origin = airports.get(random.nextInt(airports.size()));
      Airport destination = airports.get(random.nextInt(airports.size()));
      // Avoid same origin/destination
      while (destination.equals(origin)) {
        destination = airports.get(random.nextInt(airports.size()));
      }

      AircraftType aircraft = aircraftTypes.get(random.nextInt(aircraftTypes.size()));

      // Flight times
      int daysFromNow = random.nextInt(30);
      LocalDate departureDate = today.plusDays(daysFromNow);
      LocalTime departureTime = LocalTime.of(random.nextInt(23), random.nextInt(60));

      // Duration: 60 to 600 minutes
      int durationMinutes = 60 + random.nextInt(540);
      // Calculate arrival
      LocalDateTime departureDateTime = LocalDateTime.of(departureDate, departureTime);
      LocalDateTime arrivalDateTime = departureDateTime.plusMinutes(durationMinutes);

      // Generate base prices
      BigDecimal baseEconomy = BigDecimal.valueOf(100 + random.nextInt(800));
      BigDecimal basePremium =
          random.nextBoolean() ? baseEconomy.multiply(BigDecimal.valueOf(1.3)) : null;
      BigDecimal baseBusiness =
          random.nextBoolean() ? baseEconomy.multiply(BigDecimal.valueOf(2.0)) : null;
      BigDecimal baseFirst =
          random.nextBoolean() ? baseEconomy.multiply(BigDecimal.valueOf(3.5)) : null;

      // Seats
      int totalSeats = aircraft.getTotalSeats();
      int economyAvail = aircraft.getEconomySeats();
      int premiumAvail = aircraft.getPremiumEconomySeats();
      int businessAvail = aircraft.getBusinessSeats();
      int firstAvail = aircraft.getFirstClassSeats();

      // Randomly fill some seats to make it realistic
      double fillFactor = 0.3 + (random.nextDouble() * 0.6); // 30% to 90% full
      int occupied = (int) (totalSeats * fillFactor);
      int availableSeats = totalSeats - occupied;

      // Distribute occupied seats proportionally (simplified)
      int occupiedEco = Math.min(economyAvail, (int) (occupied * 0.6));
      int occupiedPremium = Math.min(premiumAvail, (int) (occupied * 0.1));
      int occupiedBiz = Math.min(businessAvail, (int) (occupied * 0.2));
      int occupiedFirst = Math.min(firstAvail, (int) (occupied * 0.1));

      Flight flight =
          Flight.builder()
              .flightNumber(airline.getCode() + String.format("%04d", 1000 + random.nextInt(9000)))
              .airline(airline)
              .originAirport(origin)
              .destinationAirport(destination)
              .aircraftType(aircraft)
              .departureDate(departureDate)
              .departureTime(departureTime)
              .arrivalDate(arrivalDateTime.toLocalDate())
              .arrivalTime(arrivalDateTime.toLocalTime())
              .durationMinutes(durationMinutes)
              .basePriceEconomy(baseEconomy)
              .basePricePremiumEconomy(basePremium)
              .basePriceBusiness(baseBusiness)
              .basePriceFirstClass(baseFirst)
              .currency("USD")
              .totalSeats(totalSeats)
              .availableSeats(availableSeats)
              .economyAvailable(economyAvail - occupiedEco)
              .premiumEconomyAvailable(premiumAvail - occupiedPremium)
              .businessAvailable(businessAvail - occupiedBiz)
              .firstClassAvailable(firstAvail - occupiedFirst)
              .status(Flight.FlightStatus.SCHEDULED)
              .baggageAllowanceKg(20 + random.nextInt(15))
              .cabinBaggageAllowanceKg(7 + random.nextInt(5))
              .cancellationPolicy(faker.lorem().sentence(6))
              .build();

      // If completely full, set status accordingly
      if (flight.getAvailableSeats() == 0) {
        flight.setStatus(Flight.FlightStatus.FULL);
      } else if (random.nextInt(10) == 0) { // 10% chance delayed
        flight.setStatus(Flight.FlightStatus.DELAYED);
      }

      flights.add(flight);
    }
    return flights;
  }

  /**
   * Creates a fixed set of flights that are guaranteed to exist. These flights use known airlines
   * and airports (JFK, LAX, LHR, DXB) and specific dates to support integration and manual testing.
   *
   * <p>The predictable flights include:
   *
   * <ul>
   *   <li>JFK → LAX on 2026-07-18 (two different times)
   *   <li>LAX → LHR on 2026-07-18
   *   <li>LHR → DXB on 2026-07-25
   *   <li>DXB → JFK on 2026-08-01
   * </ul>
   *
   * @param airlines list of airlines
   * @param airports list of airports
   * @param aircraftTypes list of aircraft types
   * @return a list of predictable {@link Flight} entities
   * @throws IllegalStateException if a required aircraft type is not present
   */
  private List<Flight> createPredictableFlights(
      List<Airline> airlines, List<Airport> airports, List<AircraftType> aircraftTypes) {

    // Quick lookup maps for airlines and airports by IATA code
    Map<String, Airline> airlineMap =
        airlines.stream().collect(Collectors.toMap(Airline::getCode, Function.identity()));
    Map<String, Airport> airportMap =
        airports.stream().collect(Collectors.toMap(Airport::getIataCode, Function.identity()));

    // We'll use specific aircraft types – pick the ones you have
    AircraftType boeing737 = findAircraftByModel(aircraftTypes, "Boeing 737-800");
    AircraftType boeing777 = findAircraftByModel(aircraftTypes, "Boeing 777-300ER");
    AircraftType airbusA380 = findAircraftByModel(aircraftTypes, "Airbus A380");

    // The departure date we want to test (adjust as needed)
    LocalDate testDate = LocalDate.of(2026, 7, 18); // matches your Postman request

    List<Flight> predictable = new ArrayList<>();

    // --- Flight 1: JFK → LAX on 2026-07-18 (Economy, 2 passengers) ---
    predictable.add(
        buildPredictableFlight(
            airlineMap.get("AA"), // American Airlines
            airportMap.get("JFK"),
            airportMap.get("LAX"),
            testDate,
            LocalTime.of(8, 0), // 08:00 departure
            360, // 6 hours
            boeing737,
            BigDecimal.valueOf(350.00), // economy price
            BigDecimal.valueOf(450.00), // premium economy
            BigDecimal.valueOf(750.00), // business
            BigDecimal.valueOf(1200.00), // first class
            189,
            162,
            0,
            27,
            0 // seat counts (total, eco, premium, business, first)
            ));

    // --- Flight 2: JFK → LAX on 2026-07-18 (another flight, different time) ---
    predictable.add(
        buildPredictableFlight(
            airlineMap.get("DL"),
            airportMap.get("JFK"),
            airportMap.get("LAX"),
            testDate,
            LocalTime.of(14, 30),
            360,
            boeing777,
            BigDecimal.valueOf(400.00),
            BigDecimal.valueOf(520.00),
            BigDecimal.valueOf(880.00),
            BigDecimal.valueOf(1500.00),
            396,
            304,
            28,
            40,
            24));

    // --- Flight 3: LAX → LHR on 2026-07-18 (First class, 3 passengers) ---
    predictable.add(
        buildPredictableFlight(
            airlineMap.get("UA"),
            airportMap.get("LAX"),
            airportMap.get("LHR"),
            testDate,
            LocalTime.of(22, 0),
            600, // 10 hours
            airbusA380,
            BigDecimal.valueOf(1200.00),
            BigDecimal.valueOf(1500.00),
            BigDecimal.valueOf(2400.00),
            BigDecimal.valueOf(4000.00),
            497,
            399,
            38,
            36,
            24));

    // --- Flight 4: LHR → DXB on 2026-07-25 (international) ---
    predictable.add(
        buildPredictableFlight(
            airlineMap.get("EK"),
            airportMap.get("LHR"),
            airportMap.get("DXB"),
            LocalDate.of(2026, 7, 25),
            LocalTime.of(9, 30),
            420, // 7 hours
            boeing777,
            BigDecimal.valueOf(650.00),
            BigDecimal.valueOf(850.00),
            BigDecimal.valueOf(1400.00),
            BigDecimal.valueOf(2200.00),
            396,
            304,
            28,
            40,
            24));

    // --- Flight 5: DXB → JFK on 2026-08-01 (return leg) ---
    predictable.add(
        buildPredictableFlight(
            airlineMap.get("EK"),
            airportMap.get("DXB"),
            airportMap.get("JFK"),
            LocalDate.of(2026, 8, 1),
            LocalTime.of(2, 0),
            840, // 14 hours
            airbusA380,
            BigDecimal.valueOf(900.00),
            BigDecimal.valueOf(1150.00),
            BigDecimal.valueOf(1800.00),
            BigDecimal.valueOf(3000.00),
            497,
            399,
            38,
            36,
            24));

    return predictable;
  }

  /**
   * Helper to find an AircraftType by its model name.
   *
   * @param types the list of aircraft types
   * @param model the model name to search for
   * @return the matching {@link AircraftType}
   * @throws IllegalStateException if no type with the given model is found
   */
  private AircraftType findAircraftByModel(List<AircraftType> types, String model) {
    return types.stream()
        .filter(a -> a.getModel().equals(model))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Aircraft type " + model + " not found"));
  }

  /**
   * Constructs a single {@link Flight} entity with explicit values.
   *
   * <p>This method sets all fields directly, including seat availability (40% occupied by default)
   * and fixed baggage policies.
   *
   * @param airline the airline
   * @param origin the departure airport
   * @param destination the arrival airport
   * @param departureDate the departure date
   * @param departureTime the departure time
   * @param durationMinutes flight duration in minutes
   * @param aircraft the aircraft type
   * @param economyPrice base economy price
   * @param premiumPrice base premium economy price (may be null)
   * @param businessPrice base business price (may be null)
   * @param firstPrice base first class price (may be null)
   * @param totalSeats total seats on the aircraft
   * @param ecoSeats total economy seats
   * @param premiumSeats total premium economy seats
   * @param businessSeats total business seats
   * @param firstSeats total first class seats
   * @return a fully built {@link Flight} instance
   */
  private Flight buildPredictableFlight(
      Airline airline,
      Airport origin,
      Airport destination,
      LocalDate departureDate,
      LocalTime departureTime,
      int durationMinutes,
      AircraftType aircraft,
      BigDecimal economyPrice,
      BigDecimal premiumPrice,
      BigDecimal businessPrice,
      BigDecimal firstPrice,
      int totalSeats,
      int ecoSeats,
      int premiumSeats,
      int businessSeats,
      int firstSeats) {

    // Calculate arrival
    LocalDateTime departureDateTime = LocalDateTime.of(departureDate, departureTime);
    LocalDateTime arrivalDateTime = departureDateTime.plusMinutes(durationMinutes);

    // We keep some seats available so the flight is bookable.
    // For predictability, we'll set available seats to a fixed fraction.
    int occupied = (int) (totalSeats * 0.4); // 40% occupied
    int available = totalSeats - occupied;

    // Distribute occupied seats proportionally (simplified)
    int occupiedEco = Math.min(ecoSeats, (int) (occupied * 0.6));
    int occupiedPremium = Math.min(premiumSeats, (int) (occupied * 0.1));
    int occupiedBiz = Math.min(businessSeats, (int) (occupied * 0.2));
    int occupiedFirst = Math.min(firstSeats, (int) (occupied * 0.1));

    return Flight.builder()
        .flightNumber(airline.getCode() + String.format("%04d", 1000 + new Random().nextInt(9000)))
        .airline(airline)
        .originAirport(origin)
        .destinationAirport(destination)
        .aircraftType(aircraft)
        .departureDate(departureDate)
        .departureTime(departureTime)
        .arrivalDate(arrivalDateTime.toLocalDate())
        .arrivalTime(arrivalDateTime.toLocalTime())
        .durationMinutes(durationMinutes)
        .basePriceEconomy(economyPrice)
        .basePricePremiumEconomy(premiumPrice)
        .basePriceBusiness(businessPrice)
        .basePriceFirstClass(firstPrice)
        .currency("USD")
        .totalSeats(totalSeats)
        .availableSeats(available)
        .economyAvailable(ecoSeats - occupiedEco)
        .premiumEconomyAvailable(premiumSeats - occupiedPremium)
        .businessAvailable(businessSeats - occupiedBiz)
        .firstClassAvailable(firstSeats - occupiedFirst)
        .status(Flight.FlightStatus.SCHEDULED)
        .baggageAllowanceKg(23)
        .cabinBaggageAllowanceKg(7)
        .cancellationPolicy("Standard cancellation 24h prior")
        .build();
  }

  /**
   * Generates a random 3‑letter IATA code.
   *
   * @return a random uppercase alphabetic string of length 3
   */
  private String randomIata() {
    return faker.lorem().characters(3, 3, true, true).toUpperCase();
  }

  /**
   * Generates a random 4‑letter ICAO code.
   *
   * @return a random uppercase alphabetic string of length 4
   */
  private String randomIcao() {
    return faker.lorem().characters(4, 4, true, true).toUpperCase();
  }
}
