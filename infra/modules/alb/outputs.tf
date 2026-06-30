output "alb_dns_name" {
  description = "Public DNS name of the ALB."
  value       = aws_lb.this.dns_name
}

output "alb_arn_suffix" {
  description = "ALB ARN suffix (for CloudWatch RequestCount metrics / autoscaling)."
  value       = aws_lb.this.arn_suffix
}

output "target_group_arn" {
  description = "API target group ARN (consumed by the ECS service)."
  value       = aws_lb_target_group.api.arn
}

output "target_group_arn_suffix" {
  description = "Target group ARN suffix (for ALBRequestCountPerTarget metric)."
  value       = aws_lb_target_group.api.arn_suffix
}

output "security_group_id" {
  description = "ALB security group id (source for the ECS ingress rule)."
  value       = aws_security_group.alb.id
}
