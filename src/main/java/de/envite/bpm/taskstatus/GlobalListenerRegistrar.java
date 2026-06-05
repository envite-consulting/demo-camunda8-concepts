package de.envite.bpm.taskstatus;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GlobalListenerRegistrar implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalListenerRegistrar.class);
  static final String LISTENER_ID = "task-status-tracker";
  static final String JOB_TYPE = "task-status-tracker";

  private final CamundaClient camundaClient;

  public GlobalListenerRegistrar(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

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
      LOG.info(
          "Registered global user task listener '{}' (jobType='{}', events=ALL)",
          LISTENER_ID,
          JOB_TYPE);
    } catch (Exception e) {
      if (isConflict(e)) {
        LOG.info(
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
