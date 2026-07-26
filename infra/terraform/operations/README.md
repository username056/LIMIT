# 운영 복구·외부 uptime Terraform

이 스택은 애플리케이션 배포와 분리해 다음 리소스를 관리한다.

- SSE-KMS, versioning, lifecycle이 적용된 비공개 DB 백업 S3
- EC2 backup 작업에 연결할 최소권한 IAM policy
- `l1mit.shop`, `api.l1mit.shop/health` Route 53 외부 health check
- `us-east-1` CloudWatch alarm과 SNS 이메일 알림

## 적용

실제 값은 추적되지 않는 파일에 둔다.

```bash
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

SNS 이메일은 `terraform apply`만으로 활성화되지 않는다. 수신 메일의 `Confirm subscription`을 눌러야 한다.

`backup_writer_policy_arn` output은 EC2 instance role에 연결한다. IAM 연결 후 EC2에 AWS CLI를 설치하고 아래 output을 `infra/.env`에 등록한다.

```text
BACKUP_UPLOAD_ENABLED=true
BACKUP_S3_URI=<backup_bucket_uri>
BACKUP_KMS_KEY_ID=<backup_kms_key_arn>
```

IAM·KMS·S3 생성 및 instance role 변경에는 운영 승인이 필요하다. apply 전 예상 비용과 plan을 검토한다.
