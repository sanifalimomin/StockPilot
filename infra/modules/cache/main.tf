# =============================================================================
# modules/cache — ElastiCache for Redis (hot stock-level reads).
#
# Reliability vs cost: a single replication group whose node count flips with
# the multi_az var — 1 node (cheap, lab default) or 2 nodes with automatic
# failover (HA). Using a replication group (not the legacy single cluster)
# means the same primary_endpoint output works in both modes.
#
# Security: private subnets + SG allowing 6379 ONLY from the ECS SG; encryption
# at rest and in transit enabled.
# =============================================================================

resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis-sg"
  description = "Redis access from ECS only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from ECS"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.ecs_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-redis-sg" })
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name_prefix}-redis-subnets"
  subnet_ids = var.private_subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-redis-subnets" })
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name_prefix}-redis"
  description          = "IMS Redis cache for hot stock reads"

  engine         = "redis"
  engine_version = var.engine_version
  node_type      = var.node_type
  port           = 6379

  # 1 node = single primary (lab). 2 nodes = primary + replica with failover.
  num_cache_clusters         = var.multi_az ? 2 : 1
  automatic_failover_enabled = var.multi_az
  multi_az_enabled           = var.multi_az

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true # encryption at rest
  transit_encryption_enabled = true # TLS in transit (app must use rediss://)

  apply_immediately = true # lab: don't defer to maintenance window

  tags = merge(var.tags, { Name = "${var.name_prefix}-redis" })
}
