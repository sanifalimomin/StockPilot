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
