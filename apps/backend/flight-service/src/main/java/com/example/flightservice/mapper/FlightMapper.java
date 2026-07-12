package com.example.flightservice.mapper;

import com.example.flightservice.dto.controller.flightsearch.FlightResponseDto;
import com.example.flightservice.entity.Flight;
import org.springframework.stereotype.Component;

/**
 * Mapper component that converts {@link Flight} entities to {@link FlightResponseDto} for API
 * responses.
 *
 * <p>This mapper flattens the entity graph (airline, airports, aircraft type) into a single DTO and
 * formats the duration as a human‑readable string.
 *
 * @see FlightResponseDto
 * @see Flight
 */
@Component
public class FlightMapper {

  /**
   * Converts a {@link Flight} entity to a complete {@link FlightResponseDto}.
   *
   * <p>All nested objects (airline, origin, destination, aircraft type) are mapped to their
   * corresponding inner DTO classes. Seat availability and pricing are also transferred.
   *
   * @param flight the flight entity (must not be {@code null})
   * @return a fully populated response DTO
   */
  public FlightResponseDto toResponse(Flight flight) {
    return FlightResponseDto.builder()
        .id(flight.getId())
        .flightNumber(flight.getFlightNumber())
        .airline(
            FlightResponseDto.AirlineInfo.builder()
                .id(flight.getAirline().getId())
                .code(flight.getAirline().getCode())
                .name(flight.getAirline().getName())
                .country(flight.getAirline().getCountry())
                .logoUrl(flight.getAirline().getLogoUrl())
                .build())
        .origin(
            FlightResponseDto.AirportInfo.builder()
                .id(flight.getOriginAirport().getId())
                .iataCode(flight.getOriginAirport().getIataCode())
                .name(flight.getOriginAirport().getName())
                .city(flight.getOriginAirport().getCity())
                .country(flight.getOriginAirport().getCountry())
                .timezone(flight.getOriginAirport().getTimezone())
                .build())
        .destination(
            FlightResponseDto.AirportInfo.builder()
                .id(flight.getDestinationAirport().getId())
                .iataCode(flight.getDestinationAirport().getIataCode())
                .name(flight.getDestinationAirport().getName())
                .city(flight.getDestinationAirport().getCity())
                .country(flight.getDestinationAirport().getCountry())
                .timezone(flight.getDestinationAirport().getTimezone())
                .build())
        .departureDate(flight.getDepartureDate())
        .departureTime(flight.getDepartureTime())
        .arrivalDate(flight.getArrivalDate())
        .arrivalTime(flight.getArrivalTime())
        .durationMinutes(flight.getDurationMinutes())
        .durationFormatted(formatDuration(flight.getDurationMinutes()))
        .pricing(
            FlightResponseDto.PricingInfo.builder()
                .economy(flight.getBasePriceEconomy())
                .premiumEconomy(flight.getBasePricePremiumEconomy())
                .business(flight.getBasePriceBusiness())
                .firstClass(flight.getBasePriceFirstClass())
                .currency(flight.getCurrency())
                .build())
        .aircraftType(flight.getAircraftType().getModel())
        .aircraftManufacturer(flight.getAircraftType().getManufacturer())
        .totalSeats(flight.getTotalSeats())
        .availableSeats(flight.getAvailableSeats())
        .seatAvailability(
            FlightResponseDto.SeatAvailability.builder()
                .economy(flight.getEconomyAvailable())
                .premiumEconomy(flight.getPremiumEconomyAvailable())
                .business(flight.getBusinessAvailable())
                .firstClass(flight.getFirstClassAvailable())
                .build())
        .status(flight.getStatus().name())
        .baggageAllowanceKg(flight.getBaggageAllowanceKg())
        .cabinBaggageAllowanceKg(flight.getCabinBaggageAllowanceKg())
        .cancellationPolicy(flight.getCancellationPolicy())
        .build();
  }

  /**
   * Formats a duration in minutes into a human‑readable string, e.g. "2h 30m".
   *
   * @param minutes the total minutes
   * @return a formatted string with hours and minutes
   */
  private String formatDuration(int minutes) {
    int hours = minutes / 60;
    int mins = minutes % 60;
    return String.format("%dh %02dm", hours, mins);
  }
}
