package de.envite.bpm.taskstatus;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class UserTaskStatusEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userTaskKey;
  private Long processInstanceKey;
  private String elementId;
  private String eventType;
  private String assignee;
  private Instant occurredAt;

  protected UserTaskStatusEvent() {}

  public UserTaskStatusEvent(
      Long userTaskKey,
      Long processInstanceKey,
      String elementId,
      String eventType,
      String assignee,
      Instant occurredAt) {
    this.userTaskKey = userTaskKey;
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.eventType = eventType;
    this.assignee = assignee;
    this.occurredAt = occurredAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public String getElementId() {
    return elementId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAssignee() {
    return assignee;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  @Override
  public String toString() {
    return "UserTaskStatusEvent{id="
        + id
        + ", userTaskKey="
        + userTaskKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", elementId='"
        + elementId
        + "', eventType='"
        + eventType
        + "', assignee='"
        + assignee
        + "', occurredAt="
        + occurredAt
        + "}";
  }
}
