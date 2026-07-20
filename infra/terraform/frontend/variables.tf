variable "project_name" {
  description = "리소스 이름에 사용할 프로젝트 식별자"
  type        = string
  default     = "limit"

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.project_name))
    error_message = "project_name은 소문자, 숫자, 하이픈만 사용할 수 있습니다."
  }
}

variable "environment" {
  description = "배포 환경 이름"
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["local", "prod"], var.environment)
    error_message = "environment는 local 또는 prod여야 합니다."
  }
}

variable "aws_region" {
  description = "S3 버킷을 생성할 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "bucket_name" {
  description = "전역에서 고유한 프론트엔드 S3 버킷 이름"
  type        = string
}

variable "custom_domain_name" {
  description = "선택적 CloudFront CNAME. 인증서와 DNS는 별도로 준비합니다."
  type        = string
  default     = null
  nullable    = true
}

variable "additional_domain_names" {
  description = "CloudFront와 인증서에 함께 연결할 추가 도메인"
  type        = list(string)
  default     = []
}

variable "create_acm_certificate" {
  description = "CloudFront용 비내보내기 ACM 인증서를 us-east-1에 생성할지 여부"
  type        = bool
  default     = false

  validation {
    condition     = !var.create_acm_certificate || var.custom_domain_name != null
    error_message = "ACM 인증서를 생성하려면 custom_domain_name이 필요합니다."
  }
}

variable "attach_custom_domain" {
  description = "검증 완료된 인증서와 custom domain을 CloudFront에 연결할지 여부"
  type        = bool
  default     = false

  validation {
    condition = !var.attach_custom_domain || (
      var.custom_domain_name != null &&
      (var.create_acm_certificate || var.acm_certificate_arn != null)
    )
    error_message = "custom domain 연결 시 도메인과 생성 또는 기존 ACM 인증서가 필요합니다."
  }
}

variable "acm_certificate_arn" {
  description = "custom_domain_name에 사용할 us-east-1 ACM 인증서 ARN"
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.acm_certificate_arn == null || can(regex("^arn:aws:acm:us-east-1:", var.acm_certificate_arn))
    error_message = "CloudFront ACM 인증서는 us-east-1 ARN이어야 합니다."
  }
}

variable "price_class" {
  description = "CloudFront edge 범위"
  type        = string
  default     = "PriceClass_200"

  validation {
    condition     = contains(["PriceClass_100", "PriceClass_200", "PriceClass_All"], var.price_class)
    error_message = "지원되는 CloudFront price class를 입력하세요."
  }
}

variable "web_acl_id" {
  description = "선택적 CloudFront용 AWS WAFv2 Web ACL ARN"
  type        = string
  default     = null
  nullable    = true
}

variable "create_gitlab_deploy_role" {
  description = "기존 GitLab OIDC provider를 신뢰하는 최소권한 배포 role 생성 여부"
  type        = bool
  default     = false
}

variable "gitlab_oidc_provider_arn" {
  description = "AWS 계정에 미리 등록한 GitLab IAM OIDC provider ARN"
  type        = string
  default     = null
  nullable    = true
}

variable "gitlab_oidc_issuer_host" {
  description = "OIDC condition key에 사용할 GitLab issuer host(스킴 제외)"
  type        = string
  default     = "gitlab.com"

  validation {
    condition     = !strcontains(var.gitlab_oidc_issuer_host, "://")
    error_message = "gitlab_oidc_issuer_host에는 https:// 같은 스킴을 넣지 마세요."
  }
}

variable "gitlab_oidc_audience" {
  description = "GitLab ID token과 AWS IAM OIDC provider에 공통으로 등록한 audience"
  type        = string
  default     = "sts.amazonaws.com"
}

variable "gitlab_oidc_subject" {
  description = "허용할 GitLab protected tag subject. 예: project_path:group/project:ref_type:tag:ref:v*"
  type        = string
  default     = null
  nullable    = true
}

variable "release_retention_days" {
  description = "S3 releases/ 경로의 배포 산출물 보존 기간"
  type        = number
  default     = 30

  validation {
    condition     = var.release_retention_days >= 7
    error_message = "rollback을 위해 release_retention_days는 7일 이상이어야 합니다."
  }
}

variable "tags" {
  description = "모든 AWS 리소스에 적용할 태그"
  type        = map(string)
  default = {
    ManagedBy = "Terraform"
    Service   = "frontend"
  }
}
