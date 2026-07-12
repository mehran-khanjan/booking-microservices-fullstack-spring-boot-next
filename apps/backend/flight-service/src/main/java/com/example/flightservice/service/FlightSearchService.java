package com.example.flightservice.service;

import com.example.flightservice.constant.ErrorCodes;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto;
import com.example.flightservice.exception.BusinessException;
import com.example.flightservice.util.ContextPropagatingSupplier;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Orchestrates flight searches, handling both one‑way and round‑trip requests.
 *
 * <p>This service delegates the actual search logic to {@link FlightSearchLegService} and, for
 * round‑trip searches, executes the outbound and return leg searches in parallel using a dedicated
 * thread pool.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Validates that return date (if present) is after departure date
 *   <li>For one‑way: simply calls {@link FlightSearchLegService#searchLeg}
 *   <li>For round‑trip: submits two async tasks with a timeout
 *   <li>Uses {@link ContextPropagatingSupplier} to preserve MDC and security context
 *   <li>Combines metadata and pagination from both legs into a single response
 *   <li>Unwraps {@link CompletionException} to expose original {@link BusinessException} or
 *       converts timeouts into a user‑friendly error
 * </ul>
 *
 * @see FlightSearchLegService
 * @see ContextPropagatingSupplier
 */
@Service
@Slf4j
public class FlightSearchService {

  private static final Duration LEG_SEARCH_TIMEOUT = Duration.ofSeconds(10);

  private final FlightSearchLegService legService;
  private final Executor flightSearchExecutor;

  public FlightSearchService(
      FlightSearchLegService legService,
      @Qualifier("flightSearchExecutor") Executor flightSearchExecutor) {
    this.legService = legService;
    this.flightSearchExecutor = flightSearchExecutor;
  }

  /**
   * Performs a flight search (one‑way or round‑trip) based on the request.
   *
   * <p>If {@link FlightSearchRequestDto#getReturnDate()} is {@code null}, this is a one‑way search
   * and the result is returned directly.
   *
   * <p>If a return date is provided, both legs are searched in parallel:
   *
   * <ul>
   *   <li>Outbound: origin → destination on departure date
   *   <li>Return: destination → origin on return date
   * </ul>
   *
   * Both tasks are submitted to the {@code flightSearchExecutor} and must
   *
   * <p>All exceptions thrown by the async tasks are unwrapped so that {@link BusinessException}
   * propagates as is, while timeouts are transformed into a {@link BusinessException} with code
   * {@link ErrorCodes#SEARCH_TIMEOUT}.
   *
   * @param request the search request (must contain origin, destination, departure date)
   * @return a {@link FlightSearchResponseDto} containing flights for one or both legs
   * @throws BusinessException if the request is invalid or a leg search fails
   */
  public FlightSearchResponseDto searchFlights(FlightSearchRequestDto request) {
    log.info(
        "Flight search started: origin={}, dest={}, dep={}, ret={}, passengers={}, cabin={}",
        request.getOrigin(),
        request.getDestination(),
        request.getDepartureDate(),
        request.getReturnDate(),
        request.getPassengers(),
        request.getCabinClass());

    if (request.getReturnDate() != null
        && !request.getReturnDate().isAfter(request.getDepartureDate())) {
      throw new BusinessException(
          "Return date must be after departure date", ErrorCodes.INVALID_SEARCH_PARAMS);
    }

    if (request.getReturnDate() == null) {
      FlightSearchResponseDto response =
          legService.searchLeg(
              request.getOrigin(),
              request.getDestination(),
              request.getDepartureDate(),
              request,
              request.getPage(),
              request.getSize(),
              request.getSortBy());
      log.info(
          "One-way search completed: found {} flights", response.getPageInfo().getTotalElements());
      return response;
    }

    log.info("Performing round-trip search – parallel async tasks");
    long start = System.currentTimeMillis();

    CompletableFuture<FlightSearchResponseDto> outbound =
        CompletableFuture.supplyAsync(
                ContextPropagatingSupplier.wrap(
                    () ->
                        legService.searchLeg(
                            request.getOrigin(),
                            request.getDestination(),
                            request.getDepartureDate(),
                            request,
                            request.getPage(),
                            request.getSize(),
                            request.getSortBy())),
                flightSearchExecutor)
            .orTimeout(LEG_SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

    CompletableFuture<FlightSearchResponseDto> returnLeg =
        CompletableFuture.supplyAsync(
                ContextPropagatingSupplier.wrap(
                    () ->
                        legService.searchLeg(
                            request.getDestination(),
                            request.getOrigin(),
                            request.getReturnDate(),
                            request,
                            request.getPage(),
                            request.getSize(),
                            request.getSortBy())),
                flightSearchExecutor)
            .orTimeout(LEG_SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

    FlightSearchResponseDto outboundResult;
    FlightSearchResponseDto returnResult;
    try {
      // Wait for both before pulling results, so one slow leg
      // doesn't block behind the other sequentially.
      CompletableFuture.allOf(outbound, returnLeg).join();
      outboundResult = outbound.join();
      returnResult = returnLeg.join();
    } catch (CompletionException ce) {
      throw unwrap(ce);
    }

    log.info("Round-trip completed in {} ms", System.currentTimeMillis() - start);

    FlightSearchResponseDto.SearchMetadata combinedMeta =
        FlightSearchResponseDto.SearchMetadata.builder()
            .origin(request.getOrigin())
            .destination(request.getDestination())
            .departureDate(request.getDepartureDate().toString())
            .returnDate(request.getReturnDate().toString())
            .passengers(request.getPassengers())
            .cabinClass(request.getCabinClass())
            .totalResults(
                outboundResult.getMetadata().getTotalResults()
                    + returnResult.getMetadata().getTotalResults())
            .searchId(UUID.randomUUID().toString())
            .fromCache(false)
            .build();

    return FlightSearchResponseDto.builder()
        .flights(outboundResult.getFlights())
        .returnFlights(returnResult.getFlights())
        .metadata(combinedMeta)
        .returnMetadata(returnResult.getMetadata())
        .pageInfo(outboundResult.getPageInfo())
        .returnPageInfo(returnResult.getPageInfo())
        .build();
  }

  /**
   * Unwraps a {@link CompletionException} thrown by {@link CompletableFuture#join()}.
   *
   * <p>This method is necessary because {@code join()} wraps any exception thrown inside the async
   * task in a {@code CompletionException}. Without unwrapping, a {@link BusinessException} (which
   * should map to a 4xx HTTP status) would be seen as a generic 500 by the global exception
   * handler. This method:
   *
   * <ul>
   *   <li>Returns the original {@link BusinessException} if present
   *   <li>Converts a {@link TimeoutException} into a {@code BusinessException} with code {@link
   *       ErrorCodes#SEARCH_TIMEOUT}
   *   <li>Returns any other {@link RuntimeException} as is
   *   <li>Wraps any non‑runtime exception in an {@link IllegalStateException}
   * </ul>
   *
   * @param ce the {@link CompletionException} caught from {@code join()}
   * @return a {@link RuntimeException} to be thrown (never returns normally)
   */
  private RuntimeException unwrap(CompletionException ce) {
    Throwable cause = ce.getCause();

    if (cause instanceof BusinessException be) {
      return be;
    }
    if (cause instanceof TimeoutException) {
      log.error("Leg search timed out after {}s", LEG_SEARCH_TIMEOUT.toSeconds());
      return new BusinessException(
          "Flight search is taking longer than expected, please try again",
          ErrorCodes.SEARCH_TIMEOUT);
    }
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new IllegalStateException("Unexpected error during round-trip flight search", cause);
  }
}
