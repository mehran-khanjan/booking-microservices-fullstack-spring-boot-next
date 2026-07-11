package com.example.outboxlib;

import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for the outbox library when run as a standalone application.
 *
 * <p>This class is primarily used for testing or demonstration purposes. It logs a startup message
 * to indicate that the library has been initialised.
 */
@Slf4j
public class Main {

  /**
   * The main method that prints a startup log message.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    log.info("Outbox lib started");
  }
}
