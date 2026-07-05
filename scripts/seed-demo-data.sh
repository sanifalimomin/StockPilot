#!/usr/bin/env bash
# =============================================================================
# seed-demo-data.sh — load 45 days of demand history into the DynamoDB
# movement ledger so the /forecast endpoint has real data to work with.
#
#   ./scripts/seed-demo-data.sh                # uses exported AWS creds
#   ./scripts/seed-demo-data.sh --days 60      # longer history
#   ./scripts/seed-demo-data.sh --table NAME   # non-default table
#
# Why direct DynamoDB writes instead of the API? The API stamps movements with
# the CURRENT time, so everything would land on one day and the forecast would
# see a single data point. Seeding the ledger directly lets us backdate
# OUTBOUND demand across many days — exactly what the EWMA/Bedrock forecast
# aggregates. Stock levels in RDS are untouched (forecasts read only the ledger).
#
# Demand patterns (per day, with noise + weekend dips):
#   SKU-1001  USB-C Cable      fast mover   ~28/day
#   SKU-1002  Wireless Mouse   steady       ~9/day
#   SKU-2001  A4 Paper Ream    seasonal-ish ~16/day
#   SKU-3001  Cordless Drill   slow mover   ~2/day
#
# Re-running adds ANOTHER batch of history (new movementIds), which doubles
# daily demand - run it once per environment.
# =============================================================================
set -euo pipefail

REGION="us-east-1"
TABLE="ims-dev-stock-movements"
DAYS=45

while [[ $# -gt 0 ]]; do
  case "$1" in
    --table)  TABLE="$2"; shift 2 ;;
    --days)   DAYS="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    -h|--help) grep '^#' "$0" | head -25; exit 0 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

command -v aws >/dev/null || { echo "aws CLI required"; exit 1; }
export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"

if [[ -z "${AWS_ACCESS_KEY_ID:-}" ]]; then
  echo "Export your Learner Lab credentials first (AWS_ACCESS_KEY_ID / SECRET / SESSION_TOKEN)."
  exit 1
fi
aws sts get-caller-identity >/dev/null || { echo "Credentials rejected/expired."; exit 1; }
aws dynamodb describe-table --table-name "$TABLE" >/dev/null \
  || { echo "Table '$TABLE' not found - deploy the stack first."; exit 1; }

RANDOM=42 # deterministic noise so repeated demos look the same

# sku|base_daily_qty|noise_spread|warehouseId
PATTERNS=(
  "SKU-1001|28|12|1"
  "SKU-1002|9|6|1"
  "SKU-2001|16|8|1"
  "SKU-3001|2|3|2"
)

ITEMS=()   # accumulated PutRequest JSON fragments
TOTAL=0

flush() {
  [[ ${#ITEMS[@]} -eq 0 ]] && return 0
  local joined
  joined="$(IFS=,; echo "${ITEMS[*]}")"
  local out
  out="$(aws dynamodb batch-write-item --request-items "{\"$TABLE\":[$joined]}" --output json)"
  if ! printf '%s' "$out" | grep -q '"UnprocessedItems": {}'; then
    echo "[warn] some items were not processed - re-run may be needed"
  fi
  ITEMS=()
}

add_item() { # sku ts type qty wh
  local id="seed-$2-$1-$RANDOM"
  ITEMS+=("{\"PutRequest\":{\"Item\":{\
\"sku\":{\"S\":\"$1\"},\
\"timestamp\":{\"S\":\"$2\"},\
\"movementId\":{\"S\":\"$id\"},\
\"type\":{\"S\":\"$3\"},\
\"qty\":{\"N\":\"$4\"},\
\"warehouseId\":{\"N\":\"$5\"},\
\"idempotencyKey\":{\"S\":\"$id\"}}}}")
  TOTAL=$((TOTAL + 1))
  [[ ${#ITEMS[@]} -ge 25 ]] && flush || true   # DynamoDB batch limit: 25
}

echo "Seeding $DAYS days of demand history into '$TABLE' ..."
for ((i = DAYS; i >= 1; i--)); do
  day="$(date -u -d "-$i days" +%Y-%m-%d)"
  dow="$(date -u -d "-$i days" +%u)" # 1=Mon .. 7=Sun

  for p in "${PATTERNS[@]}"; do
    IFS='|' read -r sku base spread wh <<< "$p"

    qty=$((base + RANDOM % (spread + 1) - spread / 2))
    [[ "$dow" -ge 6 ]] && qty=$((qty * 60 / 100)) # weekend dip
    ((qty <= 0)) && continue

    # spread each day's demand over 2 pseudo-orders at different hours
    half=$((qty / 2))
    rest=$((qty - half))
    ((half > 0)) && add_item "$sku" "${day}T10:$((RANDOM % 60 / 10))$((RANDOM % 10)):00Z" OUTBOUND "$half" "$wh"
    add_item "$sku" "${day}T15:$((RANDOM % 60 / 10))$((RANDOM % 10)):00Z" OUTBOUND "$rest" "$wh"

    # weekly INBOUND restock so the ledger looks like a real operation
    if [[ "$dow" -eq 1 ]]; then
      add_item "$sku" "${day}T08:00:00Z" INBOUND "$((base * 7))" "$wh"
    fi
  done
done
flush

echo
echo "Done: $TOTAL ledger entries written."
echo
echo "Try it:"
echo "  API : curl \"http://<alb-dns>/api/v1/forecast?sku=SKU-1001&days=30\""
echo "  UI  : Forecast page -> SKU-1001 (fast mover) or SKU-3001 (slow mover)"
echo "  Also: Movements page now shows the backdated history."
