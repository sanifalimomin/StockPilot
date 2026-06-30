# =============================================================================
# versions.tf — provider + Terraform version pinning and the AWS provider.
# =============================================================================

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Region is pinned via the variable default (us-east-1) for the Learner Lab.
provider "aws" {
  region = var.region

  # Tag EVERYTHING by default. Individual resources may add more tags, but
  # Project/Environment/ManagedBy are guaranteed on every taggable resource.
  default_tags {
    tags = local.common_tags
  }
}
