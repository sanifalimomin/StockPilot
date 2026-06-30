# =============================================================================
# modules/rds — RDS PostgreSQL in private subnets.
#
# Security: lives in private subnets only; its SG permits 5432 ONLY from the
# ECS service SG (SG chaining ALB -> ECS -> RDS). storage_encrypted = true for
# encryption at rest.
# Reliability: multi_az var toggles a synchronous standby; backups retained for
# RPO. Durability: automated backups + (optionally) PITR via retention > 0.
#
# Credentials: taken from variables here. PRODUCTION would source these from
# AWS Secrets Manager (with rotation) and inject via the task definition's
# `secrets` block — noted as an accepted lab simplification.
# =============================================================================

# ---- DB security group ---------------------------------------------------- #
resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = "PostgreSQL access from ECS only"
  vpc_id      = var.vpc_id

  # Least-open: 5432 ONLY from the ECS service security group.
  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ecs_security_group_id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-rds-sg" })
}

# ---- Subnet group (private subnets across 2 AZs) -------------------------- #
resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnets"
  subnet_ids = var.private_subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-db-subnets" })
}

# ---- The instance --------------------------------------------------------- #
resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-pg"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.allocated_storage * 2 # storage autoscaling headroom
  storage_type          = "gp3"
  storage_encrypted     = true # encryption at rest (Security). Default AWS-managed KMS key.

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false # private only

  multi_az                     = var.multi_az              # Reliability vs cost
  backup_retention_period      = var.backup_retention_days # >0 also enables PITR
  backup_window                = "03:00-04:00"
  maintenance_window           = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot        = true
  deletion_protection          = false # lab convenience; true in production
  skip_final_snapshot          = true  # lab convenience; false in production
  apply_immediately            = true  # lab: don't wait for maintenance window
  performance_insights_enabled = false # keep cost down in lab

  tags = merge(var.tags, { Name = "${var.name_prefix}-pg" })
}
