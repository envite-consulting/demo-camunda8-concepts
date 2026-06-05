package de.envite.bpm.taskstatus;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UserTaskProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskStatusTrackerWorker {

  private final UserTaskStatusEventRepository repository;

  @JobWorker(type = "task-status-tracker", autoComplete = true)
  public void track(final ActivatedJob job) {
    UserTaskProperties userTask = job.getUserTask();
    String listenerEventType =
        job.getListenerEventType() != null ? job.getListenerEventType().name() : "UNKNOWN";

    UserTaskStatusEvent event =
        new UserTaskStatusEvent(
            userTask != null ? userTask.getUserTaskKey() : null,
            job.getProcessInstanceKey(),
            job.getElementId(),
            listenerEventType,
            userTask != null ? userTask.getAssignee() : null,
            Instant.now());

    UserTaskStatusEvent saved = repository.save(event);
    log.info("Tracked user task event: {}", saved);
  }
}
