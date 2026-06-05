package de.envite.bpm.taskstatus;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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

  public UserTaskStatusEvent(
      Long userTaskKey,
      Long processInstanceKey,
      String elementId,
      String eventType,
      String assignee,
      Instant occurredAt) {
    this(null, userTaskKey, processInstanceKey, elementId, eventType, assignee, occurredAt);
  }
}
