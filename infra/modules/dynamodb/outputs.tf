output "table_name" {
  description = "DynamoDB table name."
  value       = aws_dynamodb_table.stock_movements.name
}

output "table_arn" {
  description = "DynamoDB table ARN."
  value       = aws_dynamodb_table.stock_movements.arn
}
