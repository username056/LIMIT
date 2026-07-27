output "backup_bucket_uri" {
  description = "EC2 BACKUP_S3_URI에 등록할 prefix"
  value       = "s3://${aws_s3_bucket.backup.bucket}/datastores"
}

output "backup_kms_key_arn" {
  description = "EC2 BACKUP_KMS_KEY_ID에 등록할 KMS key ARN"
  value       = aws_kms_key.backup.arn
}

output "backup_writer_policy_arn" {
  description = "EC2 instance role에 연결할 최소권한 IAM policy ARN"
  value       = aws_iam_policy.backup_writer.arn
}

output "uptime_health_check_ids" {
  description = "Route 53 외부 uptime health check ID"
  value       = { for name, check in aws_route53_health_check.uptime : name => check.id }
}

output "uptime_sns_topic_arn" {
  description = "외부 uptime 알림 SNS topic ARN"
  value       = aws_sns_topic.uptime.arn
}
