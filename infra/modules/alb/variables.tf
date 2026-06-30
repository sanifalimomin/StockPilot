variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC id the ALB and target group live in."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet ids for the internet-facing ALB."
  type        = list(string)
}

variable "container_port" {
  description = "Port the Fargate tasks listen on (target group port)."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Target group health-check path."
  type        = string
  default     = "/api/v1/health"
}

variable "enable_waf" {
  description = "Whether to create + associate an AWS WAF web ACL."
  type        = bool
  default     = true
}

variable "waf_rate_limit" {
  description = "WAF rate-based rule limit (requests / 5 min / IP)."
  type        = number
  default     = 2000
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
