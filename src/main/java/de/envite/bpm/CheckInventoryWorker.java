package de.envite.bpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CheckInventoryWorker {

  static final String FAIL_SENTINEL = "FAIL_INCIDENT";

  @JobWorker(type = "check-inventory", autoComplete = false)
  public void checkInventory(
      final JobClient jobClient,
      final ActivatedJob job,
      @Variable(name = "item") @Nullable String itemOrdered) {
    String item = (itemOrdered == null || itemOrdered.isEmpty()) ? "default-item" : itemOrdered;

    if (FAIL_SENTINEL.equals(item)) {
      log.warn(
          "Sentinel '{}' received — failing job {} with retries=0 to raise an incident",
          FAIL_SENTINEL,
          job.getKey());
      try {
        jobClient
            .newFailCommand(job)
            .retries(0)
            .errorMessage("Simulated check-inventory failure for incident-alert demo")
            .send()
            .join();
      } catch (ClientStatusException e) {
        log.error("Failed to mark job {} as failed", job.getKey(), e);
      }
      return;
    }

    log.info("Checking inventory for item: {}", item);
    jobClient.newCompleteCommand(job).variables(Map.of("item", item + " allocated")).send().join();
    log.info("check-inventory completed for job: {}", job.getKey());
  }
}
