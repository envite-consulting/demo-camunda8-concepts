package de.envite.bpm.incident.webhook;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertWebhookController {

  private final IncidentAlertRepository repository;

  @PostMapping(path = "/incidents", consumes = "application/json")
  public ResponseEntity<Void> receive(@RequestBody AlertWebhookPayload payload) {
    List<AlertWebhookPayload.Alert> alerts =
        payload.alerts() == null ? List.of() : payload.alerts();
    Instant receivedAt = Instant.now();

    alerts.stream()
        .map(alert -> IncidentAlert.build(alert, payload, receivedAt))
        .map(repository::save)
        .forEach(saved -> log.info("Persisted incident alert: {}", saved));

    return ResponseEntity.noContent().build();
  }
}
