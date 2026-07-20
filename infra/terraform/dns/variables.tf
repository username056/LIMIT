variable "cloudflare_zone_id" {
  description = "l1mit.shop Cloudflare Zone ID"
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{32}$", var.cloudflare_zone_id))
    error_message = "cloudflare_zone_id는 Cloudflare의 32자리 Zone ID여야 합니다."
  }
}

variable "zone_name" {
  description = "DNS zone 이름"
  type        = string
  default     = "l1mit.shop"

  validation {
    condition     = var.zone_name == "l1mit.shop"
    error_message = "이 스택은 l1mit.shop 운영 DNS만 관리합니다."
  }
}

variable "ec2_origin_hostname" {
  description = "관리자·API·문서·Grafana가 실행되는 EC2 public hostname"
  type        = string
  default     = "i15c203.p.ssafy.io"

  validation {
    condition     = can(regex("^[a-zA-Z0-9.-]+$", var.ec2_origin_hostname))
    error_message = "ec2_origin_hostname에는 스킴이나 경로 없이 hostname만 입력하세요."
  }
}

variable "entrypoint_subdomains" {
  description = "EC2 Nginx로 연결할 운영 서브도메인"
  type        = set(string)
  default     = ["admin", "api", "docs", "grafana"]

  validation {
    condition     = var.entrypoint_subdomains == toset(["admin", "api", "docs", "grafana"])
    error_message = "운영 진입점은 admin, api, docs, grafana 네 개를 모두 포함해야 합니다."
  }
}
