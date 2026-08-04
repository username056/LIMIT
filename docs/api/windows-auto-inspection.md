# Windows 자동 검사

## 범위

판매자가 웹에서 일회용 연결 코드를 발급하고 무설치 `LimitScanner.exe`에서 입력하면,
프로그램이 Windows의 `dxdiag.exe`와 `powercfg.exe`로 진단 파일을 생성해 기존 증거·파싱
흐름으로 업로드한다. 판매자 JWT는 프로그램에 전달하지 않는다.

검사 세션은 `inspection_session`, 선택검사 이력은 `inspection_session_test_result` 테이블에
저장한다. 연결 코드와 에이전트 토큰은 원문 대신 SHA-256 해시만 저장한다. 선택검사 제출은
세션 행을 잠가 같은 세션의 멱등성 확인과 재검사 차수 계산을 직렬화한다.

## 웹 API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/v1/inspection-sessions` | 매물의 자동 검사 세션과 6자리 코드 생성 | 판매자 |
| GET | `/api/v1/inspection-sessions/{sessionKey}` | 검사 상태 조회 | 세션 소유 판매자 |

세션 생성 요청:

```json
{ "listingId": 1001 }
```

## 진단 프로그램 API

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/v1/inspection-agent/sessions/pair` | 6자리 코드를 제한된 토큰으로 교환 |
| POST | `/api/v1/inspection-agent/sessions/{sessionKey}/uploads` | 진단 파일 Presigned URL 생성 |
| POST | `/api/v1/inspection-agent/sessions/{sessionKey}/uploads/{uploadId}/complete` | 업로드 완료 및 기존 파서 실행 |
| POST | `/api/v1/inspection-agent/sessions/{sessionKey}/complete` | 검사 완료 처리 |
| POST | `/api/v1/inspection-agent/sessions/{sessionKey}/test-results` | 선택검사(카메라·마이크 등) 결과 제출 |

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/v1/inspection-sessions/{sessionKey}/test-results` | 선택검사 결과 이력 조회 | 세션 소유 판매자 |

코드는 10분 후 만료되고 한 번 연결되면 재사용할 수 없다. 연결된 검사 세션은 연결 시점부터
1시간 동안 유효하다. 이후 요청에는 pair 응답의
`agentToken`을 `Authorization: Bearer ...`로 전달한다. 토큰은 해당 세션의 진단 파일
업로드와 선택검사 결과 제출에만 사용할 수 있다.

지원 parserType은 `DXDIAG`, `BATTERY_REPORT`다. 서버는 클라이언트가 전달한 체크리스트
항목 ID를 신뢰하지 않고 해당 매물에서 `FILE_PARSE`와 parserType이 일치하는 항목을 직접
찾는다.

### 선택검사 결과 제출

`test-results` 제출 API는 세션 인증과 상태(`PAIRED`/`UPLOADING`)를 검증한 뒤 결과와
재검사 이력을 영구 저장한다. 원본 영상·음성은 저장하지 않으며 `rawDataSaved`는 항상
`false`다. `testedAt`은 동일 시각을 유지한 채 UTC 마이크로초 정밀도로 정규화한다. 조회
API는 서버 저장 순서대로 전체 시도 이력을 반환한다.

요청:

```json
{
  "clientResultId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "testType": "CAMERA",
  "measurementStatus": "DETECTED",
  "userResult": "USER_CONFIRMED",
  "measuredValues": { "width": 1280, "height": 720 },
  "testedAt": "2026-08-03T13:00:00+09:00",
  "errorCode": null
}
```

`testType`은 `CAMERA`, `MICROPHONE`, `KEYBOARD`, `TOUCHPAD`, `SPEAKER`, `DISPLAY`,
`CHARGING` 중 하나다. `attemptNo`, `checklistItemId`, `listingId`, `rawDataSaved`는
서버가 결정하므로 요청에 포함하지 않는다.

`measurementStatus`는 `DETECTED`, `NOT_DETECTED`, `PERMISSION_DENIED`, `UNSUPPORTED`,
`EXECUTION_FAILED`, `NOT_EXECUTED` 중 하나다. 상태와 사용자 결과 조합은 다음 규칙을
따른다.

- `NOT_EXECUTED`는 `SKIPPED`와 함께만 사용할 수 있다.
- `SKIPPED`는 `NOT_EXECUTED`와 함께만 사용할 수 있다.
- `USER_CONFIRMED`는 `DETECTED`와 함께만 사용할 수 있다.
- `userResult: null`은 `CHARGING`만 허용한다. 이 경우 `DETECTED`는 체크리스트
  `SUCCESS`, 그 외 측정 상태는 `FAILED`로 반영한다.
- 실행된 검사에서 `USER_REPORTED_ISSUE`는 체크리스트 `FAILED`로 반영한다.

서버는 `(session_key, client_result_id)`로 멱등 처리한다.

- 최초 UUID 제출: 결과 생성 후 `201 Created`
- 같은 UUID와 같은 payload 재전송: 기존 결과를 반환하고 `200 OK`
- 같은 UUID와 다른 payload 재전송: `409 INSPECTION_TEST_RESULT_IDEMPOTENCY_CONFLICT`
- 새 UUID로 같은 `testType` 재검사: `(session_key, test_type)` 기준 `attemptNo` 증가

응답에는 서버가 결정한 `listingId`와 nullable `checklistItemId`가 포함된다. 매물에 매핑된
선택 기능 항목이 없으면 이력만 저장하고 `checklistItemId`는 `null`로 반환한다.

| testType | checklist itemCode |
| --- | --- |
| CAMERA | `LAP-FTR-CAM` |
| MICROPHONE | `LAP-FTR-MIC` |
| KEYBOARD | `LAP-KBD-005` |
| TOUCHPAD | `LAP-PAD-006` |
| SPEAKER | `LAP-FTR-SPK` |
| DISPLAY | `LAP-DSP-003` |
| CHARGING | `LAP-CHG-007` |

`DISPLAY`, `CHARGING` 결과는 Scanner 제출값으로 기존 영상 증빙을 대체해 최신 체크리스트
상태에 반영한다. 구매자에게 선택검사 상세 측정값을 제공하는 별도 통합 API는 MVP 범위에
포함하지 않는다.

동일한 7개 항목은 Scanner를 실행할 수 없는 경우 웹 실동작 점검 결과(`SUCCESS`/`FAILED`)도
초안 진행도 API에 저장할 수 있다. `DISPLAY`, `CHARGING`의 `VIDEO` 증빙 계약은 유지하며,
웹 성공 결과는 영상 업로드를 대체하는 완료 근거로 인정한다. 요청에서 웹 결과가 빠져도 이미
업로드된 영상의 완료 상태는 초기화하지 않는다.

웹 직접 점검 화면은 상품 체크리스트 구성과 무관하게 위 7개 항목을 항상 제공한다. 결과는
상품의 `web_device_check_results`에 `testType`별로 저장하므로 예전 체크리스트 코드를 가진
상품도 사용할 수 있다. 진단 프로그램 결과와 웹 결과 중 하나가 성공이면 자동 완료로 표시한다.

## 프로그램 빌드

소스와 빌드 방법은 `windows-scanner/README.md`에 있다. 로컬 실행은
`LIMIT_API_BASE_URL` 환경변수를 우선 사용하고, CI는 `WINDOWS_SCANNER_API_BASE_URL`을
단일 EXE에 주입한다. 별도 변수가 없으면 `VITE_API_BASE_URL`의 `/api/v1` 접미사를
제거한 API 루트 주소를 사용한다. 빌드된 파일은 프론트 배포 산출물의
`/downloads/LimitScanner.exe`에 포함되며 `VITE_WINDOWS_SCANNER_URL`로 다른 CDN 주소를
지정할 수도 있다.
