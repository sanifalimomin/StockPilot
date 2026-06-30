# =============================================================================
# modules/ecr — container image registry for the Spring Boot app.
# Security: scan-on-push surfaces CVEs. Cost: lifecycle policy caps stored
# images so old layers don't accumulate storage charges.
# =============================================================================

resource "aws_ecr_repository" "app" {
  name                 = "${var.name_prefix}-app"
  image_tag_mutability = "MUTABLE" # allows the "latest" tag to move; CI also pushes immutable sha tags

  image_scanning_configuration {
    scan_on_push = true # Security: automatic vulnerability scan on every push
  }

  encryption_configuration {
    encryption_type = "AES256" # SSE; avoids KMS key-policy management in the lab
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-app" })
}

# Keep only the newest N images; expire older ones (Cost Optimization).
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the most recent ${var.max_image_count} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.max_image_count
        }
        action = { type = "expire" }
      }
    ]
  })
}
