#!/usr/bin/env bash
# =============================================================================
# deploy.sh — one-shot deployment of the entire IMS stack to an AWS Academy
# Learner Lab account, from scratch.
#
#   ./scripts/deploy.sh                         # interactive: paste lab creds
#   ./scripts/deploy.sh --alert-email me@x.com  # subscribe SNS alerts
#   ./scripts/deploy.sh --enable-waf            # try WAF (lab SCP may deny it)
#   ./scripts/deploy.sh --skip-build            # infra + frontend only
#   ./scripts/deploy.sh --skip-frontend         # infra + image only
#   ./scripts/deploy.sh --destroy               # tear everything down
#
# Credentials: either export AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY /
# AWS_SESSION_TOKEN beforehand, or just run the script and paste the block
# from AWS Academy -> AWS Details -> AWS CLI when prompted.
#
# What it does (in order):
#   1. Collect + verify Learner Lab session credentials (us-east-1)
#   2. Bootstrap the Terraform remote-state backend (S3 bucket + DynamoDB lock)
#   3. Generate infra/terraform.tfvars (random DB password) if missing
#   4. terraform apply the full platform (VPC, ALB, ECS, RDS, Redis, DynamoDB,
#      SQS/SNS, EventBridge, ECR, S3 buckets, CloudWatch)
#   5. Build the ARM64 Spring Boot image and push it to ECR
#   6. Force new deployments of the api + worker ECS services
#   7. Build the React frontend against the live ALB and sync it to S3
#   8. Poll the health endpoint until the API answers
#
# Requirements: bash (Git Bash works), aws CLI v2, terraform >= 1.6,
# docker (with buildx), node 20+ / npm, curl.
# =============================================================================
set -euo pipefail

# ---- locate repo root (script lives in scripts/) ---------------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$ROOT/infra"
REGION="us-east-1"

# ---- options ----------------------------------------------------------------
ALERT_EMAIL=""
ENABLE_WAF="false"
SKIP_BUILD="false"
SKIP_FRONTEND="false"
DESTROY="false"
ASSUME_YES="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --alert-email)   ALERT_EMAIL="$2"; shift 2 ;;
    --enable-waf)    ENABLE_WAF="true"; shift ;;
    --skip-build)    SKIP_BUILD="true"; shift ;;
    --skip-frontend) SKIP_FRONTEND="true"; shift ;;
    --destroy)       DESTROY="true"; shift ;;
    --yes|-y)        ASSUME_YES="true"; shift ;;
    -h|--help)       grep '^#' "$0" | head -30; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)"; exit 1 ;;
  esac
done

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# =============================================================================
# 1. Credentials
# =============================================================================
if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" || -z "${AWS_SESSION_TOKEN:-}" ]]; then
  echo "Paste the credentials block from AWS Academy (AWS Details -> AWS CLI -> Show)."
  echo "It looks like:  [default] / aws_access_key_id=... / aws_secret_access_key=... / aws_session_token=..."
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

# ---- preflight ---------------------------------------------------------------
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

# Empty an S3 bucket, including all object versions and delete markers.
purge_bucket() {
  local bucket="$1"
  aws s3api head-bucket --bucket "$bucket" 2>/dev/null || return 0
  echo "Emptying s3://$bucket ..."
  aws s3 rm "s3://$bucket" --recursive --quiet || true
  while :; do
    local versions
    # Small pages + inline JSON: avoids file:// arguments, which Git Bash on
    # Windows mangles before they reach the native aws.exe.
    versions="$(aws s3api list-object-versions --bucket "$bucket" --max-keys 200 \
      --query '[Versions[].{Key:Key,VersionId:VersionId},DeleteMarkers[].{Key:Key,VersionId:VersionId}][]' \
      --output json 2>/dev/null || echo '[]')"
    [[ -z "$versions" || "$versions" == "[]" || "$versions" == "null" ]] && break
    aws s3api delete-objects --bucket "$bucket" \
      --delete "{\"Objects\":$versions,\"Quiet\":true}" >/dev/null
  done
}

# =============================================================================
# Destroy mode
# =============================================================================
if [[ "$DESTROY" == "true" ]]; then
  if [[ "$ASSUME_YES" != "true" ]]; then
    read -r -p "This will DESTROY the entire IMS stack in account $ACCOUNT_ID. Type 'destroy' to continue: " ans
    [[ "$ans" == "destroy" ]] || die "Aborted."
  fi
  aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null \
    || die "State bucket $STATE_BUCKET not found - nothing to destroy in this account."
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
  echo
  echo "Main stack destroyed. The bootstrap state bucket ($STATE_BUCKET) and lock"
  echo "table are left in place; the Learner Lab wipes them when the lab resets."
  exit 0
fi

# =============================================================================
# 2. Bootstrap remote state (S3 + DynamoDB lock)
# =============================================================================
log "Checking Terraform state backend"
BUCKET_OK=false; TABLE_OK=false
aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null && BUCKET_OK=true
aws dynamodb describe-table --table-name "$LOCK_TABLE" >/dev/null 2>&1 && TABLE_OK=true

if [[ "$BUCKET_OK" == "true" && "$TABLE_OK" == "true" ]]; then
  echo "Backend already exists ($STATE_BUCKET) - skipping bootstrap."
else
  # A leftover local state from a PREVIOUS Academy account would make Terraform
  # try to update resources that no longer exist - move it aside first.
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

# =============================================================================
# 3. terraform.tfvars (generate once; random DB password)
# =============================================================================
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

# Optional AI forecast (Bedrock is blocked in the Learner Lab). Recommended:
# a FREE Google AI Studio key (aistudio.google.com). Gemini wins over Claude;
# both empty = free statistical EWMA forecast.
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

# =============================================================================
# 4. Provision the platform
# =============================================================================
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

# =============================================================================
# 5-6. Build + push the ARM64 image, roll the ECS services
# =============================================================================
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

# =============================================================================
# 7. Frontend build + S3 sync
# =============================================================================
if [[ "$SKIP_FRONTEND" != "true" ]]; then
  log "Building the frontend against http://$ALB_DNS"
  (
    cd "$ROOT/frontend"
    # Reinstall only when needed: npm ci wipes node_modules, which fails with
    # EPERM on Windows if a Vite dev server (esbuild.exe) is still running.
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

# =============================================================================
# 8. Wait for the API to answer
# =============================================================================
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

# =============================================================================
# Summary
# =============================================================================
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
