# Frontend AWS 인프라

Vue 빌드 결과를 비공개 S3에 저장하고 CloudFront OAC를 통해서만 제공합니다. 확장자가 없는 요청은 CloudFront Function이 `/index.html`로 변경하므로 Vue Router의 history mode도 지원합니다.

## 구성

- S3 public access 전체 차단, Object Ownership 강제, AES256 암호화, versioning
- CloudFront OAC SigV4 서명과 HTTPS redirect
- HTTP/2·HTTP/3, AWS managed cache·security headers policy
- `releases/` 산출물 30일 보관, CloudFront 직접 접근 차단 및 live object noncurrent version 정리
- 선택적 custom domain, ACM, WAF 연결
- 선택적 GitLab OIDC 최소권한 배포 role

## 초기화와 적용

실제 값 파일은 추적되지 않는다. 예시를 복사한 뒤 state backend와 리소스를 순서대로 초기화한다.

```powershell
Copy-Item terraform.tfvars.example terraform.tfvars
Copy-Item backend.hcl.example backend.hcl
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

`terraform apply`는 S3, CloudFront, IAM을 실제로 변경하므로 plan 검토와 운영 승인 후 실행한다. 첫 CloudFront 배포는 수 분 이상 걸릴 수 있다.

도메인이 없으면 CloudFront 기본 도메인으로 먼저 검증한다. `create_acm_certificate=true`, `attach_custom_domain=false`로 먼저 적용하면 Terraform output에 DNS 검증 CNAME이 나온다. DNS provider에 이를 등록해 인증서가 `ISSUED`가 된 후 `attach_custom_domain=true`로 바꾸고 다시 적용한다. 인증서는 CloudFront 요구사항에 따라 `us-east-1`에서 생성된다.

## GitLab OIDC

장기 AWS access key는 저장하지 않는다. AWS에 GitLab OIDC provider를 한 번 등록한 뒤 `create_gitlab_deploy_role`과 관련 변수를 활성화한다. `gitlab_oidc_subject`는 보호된 SemVer 태그만 허용하도록 프로젝트 경로까지 고정한다.

Terraform output을 GitLab protected variable로 등록한다.

| 변수 | Terraform output | 비고 |
| --- | --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | `gitlab_deploy_role_arn` | protected |
| `FRONTEND_BUCKET_NAME` | `frontend_bucket_name` | protected |
| `CLOUDFRONT_DISTRIBUTION_ID` | `cloudfront_distribution_id` | protected |

CI의 ID token audience `sts.amazonaws.com`은 IAM OIDC provider와 `gitlab_oidc_audience`에도 동일하게 등록해야 한다.
