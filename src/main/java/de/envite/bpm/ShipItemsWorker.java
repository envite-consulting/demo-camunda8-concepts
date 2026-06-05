package de.envite.bpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShipItemsWorker {

  @JobWorker(type = "ship-items")
  public Map<String, String> shipItems(final ActivatedJob job) {
    log.info("Processing ship-items job: {}", job.getKey());
    log.info("ship-items completed: {}", job.getKey());
    return Map.of();
  }
}
