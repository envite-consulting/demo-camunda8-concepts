package de.envite.bpm.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "incident_alert")
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

  protected IncidentAlert() {}

  public Long getId() {
    return id;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(String clusterId) {
    this.clusterId = clusterId;
  }

  public String getOperateBaseUrl() {
    return operateBaseUrl;
  }

  public void setOperateBaseUrl(String operateBaseUrl) {
    this.operateBaseUrl = operateBaseUrl;
  }

  public String getClusterUrl() {
    return clusterUrl;
  }

  public void setClusterUrl(String clusterUrl) {
    this.clusterUrl = clusterUrl;
  }

  public String getOperateUrl() {
    return operateUrl;
  }

  public void setOperateUrl(String operateUrl) {
    this.operateUrl = operateUrl;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(Long jobKey) {
    this.jobKey = jobKey;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(String processName) {
    this.processName = processName;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public void setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
  }

  public String getProcessVersionTag() {
    return processVersionTag;
  }

  public void setProcessVersionTag(String processVersionTag) {
    this.processVersionTag = processVersionTag;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }

  @Override
  public String toString() {
    return "IncidentAlert{id="
        + id
        + ", processInstanceId='"
        + processInstanceId
        + "', flowNodeId='"
        + flowNodeId
        + "', errorType='"
        + errorType
        + "', errorMessage='"
        + errorMessage
        + "', creationTime="
        + creationTime
        + "}";
  }
}
