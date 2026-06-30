variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet ids for the cache subnet group."
  type        = list(string)
}

variable "ecs_security_group_id" {
  description = "ECS service SG — the ONLY source allowed to reach 6379."
  type        = string
}

variable "node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "multi_az" {
  description = "true = replication group (replica + automatic failover); false = single node."
  type        = bool
  default     = false
}

variable "engine_version" {
  description = "Redis engine version."
  type        = string
  default     = "7.1"
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
