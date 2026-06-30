# =============================================================================
# modules/dynamodb — StockMovement append-only ledger.
#
# Access-pattern design: movements are written per-SKU and read back in time
# order, so we use a COMPOSITE key:
#   * partition key  sku       (S) — groups all movements for one SKU
#   * sort key       timestamp (S, ISO-8601) — natural chronological ordering
# A GSI on movementId supports point lookups of a single movement by its id.
#
# Cost: PAY_PER_REQUEST (on-demand) — no idle capacity charges, ideal for the
# spiky/low lab workload. Durability: point-in-time recovery (PITR) enabled.
# Security: server-side encryption on (AWS-owned key by default).
# =============================================================================

resource "aws_dynamodb_table" "stock_movements" {
  name         = "${var.name_prefix}-stock-movements"
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "sku"
  range_key = "timestamp"

  attribute {
    name = "sku"
    type = "S"
  }
  attribute {
    name = "timestamp"
    type = "S"
  }
  attribute {
    name = "movementId"
    type = "S"
  }

  # GSI: look up a movement directly by its id.
  global_secondary_index {
    name            = "movementId-index"
    hash_key        = "movementId"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true # Durability / RPO
  }

  server_side_encryption {
    enabled = true # encryption at rest
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-stock-movements" })
}
