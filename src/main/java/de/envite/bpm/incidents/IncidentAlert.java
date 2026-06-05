package de.envite.bpm.incidents;

import de.envite.bpm.incidents.AlertWebhookPayload.Alert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "incident_alert")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String clusterName;
  private String clusterId;

  @Column(length = 1024)
  private String operateBaseUrl;

  @Column(length = 1024)
  private String clusterUrl;

  @Column(length = 1024)
  private String operateUrl;

  private String processInstanceId;

  @Column(length = 4096)
  private String errorMessage;

  private String errorType;
  private String flowNodeId;
  private Long jobKey;
  private OffsetDateTime creationTime;
  private String processName;
  private Integer processVersion;
  private String processVersionTag;
  private Instant receivedAt;

  @Transient
  public static IncidentAlert build(Alert alert, AlertWebhookPayload payload, Instant receivedAt) {
    IncidentAlert incidentAlert = new IncidentAlert();
    incidentAlert.setClusterName(payload.clusterName());
    incidentAlert.setClusterId(payload.clusterId());
    incidentAlert.setOperateBaseUrl(payload.operateBaseUrl());
    incidentAlert.setClusterUrl(payload.clusterUrl());
    incidentAlert.setOperateUrl(alert.operateUrl());
    incidentAlert.setProcessInstanceId(alert.processInstanceId());
    incidentAlert.setErrorMessage(alert.errorMessage());
    incidentAlert.setErrorType(alert.errorType());
    incidentAlert.setFlowNodeId(alert.flowNodeId());
    incidentAlert.setJobKey(alert.jobKey());
    incidentAlert.setCreationTime(alert.creationTime());
    incidentAlert.setProcessName(alert.processName());
    incidentAlert.setProcessVersion(alert.processVersion());
    incidentAlert.setProcessVersionTag(alert.processVersionTag());
    incidentAlert.setReceivedAt(receivedAt);
    return incidentAlert;
  }
}
