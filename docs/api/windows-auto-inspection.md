# Windows 자동 검사

## 범위

판매자가 웹에서 일회용 연결 코드를 발급하고 무설치 `LimitScanner.exe`에서 입력하면,
프로그램이 Windows의 `dxdiag.exe`와 `powercfg.exe`로 진단 파일을 생성해 기존 증거·파싱
흐름으로 업로드한다. 판매자 JWT는 프로그램에 전달하지 않는다.

검사 세션은 `inspection_session` 테이블에 저장한다. 연결 코드와 에이전트 토큰은 원문 대신
SHA-256 해시만 저장하며 세션 상태 변경에는 JPA 낙관적 락을 사용한다.

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

코드는 10분 후 만료되고 한 번 연결되면 재사용할 수 없다. 이후 요청에는 pair 응답의
`agentToken`을 `Authorization: Bearer ...`로 전달한다. 토큰은 해당 세션의 진단 파일
업로드에만 사용할 수 있다.

지원 parserType은 `DXDIAG`, `BATTERY_REPORT`다. 서버는 클라이언트가 전달한 체크리스트
항목 ID를 신뢰하지 않고 해당 매물에서 `FILE_PARSE`와 parserType이 일치하는 항목을 직접
찾는다.

## 프로그램 빌드

소스와 빌드 방법은 `windows-scanner/README.md`에 있다. 로컬 실행은
`LIMIT_API_BASE_URL` 환경변수를 우선 사용하고, CI는 `WINDOWS_SCANNER_API_BASE_URL`을
단일 EXE에 주입한다. 별도 변수가 없으면 `VITE_API_BASE_URL`의 `/api/v1` 접미사를
제거한 API 루트 주소를 사용한다. 빌드된 파일은 프론트 배포 산출물의
`/downloads/LimitScanner.exe`에 포함되며 `VITE_WINDOWS_SCANNER_URL`로 다른 CDN 주소를
지정할 수도 있다.
