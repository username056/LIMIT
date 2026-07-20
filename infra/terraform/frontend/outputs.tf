output "frontend_bucket_name" {
  description = "GitLab FRONTEND_BUCKET_NAME에 등록할 S3 버킷 이름"
  value       = aws_s3_bucket.frontend.id
}

output "cloudfront_distribution_id" {
  description = "GitLab CLOUDFRONT_DISTRIBUTION_ID에 등록할 distribution ID"
  value       = aws_cloudfront_distribution.frontend.id
}

output "cloudfront_domain_name" {
  description = "DNS 연결 전에도 사용할 수 있는 CloudFront 기본 도메인"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "gitlab_deploy_role_arn" {
  description = "GitLab AWS_DEPLOY_ROLE_ARN에 등록할 선택적 IAM role ARN"
  value       = var.create_gitlab_deploy_role ? aws_iam_role.gitlab_frontend_deploy[0].arn : null
}

output "acm_certificate_arn" {
  description = "생성된 CloudFront용 ACM 인증서 ARN"
  value       = var.create_acm_certificate ? aws_acm_certificate.frontend[0].arn : var.acm_certificate_arn
}

output "acm_dns_validation_records" {
  description = "DNS provider에 등록할 ACM CNAME 검증 레코드"
  value = var.create_acm_certificate ? {
    for option in aws_acm_certificate.frontend[0].domain_validation_options : option.domain_name => {
      name  = option.resource_record_name
      type  = option.resource_record_type
      value = option.resource_record_value
    }
  } : {}
}
