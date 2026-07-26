variable "project_name" {
  description = "리소스 이름 prefix"
  type        = string
  default     = "limit"
}

variable "environment" {
  description = "운영 환경 이름"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "백업 S3와 KMS를 생성할 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "backup_bucket_name" {
  description = "전역에서 고유한 DB 백업 S3 버킷 이름"
  type        = string
}

variable "backup_retention_days" {
  description = "백업 객체 보존 기간"
  type        = number
  default     = 35

  validation {
    condition     = var.backup_retention_days >= 30
    error_message = "backup_retention_days는 최소 30일이어야 합니다."
  }
}

variable "alert_email" {
  description = "외부 uptime SNS 알림 수신 이메일. 생성 후 구독 확인이 필요합니다."
  type        = string
}

variable "uptime_endpoints" {
  description = "Route 53이 외부에서 확인할 HTTPS endpoint"
  type = map(object({
    fqdn          = string
    resource_path = string
  }))
  default = {
    frontend = {
      fqdn          = "l1mit.shop"
      resource_path = "/"
    }
    api = {
      fqdn          = "api.l1mit.shop"
      resource_path = "/health"
    }
  }
}

variable "tags" {
  description = "공통 AWS tag"
  type        = map(string)
  default = {
    Project   = "limit"
    ManagedBy = "terraform"
  }
}
