data "aws_caller_identity" "current" {}

data "aws_iam_role" "lab" {
  name = "LabRole"
}

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  container_image = var.container_image != "" ? var.container_image : "${module.ecr.repository_url}:latest"
}

# ---- Networking ----------------------------------------------------------- #
module "vpc" {
  source = "./modules/vpc"

  name_prefix          = local.name_prefix
  vpc_cidr             = var.vpc_cidr
  azs                  = var.azs
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  tags                 = local.common_tags
}

# ---- Container registry --------------------------------------------------- #
module "ecr" {
  source = "./modules/ecr"

  name_prefix = local.name_prefix
  tags        = local.common_tags
}

# ---- Public load balancer (+ optional WAF) -------------------------------- #
module "alb" {
  source = "./modules/alb"

  name_prefix       = local.name_prefix
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  container_port    = var.container_port
  health_check_path = var.health_check_path
  enable_waf        = var.enable_waf
  waf_rate_limit    = var.waf_rate_limit
  tags              = local.common_tags
}

# ---- Relational database -------------------------------------------------- #
module "rds" {
  source = "./modules/rds"

  name_prefix           = local.name_prefix
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  ecs_security_group_id = module.ecs.service_security_group_id # SG chaining: only ECS may reach 5432
  db_name               = var.db_name
  db_username           = var.db_username
  db_password           = var.db_password
  instance_class        = var.db_instance_class
  allocated_storage     = var.db_allocated_storage
  multi_az              = var.db_multi_az
  backup_retention_days = var.db_backup_retention_days
  tags                  = local.common_tags
}

# ---- Redis cache ---------------------------------------------------------- #
module "cache" {
  source = "./modules/cache"

  name_prefix           = local.name_prefix
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  ecs_security_group_id = module.ecs.service_security_group_id # only ECS may reach 6379
  node_type             = var.redis_node_type
  multi_az              = var.redis_multi_az
  tags                  = local.common_tags
}

# ---- DynamoDB movement ledger --------------------------------------------- #
module "dynamodb" {
  source = "./modules/dynamodb"

  name_prefix = local.name_prefix
  tags        = local.common_tags
}

# ---- SQS + SNS ------------------------------------------------------------ #
module "messaging" {
  source = "./modules/messaging"

  name_prefix = local.name_prefix
  alert_email = var.alert_email
  tags        = local.common_tags
}

# ---- S3 bucket for reports/exports/images --------------------------------- #
# Small enough to define inline rather than its own module.
resource "aws_s3_bucket" "reports" {
  bucket = "${local.name_prefix}-reports-${data.aws_caller_identity.current.account_id}"
  tags   = local.common_tags
}

resource "aws_s3_bucket_versioning" "reports" {
  bucket = aws_s3_bucket.reports.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # SSE-S3; avoids KMS key-policy management in the lab
    }
  }
}

resource "aws_s3_bucket_public_access_block" "reports" {
  bucket                  = aws_s3_bucket.reports.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Cost Optimization: lifecycle reports to cheaper storage, then expire.
resource "aws_s3_bucket_lifecycle_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id

  rule {
    id     = "archive-old-reports"
    status = "Enabled"

    filter {
      prefix = "reports/"
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }
    transition {
      days          = 90
      storage_class = "GLACIER"
    }
    expiration {
      days = 365
    }
  }
}

# ---- ECS cluster + API service + worker service --------------------------- #
module "ecs" {
  source = "./modules/ecs"

  name_prefix = local.name_prefix
  region      = var.region

  # IAM (Learner Lab): same LabRole ARN for execution AND task role.
  execution_role_arn = data.aws_iam_role.lab.arn
  task_role_arn      = data.aws_iam_role.lab.arn

  vpc_id                  = module.vpc.vpc_id
  private_subnet_ids      = module.vpc.private_subnet_ids
  alb_security_group_id   = module.alb.security_group_id # SG chaining: ALB -> ECS:8080
  target_group_arn        = module.alb.target_group_arn
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix

  container_image   = local.container_image
  container_port    = var.container_port
  health_check_path = var.health_check_path

  api_cpu              = var.api_cpu
  api_memory           = var.api_memory
  api_desired_count    = var.api_desired_count
  api_min_count        = var.api_min_count
  api_max_count        = var.api_max_count
  worker_cpu           = var.worker_cpu
  worker_memory        = var.worker_memory
  worker_desired_count = var.worker_desired_count

  # App configuration wired from the other modules' outputs.
  db_endpoint    = module.rds.address
  db_port        = module.rds.port
  db_name        = var.db_name
  db_username    = var.db_username
  db_password    = var.db_password
  redis_endpoint = module.cache.primary_endpoint
  redis_port     = module.cache.port
  dynamodb_table = module.dynamodb.table_name
  sqs_queue_url  = module.messaging.queue_url
  sns_topic_arn  = module.messaging.topic_arn
  reports_bucket = aws_s3_bucket.reports.bucket

  tags = local.common_tags
}

# ---- Observability -------------------------------------------------------- #
module "observability" {
  source = "./modules/observability"

  name_prefix = local.name_prefix
  region      = var.region

  cluster_name            = module.ecs.cluster_name
  api_service_name        = module.ecs.api_service_name
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix
  db_instance_id          = module.rds.instance_id
  dlq_name                = module.messaging.dlq_name

  tags = local.common_tags
}
