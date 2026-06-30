terraform {
  backend "s3" {
    bucket = "ims-tf-state-741432490703"
    key    = "ims/main/terraform.tfstate"
    region = "us-east-1"

    dynamodb_table = "ims-tf-locks"
    encrypt        = true
  }
}
