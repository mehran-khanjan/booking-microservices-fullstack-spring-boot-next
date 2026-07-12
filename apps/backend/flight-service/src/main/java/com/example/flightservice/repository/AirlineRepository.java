package com.example.flightservice.repository;

import com.example.flightservice.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Airline} entities.
 *
 * <p>Provides standard CRUD operations. Custom finders can be added, for example by airline code or
 * active status, as the domain grows.
 *
 * @see Airline
 */
public interface AirlineRepository extends JpaRepository<Airline, Long> {}
