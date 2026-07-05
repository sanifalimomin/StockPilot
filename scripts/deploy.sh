#!/usr/bin/env bash
# deploy.sh — one-shot deployment of the IMS stack to an AWS Academy Learner Lab.
#
#   ./scripts/deploy.sh                         # interactive: paste lab creds
#   ./scripts/deploy.sh --alert-email me@x.com  # subscribe SNS alerts
#   ./scripts/deploy.sh --enable-waf            # try WAF (lab SCP may deny it)
#   ./scripts/deploy.sh --skip-build            # infra + frontend only
#   ./scripts/deploy.sh --skip-frontend         # infra + image only
#   ./scripts/deploy.sh --destroy               # tear everything down
#   ./scripts/deploy.sh --redeploy              # destroy, then rebuild from scratch
#
# Needs: bash, aws CLI v2, terraform >= 1.6, docker (buildx), node 20+, curl.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$ROOT/infra"
REGION="us-east-1"

ALERT_EMAIL=""
ENABLE_WAF="false"
SKIP_BUILD="false"
SKIP_FRONTEND="false"
DESTROY="false"
REDEPLOY="false"
ASSUME_YES="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --alert-email)   ALERT_EMAIL="$2"; shift 2 ;;
    --enable-waf)    ENABLE_WAF="true"; shift ;;
    --skip-build)    SKIP_BUILD="true"; shift ;;
    --skip-frontend) SKIP_FRONTEND="true"; shift ;;
    --destroy)       DESTROY="true"; shift ;;
    --redeploy)      DESTROY="true"; REDEPLOY="true"; shift ;;
    --yes|-y)        ASSUME_YES="true"; shift ;;
    -h|--help)       grep '^#' "$0" | head -12; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)"; exit 1 ;;
  esac
done

[[ "$REDEPLOY" == "true" && "$SKIP_BUILD" == "true" ]] \
  && { echo "[warn] --redeploy with --skip-build leaves the fresh ECR repo empty; services will not start."; }

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" || -z "${AWS_SESSION_TOKEN:-}" ]]; then
  echo "Paste the credentials block from AWS Academy (AWS Details -> AWS CLI -> Show)."
  echo "Finish with an empty line:"
  BLOCK=""
  while IFS= read -r line; do
    [[ -z "$line" && -n "$BLOCK" ]] && break
    BLOCK+="$line"$'\n'
  done
  extract() { printf '%s' "$BLOCK" | sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" | tail -1 | tr -d '[:space:]'; }
  AWS_ACCESS_KEY_ID="$(extract aws_access_key_id)"
  AWS_SECRET_ACCESS_KEY="$(extract aws_secret_access_key)"
  AWS_SESSION_TOKEN="$(extract aws_session_token)"
  [[ -n "$AWS_ACCESS_KEY_ID" && -n "$AWS_SECRET_ACCESS_KEY" && -n "$AWS_SESSION_TOKEN" ]] \
    || die "Could not parse all three credentials from the pasted block."
fi
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"

for tool in aws terraform curl; do
  command -v "$tool" >/dev/null || die "'$tool' is required but not on PATH."
done
if [[ "$SKIP_BUILD" != "true" || "$DESTROY" == "true" ]]; then
  command -v docker >/dev/null || die "'docker' is required (or pass --skip-build)."
fi
if [[ "$SKIP_FRONTEND" != "true" && "$DESTROY" != "true" ]]; then
  command -v npm >/dev/null || die "'npm' is required (or pass --skip-frontend)."
fi

log "Verifying credentials"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)" \
  || die "Credentials rejected. Lab sessions expire after ~4h - grab fresh ones."
echo "Account: $ACCOUNT_ID  Region: $REGION"

STATE_BUCKET="ims-tf-state-${ACCOUNT_ID}"
LOCK_TABLE="ims-tf-locks"

tf_init_main() {
  terraform -chdir="$INFRA" init -reconfigure -input=false \
    -backend-config="bucket=$STATE_BUCKET" \
    -backend-config="region=$REGION" \
    -backend-config="dynamodb_table=$LOCK_TABLE" >/dev/null
}

purge_bucket() {
  local bucket="$1"
  aws s3api head-bucket --bucket "$bucket" 2>/dev/null || return 0
  echo "Emptying s3://$bucket ..."
  aws s3 rm "s3://$bucket" --recursive --quiet || true
  while :; do
    local versions
    versions="$(aws s3api list-object-versions --bucket "$bucket" --max-keys 200 \
      --query '[Versions[].{Key:Key,VersionId:VersionId},DeleteMarkers[].{Key:Key,VersionId:VersionId}][]' \
      --output json 2>/dev/null || echo '[]')"
    [[ -z "$versions" || "$versions" == "[]" || "$versions" == "null" ]] && break
    aws s3api delete-objects --bucket "$bucket" \
      --delete "{\"Objects\":$versions,\"Quiet\":true}" >/dev/null
  done
}

if [[ "$DESTROY" == "true" ]]; then
  if aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null; then
    if [[ "$ASSUME_YES" != "true" ]]; then
      [[ "$REDEPLOY" == "true" ]] && ACTION="DESTROY and RE-CREATE" || ACTION="DESTROY"
      read -r -p "This will $ACTION the entire IMS stack in account $ACCOUNT_ID. Type 'destroy' to continue: " ans
      [[ "$ans" == "destroy" ]] || die "Aborted."
    fi
    tf_init_main

    log "Emptying app buckets so Terraform can delete them"
    purge_bucket "$(terraform -chdir="$INFRA" output -raw reports_bucket 2>/dev/null || echo "ims-dev-reports-$ACCOUNT_ID")"
    purge_bucket "$(terraform -chdir="$INFRA" output -raw frontend_bucket 2>/dev/null || echo "ims-dev-frontend-$ACCOUNT_ID")"

    log "Purging ECR images"
    ECR_REPO="ims-dev-app"
    IDS="$(aws ecr list-images --repository-name "$ECR_REPO" --query 'imageIds[*]' --output json 2>/dev/null || echo '[]')"
    if [[ "$IDS" != "[]" && -n "$IDS" && "$IDS" != "null" ]]; then
      aws ecr batch-delete-image --repository-name "$ECR_REPO" --image-ids "$IDS" >/dev/null || true
    fi

    log "terraform destroy (10-20 min: RDS/Redis teardown is slow)"
    terraform -chdir="$INFRA" destroy -auto-approve

    if [[ "$REDEPLOY" != "true" ]]; then
      echo
      echo "Main stack destroyed. The bootstrap state bucket ($STATE_BUCKET) and lock"
      echo "table are left in place; the Learner Lab wipes them when the lab resets."
      exit 0
    fi
    log "Destroy complete - re-creating the whole stack from scratch"
  else
    [[ "$REDEPLOY" == "true" ]] \
      || die "State bucket $STATE_BUCKET not found - nothing to destroy in this account."
    warn "No existing stack in this account - proceeding straight to a fresh deployment."
  fi
fi

log "Checking Terraform state backend"
BUCKET_OK=false; TABLE_OK=false
aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null && BUCKET_OK=true
aws dynamodb describe-table --table-name "$LOCK_TABLE" >/dev/null 2>&1 && TABLE_OK=true

if [[ "$BUCKET_OK" == "true" && "$TABLE_OK" == "true" ]]; then
  echo "Backend already exists ($STATE_BUCKET) - skipping bootstrap."
else
  # stale local state from a previous Academy account must be moved aside
  BOOT_STATE="$INFRA/bootstrap/terraform.tfstate"
  if [[ -f "$BOOT_STATE" ]] && ! grep -q "$STATE_BUCKET" "$BOOT_STATE"; then
    warn "bootstrap state is from another account - backing it up"
    mv "$BOOT_STATE" "$BOOT_STATE.bak.$(date +%Y%m%d%H%M%S)"
  fi
  log "Bootstrapping state backend ($STATE_BUCKET + $LOCK_TABLE)"
  terraform -chdir="$INFRA/bootstrap" init -input=false >/dev/null
  terraform -chdir="$INFRA/bootstrap" apply -auto-approve -input=false \
    -var "state_bucket_name=$STATE_BUCKET" -var "lock_table_name=$LOCK_TABLE"
fi

TFVARS="$INFRA/terraform.tfvars"
if [[ ! -f "$TFVARS" ]]; then
  log "Generating $TFVARS"
  if command -v openssl >/dev/null; then
    DB_PASSWORD="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 20)"
  else
    DB_PASSWORD="$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20)"
  fi
  cat > "$TFVARS" <<EOF
# Generated by scripts/deploy.sh on $(date -u +%Y-%m-%dT%H:%M:%SZ) - gitignored.
project_name = "ims"
environment  = "dev"
region       = "$REGION"

container_image = ""

db_name     = "ims"
db_username = "ims_admin"
db_password = "$DB_PASSWORD"

db_multi_az              = false
db_backup_retention_days = 7
redis_multi_az           = false

api_desired_count    = 2
api_min_count        = 2
api_max_count        = 10
worker_desired_count = 1

alert_email = "$ALERT_EMAIL"

# Optional AI forecast keys (Gemini wins; both empty = free EWMA forecast).
gemini_api_key    = ""
anthropic_api_key = ""

enable_waf     = $ENABLE_WAF
waf_rate_limit = 2000
EOF
  echo "DB password generated and stored only in the gitignored tfvars file."
else
  echo "Using existing $TFVARS."
  [[ -n "$ALERT_EMAIL" ]] && warn "--alert-email ignored: tfvars already exists; edit it manually."
fi

log "terraform init (S3 backend: $STATE_BUCKET)"
tf_init_main
log "terraform apply (first run takes 15-25 min: RDS + ElastiCache are slow)"
terraform -chdir="$INFRA" apply -auto-approve -input=false

tfout() { terraform -chdir="$INFRA" output -raw "$1"; }
ECR_URL="$(tfout ecr_repo_url)"
ALB_DNS="$(tfout alb_dns_name)"
FRONTEND_BUCKET="$(tfout frontend_bucket)"
FRONTEND_URL="$(tfout frontend_website_url)"
CLUSTER="$(tfout ecs_cluster_name)"
PREFIX="${CLUSTER%-cluster}"

if [[ "$SKIP_BUILD" != "true" ]]; then
  log "Logging in to ECR"
  aws ecr get-login-password --region "$REGION" \
    | docker login --username AWS --password-stdin "${ECR_URL%/*}"

  log "Building + pushing linux/arm64 image (Graviton)"
  docker buildx build --platform linux/arm64 -t "$ECR_URL:latest" --push "$ROOT/app"

  log "Rolling the api + worker services"
  aws ecs update-service --cluster "$CLUSTER" --service "$PREFIX-api"    --force-new-deployment >/dev/null
  aws ecs update-service --cluster "$CLUSTER" --service "$PREFIX-worker" --force-new-deployment >/dev/null
else
  warn "--skip-build: not building/pushing the image."
fi

if [[ "$SKIP_FRONTEND" != "true" ]]; then
  log "Building the frontend against http://$ALB_DNS"
  (
    cd "$ROOT/frontend"
    # npm ci wipes node_modules and fails with EPERM if a Vite dev server runs
    if [[ -f node_modules/.package-lock.json && node_modules/.package-lock.json -nt package-lock.json ]]; then
      echo "Dependencies up to date - skipping npm ci."
    else
      echo "Installing dependencies (npm ci) - this can take a few minutes..."
      npm ci --no-audit --no-fund || {
        warn "npm ci failed. If the error above says EPERM/EBUSY on esbuild.exe,"
        warn "stop any running 'npm run dev' (Vite) session and re-run this script."
        exit 1
      }
    fi
    VITE_API_BASE_URL="http://$ALB_DNS" npm run build
  )
  log "Syncing frontend to s3://$FRONTEND_BUCKET"
  aws s3 sync "$ROOT/frontend/dist" "s3://$FRONTEND_BUCKET" --delete
else
  warn "--skip-frontend: not building/deploying the frontend."
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  log "Waiting for the API to become healthy (image pull + Spring Boot startup)"
  HEALTH="http://$ALB_DNS/api/v1/health"
  for i in $(seq 1 40); do
    if curl -sf --max-time 5 "$HEALTH" >/dev/null 2>&1; then
      echo "API is UP: $(curl -sf --max-time 5 "$HEALTH")"
      break
    fi
    [[ "$i" == "40" ]] && warn "API not healthy after ~10 min - check ECS events: aws ecs describe-services --cluster $CLUSTER --services $PREFIX-api"
    printf '.'; sleep 15
  done
fi

log "Deployment complete"
cat <<EOF
  Dashboard     CloudWatch -> Dashboards -> $PREFIX-dashboard
  Reorder cron  EventBridge rule '$PREFIX-reorder-schedule' (02:00 UTC nightly)

Notes:
  - Lab credentials expire after ~4h; re-run this script with fresh creds (it is idempotent).
  - If you set an alert email, click the SNS confirmation link AWS just sent.
  - Pause spend:  aws ecs update-service --cluster $CLUSTER --service $PREFIX-api --desired-count 0
                  aws ecs update-service --cluster $CLUSTER --service $PREFIX-worker --desired-count 0
                  aws rds stop-db-instance --db-instance-identifier $PREFIX-pg
  - Full teardown: ./scripts/deploy.sh --destroy
EOF

printf '\n\033[1;32m%s\033[0m\n'   '============================================================'
printf '\033[1;32m  BACKEND (API)  %s\033[0m\n' "http://$ALB_DNS/api/v1"
printf '\033[1;32m  FRONTEND (UI)  %s\033[0m\n' "$FRONTEND_URL"
printf '\033[1;32m%s\033[0m\n\n' '============================================================'
