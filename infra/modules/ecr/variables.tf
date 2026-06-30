variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "max_image_count" {
  description = "Number of most-recent images to retain before lifecycle expiry."
  type        = number
  default     = 10
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
