#!/usr/bin/env bash
# Inspect the USER_TASK_STATUS_EVENT table that the Global User Task Listener
# populates in the running Spring Boot app's H2 in-memory database.
#
# Usage:
#   ./check-h2.sh                 # default: dump all events
#   ./check-h2.sh count           # just row count
#   ./check-h2.sh latest          # latest 5 events
#   ./check-h2.sh "<your SQL>"    # run a one-shot custom query
#   ./check-h2.sh -i              # drop into an interactive H2 Shell
#
# Prereqs:
#   - Spring Boot app running (mvn spring-boot:run). The in-memory DB lives
#     inside that JVM; H2TcpServerConfig exposes it at
#     jdbc:h2:tcp://localhost:9092/mem:taskstatus.
#   - h2.jar in ~/.m2/repository (pulled in by Spring Boot's deps).

set -euo pipefail

JDBC_URL="jdbc:h2:tcp://localhost:9092/mem:taskstatus"
DB_USER="sa"

H2_JAR=$(ls -t "$HOME"/.m2/repository/com/h2database/h2/*/h2-*.jar 2>/dev/null \
  | grep -v -- '-sources.jar' | grep -v -- '-javadoc.jar' | head -1)
if [[ -z "${H2_JAR}" ]]; then
  echo "Could not find h2.jar under ~/.m2/repository." >&2
  echo "Run 'mvn package' (or 'mvn dependency:resolve') first." >&2
  exit 1
fi

if ! nc -z 127.0.0.1 9092 2>/dev/null; then
  echo "H2 TCP server not reachable at localhost:9092." >&2
  echo "Is the Spring Boot app running? Start it with: mvn spring-boot:run" >&2
  exit 1
fi

run_sql() {
  java -cp "$H2_JAR" org.h2.tools.Shell \
    -url "$JDBC_URL" -user "$DB_USER" -password "" -sql "$1"
}

ARG="${1:-}"
case "$ARG" in
  -i)
    exec java -cp "$H2_JAR" org.h2.tools.Shell \
      -url "$JDBC_URL" -user "$DB_USER" -password ""
    ;;
  count)
    run_sql "SELECT COUNT(*) AS event_count FROM user_task_status_event;"
    ;;
  latest)
    run_sql "SELECT id, user_task_key, process_instance_key, element_id, event_type, assignee, occurred_at FROM user_task_status_event ORDER BY id DESC LIMIT 5;"
    ;;
  alerts)
    run_sql "SELECT id, process_instance_id, flow_node_id, error_type, error_message, creation_time, received_at FROM incident_alert ORDER BY id;"
    ;;
  alert-count)
    run_sql "SELECT COUNT(*) AS alert_count FROM incident_alert;"
    ;;
  records)
    run_sql "SELECT incident_key, process_instance_key, element_id, error_type, state, error_message, creation_time, last_synced_at FROM incident_record ORDER BY creation_time;"
    ;;
  record-count)
    run_sql "SELECT COUNT(*) AS record_count FROM incident_record;"
    ;;
  "")
    run_sql "SELECT id, user_task_key, process_instance_key, element_id, event_type, assignee, occurred_at FROM user_task_status_event ORDER BY id;"
    ;;
  *)
    run_sql "$ARG"
    ;;
esac
