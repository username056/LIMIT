# Limit 운영 Runbook

## 1. 안전 원칙

- 서버 변경, 패키지 설치, 재부팅, UFW, Docker, Swap, Nginx/Apache 변경과 수동 배포 실행은 사용자 승인 후 수행한다. 보호된 `dev`, `main` 브랜치의 애플리케이션 자동 배포는 승인된 CI 정책에 따라 실행한다.
- Gerrit 3.13.1과 Apache HTTPS 8989를 변경하거나 중지하지 않는다.
- 실제 Secret, PEM, `.env`, 인증서는 저장소와 CI 로그에 남기지 않는다.
- 애플리케이션 배포는 보호된 `dev`, `main` 브랜치 push에서 검증된 image digest만 자동 승격한다.

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

서버의 배포 사용자로 Docker Hub private repository에 로그인해 image pull을 확인한다. CI에는 read/write 범위의 전용 Personal Access Token을 사용하고, 서버에는 별도로 발급한 read-only token을 서버 전용 Docker credential store에 보관한다. 개인 비밀번호나 하나의 token을 CI와 서버에서 공유하지 않는다.

`infra/.env`는 서버에서 600 권한으로 생성하고 `.env.example`의 변수 이름만 참고한다. Mongo URI에는 사용자명·비밀번호와 `authSource=admin`을 포함한다. exporter용 MySQL client 파일은 서버 전용 경로에 640 권한으로 만들고, 소유 그룹 ID를 `MONITORING_SECRET_GID`로 지정한다. MySQL exporter에만 이 보조 그룹을 부여해 다른 사용자와 컨테이너의 읽기를 막는다.

운영 환경변수 한두 개를 수정할 때는 로컬 PowerShell에서 `scripts/apply-ec2-env.ps1`에
`KEY=VALUE`를 직접 전달한다. 값은 SSH의 stdin으로만 전송되고 출력하지 않으며, 원격
파일은 600 권한의 백업을 만든 뒤 같은 파일시스템에서 원자적으로 교체한다. 교체 전
`docker compose config --quiet` 검증에 실패하면 기존 파일을 유지한다.

```powershell
.\scripts\apply-ec2-env.ps1 'MAIL_HOST=smtp.example.test' 'MAIL_PORT=587'
.\scripts\apply-ec2-env.ps1 'MAIL_HOST=smtp.example.test' -DryRun
```

명령행에 입력한 값은 PowerShell history에 남을 수 있다. Secret은 인자 없이 스크립트를
실행한 뒤 `KEY=VALUE`를 대화형으로 붙여넣는 방식을 우선한다. 기본적으로 `.env.example`
또는 Compose 계약에 선언된 키만 허용하며, 새 계약을 먼저 코드에 반영할 수 없는 긴급한
경우에만 `-AllowUnknownKey`를 사용한다. 파일 변경은 실행 중인 컨테이너 환경을 바꾸지
않으며 다음 Blue-Green 배포부터 적용된다.

Spring profile은 `local`, `prod`만 사용한다. Compose도 `compose.yml` base와 `compose.local.yml`, `compose.prod.yml`만 유지한다. Testcontainers 테스트는 별도 profile 없이 동적 접속 정보를 주입한다.

`dev`와 `main`은 별도 서버 환경 이름이 아니라 동일한 운영 EC2를 갱신하는 배포 트리거다. 두 브랜치 모두 검증과 이미지 취약점 검사를 통과한 immutable digest를 `limit-prod` Blue/Green 스택에 자동 배포하며 `infra/state/prod.active`를 공유한다. `main`은 프로젝트 종료 시점의 최종 병합에만 사용한다.

```text
MYSQL_EXPORTER_CONFIG_FILE=/opt/limit-secrets/mysql-exporter.my.cnf
MONITORING_SECRET_GID=1000
```

`infra/monitoring/**/*` 또는 `infra/nginx/limit.conf` 변경은 `monitoring_deploy_prod`가 백엔드 이미지를 다시 빌드하지 않고 모니터링 파일만 동기화한다. 원격 `deploy-monitoring.sh`는 Compose 유효성을 검사하고 관측 컨테이너만 기동·재시작하며, Nginx 설정은 기존 파일을 백업한 뒤 `nginx -t`를 통과해야 reload한다. 검증 실패 시 백업 파일을 즉시 복원하고, Grafana·Prometheus·Loki readiness는 제한된 횟수만큼 재시도한다. `infra/compose*.yml`처럼 앱과 관측 스택이 함께 참조하는 파일은 기존 백엔드 배포와 모니터링 배포가 모두 직렬화된 `limit-prod` resource group에서 처리한다.

`monitoring_deploy_prod`의 GitLab environment 이름은 기존 배포 job과 동일한 `production`을 사용한다. `SSH_KNOWN_HOSTS`, `DEPLOY_SSH_PRIVATE_KEY` 등 protected file variable이 `production` scope로 제한되어 있으므로 별도 하위 environment 이름으로 변경하지 않는다.

`/actuator/prometheus`는 애플리케이션 보안 필터에서는 인증 없이 허용하지만 외부 공개 엔드포인트가 아니다. 운영 백엔드 포트는 `127.0.0.1`에만 publish하고, Nginx는 `/api/v1/`과 제한된 health 경로만 프록시하며 그 밖의 `/actuator/**` 요청은 404로 차단한다. Prometheus만 Docker `app` 네트워크에서 백엔드 컨테이너 주소로 scrape한다.

Nginx 설정 원복에서 `CRITICAL` 오류가 나더라도 reload 전이므로 기존 worker는 마지막 정상 설정으로 계속 서비스한다. 운영자는 `infra/state/limit.conf.monitoring.previous`를 활성 include 경로인 `/etc/nginx/conf.d/limit.conf`에 다시 설치하고 `sudo nginx -t`를 통과한 뒤에만 `sudo systemctl reload nginx`를 실행한다.

아키텍처 대시보드 아이콘은 Grafana와 같은 origin의 `https://grafana.l1mit.shop/limit-assets/*.svg`를 사용한다. 이 URL은 Nginx에서 정적 자산으로 제공하며 배포 후 HTTP 200을 확인한다. 아이콘 로딩 실패는 시각화에만 영향을 주고 메트릭 수집에는 영향을 주지 않는다.

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
  -f infra/compose.yml -f infra/compose.prod.yml up -d mysql mongodb redis

docker compose --env-file infra/.env -p limit-prod \
  -f infra/compose.yml -f infra/compose.prod.yml --profile observability up -d
```

최초 EC2 bootstrap에서는 실제 비밀값을 저장소에 넣지 않고 서버에서 생성한 뒤 데이터 계층, 첫 blue 백엔드와 핵심 관측 서비스를 기동한다. MongoDB·Redis exporter는 함께 기동하고, MySQL exporter는 별도 최소권한 계정이 승인·생성되기 전에는 시작하지 않는다.

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

`dev` 또는 `main`에 변경이 병합되어 push pipeline이 생성되면 관련 영역의 검증 잡 이후 배포 잡이 자동 실행된다. 백엔드는 `backend_image`와 `container_scan`을 통과한 digest를 `deploy_prod`가 동일한 `limit-prod` 스택에 배포하고, 프론트엔드는 `frontend_verify` 산출물을 `frontend_deploy_prod`가 배포한다.

백엔드 배포 파일 동기화 단계는 `infra/admin`의 운영 포털 정적 파일도 `/var/www/limit-admin`에 갱신한다.

스크립트는 비활성 색상을 기동하고 `/actuator/health/readiness`를 반복 확인한 다음 upstream을 전환한다. 외부 `/health` smoke test가 성공해야 이전 색상을 중지한다. readiness 실패 시 신규 색상만 제거한다. 전환 후 smoke 실패 시 이전 upstream을 복원·reload하고 신규 색상을 제거한다.

수동 rollback은 보호된 `dev`, `main` 브랜치 파이프라인의 선택적 `rollback_prod` job으로만 실행한다. 실행하지 않은 rollback job은 자동배포 파이프라인 완료를 막지 않는다. 운영자가 이미지 문자열을 입력하지 않으며, job은 `infra/state/prod.active`의 반대 색상에 남은 중지 컨테이너에서 이전 image digest를 자동으로 읽는다. 활성 컨테이너가 실행 중이고 반대 색상 컨테이너가 중지 상태이며 이전 이미지가 `@sha256:<64-hex>` 형식일 때만 기존 Blue/Green 배포 로직을 호출한다.

```bash
# 운영 서버에서의 직접 실행도 이미지 인자를 받지 않는다.
bash scripts/rollback-blue-green.sh https://api.example.com
```

`deploy_prod`와 `rollback_prod`는 모두 `resource_group: limit-prod`와 `interruptible: false`를 사용하므로 동시에 실행되지 않는다. 이전 컨테이너나 digest가 없으면 전환 전에 실패한다. readiness 실패 시 이전 컨테이너만 정리하고 기존 upstream을 유지하며, upstream 전환 후 smoke 실패 시 기존 upstream을 복구한다. 성공한 경우에만 `prod.active`를 이전 색상으로 갱신하고 기존 활성 컨테이너를 중지한다.

이미지 rollback은 애플리케이션 컨테이너만 복구한다. destructive DB migration은 되돌리지 않으므로 Flyway migration은 expand-and-contract 방식으로 작성하고 병합 전 코드리뷰에서 승인받는다.

Flyway 마이그레이션이 포함된 MR은 병합 즉시 운영 DB에 적용되므로 리뷰어는 마이그레이션 파일을 반드시 확인한 뒤 승인한다. 별도 사후 배포 승인 단계는 없다.

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

자동 백업, 격리 복구 훈련, Alertmanager 전송 테스트와 Route 53 외부 uptime 구성은 [DB 백업·복구·알림·외부 uptime Runbook](./backup-alert-uptime.md)을 따른다.

- MySQL: 일관성 옵션을 적용한 `mysqldump`를 압축한다.
- MongoDB: `mongodump --archive --gzip`을 사용한다.
- Redis: 영속 데이터가 재생성 불가능한 경우 RDB/AOF 사본을 포함한다.
- 결과물은 SSE-KMS가 적용된 별도 S3 버킷에 올리고 로컬 임시본을 제거한다. lifecycle 보존 기간과 실패 알림을 설정한다.

월 1회 격리된 Compose project와 별도 볼륨에 최신 백업을 복원하고 행 수·대표 쿼리·MongoDB 문서 및 vector 필드 수·애플리케이션 smoke를 검증한다. 현재 S3 버킷, KMS, IAM, 보존 주기와 실제 데이터가 제공되지 않았으므로 자동 백업 실행과 restore drill은 미완료다. 이 작업은 IAM/운영 데이터 변경 승인을 받은 뒤 수행한다.

MySQL exporter 전용 최소권한 계정 생성도 운영 DB 변경 승인 후 수행한다. `PROCESS`, `REPLICATION CLIENT`, `SELECT`만 필요한 범위로 부여하고 exporter client 파일에 기록하며 애플리케이션 계정이나 root 계정을 재사용하지 않는다.

## 9. GitLab 설정 체크리스트

- `dev`, `main`을 protected로 설정하고 직접 push를 금지한다.
- shared runner 사용 가능 여부를 확인한다. 불가하면 EC2 project runner를 우선 `concurrent = 1`로 등록하고 서버 자원을 확인한다.
- 단일 EC2 Runner는 `concurrent = 5`, `request_concurrency = 5`, job CPU 1.25개, Testcontainers·image build용 service CPU 1.5개, 각각 memory 2 GiB, swap 포함 3 GiB 제한을 사용한다. 호스트와 컨테이너 외부 DNS는 Cloudflare `1.1.1.1`을 우선하고 AWS VPC resolver `172.26.0.2`를 보조로 사용하며, CI image는 로컬 cache를 우선한다. 동시 실행 슬롯들이 같은 Gradle cache를 읽도록 host의 `/var/lib/gitlab-runner/cache`를 `/cache`에 bind mount한다. `sudo bash scripts/configure-gitlab-runner.sh apply`로 적용하고 과도한 load·OOM·DNS 이상 시 즉시 `rollback`한다.
- Docker Hub에 private backend repository를 만들고 `DOCKERHUB_IMAGE=<namespace>/<repository>`를 protected variable로 등록한다.
- `DOCKERHUB_USERNAME`은 protected variable, read/write 권한의 `DOCKERHUB_TOKEN`은 masked/protected variable로 등록한다.
- EC2 배포 사용자는 CI token과 분리된 read-only Docker Hub token으로 `docker login`을 완료한다.
- `backend_unit_test`와 `backend_integration_test`를 병렬 실행하고 각각의 JaCoCo execution data를 artifact로 전달한다.
- `backend_coverage`가 두 execution data를 합산해 coverage report와 검증 완료 JAR을 생성하고, `backend/Dockerfile.ci`가 해당 JAR을 이미지에 넣는다. CI 이미지 단계에서 Gradle 빌드를 다시 실행하지 않는다.
- `backend_image`는 unit, integration, coverage 작업이 모두 성공해야 시작하며 SonarQube 완료는 기다리지 않는다.
- `dependency_check`는 Merge Request에서는 실행되지 않으며 오직 GitLab Pipeline Schedule(예: 매주 1회)로만 동작하므로 프로젝트 설정에서 Schedule을 등록해야 한다. 새 의존성의 취약점은 다음 스케줄 실행 시 발견되어 최대 1주 지연될 수 있다. NVD 캐시(`.gradle/dependency-check-data`)는 최초 실행 시에만 느리고 이후에는 변경분만 받는다.
- PR Agent는 strategy가 없는 child pipeline에서 비동기로 실행한다. child 실패·취소는 부모 MR pipeline과 병합을 막지 않으며, 긴급한 경우 pipeline 변수 `SKIP_PR_AGENT=true`로 child 생성을 생략한다.
- 2026-07-20 로컬 `--rerun-tasks` 기준 기존 직렬 test+integration+coverage는 81초였다. 분리 후 unit 48초와 integration 53초를 병렬 실행하고 coverage/JAR 12초를 이어 실행해 예상 critical path는 약 65초로, 약 20% 단축됐다. 실제 Runner 시간은 Merge Request pipeline에서 계속 기록한다.
- `sonar-project.properties`만 변경되면 전체 테스트 대신 Sonar 분석에 필요한 `classes`만 생성한다. Secret guard는 생략하지 않고 테스트 job과 병렬로 실행한다.
- 배포 job은 원격 실행 전에 `scripts/sync-deploy-files.sh`로 배포 스크립트, Compose 정의와 모니터링 설정을 동기화한다. 서버 전용 `infra/.env`, `infra/secrets`, `infra/state`는 전송하거나 덮어쓰지 않는다.
- 데이터, 활성 백엔드와 관측 컨테이너는 `restart: unless-stopped`로 Docker 재시작 이후 복구한다. Blue/Green 전환 중 명시적으로 중지된 이전 색상은 자동 재시작하지 않는다.
- protected/file variables: `DEPLOY_SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`.
- protected variables: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PATH`, `SMOKE_BASE_URL`, `SONAR_HOST_URL`, `SONAR_TOKEN`.
- 프론트 protected variables: `AWS_DEPLOY_ROLE_ARN`, `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`, `FRONTEND_PUBLIC_URL`, `VITE_API_BASE_URL`.
- `deploy_prod`는 보호된 `dev`, `main` push에서 검증·생성된 `DEPLOY_IMAGE=...@sha256:...` artifact만 자동 배포한다. 운영자가 별도 image 문자열을 입력하지 않는다.
- `rollback_prod`는 보호된 `dev`, `main` 파이프라인에서 수동으로 노출하며 서버에 남아 있는 반대 색상 컨테이너의 immutable digest를 자동 선택한다. 운영자가 rollback image 변수를 입력하지 않는다.
- PR-Agent는 Merge Request 파이프라인의 `pr_agent_review` job에서 `pragent/pr-agent:0.35.0` 컨테이너로 1회 실행한다. 같은 프로젝트에서 `dev` 또는 `main`으로 향하는 MR만 검토하며 실패해도 필수 CI를 차단하지 않는다.
- GitLab CI/CD 변수에 MR 조회·댓글 작성에 필요한 최소 `api` 권한의 전용 Bot 또는 Project Access Token을 `GITLAB_PERSONAL_ACCESS_TOKEN`으로, SSAFY GMS Key를 `OPENAI_KEY`로 등록한다. 두 값은 masked/hidden으로 관리하며 저장소 파일이나 job 로그에 출력하지 않는다.
- OpenAI 호환 API는 GMS Base URL `https://gms.ssafy.io/gmsapi/api.openai.com/v1`을 사용한다. 기본 리뷰 모델은 비용 효율을 위해 `gpt-5-mini`, reasoning effort는 `low`로 고정하고 불필요한 중복 호출을 막기 위해 fallback model은 사용하지 않는다.
- 파이프라인은 `review`와 `improve`를 순차 실행하고, 아직 기본 브랜치에 병합되지 않은 MR에서도 동일하게 동작하도록 PR-Agent 0.35.0이 지원하는 CLI 설정 옵션을 명시한다. 전체 리뷰는 최대 6개 finding을 반환하고, 개선 제안은 chunk당 최대 5개·호출 1회로 제한하며 중요도 7 이상만 인라인 댓글로 함께 게시한다. 제안 제목은 점수 8~10에 🔴, 7에 🟡, 5~6에 🟢 신호등을 붙인다.
- 모델 변경 시 `.pr_agent.toml`과 `.gitlab-ci.yml`의 `CONFIG__MODEL`, `CONFIG__FALLBACK_MODELS`를 함께 수정한다. Python 종료 코드와 PR-Agent의 구조화된 `ERROR` 로그를 함께 검사하고, 임시 로그는 소유자 전용 권한으로 생성한다. 성공 시 로그를 삭제하고, 실패 시 장문 토큰 패턴을 마스킹해 출력한 뒤 삭제한다.
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

GitLab은 장기 access key 대신 OIDC ID token으로 `AWS_DEPLOY_ROLE_ARN`을 assume한다. IAM trust의 `sub`는 해당 프로젝트의 보호된 `dev`, `main` 브랜치만 허용하고 audience는 CI와 동일한 `sts.amazonaws.com`으로 설정한다. Terraform output의 bucket·distribution·role 값을 protected variable로 등록한다. `VITE_API_BASE_URL`은 브라우저에서 접근 가능한 운영 API의 `/api/v1` 주소이며 Secret이 아니다. 실제 IAM trust 변경은 별도 승인 후 수행한다.

보호된 `dev`, `main` push에서 `frontend_verify`가 만든 `frontend/dist`만 `frontend_deploy_prod`로 자동 배포한다. `scripts/deploy-frontend.sh`는 먼저 `releases/<commit-sha>/`에 복구본을 보존하고 해시 asset을 1년 immutable로 올린 다음 `index.html`을 no-store로 마지막에 교체한다. CloudFront invalidation은 `/`와 `/index.html`만 수행한다. 사용 중인 이전 해시 asset은 즉시 삭제하지 않으며 S3 versioning과 lifecycle을 함께 사용한다.

배포 job은 invalidation 완료까지 기다리고 `frontend_smoke_prod`가 실제 프론트 URL과 API origin의 `/health` 응답 성공을 확인한다. 주요 라우트와 사용자 흐름은 별도로 확인한다. 실패하면 GitLab manual job에 이전 commit SHA를 `FRONTEND_ROLLBACK_RELEASE`로 입력해 `frontend_rollback_prod`를 실행한다. release 기본 보존 기간은 30일이며 CloudFront에서는 `/releases/` 직접 접근을 차단한다.

현재 `l1mit.shop`과 `www.l1mit.shop`은 private S3 + CloudFront OAC 구조로 적용됐고 ACM 인증서와 HTTPS 연결까지 확인했다. GitLab OIDC provider와 배포 role은 아직 생성하지 않았으므로 승인된 OIDC 구성을 완료하기 전까지 프론트 자동 배포 job은 AWS 인증 단계에서 실패한다.
