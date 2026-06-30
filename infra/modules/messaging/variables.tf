variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "alert_email" {
  description = "Email to subscribe to the low-stock SNS topic. Empty = no subscription."
  type        = string
  default     = ""
}

variable "max_receive_count" {
  description = "Deliveries attempted before a message is moved to the DLQ (poison-message handling)."
  type        = number
  default     = 5
}

variable "visibility_timeout_seconds" {
  description = "SQS visibility timeout. Should exceed the worker's max processing time."
  type        = number
  default     = 60
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
