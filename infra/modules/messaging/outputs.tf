output "queue_url" {
  description = "URL of the movements SQS queue (worker consumes this)."
  value       = aws_sqs_queue.movements.url
}

output "queue_arn" {
  description = "ARN of the movements SQS queue."
  value       = aws_sqs_queue.movements.arn
}

output "dlq_url" {
  description = "URL of the dead-letter queue."
  value       = aws_sqs_queue.movements_dlq.url
}

output "dlq_name" {
  description = "Name of the DLQ (for the CloudWatch depth alarm)."
  value       = aws_sqs_queue.movements_dlq.name
}

output "topic_arn" {
  description = "ARN of the low-stock SNS topic."
  value       = aws_sns_topic.low_stock.arn
}
