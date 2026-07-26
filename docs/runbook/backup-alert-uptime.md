# DB 백업·복구·알림·외부 uptime Runbook

## 구성

- `backup-datastores.sh`: 운영 MySQL과 MongoDB를 일관성 dump로 만들고 checksum과 manifest를 포함한 archive를 생성한다.
- `install-backup-cron.sh`: 매일 03:17에 백업을 실행하는 운영 사용자 crontab을 설치한다.
- `restore-backup-drill.sh`: 운영 DB와 격리된 임시 MySQL·MongoDB 컨테이너에 백업을 복구하고 table·collection 수를 검증한다.
- `test-alertmanager-notification.sh`: 고유 테스트 alert를 넣고 Alertmanager 이메일 성공·실패 counter를 확인한다.
- `infra/terraform/operations`: Route 53 외부 HTTPS health check, CloudWatch alarm, SNS 이메일, SSE-KMS 백업 S3를 구성한다.

## 자동 백업

기본값은 EC2의 `/opt/limit/infra/state/backups`에 최근 3일 archive를 보관한다. 이는 실수 복구에는 도움이 되지만 EC2·EBS 장애에는 안전하지 않다. 운영 완료 조건은 별도 S3와 KMS, EC2 instance role을 구성하고 업로드를 활성화하는 것이다.

```text
BACKUP_UPLOAD_ENABLED=true
BACKUP_S3_URI=s3://<bucket>/datastores
BACKUP_KMS_KEY_ID=arn:aws:kms:ap-northeast-2:<account>:key/<id>
BACKUP_LOCAL_RETENTION_DAYS=3
```

`BACKUP_S3_URI`와 KMS ARN은 Secret이 아니지만 운영 환경별 값이므로 `infra/.env`에만 둔다. AWS access key를 파일에 저장하지 않고 EC2 instance role을 사용한다.

수동 백업과 cron 확인:

```bash
cd /opt/limit
bash scripts/backup-datastores.sh
crontab -l | grep 'limit-datastore-backup'
cat infra/state/node-exporter/limit_backup.prom
```

백업 성공 시 node-exporter textfile metric이 갱신된다. Prometheus는 최근 작업 실패, 25시간 이상 성공 없음, metric 부재를 Alertmanager로 전송한다.

## 복구 훈련

복구 스크립트는 `limit-restore-drill-*` 이름의 임시 컨테이너만 생성하며 운영 Compose project·volume·port를 사용하지 않는다.

```bash
cd /opt/limit
bash scripts/restore-backup-drill.sh infra/state/backups/limit-datastores-<timestamp>.tar.gz
bash scripts/restore-backup-drill.sh s3://<bucket>/datastores/limit-datastores-<timestamp>.tar.gz
```

월 1회 최신 S3 archive로 실행한다. checksum, MySQL table, MongoDB collection 복구가 모두 성공해야 훈련 성공이다. 실제 vector document의 개수·차원·대표 유사도 검색은 vector 기능 구현 시 도메인 검증 단계로 추가한다.

## Alertmanager 실제 수신 테스트

```bash
cd /opt/limit
bash scripts/test-alertmanager-notification.sh
```

스크립트는 Alertmanager API 접수 후 40초를 기다리고 email notification counter 증가와 실패 counter 0을 확인한다. 마지막으로 받은편지함에서 테스트 메일을 확인한다. 테스트 alert는 10분 뒤 자동 종료된다.

## 외부 uptime

Route 53 health checker가 EC2 외부에서 아래 공개 endpoint를 30초마다 확인한다.

- `https://l1mit.shop/`
- `https://api.l1mit.shop/health`

3회 연속 실패한 health check의 `HealthCheckStatus`가 2분 연속 1 미만이면 CloudWatch가 `us-east-1` SNS로 경보를 전송한다. 복구 시에도 알림을 보낸다. Terraform 적용 후 SNS 구독 확인 메일의 링크를 눌러야 실제 알림이 활성화된다.

```bash
cd infra/terraform/operations
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan
terraform apply tfplan
```

Route 53 health check와 CloudWatch/SNS, S3/KMS는 비용이 발생한다. apply 전에 plan과 AWS 가격을 확인한다.
