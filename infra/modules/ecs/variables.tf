variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "region" {
  description = "AWS region (for awslogs driver)."
  type        = string
}

# ---- IAM (Learner Lab): same LabRole ARN for both roles ------------------- #
variable "execution_role_arn" {
  description = "ECS task EXECUTION role ARN (pull image, write logs). = LabRole ARN in lab."
  type        = string
}

variable "task_role_arn" {
  description = "ECS TASK role ARN (app's AWS API calls). = LabRole ARN in lab."
  type        = string
}

# ---- Networking ----------------------------------------------------------- #
variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet ids the tasks run in."
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "ALB SG — the only allowed source of traffic to the container port."
  type        = string
}

variable "target_group_arn" {
  description = "ALB target group the API service registers into."
  type        = string
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix for the ALBRequestCountPerTarget autoscaling metric."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Target group ARN suffix for the ALBRequestCountPerTarget metric."
  type        = string
}

# ---- Container / image ---------------------------------------------------- #
variable "container_image" {
  description = "Full image reference to run."
  type        = string
}

variable "container_port" {
  description = "Container listen port."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Container health-check path (also used by ALB)."
  type        = string
  default     = "/api/v1/health"
}

# ---- Sizing / scaling ----------------------------------------------------- #
variable "api_cpu" {
  type    = number
  default = 512
}
variable "api_memory" {
  type    = number
  default = 1024
}
variable "api_desired_count" {
  type    = number
  default = 2
}
variable "api_min_count" {
  type    = number
  default = 2
}
variable "api_max_count" {
  type    = number
  default = 10
}
variable "worker_cpu" {
  type    = number
  default = 256
}
variable "worker_memory" {
  type    = number
  default = 512
}
variable "worker_desired_count" {
  type    = number
  default = 1
}
variable "scheduler_cpu" {
  description = "Fargate CPU units for the one-shot scheduler task."
  type        = number
  default     = 256
}
variable "scheduler_memory" {
  description = "Fargate memory (MiB) for the one-shot scheduler task."
  type        = number
  default     = 512
}

# ---- Scheduled reorder task (EventBridge) ---------------------------------- #
variable "reorder_schedule_expression" {
  description = "EventBridge schedule expression for the nightly reorder scan."
  type        = string
  default     = "cron(0 2 * * ? *)" # 02:00 UTC daily
}

variable "events_role_arn" {
  description = "IAM role EventBridge assumes to RunTask on the cluster (= LabRole in the lab)."
  type        = string
}

# ---- App configuration (wired from other modules) ------------------------- #
variable "db_endpoint" {
  type = string
}
variable "db_port" {
  type = number
}
variable "db_name" {
  type = string
}
variable "db_username" {
  type      = string
  sensitive = true
}
variable "db_password" {
  type      = string
  sensitive = true
}
variable "redis_endpoint" {
  type = string
}
variable "redis_port" {
  type = number
}
variable "dynamodb_table" {
  type = string
}
variable "sqs_queue_url" {
  type = string
}
variable "sns_topic_arn" {
  type = string
}
variable "reports_bucket" {
  type = string
}
variable "cors_origins" {
  description = "Comma-separated origins allowed by the API's CORS policy (injected as CORS_ORIGINS)."
  type        = string
  default     = "http://localhost:5173"
}
variable "forecast_provider" {
  description = "Forecast provider for the app: ewma (free, statistical) or claude (Anthropic API)."
  type        = string
  default     = "ewma"
}
variable "anthropic_api_key" {
  description = "Anthropic API key for the Claude forecast provider. Empty = EWMA only."
  type        = string
  sensitive   = true
  default     = ""
}
variable "gemini_api_key" {
  description = "Google AI Studio API key for the Gemini forecast provider (free tier). Empty = disabled."
  type        = string
  sensitive   = true
  default     = ""
}

# ---- Secrets Manager injection (preferred over the raw values above) -------- #
variable "db_password_secret_arn" {
  description = "Secrets Manager ARN for the DB password. Non-empty = inject via ECS `secrets`; empty = plain env var fallback."
  type        = string
  default     = ""
}
variable "anthropic_api_key_secret_arn" {
  description = "Secrets Manager ARN for the Anthropic API key. Empty = env var fallback (when key set)."
  type        = string
  default     = ""
}
variable "gemini_api_key_secret_arn" {
  description = "Secrets Manager ARN for the Gemini API key. Empty = env var fallback (when key set)."
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention for the ECS log groups."
  type        = number
  default     = 14
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
