# Cloudflare 운영 DNS

`admin`, `api`, `docs`, `grafana`를 EC2 Nginx로 연결하는 DNS-only CNAME을 관리합니다. 기존 프론트 도메인 `l1mit.shop`, `www.l1mit.shop`과 ACM 검증 레코드는 이 스택에서 변경하지 않습니다.

## 인증

Cloudflare에서 `l1mit.shop` zone으로 범위를 제한하고 DNS 편집 권한만 가진 API Token을 발급합니다. 토큰은 파일에 저장하거나 Git에 커밋하지 않고 현재 PowerShell 세션의 환경변수로만 전달합니다.

```powershell
$env:CLOUDFLARE_API_TOKEN = Read-Host "Cloudflare API Token" -MaskInput
```

Zone ID는 Cloudflare `l1mit.shop` 개요 화면 오른쪽의 API 영역에서 확인합니다. Zone ID 자체는 인증 비밀값이 아니지만 실제 값 파일은 다른 운영 입력과 동일하게 Git에서 제외합니다.

## 적용

프론트 Terraform과 같은 state bucket을 사용하되 state key는 반드시 분리합니다.

```powershell
Copy-Item terraform.tfvars.example terraform.tfvars
Copy-Item backend.hcl.example backend.hcl

# terraform.tfvars의 cloudflare_zone_id와 backend.hcl의 bucket을 실제 값으로 수정
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

적용 후 네 레코드가 Cloudflare 권한 DNS에서 조회되는지 확인하고, EC2에서 `provision-ec2-entrypoints.sh full`을 실행해 인증서와 HTTPS Nginx 설정을 마무리합니다.

```powershell
$names = 'admin.l1mit.shop','api.l1mit.shop','docs.l1mit.shop','grafana.l1mit.shop'
$names | ForEach-Object { Resolve-DnsName -Server anahi.ns.cloudflare.com -Name $_ -Type CNAME }
```
