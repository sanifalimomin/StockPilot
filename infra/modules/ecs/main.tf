# =============================================================================
# modules/ecs — ECS cluster, API service (autoscaled, behind ALB) and SQS
# worker service (Fargate Spot).
#
# Key decisions:
#   * Capacity providers FARGATE + FARGATE_SPOT. API runs on FARGATE (stable);
#     the worker runs on FARGATE_SPOT (Cost + Sustainability — reuses spare
#     capacity; interruptions are fine because SQS redelivers).
#   * ARM64 / Graviton runtime_platform (Sustainability + Cost: ~20% cheaper,
#     lower energy). The app image MUST be built for linux/arm64.
#   * IAM: execution_role_arn AND task_role_arn both = LabRole ARN. The lab
#     forbids creating roles, so a single over-privileged role backs both
#     functions. Production = two scoped least-privilege roles.
#   * Autoscaling: target-tracking on BOTH ECS CPU and ALB request-count-per-
#     target, 2 -> 10 tasks (matches the 50->200 RPS NFR).
#   * Security-group chain: this service SG accepts the container port ONLY
#     from the ALB SG; RDS/Redis SGs accept their ports ONLY from this SG.
# =============================================================================

# ---- Cluster -------------------------------------------------------------- #
resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled" # Operational Excellence: per-task CPU/mem metrics
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-cluster" })
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  # Default strategy for services that don't specify their own (API pins FARGATE).
  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

# ---- Service security group (the chain hub) ------------------------------- #
resource "aws_security_group" "service" {
  name        = "${var.name_prefix}-ecs-sg"
  description = "ECS tasks: ingress from ALB only; egress anywhere"
  vpc_id      = var.vpc_id

  # Least-open: container port reachable ONLY from the ALB SG.
  ingress {
    description     = "App port from ALB"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  # Egress open so tasks can reach RDS/Redis/AWS APIs via NAT (or endpoints).
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-ecs-sg" })
}

# ---- Log groups ----------------------------------------------------------- #
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name_prefix}/api"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/ecs/${var.name_prefix}/worker"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

# ---- Shared env vars wired from other modules ----------------------------- #
locals {
  # Common application environment for BOTH task definitions.
  base_environment = [
    { name = "AWS_REGION", value = var.region },
    { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${var.db_endpoint}:${var.db_port}/${var.db_name}" },
    { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
    { name = "DB_HOST", value = var.db_endpoint },
    { name = "DB_PORT", value = tostring(var.db_port) },
    { name = "DB_NAME", value = var.db_name },
    { name = "REDIS_HOST", value = var.redis_endpoint },
    { name = "REDIS_PORT", value = tostring(var.redis_port) },
    { name = "DYNAMODB_TABLE", value = var.dynamodb_table },
    { name = "SQS_QUEUE_URL", value = var.sqs_queue_url },
    { name = "SNS_TOPIC_ARN", value = var.sns_topic_arn },
    { name = "REPORTS_BUCKET", value = var.reports_bucket },
  ]

  # SENSITIVE: the DB password is injected as a plain env var here for the lab.
  # PRODUCTION: store it in Secrets Manager and use the container `secrets`
  # block so it never appears in the task definition / state in cleartext.
  secret_environment = [
    { name = "SPRING_DATASOURCE_PASSWORD", value = var.db_password },
    { name = "DB_PASSWORD", value = var.db_password },
  ]
}

# ---- API task definition -------------------------------------------------- #
resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = var.execution_role_arn # LabRole
  task_role_arn            = var.task_role_arn      # LabRole

  # Sustainability + Cost: Graviton/ARM64.
  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = var.container_image
      essential = true
      portMappings = [
        { containerPort = var.container_port, protocol = "tcp" }
      ]
      environment = concat(local.base_environment, local.secret_environment, [
        { name = "APP_ROLE", value = "api" }
      ])
      # Container-level health check (belt-and-braces alongside the ALB check).
      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${var.container_port}${var.health_check_path} || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "api"
        }
      }
      # NOTE: X-Ray tracing would be added as a sidecar container here, e.g.
      # an "xray-daemon" container (public.ecr.aws/xray/aws-xray-daemon) on
      # UDP 2000, sharing the task network. Omitted to keep the lab task lean;
      # documented in the observability module and the report.
    }
  ])

  tags = merge(var.tags, { Name = "${var.name_prefix}-api" })
}

# ---- API service ---------------------------------------------------------- #
resource "aws_ecs_service" "api" {
  name            = "${var.name_prefix}-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count

  # Pin the API to on-demand FARGATE for stability (no Spot interruptions).
  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false # private subnets; egress via NAT
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "api"
    container_port   = var.container_port
  }

  # Reliability: rolling deploy with circuit breaker + auto-rollback.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  health_check_grace_period_seconds = 90 # give Spring Boot time to warm up

  # Autoscaling controls desired_count after creation; ignore drift.
  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [aws_ecs_cluster_capacity_providers.this]

  tags = merge(var.tags, { Name = "${var.name_prefix}-api" })
}

# ---- Worker task definition (same image, APP_ROLE=worker) ----------------- #
resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name_prefix}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.worker_cpu
  memory                   = var.worker_memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = var.container_image # same image as the API
      essential = true
      # No portMappings: the worker polls SQS, it is not behind the ALB.
      environment = concat(local.base_environment, local.secret_environment, [
        { name = "APP_ROLE", value = "worker" } # selects the Spring "worker" profile
      ])
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.worker.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "worker"
        }
      }
    }
  ])

  tags = merge(var.tags, { Name = "${var.name_prefix}-worker" })
}

# ---- Worker service (Fargate Spot) ---------------------------------------- #
resource "aws_ecs_service" "worker" {
  name            = "${var.name_prefix}-worker"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_desired_count

  # Cost/Sustainability: run on Spot. SQS redelivery tolerates interruptions.
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 1
  }

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_ecs_cluster_capacity_providers.this]

  tags = merge(var.tags, { Name = "${var.name_prefix}-worker" })
}

# =============================================================================
# Application Auto Scaling for the API service (target tracking, 2 -> 10).
# =============================================================================
resource "aws_appautoscaling_target" "api" {
  max_capacity       = var.api_max_count
  min_capacity       = var.api_min_count
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Scale on average CPU across tasks.
resource "aws_appautoscaling_policy" "api_cpu" {
  name               = "${var.name_prefix}-api-cpu-tt"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60 # keep average CPU ~60%
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}

# Scale on ALB requests per target (directly tracks the RPS NFR).
resource "aws_appautoscaling_policy" "api_requests" {
  name               = "${var.name_prefix}-api-req-tt"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      # Resource label ties the metric to THIS ALB + target group.
      resource_label = "${var.alb_arn_suffix}/${var.target_group_arn_suffix}"
    }
    target_value       = 1000 # ~requests per task per minute before scaling out
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}
