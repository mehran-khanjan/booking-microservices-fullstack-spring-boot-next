package com.example.communicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.commonlib.event.CommunicationRoutingKeys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;

import org.springframework.amqp.rabbit.core.RabbitAdmin;

class DlqMonitorServiceTest {

  @Test
  void registerGauges_registersOneGaugePerMonitoredDlq() {
    RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DlqMonitorService service = new DlqMonitorService(rabbitAdmin, meterRegistry);

    service.registerGauges();

    assertThat(meterRegistry.find("communication.dlq.depth").gauges()).hasSize(2);
    assertThat(meterRegistry.get("communication.dlq.depth")
            .tag("queue", CommunicationRoutingKeys.DLQ_OTP_EMAIL)
            .gauge()
            .value())
        .isZero();
  }

  @Test
  void checkDlqDepths_updatesGaugeValueFromRabbitAdmin() {
    RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    QueueInformation info = mock(QueueInformation.class);
    when(info.getMessageCount()).thenReturn(5L);
    when(rabbitAdmin.getQueueInfo(anyString())).thenReturn(info);
    DlqMonitorService service = new DlqMonitorService(rabbitAdmin, meterRegistry);
    service.registerGauges();

    service.checkDlqDepths();

    assertThat(
            meterRegistry
                .get("communication.dlq.depth")
                .tag("queue", CommunicationRoutingKeys.DLQ_OTP_EMAIL)
                .gauge()
                .value())
        .isEqualTo(5);
  }

  @Test
  void checkDlqDepths_queueInfoUnavailable_recordsNegativeOneDepth() {
    RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(rabbitAdmin.getQueueInfo(anyString())).thenReturn(null);
    DlqMonitorService service = new DlqMonitorService(rabbitAdmin, meterRegistry);
    service.registerGauges();

    service.checkDlqDepths();

    assertThat(
            meterRegistry
                .get("communication.dlq.depth")
                .tag("queue", CommunicationRoutingKeys.DLQ_OTP_SMS)
                .gauge()
                .value())
        .isEqualTo(-1);
  }

  @Test
  void checkDlqDepths_emptyQueue_recordsZeroDepth() {
    RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    QueueInformation info = mock(QueueInformation.class);
    when(info.getMessageCount()).thenReturn(0L);
    when(rabbitAdmin.getQueueInfo(anyString())).thenReturn(info);
    DlqMonitorService service = new DlqMonitorService(rabbitAdmin, meterRegistry);
    service.registerGauges();

    service.checkDlqDepths();

    assertThat(
            meterRegistry
                .get("communication.dlq.depth")
                .tag("queue", CommunicationRoutingKeys.DLQ_OTP_EMAIL)
                .gauge()
                .value())
        .isZero();
  }
}
