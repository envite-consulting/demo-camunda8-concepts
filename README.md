# demo-camunda-jboss-plugin-replacement

A Spring Boot 4 application that runs Camunda 8 (Zeebe) **job workers** for a small "Process Order" BPMN process, registers a **Global User Task Listener** at startup, and persists three streams of Camunda data into an in-memory **H2 database**:

| Table                    | Source                                                                | Mechanism                                                               |
|--------------------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------|
| `USER_TASK_STATUS_EVENT` | User-task lifecycle events (`creating`, `assigning`, `completing`, …) | Camunda 8.9 **Global User Task Listener** — push, job worker            |
| `INCIDENT_ALERT`         | Cluster-wide incident-creation alerts                                 | **Camunda Console Alerts Webhook** receiver at `POST /alerts/incidents` |
| `INCIDENT_RECORD`        | All incidents (with state transitions)                                | **Polling** the Orchestration Cluster REST search APIs                  |

The project targets a **self-managed** Camunda 8 cluster — `io.camunda.client`, `@JobWorker`, gRPC + REST. It is not Camunda 7 and not Camunda SaaS.

---

## BPMN process

`src/main/resources/process.bpmn`, id `demo-camunda-jboss-plugin-replacement-process`:

```
(Start)──▶[Approve order]──▶[Check inventory]──▶[Charge payment method]──▶[Ship items]──▶(End)
            user task           service task            service task          service task
```

| Element                                | Type            | Worker / handler                                                                                              |
|----------------------------------------|-----------------|---------------------------------------------------------------------------------------------------------------|
| `Activity_ApproveOrder`                | Zeebe user task | none — completed externally (via `c8ctl complete ut` or Tasklist)                                             |
| `Activity_0tw2fu0` (`check-inventory`) | service task    | `CheckInventoryWorker` — `item: "<x>" → "<x> allocated"`, or fail with retries=0 if `item == "FAIL_INCIDENT"` |
| `Activity_1ppsbgi` (`charge-payment`)  | service task    | `ChargePaymentWorker` — logs only                                                                             |
| `Activity_08pg6im` (`ship-items`)      | service task    | `ShipItemsWorker` — logs only                                                                                 |

The Global User Task Listener applies cluster-wide to **every** user task (currently just `Activity_ApproveOrder`). It is registered at app startup by `GlobalListenerRegistrar` (job type `task-status-tracker`, event types `ALL`) and handled by `TaskStatusTrackerWorker`.

---

## Requirements

| Tool         | Version      | Why                                                                           |
|--------------|--------------|-------------------------------------------------------------------------------|
| **Java**     | 21 (Temurin) | Project's `.sdkmanrc`; system `java` must resolve to 21.                      |
| **Maven**    | 3.9+         | No Maven wrapper.                                                             |
| **c8ctl**    | 3.x          | Local cluster + deploys + instance ops. Install via `npm i -g @camunda8/cli`. |
| **jq**       | any          | Parsing c8ctl JSON output and building the webhook payload.                   |
| **openssl**  | any          | HMAC signing in the webhook simulator.                                        |
| **curl, nc** | any          | Smoke tests and connectivity probes.                                          |

Activate Java 21 in the project directory:

```bash
sdk env          # reads .sdkmanrc
java -version    # should print "21.0.x"
```

---

## Quick start

From the project root, in three terminals (or a tmux split):

**1. Start the local Camunda 8 cluster** (Camunda 8 Run — Zeebe + Operate + Tasklist + Identity + Connectors; **no Console**):

```bash
c8ctl cluster start 8.9
until curl -sf http://127.0.0.1:8080/v2/topology > /dev/null; do sleep 2; done
c8ctl get topology      # should show a single broker at version 8.9.x
```

**2. Deploy the BPMN**:

```bash
c8ctl deploy src/main/resources/process.bpmn
c8ctl list pd           # confirm demo-camunda-jboss-plugin-replacement-process appears
```

**3. Build & run the workers** (in the project directory):

```bash
sdk env && mvn clean package -DskipTests && mvn spring-boot:run
```

You should see, in the Spring Boot log:

- `Tomcat started on port 8090` — the webhook receiver + the GET endpoint
- `HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:taskstatus` — the in-memory DB
- `Starting job worker: chargePaymentWorker#processPayment with type charge-payment` (and the other three workers)
- `Registered global user task listener 'task-status-tracker'` **or** `already exists; updating to ensure config matches` — the global listener is in place
- Every ~5 s, silent or `Polled and upserted N incident(s)` at DEBUG level — the incident poller is alive

**4. Drive a happy-path instance**:

```bash
PI=$(c8ctl --json create pi --bpmnProcessId demo-camunda-jboss-plugin-replacement-process \
       --variables '{"item":"book"}' 2>/dev/null | jq -r '.key')
UT=$(c8ctl --json list ut --state created 2>/dev/null \
       | jq -r '.[] | select(.["Process Instance"]=="'"$PI"'") | .Key' | head -1)
c8ctl complete ut "$UT"
c8ctl --json get pi "$PI" | jq '{state, endDate}'  # → "COMPLETED"
```

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=Process Order

# Camunda 8 self-managed cluster (c8run defaults)
camunda.client.mode=self-managed
camunda.client.grpc-address=http://127.0.0.1:26500
camunda.client.rest-address=http://127.0.0.1:8080

# H2 in-memory DB, kept alive even when all connections close
spring.datasource.url=jdbc:h2:mem:taskstatus;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop

# Spring Boot web container — must NOT collide with Camunda REST (8080)
server.port=8090

# Optional HMAC for the Console Alerts Webhook. Blank = no signature required.
app.alerts.webhook.hmac-secret=

# Incident poller cadence (ISO-8601 duration)
app.incidents.poll-interval=PT5S
```

Network ports in use when the app + cluster are running:

| Port  | Owner       | Purpose                                                                      |
|-------|-------------|------------------------------------------------------------------------------|
| 8080  | Camunda Run | Zeebe REST API + Operate + Tasklist UIs                                      |
| 26500 | Camunda Run | Zeebe gRPC                                                                   |
| 8090  | this app    | webhook receiver + `GET /api/process-instances/{piKey}/incidents`            |
| 9092  | this app    | H2 TCP server exposing the in-memory DB to IntelliJ Database / `check-h2.sh` |

---

## Verifying the Global User Task Listener sync

The listener fires on every user task lifecycle event and inserts a row into `USER_TASK_STATUS_EVENT`. The minimum demo is one process instance with one user task — the listener fires twice (`CREATING` when the token reaches the user task, `COMPLETING` when you complete it).

```bash
# 1. Start an instance and complete the user task.
PI=$(c8ctl --json create pi --bpmnProcessId demo-camunda-jboss-plugin-replacement-process \
       --variables '{"item":"book"}' 2>/dev/null | jq -r '.key')

# 2. Check H2 immediately after creation — should see one CREATING row.
./check-h2.sh

# 3. Complete the user task.
UT=$(c8ctl --json list ut --state created 2>/dev/null \
       | jq -r '.[] | select(.["Process Instance"]=="'"$PI"'") | .Key' | head -1)
c8ctl complete ut "$UT"

# 4. After ~1s, check H2 again — should now see two rows for this PI.
./check-h2.sh
```

**Pass condition**: ≥ 2 rows in `USER_TASK_STATUS_EVENT` for `process_instance_key = $PI`, with `event_type` values `CREATING` and `COMPLETING` and `element_id = Activity_ApproveOrder`. The Spring Boot log shows a matching pair of `Tracked user task event:` lines.

**Sanity check** that the listener is genuinely *global* and not BPMN-embedded:

```bash
grep -c "zeebe:taskListener" src/main/resources/process.bpmn      # → 0
```

The BPMN contains no `<zeebe:taskListener>` element. Capture happens because `GlobalListenerRegistrar.run(...)` calls `camundaClient.newCreateGlobalTaskListenerRequest()...send()` at startup; the cluster persists that registration and fires the listener job for every user task it sees.

---

## Verifying incident sync (two independent paths)

To produce an incident on demand, run a process instance with the sentinel `item = FAIL_INCIDENT`. `CheckInventoryWorker` then calls `jobClient.newFailCommand(job).retries(0).send()` and Camunda raises an immediate incident on the `check-inventory` job.

```bash
PI=$(c8ctl --json create pi --bpmnProcessId demo-camunda-jboss-plugin-replacement-process \
       --variables '{"item":"FAIL_INCIDENT"}' 2>/dev/null | jq -r '.key')
UT=$(c8ctl --json list ut --state created 2>/dev/null \
       | jq -r '.[] | select(.["Process Instance"]=="'"$PI"'") | .Key' | head -1)
c8ctl complete ut "$UT"          # token reaches check-inventory → incident
sleep 2
c8ctl search inc --processInstanceKey "$PI"   # confirm cluster recorded it
```

### Path A — pull (poller, REST search APIs)

`IncidentPoller` runs every `PT5S` (configurable). It calls `camundaClient.newIncidentSearchRequest()` (= `POST /v2/incidents/search`) and **upserts** each incident into `INCIDENT_RECORD` keyed by `incident_key`. State changes overwrite the row in place — the same row's `state` transitions `ACTIVE → RESOLVED` without inserting a new one.

```bash
# Wait for one poll cycle, then inspect the table.
sleep 7
./check-h2.sh records
./check-h2.sh record-count
```

**Pass condition**: a row with `incident_key` matching the cluster's incident key, `state = ACTIVE`, `error_type = JOB_NO_RETRIES`, error message containing `Simulated check-inventory failure`, and a recent `last_synced_at`.

**State-transition check** (the value-add over the webhook):

```bash
# Job needs retries > 0 before its incident can be resolved.
JOB=$(c8ctl --json search jobs --processInstanceKey "$PI" --type check-inventory 2>/dev/null \
       | jq -r '.[0].Key')
INC=$(c8ctl --json search inc --processInstanceKey "$PI" 2>/dev/null | jq -r '.[0].Key')
curl -sS -X PATCH http://127.0.0.1:8080/v2/jobs/$JOB \
  -H "Content-Type: application/json" -d '{"changeset":{"retries":3}}'
curl -sS -X POST http://127.0.0.1:8080/v2/incidents/$INC/resolution

sleep 7
./check-h2.sh "SELECT incident_key, state, last_synced_at FROM incident_record WHERE incident_key = $INC"
```

**Pass condition**: same `incident_key` row, but `state = RESOLVED` and `last_synced_at` is more recent than the prior check. The Spring Boot log shows a matching `Incident <key> state transition: ACTIVE → RESOLVED` line emitted by `IncidentPoller`.

`ProcessInstanceIncidentController` exposes the second REST search endpoint (`POST /v2/process-instances/{key}/incidents/search` via `camundaClient.newIncidentsByProcessInstanceSearchRequest(piKey)`) as a read-through:

```bash
curl -s http://localhost:8090/api/process-instances/$PI/incident | jq
```

**Pass condition**: HTTP 200 with a JSON array whose `incidentKey` matches the DB row. The endpoint goes straight to the cluster — it does not read H2 — so this also doubles as a check that the second REST API is reachable from the app.

### Path B — push (Camunda Console Alerts Webhook)

`AlertWebhookController` receives `POST /alerts/incidents` with the JSON payload that Camunda Console sends (documented at https://docs.camunda.io/docs/components/console/manage-clusters/manage-alerts/) and persists each alert in the `alerts[]` array as a row in `INCIDENT_ALERT`. Optional HMAC SHA-256 is validated against `X-Camunda-Signature-256` when `app.alerts.webhook.hmac-secret` is set.

**Caveat**: c8run does not bundle Console, so the real webhook does not fire against the local cluster. Two test paths cover the receiver:

#### B1 — Smoke test with a hand-crafted payload

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8090/alerts/incidents \
  -H "Content-Type: application/json" \
  -d '{"clusterName":"local-c8run","clusterId":"local",
       "operateBaseUrl":"http://127.0.0.1:8080/operate","clusterUrl":"http://127.0.0.1:8080",
       "alerts":[{"operateUrl":"http://127.0.0.1:8080/operate/#/instances/0",
                  "processInstanceId":"0","errorMessage":"smoke test","errorType":"SMOKE",
                  "flowNodeId":"x","jobKey":0,
                  "creationTime":"2026-06-05T08:00:00.000+0000",
                  "processName":"x","processVersion":1,"processVersionTag":null}]}'
./check-h2.sh alert-count    # should have grown by 1
./check-h2.sh alerts
```

Expect `HTTP 204` and one new row in `INCIDENT_ALERT` with `error_type=SMOKE`, `error_message=smoke test`.

#### B2 — Replay a *real* incident through the webhook simulator

`scripts/simulate-alert-webhook.sh` scrapes a real incident from the cluster (via `POST /v2/incidents/search`), reshapes it into the documented Console payload, and POSTs it to the receiver — so the receiver sees exactly the bytes Camunda Console would have sent.

```bash
# With the FAIL_INCIDENT instance from above still on the cluster:
scripts/simulate-alert-webhook.sh "$PI"
./check-h2.sh alerts
```

Expect `HTTP 204` and a new row in `INCIDENT_ALERT` whose `process_instance_id = $PI`, `flow_node_id = Activity_0tw2fu0`, `error_type = JOB_NO_RETRIES`, full `Simulated check-inventory failure...` message.

#### B3 — HMAC enforcement

```bash
# Restart the app with a secret:
pkill -f ProcessOrderApplication
APP_ALERTS_WEBHOOK_HMAC_SECRET='hunter2' mvn spring-boot:run &
# In another shell, once it's up:

# Unsigned   → 401
scripts/simulate-alert-webhook.sh "$PI"

# Correctly signed → 204
APP_ALERTS_WEBHOOK_HMAC_SECRET='hunter2' scripts/simulate-alert-webhook.sh "$PI"

# Wrong secret → 401
APP_ALERTS_WEBHOOK_HMAC_SECRET='wrong'   scripts/simulate-alert-webhook.sh "$PI"

# Garbage header → 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8090/alerts/incidents \
  -H "Content-Type: application/json" -H "X-Camunda-Signature-256: deadbeef" \
  -d '{"clusterName":"x","clusterId":"x","operateBaseUrl":"x","clusterUrl":"x","alerts":[]}'
```

`AlertWebhookController` reads `@RequestBody byte[] rawBody` so the HMAC is computed over the **exact bytes** Camunda signed (constant-time comparison via `MessageDigest.isEqual`).

### Independence of the two paths

Webhook rows land in `INCIDENT_ALERT`, poller rows land in `INCIDENT_RECORD`. They are not joined or merged; each table is the artifact of one mechanism. After a real incident, both paths populate their own table independently:

```bash
./check-h2.sh alert-count     # webhook
./check-h2.sh record-count    # poller
```

---

## Inspecting the H2 database

### Via the `check-h2.sh` script

The script connects over the H2 TCP server (started by `H2TcpServerConfig` on port 9092). The Spring Boot app must be running.

```bash
./check-h2.sh                # default: dump USER_TASK_STATUS_EVENT
./check-h2.sh count          # row count of USER_TASK_STATUS_EVENT
./check-h2.sh latest         # newest 5 user-task events

./check-h2.sh alerts         # dump INCIDENT_ALERT (webhook)
./check-h2.sh alert-count

./check-h2.sh records        # dump INCIDENT_RECORD (poller)
./check-h2.sh record-count

./check-h2.sh "SELECT … "    # arbitrary SQL
./check-h2.sh -i             # interactive H2 Shell
```

Under the hood: `java -cp <h2.jar> org.h2.tools.Shell -url jdbc:h2:tcp://localhost:9092/mem:taskstatus -user sa -password '' -sql "…"`.

### Via IntelliJ Database

A pre-configured data source is checked in at `.idea/dataSources.xml`. With the Spring Boot app running:

1. **View → Tool Windows → Database**. You should see *"H2 - Task Status (in-memory via TCP)"*.
2. First time: click *Download missing driver files* in the yellow banner; IntelliJ pulls the H2 driver from Maven Central.
3. *Test Connection* — green tick.
4. Expand → `TASKSTATUS` → `PUBLIC` → tables → `USER_TASK_STATUS_EVENT` / `INCIDENT_ALERT` / `INCIDENT_RECORD`. Double-click for a data view, or right-click → *Jump to Query Console*.

If the data source can't connect, the Spring Boot app isn't running (the H2 TCP server lives in that JVM) — start it and retry.

---

## Cleanup & troubleshooting

| Symptom                                                                                     | Cause                                                                                  | Fix                                                                                          |
|---------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `mvn spring-boot:run` fails with "port 8080 in use"                                         | Camunda Run already owns 8080; `server.port=8090` should prevent this.                 | Confirm `application.properties` still sets `server.port=8090`.                              |
| `Cluster status: starting or unresponsive` from `c8ctl cluster status`, but no Java process | Stale marker file from a previous crash.                                               | `c8ctl cluster stop` (it cleans up the marker), then `c8ctl cluster start 8.9`.              |
| `Could not find h2.jar under ~/.m2/repository` from `check-h2.sh`                           | First build hasn't run yet.                                                            | `mvn -B package -DskipTests`.                                                                |
| `H2 TCP server not reachable at localhost:9092` from `check-h2.sh`                          | Spring Boot app isn't running.                                                         | Start it.                                                                                    |
| `c8ctl resolve inc <key>` returns `INVALID_STATE … job has no retries left`                 | `FAIL_INCIDENT` left the job at retries=0.                                             | `PATCH /v2/jobs/{jobKey}` with `{"changeset":{"retries":3}}`, then resolve.                  |
| Process instance stuck after `FAIL_INCIDENT`                                                | The worker re-raises the incident every time the job re-activates.                     | `c8ctl cancel pi <piKey>` to dispose of the instance.                                        |
| H2 tables disappear between runs                                                            | `spring.jpa.hibernate.ddl-auto=create-drop` plus in-memory DB. Intentional for a demo. | If you want persistence, switch to `jdbc:h2:file:./data/taskstatus` and `create` / `update`. |

Stop everything:

```bash
pkill -f ProcessOrderApplication
c8ctl cluster stop
```

---

## Build & format

- Build: `mvn clean package`
- Run: `mvn spring-boot:run`
- Run a single test: `mvn test -Dtest=ClassName#methodName`
- Format Java sources (Google Java Format via Spotless): `mvn spotless:apply`
- Verify formatting without writes: `mvn spotless:check`

Spotless has no `<executions>` binding, so it does **not** run during `mvn package` — invoke it manually before committing.

Lombok is on the classpath as the annotation processor. IntelliJ needs the bundled **Lombok plugin** enabled (default since IntelliJ 2020.3).

---

## Project layout

```
src/main/
├── java/de/envite/bpm/
│   ├── ProcessOrderApplication.java        @SpringBootApplication + @EnableScheduling
│   ├── CheckInventoryWorker.java           @JobWorker("check-inventory") + FAIL_INCIDENT sentinel
│   ├── ChargePaymentWorker.java            @JobWorker("charge-payment") — logs only
│   ├── ShipItemsWorker.java                @JobWorker("ship-items")     — logs only
│   ├── taskstatus/
│   │   ├── GlobalListenerRegistrar.java    Registers the Global User Task Listener at startup
│   │   ├── TaskStatusTrackerWorker.java    @JobWorker("task-status-tracker") — persists to USER_TASK_STATUS_EVENT
│   │   ├── UserTaskStatusEvent.java        JPA entity
│   │   ├── UserTaskStatusEventRepository.java
│   │   └── H2TcpServerConfig.java          Starts the H2 TCP server on port 9092
│   └── incidents/
│       ├── AlertWebhookController.java     POST /alerts/incidents — webhook receiver + HMAC
│       ├── AlertWebhookPayload.java        DTO matching Camunda Console's documented JSON
│       ├── IncidentAlert.java              JPA entity for webhook rows
│       ├── IncidentAlertRepository.java
│       ├── IncidentPoller.java             @Scheduled poller → INCIDENT_RECORD
│       ├── IncidentRecord.java             JPA entity for polled rows (PK = incidentKey)
│       ├── IncidentRecordRepository.java
│       └── ProcessInstanceIncidentController.java   GET /api/process-instances/{piKey}/incidents
└── resources/
    ├── application.properties
    └── process.bpmn

scripts/
└── simulate-alert-webhook.sh   POST a real-incident-derived payload to the local receiver

check-h2.sh                     Query the H2 DB over its TCP server
.idea/dataSources.xml           Pre-configured IntelliJ Database data source
```
