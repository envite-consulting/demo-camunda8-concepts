package de.envite.bpm.incident.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertWebhookPayload(
    String clusterName,
    String clusterId,
    String operateBaseUrl,
    String clusterUrl,
    List<Alert> alerts) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Alert(
      String operateUrl,
      String processInstanceId,
      String errorMessage,
      String errorType,
      String flowNodeId,
      Long jobKey,
      OffsetDateTime creationTime,
      String processName,
      Integer processVersion,
      String processVersionTag) {}
}
