package de.envite.bpm.incidents;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProcessInstanceIncidentController {

  private final CamundaClient camundaClient;

  @GetMapping("/api/process-instances/{piKey}/incidents")
  public List<IncidentView> byProcessInstance(@PathVariable("piKey") long piKey) {
    return camundaClient
        .newIncidentsByProcessInstanceSearchRequest(piKey)
        .send()
        .join()
        .items()
        .stream()
        .map(IncidentView::from)
        .toList();
  }

  public record IncidentView(
      Long incidentKey,
      Long processDefinitionKey,
      String processDefinitionId,
      Long processInstanceKey,
      Long rootProcessInstanceKey,
      String errorType,
      String errorMessage,
      String elementId,
      Long elementInstanceKey,
      OffsetDateTime creationTime,
      String state,
      Long jobKey,
      String tenantId) {

    static IncidentView from(Incident incident) {
      return new IncidentView(
          incident.getIncidentKey(),
          incident.getProcessDefinitionKey(),
          incident.getProcessDefinitionId(),
          incident.getProcessInstanceKey(),
          incident.getRootProcessInstanceKey(),
          incident.getErrorType() != null ? incident.getErrorType().name() : null,
          incident.getErrorMessage(),
          incident.getElementId(),
          incident.getElementInstanceKey(),
          incident.getCreationTime(),
          incident.getState() != null ? incident.getState().name() : null,
          incident.getJobKey(),
          incident.getTenantId());
    }
  }
}
