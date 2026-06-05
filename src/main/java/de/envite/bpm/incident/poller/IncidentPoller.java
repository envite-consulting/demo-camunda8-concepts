package de.envite.bpm.incident.poller;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentPoller {

  private final CamundaClient camundaClient;
  private final IncidentRecordRepository repository;

  @Scheduled(fixedDelayString = "${app.incidents.poll-interval:PT5S}", initialDelayString = "PT2S")
  public void sync() {
    SearchResponse<Incident> result;
    try {
      result =
          camundaClient
              .newIncidentSearchRequest()
              .sort(s -> s.creationTime().asc())
              .page(p -> p.limit(1000))
              .send()
              .join();
    } catch (Exception e) {
      log.warn("Incident polling failed: {}", e.getMessage());
      return;
    }

    Instant now = Instant.now();
    int upserts = 0;
    for (Incident inc : result.items()) {
      IncidentRecord row = repository.findById(inc.getIncidentKey()).orElseGet(IncidentRecord::new);
      String previousState = row.getState();
      row.populateFrom(inc, now);
      repository.save(row);
      upserts++;
      if (previousState != null && !previousState.equals(row.getState())) {
        log.info(
            "Incident {} state transition: {} → {}",
            row.getIncidentKey(),
            previousState,
            row.getState());
      }
    }
    if (upserts > 0) {
      log.debug("Polled and upserted {} incident(s)", upserts);
    }
  }
}
