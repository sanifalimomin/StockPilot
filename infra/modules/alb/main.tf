# =============================================================================
# modules/alb — public Application Load Balancer + target group + listener,
# plus an optional AWS WAF web ACL.
#
# Security-group chain (least-open): the ALB SG allows 80 from the internet;
# the ECS SG (in the ecs module) allows the container port ONLY from this ALB
# SG; RDS/Redis SGs allow their ports ONLY from the ECS SG. No direct internet
# access reaches the tasks or data stores.
# =============================================================================

# ---- ALB security group --------------------------------------------------- #
resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb-sg"
  description = "Public ingress to the ALB"
  vpc_id      = var.vpc_id

  # HTTP from anywhere. PRODUCTION: terminate HTTPS:443 here with an ACM cert
  # and redirect 80->443 (see commented listener below).
  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound (to ECS targets)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb-sg" })
}

# ---- Load balancer -------------------------------------------------------- #
resource "aws_lb" "this" {
  name               = "${var.name_prefix}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  # Reliability/Security niceties suitable for the lab.
  drop_invalid_header_fields = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb" })
}

# ---- Target group (ip type, required for Fargate awsvpc networking) ------- #
resource "aws_lb_target_group" "api" {
  name        = "${var.name_prefix}-api-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip" # Fargate tasks register by IP, not instance id

  health_check {
    path                = var.health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Give in-flight requests time to finish when a task is draining (rolling deploy).
  deregistration_delay = 30

  tags = merge(var.tags, { Name = "${var.name_prefix}-api-tg" })
}

# ---- HTTP listener -------------------------------------------------------- #
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# PRODUCTION (HTTPS/ACM) — request/validate an ACM cert, then:
# resource "aws_lb_listener" "https" {
#   load_balancer_arn = aws_lb.this.arn
#   port              = 443
#   protocol          = "HTTPS"
#   ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
#   certificate_arn   = var.acm_certificate_arn
#   default_action { type = "forward"  target_group_arn = aws_lb_target_group.api.arn }
# }
# ...and switch the :80 listener default_action to a redirect to :443.
# Skipped in the lab: ACM domain validation needs a hosted zone / DNS we don't own.

# ---- Optional WAF web ACL ------------------------------------------------- #
# Cost vs protection trade-off: count-gated so we can disable it to save money.
resource "aws_wafv2_web_acl" "this" {
  count = var.enable_waf ? 1 : 0

  name        = "${var.name_prefix}-waf"
  description = "Common protections + rate limiting for the IMS ALB"
  scope       = "REGIONAL" # ALB is regional (CLOUDFRONT scope is for CF only)

  default_action {
    allow {}
  }

  # AWS-managed common rule set (OWASP-ish: bad inputs, common exploits).
  rule {
    name     = "AWSCommonRules"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-common"
      sampled_requests_enabled   = true
    }
  }

  # Rate-based rule: throttle abusive IPs (DDoS / scraping mitigation).
  rule {
    name     = "RateLimit"
    priority = 2

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-ratelimit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name_prefix}-waf"
    sampled_requests_enabled   = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-waf" })
}

resource "aws_wafv2_web_acl_association" "this" {
  count        = var.enable_waf ? 1 : 0
  resource_arn = aws_lb.this.arn
  web_acl_arn  = aws_wafv2_web_acl.this[0].arn
}
