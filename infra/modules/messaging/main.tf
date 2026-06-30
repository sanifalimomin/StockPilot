# =============================================================================
# modules/messaging — SQS movements queue (+ DLQ redrive) and SNS low-stock topic.
#
# Reliability: the worker consumes the movements queue; messages that fail
# `max_receive_count` times are redriven to the DLQ instead of looping forever
# (poison-message isolation). A CloudWatch alarm on DLQ depth (observability
# module) flags stuck processing.
# Delivery is at-least-once, so the worker must be idempotent (documented in app).
# =============================================================================

# ---- Dead-letter queue ---------------------------------------------------- #
resource "aws_sqs_queue" "movements_dlq" {
  name                      = "${var.name_prefix}-movements-dlq"
  message_retention_seconds = 1209600 # 14 days — max retention to debug failures
  sqs_managed_sse_enabled   = true    # encryption at rest

  tags = merge(var.tags, { Name = "${var.name_prefix}-movements-dlq" })
}

# ---- Main movements queue ------------------------------------------------- #
resource "aws_sqs_queue" "movements" {
  name                       = "${var.name_prefix}-movements"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  message_retention_seconds  = 345600 # 4 days
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.movements_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  tags = merge(var.tags, { Name = "${var.name_prefix}-movements" })
}

# ---- SNS topic for low-stock alerts --------------------------------------- #
resource "aws_sns_topic" "low_stock" {
  name = "${var.name_prefix}-low-stock-alerts"
  tags = merge(var.tags, { Name = "${var.name_prefix}-low-stock-alerts" })
}

# Optional email subscription. AWS emails a confirmation link that the
# recipient must click before delivery begins (cannot be auto-confirmed).
resource "aws_sns_topic_subscription" "email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.low_stock.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
