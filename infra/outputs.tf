# =============================================================================
# outputs.tf — handy values after apply (CI/CD + manual demos consume these).
# =============================================================================

output "alb_dns_name" {
  description = "Public DNS name of the ALB — the app's entry point (http://<dns>)."
  value       = module.alb.alb_dns_name
}

output "ecr_repo_url" {
  description = "ECR repository URL. CI/CD pushes the app image here."
  value       = module.ecr.repository_url
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (host:port). App connects via JDBC."
  value       = module.rds.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint."
  value       = module.cache.primary_endpoint
}

output "dynamodb_table_name" {
  description = "DynamoDB StockMovement ledger table name."
  value       = module.dynamodb.table_name
}

output "sqs_queue_url" {
  description = "SQS movements queue URL consumed by the worker service."
  value       = module.messaging.queue_url
}

output "sns_topic_arn" {
  description = "SNS topic ARN for low-stock alerts."
  value       = module.messaging.topic_arn
}

output "reports_bucket" {
  description = "S3 bucket for report/export/image storage."
  value       = aws_s3_bucket.reports.bucket
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = module.ecs.cluster_name
}

output "cloudwatch_dashboard" {
  description = "Name of the CloudWatch dashboard."
  value       = module.observability.dashboard_name
}
