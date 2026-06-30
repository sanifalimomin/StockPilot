variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet ids for the DB subnet group."
  type        = list(string)
}

variable "ecs_security_group_id" {
  description = "ECS service SG — the ONLY source allowed to reach 5432 (SG chaining)."
  type        = string
}

variable "db_name" {
  description = "Initial database name."
  type        = string
}

variable "db_username" {
  description = "Master username."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Master password (sensitive)."
  type        = string
  sensitive   = true
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Allocated storage (GiB)."
  type        = number
  default     = 20
}

variable "multi_az" {
  description = "Multi-AZ standby toggle (reliability vs cost)."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Automated backup retention (days)."
  type        = number
  default     = 7
}

variable "engine_version" {
  description = "PostgreSQL engine major.minor version. Must be one offered in-region (aws rds describe-db-engine-versions --engine postgres); 16.4 was removed, 16.9 is the lowest 16.x available."
  type        = string
  default     = "16.9"
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
