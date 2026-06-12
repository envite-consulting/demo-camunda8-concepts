# demo-camunda-jboss-plugin-replacement

Spring Boot 4 demo for a **self-managed Camunda 8** cluster (`io.camunda.client`, gRPC + REST — not Camunda 7, not SaaS). It runs the job workers for a small "Process Order" BPMN process and mirrors Camunda runtime data into an in-memory H2 database:

| Problem                                                                     | Solution                                                        | Entry point                                                                                                                                                                                         | H2 table                 |
|-----------------------------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|
| Track user-task lifecycle events (`creating`, `assigning`, `completing`, …) | **Global User Task Listener** (Camunda 8.9, push)               | [`GlobalListenerRegistrar`](src/main/java/de/envite/bpm/taskstatus/GlobalListenerRegistrar.java) → [`TaskStatusTrackerWorker`](src/main/java/de/envite/bpm/taskstatus/TaskStatusTrackerWorker.java) | `USER_TASK_STATUS_EVENT` |
| Mirror incidents — **alternative A: push**                                  | **Console Alerts Webhook** receiver at `POST /alerts/incidents` | [`AlertWebhookController`](src/main/java/de/envite/bpm/incident/webhook/AlertWebhookController.java)                                                                                                | `INCIDENT_ALERT`         |
| Mirror incidents — **alternative B: pull**                                  | **Polling** the incident search REST API                        | [`IncidentPoller`](src/main/java/de/envite/bpm/incident/poller/IncidentPoller.java)                                                                                                                 | `INCIDENT_RECORD`        |

Rows 2 and 3 solve the same problem two ways. The paths are independent — each writes only its own table.

## BPMN process

`src/main/resources/process.bpmn`, id `demo-camunda-jboss-plugin-replacement-process`:

```
(Start)──▶[Approve order]──▶[Check inventory]──▶[Charge payment method]──▶[Ship items]──▶(End)
            user task           service task            service task          service task
```

| Element               | Type            | Worker / handler                                                                                              |
|-----------------------|-----------------|---------------------------------------------------------------------------------------------------------------|
| `approveOrder`        | Zeebe user task | none — completed externally (`c8ctl complete ut` or Tasklist)                                                 |
| `checkInventory`      | service task    | `CheckInventoryWorker` — `item: "<x>" → "<x> allocated"`, or fail with retries=0 if `item == "FAIL_INCIDENT"` |
| `chargePaymentMethod` | service task    | `ChargePaymentWorker` — logs only                                                                             |
| `shipItems`           | service task    | `ShipItemsWorker` — logs only                                                                                 |

The user task listener is **global**: the BPMN contains no `<zeebe:taskListener>`. `GlobalListenerRegistrar` registers it once at startup (job type `task-status-tracker`, event types `ALL`); the cluster then fires it for every user task.

## Requirements

| Tool         | Version      | Why                                                                           |
|--------------|--------------|-------------------------------------------------------------------------------|
| **Java**     | 21 (Temurin) | Per `.sdkmanrc` — activate with `sdk env`.                                    |
| **Maven**    | 3.9+         | No Maven wrapper.                                                             |
| **c8ctl**    | 3.x          | Local cluster + deploys + instance ops. Install via `npm i -g @camunda8/cli`. |
| **jq, curl** | any          | Command snippets below and `scripts/simulate-alert-webhook.sh`.               |

## Quick start

**1. Start the local cluster** (Camunda 8 Run — bundles Zeebe, Operate, Tasklist; **no Console**):

```bash
c8ctl cluster start
```

**2. Deploy the BPMN** (the app does not deploy it itself):

```bash
c8ctl deploy src/main/resources/process.bpmn
```

**3. Build & run the app:**

```bash
sdk env && mvn clean package && mvn spring-boot:run
```

Startup log checklist:

- `Tomcat started on port 8090`
- `HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:taskstatus`
- `Starting job worker: … type charge-payment` (plus the other three workers)
- `Registered global user task listener 'task-status-tracker'` (or `already exists; updating …`)

**4. Drive a happy-path instance** — this also exercises the user-task listener:

```bash
c8ctl --json create pi --bpmnProcessId demo-camunda-jboss-plugin-replacement-process --variables '{"item":"book"}'
# listener fired: one CREATING row for the started process instance
./check-h2.sh latest
c8ctl --json list ut --state created
# $UT is USER_TASK_KEY, workers run: check-inventory → charge-payment → ship-items
c8ctl complete ut "$UT"
# $PI is PROCESS_INSTANCE_KEY, state should be COMPLETED
c8ctl --json get pi "$PI"
# after ~1 s: CREATING + COMPLETING rows, element_id = approveOrder
./check-h2.sh latest
```

## Configuration

Key settings in `src/main/resources/application.properties`:

```properties
camunda.client.mode=self-managed
camunda.client.grpc-address=http://127.0.0.1:26500
camunda.client.rest-address=http://127.0.0.1:8080

# Spring web container — Camunda REST already owns 8080
server.port=8090

# incident poller cadence (ISO-8601 duration)
app.incidents.poll-interval=PT5S

spring.datasource.url=jdbc:h2:mem:taskstatus;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
```

Ports when app + cluster are running:

| Port  | Owner       | Purpose                                                                      |
|-------|-------------|------------------------------------------------------------------------------|
| 8080  | Camunda Run | Zeebe REST API + Operate + Tasklist UIs                                      |
| 26500 | Camunda Run | Zeebe gRPC                                                                   |
| 8090  | this app    | webhook receiver + `GET /api/process-instances/{piKey}/incidents`            |
| 9092  | this app    | H2 TCP server exposing the in-memory DB to IntelliJ Database / `check-h2.sh` |

## Incident sync — two alternatives

Produce an incident on demand with the sentinel `item = FAIL_INCIDENT`; `CheckInventoryWorker` then fails the job with retries=0:

```bash
c8ctl --json create pi --bpmnProcessId demo-camunda-jboss-plugin-replacement-process --variables '{"item":"FAIL_INCIDENT"}'
c8ctl --json list ut --state created
# token reaches check-inventory → incident
c8ctl complete ut "$UT"
c8ctl search inc --processInstanceKey "$PI"
```

### Alternative A: push (Console Alerts Webhook)

`AlertWebhookController` accepts the [documented Console alert payload](https://docs.camunda.io/docs/components/console/manage-clusters/manage-alerts/) at `POST /alerts/incidents` and writes each entry of `alerts[]` to `INCIDENT_ALERT`. c8run has no Console, so `scripts/simulate-alert-webhook.sh` replays a real cluster incident in exactly that payload shape:

```bash
./scripts/simulate-alert-webhook.sh "$PI"
./check-h2.sh alerts
```

**Pass**: `HTTP 204` and a row with `process_instance_id = $PI`, `flow_node_id = checkInventory`, `error_type = JOB_NO_RETRIES`.

### Alternative B: pull (REST polling)

`IncidentPoller` calls the incident search API (`POST /v2/incidents/search`) every `PT5S` and **upserts** into `INCIDENT_RECORD` keyed by `incident_key`:

```bash
./check-h2.sh records
```

**Pass**: a row with the cluster's incident key, `state = ACTIVE`, `error_type = JOB_NO_RETRIES`.

The upsert is the value-add over the webhook: state changes update the row in place. Give the job retries, resolve the incident, and the same row flips to `RESOLVED`:

```bash
c8ctl --json search jobs --processInstanceKey "$PI" --type check-inventory
c8ctl --json search inc --processInstanceKey "$PI"
# $JOB is JOB_KEY from first instruction
curl -sS -X PATCH http://127.0.0.1:8080/v2/jobs/$JOB -H "Content-Type: application/json" -d '{"changeset":{"retries":3}}'
# $INC is INCIDENT_KEY from second instruction
curl -sS -X POST http://127.0.0.1:8080/v2/incidents/$INC/resolution
# same incident_key, state = RESOLVED
./check-h2.sh records
```

`ProcessInstanceIncidentController` additionally exposes the per-instance search (`POST /v2/process-instances/{key}/incidents/search`) as a read-through — it queries the cluster, not H2:

```bash
curl -s http://localhost:8090/api/process-instances/$PI/incidents
```

## Inspecting the H2 database

`check-h2.sh` connects through the H2 TCP server on port 9092 (started by `H2TcpServerConfig`); the Spring Boot app must be running.

```bash
./check-h2.sh                # dump USER_TASK_STATUS_EVENT
./check-h2.sh latest         # newest 5 user-task events
./check-h2.sh count
./check-h2.sh alerts         # INCIDENT_ALERT (webhook)
./check-h2.sh alert-count
./check-h2.sh records        # INCIDENT_RECORD (poller)
./check-h2.sh record-count
./check-h2.sh "SELECT … "    # arbitrary SQL
./check-h2.sh -i             # interactive H2 shell
```

**IntelliJ**: the data source *"H2 - Task Status (in-memory via TCP)"* is checked in (`.idea/dataSources.xml`). Open **View → Tool Windows → Database**, download the driver on first use, *Test Connection*, then browse `TASKSTATUS → PUBLIC` for the three tables.

## Troubleshooting

| Symptom                                                                                     | Cause                                                                                  | Fix                                                                                          |
|---------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `mvn spring-boot:run` fails with "port 8080 in use"                                         | Camunda Run already owns 8080.                                                         | Confirm `application.properties` still sets `server.port=8090`.                              |
| `Cluster status: starting or unresponsive` from `c8ctl cluster status`, but no Java process | Stale marker file from a previous crash.                                               | `c8ctl cluster stop`, then `c8ctl cluster start 8.9`.                                        |
| `Could not find h2.jar under ~/.m2/repository` from `check-h2.sh`                           | First build hasn't run yet.                                                            | `mvn -B package -DskipTests`.                                                                |
| `H2 TCP server not reachable at localhost:9092` from `check-h2.sh`                          | Spring Boot app isn't running.                                                         | Start it.                                                                                    |
| `c8ctl resolve inc <key>` returns `INVALID_STATE … job has no retries left`                 | `FAIL_INCIDENT` left the job at retries=0.                                             | `PATCH /v2/jobs/{jobKey}` with `{"changeset":{"retries":3}}`, then resolve.                  |
| Process instance stuck after `FAIL_INCIDENT`                                                | The worker re-raises the incident every time the job re-activates.                     | `c8ctl cancel pi <piKey>`.                                                                   |
| H2 tables disappear between runs                                                            | `create-drop` + in-memory DB; intentional for a demo.                                  | For persistence switch to `jdbc:h2:file:./data/taskstatus` and `create` / `update`.          |

Stop everything:

```bash
pkill -f ProcessOrderApplication
c8ctl cluster stop
```

## Build & format

- Build: `mvn clean package` — run: `mvn spring-boot:run` — single test: `mvn test -Dtest=ClassName#methodName`
- Format (Google Java Format via Spotless): `mvn spotless:apply`, verify with `mvn spotless:check`. Not bound to `mvn package` — run manually before committing.
- Lombok is the annotation processor; IntelliJ needs its bundled Lombok plugin enabled.

## Project layout

```
src/main/
├── java/de/envite/bpm/
│   ├── ProcessOrderApplication.java        @SpringBootApplication + @EnableScheduling
│   ├── CheckInventoryWorker.java           @JobWorker("check-inventory") + FAIL_INCIDENT sentinel
│   ├── ChargePaymentWorker.java            @JobWorker("charge-payment") — logs only
│   ├── ShipItemsWorker.java                @JobWorker("ship-items")     — logs only
│   ├── taskstatus/
│   │   ├── GlobalListenerRegistrar.java    registers the Global User Task Listener at startup
│   │   ├── TaskStatusTrackerWorker.java    @JobWorker("task-status-tracker") → USER_TASK_STATUS_EVENT
│   │   ├── UserTaskStatusEvent.java        JPA entity (+ repository)
│   │   └── H2TcpServerConfig.java          H2 TCP server on port 9092
│   └── incident/
│       ├── webhook/
│       │   ├── AlertWebhookController.java POST /alerts/incidents
│       │   ├── AlertWebhookPayload.java    DTO of the documented Console JSON
│       │   └── IncidentAlert.java          JPA entity (+ repository)
│       └── poller/
│           ├── IncidentPoller.java         @Scheduled upsert → INCIDENT_RECORD
│           ├── IncidentRecord.java         JPA entity, PK = incidentKey (+ repository)
│           └── ProcessInstanceIncidentController.java  GET /api/process-instances/{piKey}/incidents
└── resources/
    ├── application.properties
    └── process.bpmn

scripts/simulate-alert-webhook.sh   replay a real incident as a Console webhook call
check-h2.sh                         query the H2 DB over its TCP server
.idea/dataSources.xml               pre-configured IntelliJ Database data source
```
