locals {
  resource_name = "${var.project_name}-${var.environment}-operations"
}

resource "aws_kms_key" "backup" {
  description             = "${local.resource_name} database backup encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_kms_alias" "backup" {
  name          = "alias/${local.resource_name}-backup"
  target_key_id = aws_kms_key.backup.key_id
}

resource "aws_s3_bucket" "backup" {
  bucket        = var.backup_bucket_name
  force_destroy = false
}

resource "aws_s3_bucket_ownership_controls" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.backup.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "database-backup-retention"
    status = "Enabled"

    filter {
      prefix = "datastores/"
    }

    transition {
      days          = 7
      storage_class = "GLACIER_IR"
    }

    expiration {
      days = var.backup_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.backup_retention_days
    }
  }

  depends_on = [aws_s3_bucket_versioning.backup]
}

data "aws_iam_policy_document" "backup_bucket" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.backup.arn,
      "${aws_s3_bucket.backup.arn}/*",
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "backup" {
  bucket = aws_s3_bucket.backup.id
  policy = data.aws_iam_policy_document.backup_bucket.json
}

data "aws_iam_policy_document" "backup_writer" {
  statement {
    sid       = "ListBackupPrefix"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.backup.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["datastores/*"]
    }
  }

  statement {
    sid = "ReadWriteBackupObjects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.backup.arn}/datastores/*"]
  }

  statement {
    sid = "UseBackupKmsKey"
    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKey",
    ]
    resources = [aws_kms_key.backup.arn]
  }
}

resource "aws_iam_policy" "backup_writer" {
  name        = "${local.resource_name}-backup-writer"
  description = "Least-privilege upload and restore access for Limit database backups"
  policy      = data.aws_iam_policy_document.backup_writer.json
}

resource "aws_sns_topic" "uptime" {
  provider = aws.us_east_1
  name     = "${local.resource_name}-uptime"
}

resource "aws_sns_topic_subscription" "uptime_email" {
  provider  = aws.us_east_1
  topic_arn = aws_sns_topic.uptime.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_route53_health_check" "uptime" {
  for_each = var.uptime_endpoints

  fqdn              = each.value.fqdn
  port              = 443
  type              = "HTTPS"
  resource_path     = each.value.resource_path
  failure_threshold = 3
  request_interval  = 30
  measure_latency   = true
  enable_sni        = true

  tags = {
    Name = "${local.resource_name}-${each.key}"
  }
}

resource "aws_cloudwatch_metric_alarm" "uptime" {
  for_each = var.uptime_endpoints
  provider = aws.us_east_1

  alarm_name          = "${local.resource_name}-${each.key}-unhealthy"
  alarm_description   = "Route 53 external health check failed for ${each.value.fqdn}${each.value.resource_path}"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  metric_name         = "HealthCheckStatus"
  namespace           = "AWS/Route53"
  period              = 60
  statistic           = "Minimum"
  threshold           = 1
  treat_missing_data  = "breaching"

  dimensions = {
    HealthCheckId = aws_route53_health_check.uptime[each.key].id
  }

  alarm_actions = [aws_sns_topic.uptime.arn]
  ok_actions    = [aws_sns_topic.uptime.arn]
}
