variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "region" {
  description = "AWS region (for dashboard widgets)."
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name (alarm/dashboard dimension)."
  type        = string
}

variable "api_service_name" {
  description = "API ECS service name (alarm/dashboard dimension)."
  type        = string
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix (HTTPCode_ELB_5XX metric)."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Target group ARN suffix (request metrics)."
  type        = string
}

variable "db_instance_id" {
  description = "RDS instance identifier (CPU alarm)."
  type        = string
}

variable "dlq_name" {
  description = "DLQ queue name (depth alarm)."
  type        = string
}

variable "alarm_sns_topic_arn" {
  description = "Optional SNS topic for alarm notifications. Empty = no alarm action."
  type        = string
  default     = ""
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
