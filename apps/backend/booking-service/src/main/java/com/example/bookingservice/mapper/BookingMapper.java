package com.example.bookingservice.mapper;

import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.Passenger;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Mapper for converting between booking entities and response DTOs. */
@Component
public class BookingMapper {

  /**
   * Converts a Booking entity to a BookingResponse DTO.
   *
   * @param booking the entity to convert
   * @return the response DTO
   */
  public BookingResponse toResponse(Booking booking) {
    return BookingResponse.builder()
        .id(booking.getId())
        .bookingReference(booking.getBookingReference())
        .status(booking.getStatus().name())
        .bookingType(booking.getBookingType().name())
        .totalAmount(booking.getTotalAmount())
        .currency(booking.getCurrency())
        .contactEmail(booking.getContactEmail())
        .contactPhone(booking.getContactPhone())
        .flights(
            booking.getBookingFlights().stream()
                .map(
                    bf ->
                        BookingResponse.FlightInfo.builder()
                            .flightId(bf.getFlightId())
                            .flightNumber(bf.getFlightNumber())
                            .airline(bf.getAirlineName())
                            .origin(bf.getOriginAirportCode())
                            .destination(bf.getDestinationAirportCode())
                            .departureTime(bf.getDepartureDate() + " " + bf.getDepartureTime())
                            .arrivalTime(bf.getArrivalDate() + " " + bf.getArrivalTime())
                            .cabinClass(bf.getCabinClass().name())
                            .price(bf.getBasePrice())
                            .segmentOrder(bf.getSegmentOrder())
                            .build())
                .collect(Collectors.toList()))
        .passengers(
            booking.getPassengers().stream()
                .map(
                    p ->
                        BookingResponse.PassengerInfo.builder()
                            .id(p.getId())
                            .title(p.getTitle())
                            .firstName(p.getFirstName())
                            .lastName(p.getLastName())
                            .dateOfBirth(p.getDateOfBirth().toString())
                            .nationality(p.getNationality())
                            .passportNumber(p.getPassportNumber())
                            .mealPreference(p.getMealPreference())
                            .build())
                .collect(Collectors.toList()))
        .additionalServices(
            booking.getAdditionalServices().stream()
                .map(
                    s ->
                        BookingResponse.ServiceInfo.builder()
                            .serviceType(s.getServiceType().name())
                            .description(s.getDescription())
                            .price(s.getPrice())
                            .build())
                .collect(Collectors.toList()))
        .holdExpiresAt(booking.getHoldExpiresAt())
        .confirmedAt(booking.getConfirmedAt())
        .cancelledAt(booking.getCancelledAt())
        .cancellationReason(booking.getCancellationReason())
        .refundAmount(booking.getRefundAmount())
        .paymentTransactionId(booking.getPaymentTransactionId())
        .createdAt(booking.getCreatedAt())
        .build();
  }

  /**
   * Converts a passenger details DTO to a Passenger entity.
   *
   * @param details the DTO
   * @return the Passenger entity (without booking association)
   */
  public Passenger toPassengerEntity(CreateBookingRequest.PassengerDetails details) {
    return Passenger.builder()
        .title(details.getTitle())
        .firstName(details.getFirstName())
        .lastName(details.getLastName())
        .middleName(details.getMiddleName())
        .dateOfBirth(details.getDateOfBirth())
        .gender(details.getGender())
        .nationality(details.getNationality())
        .passportNumber(details.getPassportNumber())
        .passportExpiryDate(details.getPassportExpiryDate())
        .frequentFlyerNumber(details.getFrequentFlyerNumber())
        .mealPreference(details.getMealPreference())
        .specialAssistance(details.getSpecialAssistance())
        .build();
  }
}
