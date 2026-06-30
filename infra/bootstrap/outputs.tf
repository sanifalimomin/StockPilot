output "state_bucket_name" {
  description = "Name of the S3 bucket holding the main stack state. Plug into backend.tf."
  value       = aws_s3_bucket.state.id
}

output "lock_table_name" {
  description = "Name of the DynamoDB lock table. Plug into backend.tf."
  value       = aws_dynamodb_table.locks.name
}
