package com.example.flightservice.unit;

import com.example.flightservice.entity.Flight;
import com.example.flightservice.service.FlightSpecification;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSpecificationTest {

  @Mock private Root<Flight> root;
  @Mock private CriteriaQuery<?> query;
  @Mock private CriteriaBuilder cb;

  @SuppressWarnings("unchecked")
  private <T> Path<T> mockPath() {
    return (Path<T>) mock(Path.class);
  }

  @SuppressWarnings("unchecked")
  private <T> Expression<T> mockExpression() {
    return (Expression<T>) mock(Expression.class);
  }

  @Test
  void hasOrigin_createsEqualPredicate() {
    Path<Long> originPath = mockPath();
    Path<Long> idPath = mockPath();
    when(root.get("originAirport")).thenReturn((Path) originPath);
    when(originPath.get("id")).thenReturn((Path) idPath);
    when(cb.equal(idPath, 1L)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasOrigin(1L);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasDestination_createsEqualPredicate() {
    Path<Long> destPath = mockPath();
    Path<Long> idPath = mockPath();
    when(root.get("destinationAirport")).thenReturn((Path) destPath);
    when(destPath.get("id")).thenReturn((Path) idPath);
    when(cb.equal(idPath, 2L)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasDestination(2L);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasDepartureDate_createsEqualPredicate() {
    LocalDate date = LocalDate.of(2025, 6, 1);
    Path<LocalDate> datePath = mockPath();
    when(root.get("departureDate")).thenReturn((Path) datePath);
    when(cb.equal(datePath, date)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasDepartureDate(date);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void isAvailable_checksStatusAndSeats() {
    Path<Object> statusPath = mockPath();
    Path<Object> seatsPath = mockPath();
    Predicate statusPred = mock(Predicate.class);
    Predicate seatsPred = mock(Predicate.class);
    when(root.get("status")).thenReturn((Path) statusPath);
    when(root.get("availableSeats")).thenReturn((Path) seatsPath);
    when(cb.equal(statusPath, Flight.FlightStatus.SCHEDULED)).thenReturn(statusPred);
    when(cb.greaterThan((Path) seatsPath, 0)).thenReturn(seatsPred);
    when(cb.and(statusPred, seatsPred)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.isAvailable();
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasPriceGreaterThan_createsPredicate() {
    Path<BigDecimal> pricePath = mockPath();
    when(root.get("basePriceEconomy")).thenReturn((Path) pricePath);
    when(cb.greaterThanOrEqualTo(pricePath, BigDecimal.valueOf(100))).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasPriceGreaterThan(BigDecimal.valueOf(100));
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasPriceLessThan_createsPredicate() {
    Path<BigDecimal> pricePath = mockPath();
    when(root.get("basePriceEconomy")).thenReturn((Path) pricePath);
    when(cb.lessThanOrEqualTo(pricePath, BigDecimal.valueOf(500))).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasPriceLessThan(BigDecimal.valueOf(500));
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasAirlineCode_usesCaseInsensitiveMatch() {
    Path<String> airlinePath = mockPath();
    Path<String> codePath = mockPath();
    Expression<String> upperExpr = mockExpression();
    when(root.get("airline")).thenReturn((Path) airlinePath);
    when(airlinePath.get("code")).thenReturn((Path) codePath);
    when(cb.upper(codePath)).thenReturn(upperExpr);
    when(cb.equal(upperExpr, "AA")).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasAirlineCode("aa");
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
    verify(cb).upper(codePath);
    verify(cb).equal(upperExpr, "AA");
  }

  @Test
  void hasCabinClassAvailable_forEconomy_checksEconomyAvailable() {
    Path<Integer> seatsPath = mockPath();
    when(root.get("economyAvailable")).thenReturn((Path) seatsPath);
    when(cb.greaterThanOrEqualTo(seatsPath, 1)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasCabinClassAvailable("ECONOMY", 1);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasCabinClassAvailable_forBusiness_checksBusinessAvailable() {
    Path<Integer> seatsPath = mockPath();
    when(root.get("businessAvailable")).thenReturn((Path) seatsPath);
    when(cb.greaterThanOrEqualTo(seatsPath, 2)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasCabinClassAvailable("BUSINESS", 2);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasCabinClassAvailable_forFirstClass_checksFirstClassAvailable() {
    Path<Integer> seatsPath = mockPath();
    when(root.get("firstClassAvailable")).thenReturn((Path) seatsPath);
    when(cb.greaterThanOrEqualTo(seatsPath, 1)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasCabinClassAvailable("FIRST_CLASS", 1);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  void hasCabinClassAvailable_forUnknownClass_fallsBackToAvailableSeats() {
    Path<Integer> seatsPath = mockPath();
    when(root.get("availableSeats")).thenReturn((Path) seatsPath);
    when(cb.greaterThanOrEqualTo(seatsPath, 3)).thenReturn(mock(Predicate.class));

    Specification<Flight> spec = FlightSpecification.hasCabinClassAvailable("UNKNOWN", 3);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }
}