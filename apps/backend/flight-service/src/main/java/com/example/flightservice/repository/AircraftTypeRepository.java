package com.example.flightservice.repository;

import com.example.flightservice.entity.AircraftType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AircraftType} entities.
 *
 * <p>Provides basic CRUD operations and custom queries can be added here if needed. This repository
 * is used to retrieve aircraft type configurations when creating flights.
 *
 * @see AircraftType
 */
public interface AircraftTypeRepository extends JpaRepository<AircraftType, Long> {}
