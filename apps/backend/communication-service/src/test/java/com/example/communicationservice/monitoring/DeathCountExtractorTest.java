package com.example.communicationservice.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

class DeathCountExtractorTest {

  private final DeathCountExtractor extractor = new DeathCountExtractor();

  @Test
  void extractDeathCount_noXDeathHeader_returnsZero() {
    MessageProperties props = new MessageProperties();

    long count = extractor.extractDeathCount(props, "otp.email.queue");

    assertThat(count).isZero();
  }

  @Test
  void extractDeathCount_emptyXDeathHeader_returnsZero() {
    MessageProperties props = new MessageProperties();
    props.getHeaders().put("x-death", List.of());

    long count = extractor.extractDeathCount(props, "otp.email.queue");

    assertThat(count).isZero();
  }

  @Test
  void extractDeathCount_matchingQueue_returnsRecordedCount() {
    MessageProperties props = new MessageProperties();
    props
        .getHeaders()
        .put(
            "x-death",
            List.of(Map.of("queue", "otp.email.queue", "count", 3L, "reason", "rejected")));

    long count = extractor.extractDeathCount(props, "otp.email.queue");

    assertThat(count).isEqualTo(3L);
  }

  @Test
  void extractDeathCount_noMatchingQueueEntry_returnsZero() {
    MessageProperties props = new MessageProperties();
    props
        .getHeaders()
        .put("x-death", List.of(Map.of("queue", "otp.sms.queue", "count", 5L)));

    long count = extractor.extractDeathCount(props, "otp.email.queue");

    assertThat(count).isZero();
  }

  @Test
  void extractDeathCount_multipleEntries_returnsFirstMatchingQueue() {
    MessageProperties props = new MessageProperties();
    props
        .getHeaders()
        .put(
            "x-death",
            List.of(
                Map.of("queue", "otp.sms.queue", "count", 1L),
                Map.of("queue", "otp.email.queue", "count", 7L)));

    long count = extractor.extractDeathCount(props, "otp.email.queue");

    assertThat(count).isEqualTo(7L);
  }
}
