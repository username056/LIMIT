# Limit 운영 Runbook

## 1. 안전 원칙

- 서버 변경, 패키지 설치, 재부팅, UFW, Docker, Swap, Nginx/Apache, 배포 실행은 사용자 승인 후 수행한다.
- Gerrit 3.13.1과 Apache HTTPS 8989를 변경하거나 중지하지 않는다.
- 실제 Secret, PEM, `.env`, 인증서는 저장소와 CI 로그에 남기지 않는다.
- 운영 배포는 보호된 SemVer 태그의 수동 job에서 검증된 image digest만 승격한다.

## 2. 확인된 환경과 적용 전 재확인

2026-07-19 서버에 직접 접속해 Ubuntu 24.04.3, 4 vCPU, 약 15 GiB RAM, 309 GiB 루트 디스크를 확인했다. 승인 후 패키지 61개를 업데이트하고 재부팅해 커널 `6.17.0-1019-aws`를 적용했다. `/swapfile` 4 GiB와 `vm.swappiness=10`을 설정했으며 Docker Engine 29.6.2, Compose 5.3.1을 공식 Docker apt 저장소에서 설치했다. Docker 기본 설정은 live-restore와 json-file 10 MiB × 5개 로그 회전을 사용한다.

UFW는 incoming/routed deny를 유지하고 22/443/8989만 허용한다. 실제 외부 점검에서는 SSH 22와 Gerrit 8989만 응답했고, Gerrit 내부 8988·29418과 DB·모니터링 포트는 차단됐다. `/opt/httpd`의 `httpd.service`가 Gerrit 8988을 HTTPS 8989로 프록시하며, 재부팅과 Docker 설치 후에도 Gerrit·httpd 응답 200을 확인했다. 443은 허용돼 있지만 아직 리스너가 없다.

Docker 테스트 컨테이너를 `127.0.0.1:18080`에 바인딩해 로컬 응답과 외부 차단을 확인한 뒤 컨테이너와 테스트 이미지를 제거했다. 운영 변경 전에는 다음을 다시 확인한다.

```bash
uname -a
free -h
df -h
sudo ufw status verbose
sudo ss -lntup
sudo /opt/httpd/bin/httpd -S
sudo /opt/httpd/bin/httpd -t
test -S /var/run/docker.sock && docker version
test -f /var/run/reboot-required && cat /var/run/reboot-required
```

추가 재부팅, 패키지 업데이트, Swap 변경, Docker 업그레이드는 각각 다시 승인받는다. SSH 키 원본 권한은 수정하지 않고 필요한 경우 임시 사본을 600으로 제한하며 내용은 출력하지 않는다.

## 3. 서버 배치

권장 배치 경로는 `/opt/limit`이다. 배포 사용자는 이 경로와 Docker에 필요한 최소 권한만 갖고, `sudo -n install`, `nginx -t`, `systemctl reload nginx`만 제한적으로 허용한다. Docker socket 권한은 사실상 root 권한이므로 GitLab project runner/배포 사용자 범위를 protected branch/tag로 제한한다.

서버의 배포 사용자로 GitLab Container Registry에 로그인해 private image pull을 확인한다. 가능하면 장기 개인 토큰 대신 read-registry 범위의 project deploy token을 사용하고 서버 전용 Docker credential store에 보관한다.

`infra/.env`는 서버에서 600 권한으로 생성하고 `.env.example`의 변수 이름만 참고한다. Mongo URI에는 사용자명·비밀번호와 `authSource=admin`을 포함한다. exporter용 MySQL client 파일과 Qdrant API key 파일도 서버 전용 경로에 600 권한으로 만들고 다음 변수로 연결한다.

Spring profile은 `local`, `prod`만 사용한다. Compose도 `compose.yml` base와 `compose.local.yml`, `compose.prod.yml`만 유지한다. dev 배포는 운영 설정과의 차이를 줄이기 위해 `compose.prod.yml`과 `prod` profile을 그대로 사용하고 Compose project/state만 `limit-dev`로 분리한다. Testcontainers 테스트는 별도 profile 없이 동적 접속 정보를 주입한다.

Blue/Green host port가 8081/8082로 고정되어 있으므로 dev와 prod를 같은 EC2에서 동시에 실행할 수 없다. 자동 dev 배포와 운영 배포를 병행하려면 별도 dev 호스트를 사용해야 하며, 단일 EC2만 사용할 때는 운영 전환 시 dev stack을 중지하는 별도 승인 절차가 필요하다.

```text
MYSQL_EXPORTER_CONFIG_FILE=/opt/limit-secrets/mysql-exporter.my.cnf
QDRANT_API_KEY_FILE=/opt/limit-secrets/qdrant-api-key
```

## 4. Compose 기동

로컬에서는 DB 포트도 루프백에만 열린다.

```bash
docker compose --env-file infra/.env -p limit-local \
  -f infra/compose.yml -f infra/compose.local.yml up -d --build
```

로컬 Compose는 백엔드와 함께 Prometheus, Loki, Grafana를 기본 기동하고 백엔드는 호스트 `127.0.0.1:18080`에 바인딩한다. `scripts/open-platform-tools.ps1 local`은 추적되지 않는 `infra/.env`가 없으면 필요한 로컬 비밀값을 생성하고 Docker Desktop 기동을 확인한 다음, Swagger의 `/v3/api-docs`와 Grafana의 `/api/health`가 준비될 때까지 기다려 두 화면을 브라우저에서 연다. `prod` 인자는 이미 배포된 `docs.l1mit.shop`, `grafana.l1mit.shop`의 상태를 확인하고 공개 화면을 연다.

운영 데이터 계층은 published port가 없다. 앱은 배포 스크립트가 한 색상씩 기동한다.

```bash
docker compose --env-file infra/.env -p limit-prod \
  -f infra/compose.yml -f infra/compose.prod.yml up -d mysql mongodb redis qdrant

docker compose --env-file infra/.env -p limit-prod \
  -f infra/compose.yml -f infra/compose.prod.yml --profile observability up -d
```

최초 EC2 bootstrap에서는 실제 비밀값을 저장소에 넣지 않고 서버에서 생성한 뒤 데이터 계층, 첫 blue 백엔드와 핵심 관측 서비스를 기동한다. MySQL exporter는 별도 최소권한 계정이 승인·생성되기 전에는 시작하지 않는다.

```bash
bash scripts/bootstrap-ec2-stack.sh
```

기동 후 `docker compose ps`, `docker inspect`, `ss -lntup`을 교차 확인한다. 외부 호스트에서도 3306/27017/6379/6333/6334/3000/9090/3100/9093 연결 실패를 확인한다.

## 5. Nginx 443 공존안

현재 우선안은 새 Nginx가 443만 듣고 기존 Apache/Gerrit은 8989를 유지하는 방식이다. 실제 적용 전에 443 listener와 인증서 경로를 확인한다. Nginx `http` 문맥에서 `/etc/nginx/conf.d/limit-upstream.conf`를 include하고 API server block은 `proxy_pass http://limit_backend`를 사용한다. 배포 스크립트가 이 upstream 파일만 원자적으로 바꾼다.

Nginx exporter용 `stub_status`는 8088에서 제공하되 UFW에 포트를 추가하지 않고 Docker bridge CIDR만 `allow`, 나머지는 `deny all`로 제한한다. `host.docker.internal`에서 접근되는 실제 bridge 주소와 외부 접근 차단을 확인한다. `nginx -t` 성공 전에는 reload하지 않는다.

운영 진입점은 역할별 서브도메인으로 분리한다.

| 도메인 | 역할 | 공개 범위 |
| --- | --- | --- |
| `admin.l1mit.shop` | 운영 포털 | 정적 링크와 API readiness만 표시 |
| `api.l1mit.shop` | REST API | `/api/v1/*`, `/health`만 프록시 |
| `docs.l1mit.shop` | Swagger UI | Swagger UI와 `/v3/api-docs`만 프록시 |
| `grafana.l1mit.shop` | Grafana | Grafana 로그인 필수, 익명 가입 차단 |

Cloudflare의 네 도메인은 `infra/terraform/dns`에서 GitLab protected variable `DEPLOY_HOST`와 같은 EC2 public hostname으로 향하는 DNS-only CNAME으로 관리한다. 루트와 `www` 프론트 레코드 및 ACM 검증 레코드는 기존 AWS 프론트 스택과 충돌하지 않도록 이 DNS 스택에서 제외한다. Cloudflare API Token은 `l1mit.shop` zone의 DNS 편집 권한으로 제한하고 `CLOUDFLARE_API_TOKEN` 환경변수로만 전달한다. `scripts/provision-ec2-entrypoints.sh`는 Nginx를 설치하고 HTTP-01 인증용 80 포트를 연 뒤 Let's Encrypt 인증서와 HTTPS 프록시를 구성한다. 인증서 갱신을 위해 80 포트는 ACME challenge와 HTTPS redirect에만 사용한다. 기존 Apache/Gerrit 8989 설정은 수정하지 않는다.

```bash
# 로컬에서 infra/terraform/dns plan/apply 후
sudo bash scripts/provision-ec2-entrypoints.sh prepare
# DNS 전파 확인 후
sudo bash scripts/provision-ec2-entrypoints.sh full
```

Swagger는 Spring `prod` profile에서 기본적으로 비활성화하고 Compose 운영 배포에서만 명시적으로 활성화한다. Grafana는 `127.0.0.1:3000` 바인딩을 유지하며 Nginx를 통해서만 외부에 제공한다.

Spring Boot 4의 MongoDB 연결 속성은 `spring.mongodb.uri`를 사용한다. 이전 버전의 `spring.data.mongodb.uri`를 사용하면 운영 컨테이너가 기본값인 `localhost:27017`로 접속하므로 profile 변경 시 공식 configuration metadata와 실제 readiness를 함께 확인한다.

## 6. Blue-Green 배포와 rollback

```bash
bash scripts/deploy-blue-green.sh prod \
  registry.example/limit/backend@sha256:<64-hex-digest> \
  https://api.example.com
```

스크립트는 비활성 색상을 기동하고 `/actuator/health/readiness`를 반복 확인한 다음 upstream을 전환한다. `/api/v1/hello`까지 smoke test가 성공해야 이전 색상을 중지한다. readiness 실패 시 신규 색상만 제거한다. 전환 후 smoke 실패 시 이전 upstream을 복원·reload하고 신규 색상을 제거한다.

수동 rollback은 이전 digest로 같은 명령을 실행한다. destructive DB migration은 이미지 rollback으로 복구되지 않으므로 Flyway migration은 expand-and-contract 방식으로 작성하고 운영 migration은 별도 승인을 받는다.

## 7. 장애 확인과 로그

```bash
docker compose --env-file infra/.env -p limit-prod \
  -f infra/compose.yml -f infra/compose.prod.yml ps
docker compose --env-file infra/.env -p limit-prod \
  -f infra/compose.yml -f infra/compose.prod.yml logs --since 15m backend-blue
curl -fsS http://127.0.0.1:8081/actuator/health/readiness
curl -fsS http://127.0.0.1:8082/actuator/health/readiness
```

Grafana는 `https://grafana.l1mit.shop`에서 로그인하거나 장애 시 SSH 터널로 `127.0.0.1:3000`에 접속한다. `Limit Platform Overview` Canvas에서 노드를 클릭하면 해당 service 라벨의 Loki Explore로 이동한다. `userId`, 전체 URL, traceId는 Prometheus/Loki label로 승격하지 않는다. EC2 자체 장애는 내부 스택이 감지하지 못하므로 외부 uptime monitor가 별도로 필요하다.

## 8. 백업과 복구 훈련

- MySQL: 일관성 옵션을 적용한 `mysqldump`를 압축한다.
- MongoDB: `mongodump --archive --gzip`을 사용한다.
- Qdrant: collections별 snapshot API를 사용한다.
- Redis: 영속 데이터가 재생성 불가능한 경우 RDB/AOF 사본을 포함한다.
- 결과물은 SSE-KMS가 적용된 별도 S3 버킷에 올리고 로컬 임시본을 제거한다. lifecycle 보존 기간과 실패 알림을 설정한다.

월 1회 격리된 Compose project와 별도 볼륨에 최신 백업을 복원하고 행 수·대표 쿼리·Qdrant collection/vector 수·애플리케이션 smoke를 검증한다. 현재 S3 버킷, KMS, IAM, 보존 주기와 실제 데이터가 제공되지 않았으므로 자동 백업 실행과 restore drill은 미완료다. 이 작업은 IAM/운영 데이터 변경 승인을 받은 뒤 수행한다.

MySQL exporter 전용 최소권한 계정 생성도 운영 DB 변경 승인 후 수행한다. `PROCESS`, `REPLICATION CLIENT`, `SELECT`만 필요한 범위로 부여하고 exporter client 파일에 기록하며 애플리케이션 계정이나 root 계정을 재사용하지 않는다.

## 9. GitLab 설정 체크리스트

- `dev`, `main`, `v*`를 protected로 설정하고 직접 push를 금지한다.
- shared runner/Container Registry 사용 가능 여부를 확인한다. 불가하면 EC2 project runner를 `concurrent = 1`로 시작한다.
- protected/file variables: `DEPLOY_SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`.
- protected variables: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PATH`, `SMOKE_BASE_URL`, `SONAR_HOST_URL`, `SONAR_TOKEN`.
- 프론트 protected variables: `AWS_DEPLOY_ROLE_ARN`, `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`, `FRONTEND_PUBLIC_URL`, `VITE_API_BASE_URL`.
- 운영 수동 job에는 dev에서 검증된 `PRODUCTION_IMAGE=...@sha256:...`를 제공한다.
- CodeRabbit GitLab app을 연결하고 `review-ready` label을 만든다. `.coderabbit.yaml`이 Draft/WIP를 제외하고 해당 label만 opt-in한다.
- 프론트엔드는 개발 기간 동안 ESLint·build로 검증하고, SonarQube와 Quality Gate는 백엔드만 대상으로 한다. 필수 CI와 승인자 리뷰를 merge 조건으로 지정하고 redundant pipeline auto-cancel을 활성화한다.
- GitLab과 GitHub 중 하나만 배포 권한을 갖게 하며 public 전환 전 전체 Git history를 gitleaks로 검사한다.

## 10. 프론트엔드 배포

기준 프론트엔드는 React가 아니라 Vue 3/Vite다. 인프라 코드는 `infra/terraform/frontend`에 있으며 다음을 생성한다.

- public access를 전부 차단한 versioning S3 버킷
- SigV4 `always` 서명을 사용하는 CloudFront OAC와 HTTPS distribution
- 확장자 없는 Vue Router 요청만 `/index.html`로 바꾸는 CloudFront Function
- AWS managed cache·security headers policy
- 선택적 custom domain·ACM·WAF와 GitLab OIDC 배포 role

Terraform state 버킷은 애플리케이션 리소스와 분리해 먼저 생성한다. 실제 값은 추적되지 않는 `terraform.tfvars`, `backend.hcl`에 두고 plan을 검토한 뒤 승인된 작업에서만 apply한다.

```bash
cd infra/terraform/frontend
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

도메인을 구매하기 전에는 `cloudfront_domain_name` output으로 검증한다. custom domain을 연결할 때는 `us-east-1`의 ACM 인증서 ARN을 함께 설정하고 사용하는 DNS provider에서 CloudFront domain으로 CNAME 또는 alias를 연결한다.

GitLab은 장기 access key 대신 OIDC ID token으로 `AWS_DEPLOY_ROLE_ARN`을 assume한다. IAM trust의 `sub`는 해당 프로젝트의 보호된 `v*` 태그만 허용하고 audience는 CI와 동일한 `sts.amazonaws.com`으로 설정한다. Terraform output의 bucket·distribution·role 값을 protected variable로 등록한다. `VITE_API_BASE_URL`은 브라우저에서 접근 가능한 운영 API의 `/api/v1` 주소이며 Secret이 아니다.

보호된 SemVer 태그에서 `frontend_verify`가 만든 `frontend/dist`만 `frontend_deploy_prod`로 수동 배포한다. `scripts/deploy-frontend.sh`는 먼저 `releases/<commit-sha>/`에 복구본을 보존하고 해시 asset을 1년 immutable로 올린 다음 `index.html`을 no-store로 마지막에 교체한다. CloudFront invalidation은 `/`와 `/index.html`만 수행한다. 사용 중인 이전 해시 asset은 즉시 삭제하지 않으며 S3 versioning과 lifecycle을 함께 사용한다.

배포 job은 invalidation 완료까지 기다리고 `frontend_smoke_prod`가 실제 프론트 URL과 `${VITE_API_BASE_URL}/hello`의 HTTP 성공을 확인한다. 주요 라우트와 사용자 흐름은 별도로 확인한다. 실패하면 GitLab manual job에 이전 commit SHA를 `FRONTEND_ROLLBACK_RELEASE`로 입력해 `frontend_rollback_prod`를 실행한다. release 기본 보존 기간은 30일이며 CloudFront에서는 `/releases/` 직접 접근을 차단한다.

현재 `l1mit.shop`과 `www.l1mit.shop`은 private S3 + CloudFront OAC 구조로 적용됐고 ACM 인증서와 HTTPS 연결까지 확인했다. GitLab OIDC provider와 배포 role은 아직 생성하지 않았으므로 프론트 자동 배포는 AWS 로그인 세션이 아니라 OIDC 구성을 완료한 뒤 활성화한다.
