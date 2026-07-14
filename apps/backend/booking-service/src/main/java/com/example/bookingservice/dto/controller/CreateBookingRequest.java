package com.example.bookingservice.dto.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new booking. Includes flight selections, passenger details, and
 * contact information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

  @NotEmpty(message = "At least one flight must be selected")
  @Valid
  private List<FlightSelection> flights;

  @NotEmpty(message = "At least one passenger is required")
  @Valid
  private List<PassengerDetails> passengers;

  @Email(message = "Valid email is required")
  @NotBlank(message = "Contact email is required")
  private String contactEmail;

  @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
  private String contactPhone;

  @Valid private List<AdditionalServiceRequest> additionalServices;

  private String currency;

  /** Flight selection with cabin class and segment order. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class FlightSelection {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Segment order is required")
    @Min(0)
    private Integer segmentOrder;

    @NotBlank(message = "Cabin class is required")
    private String cabinClass;
  }

  /** Passenger details including personal information and passport data. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PassengerDetails {

    private String title;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100)
    private String lastName;

    private String middleName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String gender;

    @Size(min = 2, max = 3)
    private String nationality;

    private String passportNumber;

    private LocalDate passportExpiryDate;

    private String frequentFlyerNumber;

    private String mealPreference;

    private String specialAssistance;
  }

  /** Additional service request (e.g., extra baggage, lounge access). */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AdditionalServiceRequest {

    @NotBlank(message = "Service type is required")
    private String serviceType;

    private Long passengerId;

    private String description;
  }
}
