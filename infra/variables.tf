# =============================================================================
# variables.tf — inputs for the main IMS stack.
# =============================================================================

# ---- Identity / tagging --------------------------------------------------- #
variable "project_name" {
  description = "Short project slug, prefixed onto resource names."
  type        = string
  default     = "ims"
}

variable "environment" {
  description = "Deployment environment (dev/stage/prod). Lab uses 'dev'."
  type        = string
  default     = "dev"
}

variable "region" {
  description = "AWS region. Pinned to us-east-1 for AWS Academy Learner Lab."
  type        = string
  default     = "us-east-1"
}

# ---- Networking ----------------------------------------------------------- #
variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Availability Zones to spread subnets across (exactly 2 for the lab)."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDRs for the public subnets (one per AZ)."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDRs for the private subnets (one per AZ)."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

# ---- Container image / app ------------------------------------------------ #
variable "container_image" {
  description = <<-EOT
    Full image reference for the Spring Boot app (e.g. <ecr_url>:<tag>).
    If left empty the stack defaults to "<this stack's ECR repo url>:latest".
    CI/CD overrides this with the immutable git-sha tag on each deploy.
  EOT
  type        = string
  default     = ""
}

variable "container_port" {
  description = "Port the Spring Boot app listens on."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "ALB/health-check path exposed by the app."
  type        = string
  default     = "/api/v1/health"
}

# ---- ECS sizing / scaling ------------------------------------------------- #
variable "api_cpu" {
  description = "Fargate CPU units for the API task (256/512/1024...)."
  type        = number
  default     = 512
}

variable "api_memory" {
  description = "Fargate memory (MiB) for the API task."
  type        = number
  default     = 1024
}

variable "api_desired_count" {
  description = "Baseline number of API tasks (Reliability: >=2 for AZ spread)."
  type        = number
  default     = 2
}

variable "api_min_count" {
  description = "Auto-scaling floor for the API service."
  type        = number
  default     = 2
}

variable "api_max_count" {
  description = "Auto-scaling ceiling for the API service."
  type        = number
  default     = 10
}

variable "worker_cpu" {
  description = "Fargate CPU units for the SQS worker task."
  type        = number
  default     = 256
}

variable "worker_memory" {
  description = "Fargate memory (MiB) for the SQS worker task."
  type        = number
  default     = 512
}

variable "worker_desired_count" {
  description = "Number of worker tasks (runs on Fargate Spot for cost)."
  type        = number
  default     = 1
}

# ---- Database (RDS PostgreSQL) -------------------------------------------- #
variable "db_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "ims"
}

variable "db_username" {
  description = "Master DB username. NOTE: in production this would come from Secrets Manager."
  type        = string
  default     = "ims_admin"
  sensitive   = true
}

variable "db_password" {
  description = "Master DB password. SENSITIVE — set via tfvars/env, never commit. Prod = Secrets Manager."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro" # Graviton/ARM, cheapest burstable
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GiB."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = <<-EOT
    Reliability vs Cost trade-off. true = Multi-AZ standby (99.9% target, ~2x cost);
    false = single-AZ (cheaper, fine for short-lived lab sessions).
  EOT
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Days of automated RDS backups (Durability/RPO)."
  type        = number
  default     = 7
}

# ---- Cache (ElastiCache Redis) -------------------------------------------- #
variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro" # Graviton/ARM
}

variable "redis_multi_az" {
  description = "true = replication group with a replica + automatic failover; false = single node (lab default)."
  type        = bool
  default     = false
}

# ---- Messaging (SNS) ------------------------------------------------------ #
variable "alert_email" {
  description = <<-EOT
    Email address subscribed to the low-stock SNS topic. Leave empty to skip the
    subscription. AWS sends a confirmation email that must be clicked manually.
  EOT
  type        = string
  default     = ""
}

# ---- Security ------------------------------------------------------------- #
variable "enable_waf" {
  description = "Gate the optional AWS WAF web ACL on the ALB (cost vs protection trade-off)."
  type        = bool
  default     = true
}

variable "waf_rate_limit" {
  description = "WAF rate-based rule: max requests per 5-minute window per IP."
  type        = number
  default     = 2000
}
