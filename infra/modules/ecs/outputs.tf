output "cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "api_service_name" {
  description = "API ECS service name (for CloudWatch alarms)."
  value       = aws_ecs_service.api.name
}

output "worker_service_name" {
  description = "Worker ECS service name."
  value       = aws_ecs_service.worker.name
}

output "service_security_group_id" {
  description = "ECS service SG id — source for RDS/Redis ingress (SG chaining)."
  value       = aws_security_group.service.id
}
