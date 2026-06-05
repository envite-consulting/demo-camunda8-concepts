package de.envite.bpm.incidents;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IncidentPoller {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentPoller.class);

  private final CamundaClient camundaClient;
  private final IncidentRecordRepository repository;

  public IncidentPoller(CamundaClient camundaClient, IncidentRecordRepository repository) {
    this.camundaClient = camundaClient;
    this.repository = repository;
  }

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
      LOG.warn("Incident polling failed: {}", e.getMessage());
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
        LOG.info(
            "Incident {} state transition: {} → {}",
            row.getIncidentKey(),
            previousState,
            row.getState());
      }
    }
    if (upserts > 0) {
      LOG.debug("Polled and upserted {} incident(s)", upserts);
    }
  }
}
