package de.envite.bpm.taskstatus;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalListenerRegistrar implements ApplicationRunner {

  private static final String LISTENER_ID = "task-status-tracker";
  private static final String JOB_TYPE = "task-status-tracker";

  private final CamundaClient camundaClient;

  @Override
  public void run(ApplicationArguments args) {
    try {
      camundaClient
          .newCreateGlobalTaskListenerRequest()
          .id(LISTENER_ID)
          .type(JOB_TYPE)
          .eventTypes(GlobalTaskListenerEventType.ALL)
          .send()
          .join();
      log.info(
          "Registered global user task listener '{}' (jobType='{}', events=ALL)",
          LISTENER_ID,
          JOB_TYPE);
    } catch (Exception e) {
      if (isConflict(e)) {
        log.info(
            "Global user task listener '{}' already exists; updating to ensure config matches",
            LISTENER_ID);
        camundaClient
            .newUpdateGlobalTaskListenerRequest(LISTENER_ID)
            .type(JOB_TYPE)
            .eventTypes(GlobalTaskListenerEventType.ALL)
            .send()
            .join();
      } else {
        throw e;
      }
    }
  }

  private static boolean isConflict(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      String msg = t.getMessage();
      if (msg != null
          && (msg.contains("409")
              || msg.toLowerCase().contains("conflict")
              || msg.toLowerCase().contains("already exists"))) {
        return true;
      }
    }
    return false;
  }
}
