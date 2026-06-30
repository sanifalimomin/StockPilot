# =============================================================================
# modules/vpc — network foundation.
#
# Layout: 1 VPC, 2 public + 2 private subnets across 2 AZs, 1 IGW, a SINGLE
# NAT gateway, and route tables.
#
# COST vs RELIABILITY trade-off (Well-Architected):
#   We provision ONE NAT gateway (in the first public subnet) shared by both
#   private subnets, rather than one NAT per AZ. This roughly halves NAT cost
#   (~$32/mo + data each) which matters for a budget-capped Learner Lab.
#   The downside: if the NAT's AZ fails, private-subnet egress (image pulls on
#   task restart, outbound API calls) is lost until recovery. For a short-lived
#   lab this is an acceptable risk; production would run one NAT per AZ.
#
# MORE-SECURE / cost ALTERNATIVE (document in report): replace the NAT gateway
# entirely with VPC *interface endpoints* (ECR api+dkr, CloudWatch Logs, SQS,
# SNS, Secrets Manager/SSM) plus a *gateway* endpoint for S3 and DynamoDB.
# Tasks then reach AWS APIs privately without traversing the public internet,
# improving the Security posture and removing NAT data-processing charges — at
# the cost of a flat hourly fee per interface endpoint. Sketched (commented)
# at the bottom of this file.
# =============================================================================

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true # required for RDS/private DNS and endpoints

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

# ---- Public subnets (ALB + NAT live here) --------------------------------- #
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-public-${var.azs[count.index]}"
    Tier = "public"
  })
}

# ---- Private subnets (ECS tasks, RDS, Redis live here) -------------------- #
resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-private-${var.azs[count.index]}"
    Tier = "private"
  })
}

# ---- Single NAT gateway --------------------------------------------------- #
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-nat-eip" })
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id # NAT sits in the first public subnet
  depends_on    = [aws_internet_gateway.this]

  tags = merge(var.tags, { Name = "${var.name_prefix}-nat" })
}

# ---- Route tables --------------------------------------------------------- #
# Public route table: default route to the IGW.
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-public-rt" })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Single private route table shared by both private subnets — both egress via
# the one NAT gateway (this is what makes the single-NAT cost saving work).
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-private-rt" })
}

resource "aws_route" "private_nat" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this.id
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# -----------------------------------------------------------------------------
# ALTERNATIVE (commented): VPC endpoints instead of / alongside the NAT.
# Uncomment + remove the NAT to harden egress and drop NAT data charges.
#
# resource "aws_vpc_endpoint" "s3" {              # gateway endpoint, no hourly cost
#   vpc_id            = aws_vpc.this.id
#   service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
#   vpc_endpoint_type = "Gateway"
#   route_table_ids   = [aws_route_table.private.id]
# }
# resource "aws_vpc_endpoint" "ecr_dkr" {         # interface endpoint, hourly fee
#   vpc_id              = aws_vpc.this.id
#   service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.dkr"
#   vpc_endpoint_type   = "Interface"
#   subnet_ids          = aws_subnet.private[*].id
#   private_dns_enabled = true
#   security_group_ids  = [<endpoint-sg allowing 443 from ECS SG>]
# }
# ...repeat for ecr.api, logs, sqs, sns, ssm/secretsmanager...
# -----------------------------------------------------------------------------
