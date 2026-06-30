output "endpoint" {
  description = "RDS endpoint as host:port."
  value       = aws_db_instance.this.endpoint
}

output "address" {
  description = "RDS hostname only (no port)."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "RDS port."
  value       = aws_db_instance.this.port
}

output "instance_id" {
  description = "RDS instance identifier (for CloudWatch alarm dimensions)."
  value       = aws_db_instance.this.identifier
}

output "security_group_id" {
  description = "RDS security group id."
  value       = aws_security_group.rds.id
}
