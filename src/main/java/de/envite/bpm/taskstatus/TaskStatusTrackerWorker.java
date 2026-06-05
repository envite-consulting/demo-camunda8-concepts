package de.envite.bpm.taskstatus;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UserTaskProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusTrackerWorker {

  private static final Logger LOG = LoggerFactory.getLogger(TaskStatusTrackerWorker.class);

  private final UserTaskStatusEventRepository repository;

  public TaskStatusTrackerWorker(UserTaskStatusEventRepository repository) {
    this.repository = repository;
  }

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
    LOG.info("Tracked user task event: {}", saved);
  }
}
