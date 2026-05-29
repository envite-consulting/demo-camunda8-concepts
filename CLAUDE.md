# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot application that runs Camunda 8 (Zeebe) job workers for a "Process Order" BPMN process. It targets a **self-managed** Camunda 8 cluster — this is Camunda 8 (`io.camunda.client`, `@JobWorker`, Zeebe gRPC), not Camunda 7 and not SaaS.

## Commands

Java 21 is required (see `.sdkmanrc`; run `sdk env` to activate it via SDKMAN). There is no Maven wrapper — use the system `mvn`.

- Build: `mvn clean package`
- Run (starts the workers and connects to the cluster): `mvn spring-boot:run`
- Run all tests: `mvn test`
- Run a single test: `mvn test -Dtest=ClassName#methodName`
- Format code: `mvn spotless:apply` (verify only, no writes: `mvn spotless:check`)

No tests exist yet; `spring-boot-starter-test` is on the classpath for adding them.

Running the app requires a self-managed Camunda 8 cluster reachable at gRPC `127.0.0.1:26500` and REST `127.0.0.1:8080` (configured in `src/main/resources/application.properties`). Without a reachable cluster, the workers fail to connect on startup.

## Architecture

**Job worker ↔ BPMN task-type contract.** The process model is `src/main/resources/process.bpmn` (process id `demo-camunda-jboss-plugin-replacement-process`). Each `<serviceTask>` declares a `zeebe:taskDefinition type="..."`, and each worker method is annotated `@JobWorker(type = "...")`. These two `type` strings are the *only* link between the model and the code and must match exactly — changing a task type in one place requires the same change in the other.

| BPMN service task | type | Worker | Behavior |
|---|---|---|---|
| Check inventory | `check-inventory` | `CheckInventoryWorker` | reads nullable `item` variable (falls back to `default-item` when null/empty), returns `{item: "<item> allocated"}` |
| Charge payment method | `charge-payment` | `ChargePaymentWorker` | logs only |
| Ship items | `ship-items` | `ShipItemsWorker` | logs only |

The tasks run in sequence as the process advances: check-inventory → charge-payment → ship-items. A worker method that returns a `Map` writes those entries back as process variables; returning normally completes the job.

**Deployment.** The application does not deploy the BPMN itself (there is no `@Deployment` annotation anywhere). `process.bpmn` must be deployed to the cluster separately (e.g. via Camunda Modeler or the cluster REST API) before a process instance can be started.

**Stack.** Spring Boot 4.0.6 with `camunda-spring-boot-starter` (Camunda BOM 8.9.5). All code lives under the `de.envite.bpm` package.

## Tooling

- Code follows Google Java Format (2-space indentation). The Spotless Maven plugin enforces it via the `googleJavaFormat` formatter; it has no `<executions>` binding, so it does not run during `mvn package` — invoke `spotless:apply`/`spotless:check` manually. IntelliJ formatting is configured via `.idea/google-java-format.xml`.
- `.mcp.json` (project root) configures two MCP servers: **camunda** (the local cluster at `localhost:8080/mcp/cluster`, for inspecting and operating the running cluster) and **camunda docs** (Camunda 8 documentation reference).
