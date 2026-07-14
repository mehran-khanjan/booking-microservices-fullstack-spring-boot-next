package com.example.bookingservice.config;

import com.example.flightservice.grpc.FlightServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client configuration for the Flight Service. Provides a blocking stub for making
 * flight-related RPC calls.
 */
@Configuration
public class GrpcClientConfig {

  @Value("${grpc.client.flight-service.address}")
  private String flightServiceAddress;

  /**
   * Creates a blocking stub for the Flight service.
   *
   * @return the FlightServiceBlockingStub
   */
  @Bean
  public FlightServiceGrpc.FlightServiceBlockingStub flightServiceStub() {
    ManagedChannel channel =
        ManagedChannelBuilder.forTarget(flightServiceAddress).usePlaintext().build();
    return FlightServiceGrpc.newBlockingStub(channel);
  }
}
