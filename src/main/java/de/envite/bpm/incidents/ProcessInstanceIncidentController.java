package de.envite.bpm.incidents;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessInstanceIncidentController {

  private final CamundaClient camundaClient;

  public ProcessInstanceIncidentController(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

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

    static IncidentView from(Incident inc) {
      return new IncidentView(
          inc.getIncidentKey(),
          inc.getProcessDefinitionKey(),
          inc.getProcessDefinitionId(),
          inc.getProcessInstanceKey(),
          inc.getRootProcessInstanceKey(),
          inc.getErrorType() != null ? inc.getErrorType().name() : null,
          inc.getErrorMessage(),
          inc.getElementId(),
          inc.getElementInstanceKey(),
          inc.getCreationTime(),
          inc.getState() != null ? inc.getState().name() : null,
          inc.getJobKey(),
          inc.getTenantId());
    }
  }
}
