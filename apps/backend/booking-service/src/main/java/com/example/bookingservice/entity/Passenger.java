package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** Entity representing a passenger associated with a booking. */
@Entity
@Table(name = "passengers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Passenger {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id", nullable = false)
  private Booking booking;

  @Column(length = 10)
  private String title;

  @Column(nullable = false, length = 100)
  private String firstName;

  @Column(nullable = false, length = 100)
  private String lastName;

  @Column(length = 100)
  private String middleName;

  @Column(nullable = false)
  private LocalDate dateOfBirth;

  @Column(length = 10)
  private String gender;

  @Column(length = 3)
  private String nationality;

  @Column(length = 50)
  private String passportNumber;

  private LocalDate passportExpiryDate;

  @Column(length = 50)
  private String frequentFlyerNumber;

  @Column(length = 50)
  private String mealPreference;

  @Column(columnDefinition = "TEXT")
  private String specialAssistance;

  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;
}
