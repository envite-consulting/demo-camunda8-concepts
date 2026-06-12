#!/usr/bin/env bash
# Simulate the Camunda Console Alerts Webhook against the local app.
#
# c8run does not bundle Console, so the real webhook can't fire here. This
# script scrapes incident details from the local cluster via the Orchestration
# Cluster REST API (POST /v2/incidents/search) and POSTs them in the documented
# Console schema to the local AlertWebhookController.
#
# Usage:
#   scripts/simulate-alert-webhook.sh                  # all incidents in cluster
#   scripts/simulate-alert-webhook.sh <piKey>          # incidents for one PI
#
# Env vars (optional):
#   RECEIVER_URL                    default http://localhost:8090/alerts/incidents
#   CLUSTER_REST                    default http://127.0.0.1:8080

set -euo pipefail

PI_KEY="${1:-}"
RECEIVER="${RECEIVER_URL:-http://localhost:8090/alerts/incidents}"
CLUSTER_REST="${CLUSTER_REST:-http://127.0.0.1:8080}"

for bin in jq curl; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "Required tool not found: $bin" >&2
    exit 1
  fi
done

if [[ -n "$PI_KEY" ]]; then
  FILTER=$(jq -nc --arg pi "$PI_KEY" '{filter:{processInstanceKey:$pi}}')
else
  FILTER='{}'
fi

INCIDENTS=$(curl -sS -X POST "$CLUSTER_REST/v2/incidents/search" \
  -H "Content-Type: application/json" -d "$FILTER")

COUNT=$(echo "$INCIDENTS" | jq '.items | length')
if [[ "$COUNT" == "0" ]]; then
  echo "No incidents found in cluster${PI_KEY:+ for processInstanceKey=$PI_KEY}." >&2
  exit 2
fi
echo "Found $COUNT incident(s) in cluster; building webhook payload..." >&2

PAYLOAD=$(echo "$INCIDENTS" | jq -c '{
  clusterName: "local-c8run",
  clusterId: "local",
  operateBaseUrl: "http://localhost:8080/operate",
  clusterUrl: "http://localhost:8080",
  alerts: [.items[] | {
    operateUrl: ("http://localhost:8080/operate/#/instances/" + (.processInstanceKey|tostring)),
    processInstanceId: (.processInstanceKey|tostring),
    errorMessage: .errorMessage,
    errorType: .errorType,
    flowNodeId: .elementId,
    jobKey: ((.jobKey // 0) | tonumber),
    creationTime: .creationTime,
    processName: .processDefinitionId,
    processVersion: 1,
    processVersionTag: null
  }]
}')

echo "POST $RECEIVER" >&2
curl -sS -o /dev/null -w "HTTP %{http_code}\n" -X POST "$RECEIVER" \
  -H "Content-Type: application/json" \
  --data-binary "$PAYLOAD"
