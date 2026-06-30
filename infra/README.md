# IMS Infrastructure (Terraform)

Terraform IaC for the Cloud-Native Inventory Management System (IMS) on AWS
ECS Fargate. Built for the **AWS Academy Learner Lab**, so it is engineered
around that environment's hard constraints (see below).

## Architecture at a glance

```
Internet ─HTTP─► ALB (public subnets, 2 AZ, optional WAF)
                  └─► ECS Fargate API service (private subnets, ARM64, autoscale 2→10)
                        ├─ RDS PostgreSQL (private, Multi-AZ optional)
                        ├─ ElastiCache Redis (private)
                        ├─ DynamoDB StockMovement ledger (PITR)
                        ├─ SQS movements queue ──► ECS Fargate Spot worker
                        ├─ SNS low-stock topic (email)
                        └─ S3 reports bucket
CloudWatch dashboard + alarms · X-Ray (sidecar) · ECR (images)
```

## Modules

| Module | Responsibility |
|---|---|
| `vpc` | VPC, 2 public + 2 private subnets (2 AZ), IGW, **single NAT gateway**, route tables. Comment offers VPC-endpoint alternative. |
| `alb` | Internet-facing ALB, `ip`-type target group, HTTP:80 listener (HTTPS/ACM noted), `/api/v1/health` check, optional WAF (common rules + rate limit). |
| `ecr` | App image repo, scan-on-push, lifecycle keep-last-N. |
| `ecs` | Cluster (FARGATE + FARGATE_SPOT), API service (autoscale 2→10 on CPU + ALB req/target), Fargate-Spot worker, ARM64/Graviton, LabRole for both roles, awslogs. |
| `rds` | PostgreSQL in private subnets, SG 5432 from ECS only, encrypted, 7-day backups, Multi-AZ var. |
| `dynamodb` | StockMovement ledger (PK `sku` / SK `timestamp`, GSI on `movementId`), PAY_PER_REQUEST, PITR. |
| `cache` | ElastiCache Redis (single node or replica via var), SG 6379 from ECS only, private subnet group. |
| `messaging` | SQS movements queue + DLQ redrive, SNS low-stock topic + email subscription. |
| `observability` | CloudWatch dashboard + alarms (ECS CPU, ALB 5xx, RDS CPU, DLQ depth). X-Ray as ECS sidecar (noted). |

## Apply order

The backend state bucket must exist before the main stack can initialise, so
**bootstrap runs first**.

```bash
# 1) One-time: create the remote-state bucket + lock table (local backend).
cd infra/bootstrap
terraform init
terraform apply -var 'state_bucket_name=ims-tf-state-<ACCOUNT_ID>'
# note the output bucket + table names

# 2) Point the main backend at them: edit infra/backend.tf
#    bucket = "ims-tf-state-<ACCOUNT_ID>"   dynamodb_table = "ims-tf-locks"

# 3) Main stack.
cd ..
cp terraform.tfvars.example terraform.tfvars   # then edit (set db_password!)
terraform init
terraform plan
terraform apply
```

> First apply: leave `container_image = ""`. The ECS service will try to pull
> `:latest` which won't exist yet — push an image to the ECR repo (output
> `ecr_repo_url`) via CI/CD, then the service stabilises. Alternatively apply
> the data layer first and add ECS once an image exists.

## Key variables you must set

| Variable | Required | Notes |
|---|---|---|
| `db_password` | **Yes** | Sensitive. Set in `terraform.tfvars` (git-ignored) or `TF_VAR_db_password`. |
| `state_bucket_name` (bootstrap) | **Yes** | Globally unique S3 name. |
| `container_image` | After first build | `<ecr_repo_url>:<git-sha>`; CI/CD sets this. |
| `alert_email` | Optional | Subscribes to SNS low-stock topic (confirm via email link). |
| `db_multi_az` | Optional | `false` (cheap, lab) → `true` (HA, 99.9% story). |
| `redis_multi_az` | Optional | `false` single node → `true` replica + failover. |
| `enable_waf` | Optional | `true` for the security story; `false` to save cost. |

## LabRole & credential handling (Learner Lab constraints)

- **No IAM creation.** We never create `aws_iam_role`/policy/user. The stack
  does `data "aws_iam_role" "lab" { name = "LabRole" }` and passes
  `data.aws_iam_role.lab.arn` as **both** the ECS task *execution* role and the
  ECS *task* role. This is intentionally over-privileged; the report documents
  that production would use two scoped least-privilege roles (+ GitHub OIDC).
- **Session credentials expire ~4h.** Re-export the vended `aws_access_key_id`,
  `aws_secret_access_key`, **and `aws_session_token`** before each work session
  / `apply`. For CI/CD, store all three as GitHub Actions secrets and refresh
  them per session (OIDC federation is not possible without IAM provider
  creation — noted as the production approach).
- **Region pinned** to `us-east-1` everywhere (`var.region` default).
- **Encryption** uses SSE-S3 / AWS-managed KMS keys to avoid managing KMS key
  policies (which the lab restricts).
- **Bedrock** is NOT provisioned by Terraform — the app implements the AI
  forecast behind an interface with a statistical (EWMA) fallback.

## Cost controls (keep the lab budget low)

- **Single NAT gateway** shared by both private subnets (≈ half the NAT cost).
- **Fargate Spot** for the worker; ARM64/Graviton for both services.
- **PAY_PER_REQUEST** DynamoDB; burstable `t4g.micro` RDS/Redis.
- **S3 lifecycle** transitions reports to IA → Glacier → expiry.
- Scale in / `terraform destroy` between sessions if idle.

### NAT vs VPC endpoints trade-off

The single NAT is the cheap default but is a single-AZ egress point and routes
AWS-API traffic over the public internet. The more-secure / Well-Architected
alternative is to drop the NAT and add **VPC endpoints**: gateway endpoints for
S3 + DynamoDB (free) and interface endpoints for ECR (api+dkr), CloudWatch
Logs, SQS, SNS, and Secrets Manager/SSM (flat hourly fee each). A commented
sketch lives at the bottom of `modules/vpc/main.tf`.

## Teardown

```bash
cd infra
terraform destroy            # tears down the platform
cd bootstrap
terraform destroy            # removes state bucket + lock table (do last)
```

> If the state bucket has `prevent_destroy` or versioned objects, empty it
> first (`aws s3 rm s3://<bucket> --recursive`) before destroying bootstrap.

## Validation

```bash
terraform fmt -recursive
cd infra && terraform init -backend=false && terraform validate
```

`init`/`apply` against AWS need valid Learner Lab session credentials.
