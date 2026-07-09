#!/usr/bin/env bash
#
# demo-agents.sh — end-to-end smoke of the multi-agent AI platform against a running backend.
#
#   Logs in, picks a project, lists the agents, runs a "sweep" (every agent), polls the runs to
#   completion, then prints the findings the agents produced. Read-only except for the agent runs,
#   which only write to the ai.* schema.
#
# Prereqs: backend on :8080 (see CLAUDE.md "Running the full stack locally"), an LLM provider
# configured for narration (optional — agents fall back to templated narratives), and `jq`.
#
# Usage:  ./scripts/demo-agents.sh [BASE_URL] [USERNAME] [PASSWORD]
#
set -euo pipefail

BASE="${1:-${BIPROS_API:-http://localhost:8080}}"
USER="${2:-admin}"
PASS="${3:-admin123}"

command -v jq >/dev/null || { echo "jq is required (brew install jq)"; exit 1; }

say() { printf '\n\033[1;33m▸ %s\033[0m\n' "$*"; }
api() { # api METHOD PATH  → prints response body; auth header attached after login
  local method="$1" path="$2"
  curl -fsS -X "$method" "$BASE$path" \
    -H "Content-Type: application/json" \
    ${TOKEN:+-H "Authorization: Bearer $TOKEN"}
}

say "Logging in as $USER"
TOKEN="$(curl -fsS -X POST "$BASE/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq -r '.data.accessToken')"
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "login failed"; exit 1; }
echo "  ok"

say "Picking a project"
PID="$(api GET "/v1/projects?page=0&size=1" | jq -r '.data.content[0].id')"
PNAME="$(api GET "/v1/projects/$PID" | jq -r '.data.name')"
[ -n "$PID" ] && [ "$PID" != "null" ] || { echo "no projects found"; exit 1; }
echo "  $PNAME ($PID)"

say "Registered agents"
api GET "/v1/projects/$PID/agents" \
  | jq -r '.data[] | "  \(.key)\t\(.displayName)\tlast: \(.lastRun.status // "—")"'

say "Running a sweep — firing every agent"
AGENT_KEYS="$(api GET "/v1/projects/$PID/agents" | jq -r '.data[].key')"
RUN_IDS=()
for key in $AGENT_KEYS; do
  rid="$(api POST "/v1/projects/$PID/agents/$key/run" | jq -r '.data.runId')"
  echo "  $key → run $rid"
  [ "$rid" != "null" ] && RUN_IDS+=("$rid")
done

say "Waiting for runs to finish"
for rid in "${RUN_IDS[@]}"; do
  for _ in $(seq 1 30); do
    status="$(api GET "/v1/agent-runs/$rid" | jq -r '.data.run.status')"
    case "$status" in
      SUCCEEDED|FAILED|SKIPPED_NO_CHANGE) break ;;
      *) sleep 1 ;;
    esac
  done
  n="$(api GET "/v1/agent-runs/$rid" | jq -r '.data.findings | length')"
  echo "  run $rid → $status ($n findings)"
done

say "Active findings (top of the board)"
api GET "/v1/projects/$PID/agent-findings?status=ACTIVE&size=20" \
  | jq -r '.data.content[] | "  [\(.severity)] \(.agentKey): \(.title)  (conf \(.confidence), \(.confidenceBasis))"'

say "Done. Open the AI Overview in the app: /projects/$PID/ai"
