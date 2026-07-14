package com.example.bookingservice.repository;

import com.example.bookingservice.entity.AdditionalService;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for AdditionalService entities. */
@Repository
public interface AdditionalServiceRepository extends JpaRepository<AdditionalService, Long> {

  List<AdditionalService> findByBookingId(Long bookingId);
}
