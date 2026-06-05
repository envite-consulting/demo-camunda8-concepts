package de.envite.bpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import jakarta.annotation.Nullable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CheckInventoryWorker {

  private static final Logger LOG = LoggerFactory.getLogger(CheckInventoryWorker.class);
  static final String FAIL_SENTINEL = "FAIL_INCIDENT";

  @JobWorker(type = "check-inventory", autoComplete = false)
  public void checkInventory(
      final JobClient jobClient,
      final ActivatedJob job,
      @Variable(name = "item") @Nullable String itemOrdered) {
    String item = (itemOrdered == null || itemOrdered.isEmpty()) ? "default-item" : itemOrdered;

    if (FAIL_SENTINEL.equals(item)) {
      LOG.warn(
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
        LOG.error("Failed to mark job {} as failed", job.getKey(), e);
      }
      return;
    }

    LOG.info("Checking inventory for item: {}", item);
    jobClient.newCompleteCommand(job).variables(Map.of("item", item + " allocated")).send().join();
    LOG.info("check-inventory completed for job: {}", job.getKey());
  }
}
