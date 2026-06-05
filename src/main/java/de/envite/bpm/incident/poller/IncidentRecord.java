package de.envite.bpm.incident.poller;

import io.camunda.client.api.search.response.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "incident_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentRecord {

  @Id private Long incidentKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private String errorType;

  @Column(length = 4096)
  private String errorMessage;

  private String elementId;
  private Long elementInstanceKey;
  private OffsetDateTime creationTime;
  private String state;
  private Long jobKey;
  private String tenantId;
  private Instant lastSyncedAt;

  @Transient
  public void populateFrom(Incident inc, Instant syncedAt) {
    this.incidentKey = inc.getIncidentKey();
    this.processDefinitionKey = inc.getProcessDefinitionKey();
    this.processDefinitionId = inc.getProcessDefinitionId();
    this.processInstanceKey = inc.getProcessInstanceKey();
    this.rootProcessInstanceKey = inc.getRootProcessInstanceKey();
    this.errorType = inc.getErrorType() != null ? inc.getErrorType().name() : null;
    this.errorMessage = inc.getErrorMessage();
    this.elementId = inc.getElementId();
    this.elementInstanceKey = inc.getElementInstanceKey();
    this.creationTime = inc.getCreationTime();
    this.state = inc.getState() != null ? inc.getState().name() : null;
    this.jobKey = inc.getJobKey();
    this.tenantId = inc.getTenantId();
    this.lastSyncedAt = syncedAt;
  }
}
