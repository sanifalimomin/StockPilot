# =============================================================================
# bootstrap/main.tf — RUN ONCE, BEFORE the main stack.
#
# Purpose: create the remote-state backend that the main stack relies on:
#   * an S3 bucket (versioned + encrypted) to hold terraform.tfstate
#   * a DynamoDB table to provide state locking (prevents concurrent applies)
#
# This stack uses a LOCAL backend (state lives on disk next to these files),
# because you cannot store the state backend's own state in the backend it is
# creating (chicken-and-egg). Commit bootstrap/terraform.tfstate or keep it
# somewhere safe — it only tracks two cheap resources.
#
# Well-Architected (Operational Excellence): remote state + locking lets the
# team (and CI/CD) apply safely without clobbering each other's state.
#
# AWS Academy Learner Lab notes:
#   * No IAM resources are created here (the lab forbids it). The LabRole that
#     your vended session credentials assume already has S3 + DynamoDB access.
#   * Keep this cheap: S3 + an on-demand DynamoDB table cost effectively nothing.
# =============================================================================

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Local backend: bootstrap cannot use the S3 backend it is busy creating.
  backend "local" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}

# -----------------------------------------------------------------------------
# S3 bucket holding the main stack's Terraform state.
# -----------------------------------------------------------------------------
resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  # Safety: refuse to destroy a bucket that still contains state objects.
  # Override deliberately during teardown if you really mean it.
  lifecycle {
    prevent_destroy = false # set true in a real environment
  }
}

# Versioning: keep every prior state file so we can roll back a bad apply.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Encryption at rest (Security pillar). SSE-S3 (AES256) needs no KMS key/policy,
# which matters in the lab where we cannot manage KMS key policies freely.
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Block all public access to the state bucket — state can contain secrets.
resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# -----------------------------------------------------------------------------
# DynamoDB table for state locking. PAY_PER_REQUEST = no idle capacity cost.
# The main backend references this table by name; the hash key MUST be LockID.
# -----------------------------------------------------------------------------
resource "aws_dynamodb_table" "locks" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
