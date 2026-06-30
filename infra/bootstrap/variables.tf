variable "project_name" {
  description = "Project name used for tagging."
  type        = string
  default     = "ims"
}

variable "region" {
  description = "AWS region. Pinned to us-east-1 for AWS Academy Learner Lab."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = <<-EOT
    Globally-unique S3 bucket name for the main stack's Terraform state.
    Must be globally unique across all of AWS — append your account id or a
    random suffix, e.g. "ims-tf-state-123456789012".
  EOT
  type        = string
}

variable "lock_table_name" {
  description = "DynamoDB table name for Terraform state locking."
  type        = string
  default     = "ims-tf-locks"
}
