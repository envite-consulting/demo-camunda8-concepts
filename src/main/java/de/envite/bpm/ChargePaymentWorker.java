package de.envite.bpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChargePaymentWorker {

  @JobWorker(type = "charge-payment")
  public void processPayment(final ActivatedJob job) {
    log.info("Processing charge-payment job: {}", job.getKey());
    log.info("charge-payment completed: {}", job.getKey());
  }
}
