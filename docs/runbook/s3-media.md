# S3 미디어 저장소 Runbook

## 목적

상품 대표·추가 이미지와 체크리스트 증빙 파일을 개발·운영 환경별 비공개 S3 버킷에 저장한다.
DB에는 객체 자체가 아니라 `bucket`과 `s3Key` 등 메타데이터만 저장한다.

## 리소스

| 환경 | 버킷 | 백엔드 IAM Role |
| --- | --- | --- |
| dev | `l1mit-dev-media-0b849303` | `l1mit-dev-backend` |
| prod | `l1mit-prod-media-0b849303` | `l1mit-prod-backend` |

- 리전: `ap-northeast-2`
- Public Access: 네 가지 설정 모두 차단
- 기본 암호화: SSE-S3 `AES256`
- Versioning: 활성화
- 임시 객체: `tmp/`에서 1일 후 만료
- 미완료 Multipart Upload: 시작 1일 후 중단

## 인증

루트 Access Key를 생성하지 않는다. 최초 부트스트랩은 브라우저 기반 임시 세션을 사용하며,
일상 운영자는 IAM Identity Center, 배포는 GitLab OIDC, EC2 백엔드는 Instance Profile을 사용한다.

```powershell
aws login --profile limit-bootstrap --region ap-northeast-2
aws sts get-caller-identity --profile limit-bootstrap
```

작업이 끝나면 부트스트랩 세션을 제거한다.

```powershell
aws logout --profile limit-bootstrap
```

## 적용과 검증

저장소 루트에서 실행한다.

```powershell
.\infra\aws\s3-media\configure.ps1 -Environment dev -Action Apply
.\infra\aws\s3-media\configure.ps1 -Environment prod -Action Apply
```

설정만 다시 검증할 수 있다.

```powershell
.\infra\aws\s3-media\configure.ps1 -Environment dev -Action Verify
.\infra\aws\s3-media\configure.ps1 -Environment prod -Action Verify
```

## Smoke Test

각 버킷의 `tmp/smoke/`에 고유한 텍스트 객체를 업로드하고 `HEAD`, 다운로드, 삭제를 수행한다.
Versioning이 활성화되어 있으므로 삭제 후에도 이전 버전은 Lifecycle 또는 명시적인 버전 삭제 전까지 남는다.

```powershell
.\infra\aws\s3-media\configure.ps1 -Environment dev -Action Smoke
.\infra\aws\s3-media\configure.ps1 -Environment prod -Action Smoke
```

## CORS

- dev origin: `http://localhost:5173`
- prod origins: `https://l1mit.shop`, `https://www.l1mit.shop`
- methods: `PUT`, `GET`, `HEAD`
- response header: `ETag`

추가 프론트 도메인이 생기면 와일드카드 대신 정확한 HTTPS origin을 스크립트에 추가한 뒤 재적용한다.
브라우저에 `DELETE` 권한을 주지 않고 삭제는 백엔드가 수행한다.

## 환경변수

영상 바이너리 길이를 서버에서 직접 확인하려면 실행 환경에 `ffprobe`를 설치하고
`S3_FFPROBE_ENABLED=true`로 설정합니다. 검증기는 임시 S3 객체의 단기 Presigned GET URL만
프로세스 인자로 전달하며 URL이나 자격 증명을 로그에 남기지 않습니다.

```dotenv
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
S3_MEDIA_BUCKET=l1mit-dev-media-0b849303
S3_ENDPOINT=
S3_PATH_STYLE_ACCESS=false
S3_UPLOAD_TTL=600
S3_DOWNLOAD_TTL=300
S3_PUBLIC_BASE_URL=
```

EC2에서는 `AWS_ACCESS_KEY_ID`와 `AWS_SECRET_ACCESS_KEY`를 비워 두고 Instance Profile 자격 증명을 사용한다.
실제 AWS에서는 `S3_ENDPOINT`를 비워 두며 MinIO나 LocalStack 같은 호환 저장소에서만 설정한다.

### 운영 배포와 GitLab 변수

운영 백엔드는 장기 Access Key를 사용하지 않습니다. `l1mit-prod-backend` Role을 운영 EC2의
Instance Profile에 연결하고, 서버의 `infra/.env`에는 아래 비밀이 아닌 설정만 둡니다.
GitLab의 앱·모니터링 배포는 `apply-ci-s3-env-remote.sh`를 통해 아래 S3 런타임
설정만 서버 `infra/.env`에 원자적으로 반영한 뒤 Compose 설정을 검증합니다.
기존 파일은 타임스탬프 백업으로 보존됩니다. `AWS_ACCESS_KEY_ID`와
`AWS_SECRET_ACCESS_KEY`는 전달 대상에서 제외하며 EC2 Instance Profile을 사용합니다.

```dotenv
AWS_REGION=ap-northeast-2
S3_MEDIA_BUCKET=l1mit-prod-media-0b849303
S3_ENDPOINT=
S3_PATH_STYLE_ACCESS=false
S3_UPLOAD_TTL=600s
S3_DOWNLOAD_TTL=300s
S3_PUBLIC_BASE_URL=
S3_FFPROBE_ENABLED=false
S3_FFPROBE_EXECUTABLE=ffprobe
S3_FFPROBE_TIMEOUT=10s
```

GitLab CI/CD 변수에도 동일한 이름을 `production` environment scope와 `Protected`로 등록해
배포 전 서버 환경 갱신과 설정 검증에 사용합니다. `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`는 등록하거나 서버로 전달하지 않습니다.
CI 작업이 AWS API를 직접 호출해야 할 때만 기존 GitLab OIDC 토큰으로 Role을 assume하고,
그 작업 안에서 발급되는 단기 자격 증명을 사용합니다.

| GitLab 변수 | 운영 값 |
| --- | --- |
| `AWS_REGION` | `ap-northeast-2` |
| `S3_MEDIA_BUCKET` | `l1mit-prod-media-0b849303` |
| `S3_ENDPOINT` | 빈 값 |
| `S3_PATH_STYLE_ACCESS` | `false` |
| `S3_UPLOAD_TTL` | `600s` |
| `S3_DOWNLOAD_TTL` | `300s` |
| `S3_PUBLIC_BASE_URL` | 빈 값 |
| `S3_FFPROBE_ENABLED` | 운영 이미지에 ffprobe 설치 후 `true` |
| `S3_FFPROBE_EXECUTABLE` | `ffprobe` |
| `S3_FFPROBE_TIMEOUT` | `10s` |

GitLab API로 변수를 자동 등록하려면 프로젝트 Maintainer 권한의 `api` scope 토큰이 필요합니다.
Git 저장소 clone/push용 자격 증명만으로는 CI/CD 변수 API를 변경할 수 없습니다.

## 상품 이미지와 증빙 분리

- 상품 대표·추가 이미지는 `listings/{listingId}/images/`와 `listing_image`에 저장한다.
- 체크리스트 증빙은 `evidence/{listingId}/{checklistItemId}/`와 `evidence`에 저장한다.
- 첫 상품 이미지는 대표 이미지(`THUMBNAIL`), 이후 이미지는 추가 이미지(`DETAIL`)로 등록한다.
- 상품 이미지는 최대 10개이며 JPEG, PNG, WebP만 허용한다.
- 대표 이미지가 없더라도 증빙 이미지를 자동 썸네일로 사용하지 않는다.

증빙은 구매자 공개 여부와 개인정보 마스킹 조건이 별도로 존재한다. 공개 가능 여부를 완전히
검증하지 않은 증빙을 상품 목록 썸네일로 자동 노출하지 않고, 판매자가 명시적으로 등록한
`listing_image`만 상품 이미지로 사용한다.

## 런타임 Role 연결

이 계정의 서울 리전에 연결 가능한 EC2 인스턴스가 확인되지 않아 Instance Profile 연결은 보류한다.
대상 EC2가 준비되면 Role을 Instance Profile에 추가하고 EC2에 연결한다. 운영 IAM 변경 승인을 받은 뒤 수행한다.

## 롤백

버킷은 이름 변경이 불가능하며 Versioning된 객체가 하나라도 남으면 삭제할 수 없다. 롤백 전에 다음을 확인한다.

1. 애플리케이션 쓰기를 중지한다.
2. DB가 해당 버킷의 키를 참조하지 않는지 확인한다.
3. 보존해야 할 객체와 이전 버전을 별도 위치에 백업한다.
4. 정확한 환경과 버킷 이름을 다시 확인한다.
5. 객체, 이전 버전, delete marker, 미완료 Multipart Upload를 제거한다.
6. 인라인 정책을 삭제한 뒤 사용 중이지 않은 Role을 삭제한다.
7. 마지막에 빈 버킷을 삭제한다.

아래 명령은 데이터 삭제이므로 자동화하지 않는다. `<environment>`와 `<bucket>`을 실제 값으로 치환하고
변경 승인을 받은 뒤 한 명이 대상 목록을 검토하고 다른 한 명이 실행한다.

```powershell
aws s3api list-object-versions --bucket <bucket> --profile limit-bootstrap
aws s3api list-multipart-uploads --bucket <bucket> --profile limit-bootstrap
aws iam delete-role-policy --role-name l1mit-<environment>-backend --policy-name l1mit-<environment>-media-access --profile limit-bootstrap
aws iam delete-role --role-name l1mit-<environment>-backend --profile limit-bootstrap
aws s3api delete-bucket --bucket <bucket> --region ap-northeast-2 --profile limit-bootstrap
```

객체와 버전 삭제는 복구할 수 없으므로 이 Runbook은 삭제 명령을 제공하지 않는다.
