locals {
  resource_name                 = "${var.project_name}-${var.environment}-frontend"
  origin_id                     = "${local.resource_name}-s3"
  requested_domain_names        = var.custom_domain_name == null ? [] : concat([var.custom_domain_name], var.additional_domain_names)
  aliases                       = var.attach_custom_domain ? local.requested_domain_names : []
  effective_acm_certificate_arn = var.create_acm_certificate ? aws_acm_certificate.frontend[0].arn : var.acm_certificate_arn
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_response_headers_policy" "security" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_acm_certificate" "frontend" {
  count    = var.create_acm_certificate ? 1 : 0
  provider = aws.us_east_1

  domain_name               = var.custom_domain_name
  subject_alternative_names = var.additional_domain_names
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_s3_bucket" "frontend" {
  bucket        = var.bucket_name
  force_destroy = false
}

resource "aws_s3_bucket_ownership_controls" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    id     = "expire-release-artifacts"
    status = "Enabled"

    filter {
      prefix = "releases/"
    }

    expiration {
      days = var.release_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }

  rule {
    id     = "expire-noncurrent-live-objects"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.release_retention_days
    }
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = local.resource_name
  description                       = "Private S3 origin access for ${local.resource_name}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "${local.resource_name}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrite extensionless Vue routes to index.html"
  publish = true
  code    = file("${path.module}/spa-rewrite.js")
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = local.resource_name
  default_root_object = "index.html"
  aliases             = local.aliases
  price_class         = var.price_class
  http_version        = "http2and3"
  web_acl_id          = var.web_acl_id

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = local.origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  default_cache_behavior {
    target_origin_id           = local.origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD", "OPTIONS"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  dynamic "viewer_certificate" {
    for_each = var.attach_custom_domain ? [] : [1]
    content {
      cloudfront_default_certificate = true
    }
  }

  dynamic "viewer_certificate" {
    for_each = var.attach_custom_domain ? [1] : []
    content {
      acm_certificate_arn      = local.effective_acm_certificate_arn
      minimum_protocol_version = "TLSv1.2_2021"
      ssl_support_method       = "sni-only"
    }
  }

  depends_on = [
    aws_s3_bucket_ownership_controls.frontend,
    aws_s3_bucket_public_access_block.frontend,
  ]
}

data "aws_iam_policy_document" "cloudfront_read" {
  statement {
    sid       = "AllowCloudFrontReadOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.cloudfront_read.json
}

data "aws_iam_policy_document" "gitlab_assume_role" {
  count = var.create_gitlab_deploy_role ? 1 : 0

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [coalesce(var.gitlab_oidc_provider_arn, "arn:aws:iam::000000000000:oidc-provider/invalid.example")]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.gitlab_oidc_issuer_host}:aud"
      values   = [var.gitlab_oidc_audience]
    }

    condition {
      test     = "StringLike"
      variable = "${var.gitlab_oidc_issuer_host}:sub"
      values   = [coalesce(var.gitlab_oidc_subject, "project_path:invalid/invalid:ref_type:tag:ref:invalid")]
    }
  }
}

resource "aws_iam_role" "gitlab_frontend_deploy" {
  count = var.create_gitlab_deploy_role ? 1 : 0

  name               = "${local.resource_name}-gitlab-deploy"
  assume_role_policy = data.aws_iam_policy_document.gitlab_assume_role[0].json

  lifecycle {
    precondition {
      condition = (
        var.gitlab_oidc_provider_arn != null &&
        var.gitlab_oidc_subject != null
      )
      error_message = "배포 role 생성 시 gitlab_oidc_provider_arn과 gitlab_oidc_subject가 필요합니다."
    }
  }
}

data "aws_iam_policy_document" "gitlab_frontend_deploy" {
  count = var.create_gitlab_deploy_role ? 1 : 0

  statement {
    sid       = "ListFrontendBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.frontend.arn]
  }

  statement {
    sid = "ManageFrontendObjects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid       = "InvalidateFrontendDistribution"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [aws_cloudfront_distribution.frontend.arn]
  }
}

resource "aws_iam_role_policy" "gitlab_frontend_deploy" {
  count = var.create_gitlab_deploy_role ? 1 : 0

  name   = "frontend-deploy"
  role   = aws_iam_role.gitlab_frontend_deploy[0].id
  policy = data.aws_iam_policy_document.gitlab_frontend_deploy[0].json
}
