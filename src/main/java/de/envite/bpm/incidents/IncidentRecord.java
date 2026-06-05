package de.envite.bpm.incidents;

import io.camunda.client.api.search.response.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "incident_record")
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

  protected IncidentRecord() {}

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

  public Long getIncidentKey() {
    return incidentKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public String getErrorType() {
    return errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getElementId() {
    return elementId;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public String getState() {
    return state;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  @Override
  public String toString() {
    return "IncidentRecord{incidentKey="
        + incidentKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", elementId='"
        + elementId
        + "', errorType='"
        + errorType
        + "', state='"
        + state
        + "', lastSyncedAt="
        + lastSyncedAt
        + "}";
  }
}
