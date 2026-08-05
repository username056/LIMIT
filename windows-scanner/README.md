# Limit Windows Scanner

Windows가 제공하는 `dxdiag.exe`와 `powercfg.exe`를 실행해 진단 파일을 만들고,
웹에서 발급한 일회용 연결 코드로 Limit 검사 세션에 업로드하는 무설치 WinForms 앱이다.

SDK는 `global.json`에서 .NET 8.0.415로 고정한다.

## 로컬 실행

```powershell
$env:LIMIT_API_BASE_URL='http://localhost:18080/'
dotnet run --project .\src\LimitScanner\LimitScanner.csproj
```

## 로컬 웹 다운로드 준비

로컬 프론트 개발 서버는 GitLab CI처럼 EXE를 자동으로 포함하지 않는다. 일반 `npm run dev`로
`/downloads/LimitScanner.exe`를 받으면 EXE가 없는 응답을 내려받아 Windows에서 손상된 파일로
표시될 수 있다. 아래 명령으로 로컬 전용 EXE를 빌드·복사한 뒤 개발 서버를 실행한다.

```powershell
cd frontend
npm run dev:with-scanner
```

API 주소가 기본 로컬 주소와 다르면 실행 전에 `LIMIT_API_BASE_URL`에 절대 HTTP(S) 주소를 지정한다.
명령은 .NET SDK 8.0.415를 우선 사용하고, SDK가 없으면 실행 중인 Docker Desktop의
`mcr.microsoft.com/dotnet/sdk:8.0.415` 컨테이너로 빌드한다. 생성된
`frontend/public/downloads/LimitScanner.exe`는 gitignore 대상이며 CI·배포 산출물에는 영향을 주지 않는다.

## 단일 실행 파일 빌드

```powershell
dotnet publish .\src\LimitScanner\LimitScanner.csproj `
  -c Release `
  -r win-x64 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -p:PublishTrimmed=false
```

출력 파일은 `src/LimitScanner/bin/Release/net8.0-windows10.0.19041.0/win-x64/publish/LimitScanner.exe`다.

운영 배포본은 `LimitApiBaseUrl` MSBuild 속성으로 공개 API 주소를 주입한다.

```powershell
dotnet publish .\src\LimitScanner\LimitScanner.csproj `
  -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true `
  -p:LimitApiBaseUrl='https://api.example.com/'
```

GitLab CI는 `WINDOWS_SCANNER_API_BASE_URL` 변수로 이 값을 전달하고, 변수가 없으면 기존
`VITE_API_BASE_URL`에서 `/api/v1` 접미사를 제거해 사용한다. 두 변수에는 브라우저가
접근할 수 있는 절대 HTTP(S) URL을 지정해야 한다. 빌드된 EXE는 프론트 배포 산출물의
`/downloads/LimitScanner.exe`에 포함한다. 운영 배포본은 코드 서명 후 제공하는 것을
권장한다. 앱에는 판매자 JWT, AWS 키 또는 고정된 에이전트 토큰을 포함하지 않는다.

## 사용자 선택 검사

진단 파일 업로드 후 키보드와 포인터 검사를 순서대로 실행한다.

- 키보드: WinForms가 감지 가능한 모든 키를 기록하고 눌린 키를 화면에 표시하지만 `Fn`과
  Windows가 선점하는 OS 예약 키 감지는 보장하지 않는다.
- 포인터: 이동, 좌우 클릭, 휠 스크롤을 각각 감지한다.

각 단계에서 사용자가 정상·이상 또는 건너뜀을 선택하면 검사 결과를 `test-results` API로
전송한다.

스피커·디스플레이·충전·카메라·마이크 진단 폼(`SpeakerDiagnosticForm` 등)과 관련 서비스는
코드에 남아 있지만 메인 검사 플로우와 재검사 버튼 목록(`InteractiveDeviceDiagnostics.
CreateModules()`, `MainForm.CreateRerunPanel()`)에서 빠졌다. 카메라·마이크·스피커는
실동작 검증 대신 판매자가 등록 화면에서 직접 확인하는 스펙 항목으로, 디스플레이·충전은
영상 증빙으로만 확인한다 — 실제 영상 없이 이 검사 결과만으로 완료 처리되는 것을 막기 위해서다.
`ModuleTestTypes` enum과 이 폼들은 과거 이력 호환을 위해 즉시 삭제하지 않았다.

### 정상 판정 선행 조건

- UI를 우회해 결과 생성 로직을 호출하더라도 선행 조건이 충족되지 않은 `USER_CONFIRMED`는
  `SKIPPED`로 변환한다(키보드·포인터에는 별도 재생·표시 선행 조건이 없다).

## 검증 범위

공통 검사 화면에서는 키보드·포인터 검사를 이전/다음으로 이동하며 개별 실행하거나 재실행할 수 있다. 건너뛴 항목도 다음 검사 진행을 막지 않으며, 미완료 상태에서 창을 닫으면 중간 종료 경고를 표시한다.

각 실행 및 재실행은 새 `clientResultId` UUID가 포함된 `ModuleResult`를 만들며, 최종 결과는 서버 `test-results` 요청 DTO로 변환되어 전송된다.

자동 테스트는 `ModuleResult`와 `test-results` 요청 DTO의 JSON 계약을 검증한다.

실제 하드웨어는 Windows 실장비에서 다음 smoke test를 수행한다.

1. 실제 키보드로 각 키를 눌러 감지 여부를 확인한다.
2. 실제 포인터(마우스/터치패드)로 이동·클릭·스크롤을 확인한다.
3. 각 검사 결과의 `test-results` API 요청이 성공하는지 확인한다.

현재 서버는 성공한 요청을 영속화하고, 키보드·포인터로 매핑된 체크리스트 항목이 있으면
결과를 반영한다. 그 외 `testType`(카메라·마이크·스피커·디스플레이·충전)은 이력에만
저장되고 체크리스트 항목은 반영하지 않는다. 동일한 `clientResultId`와 payload의 재전송은
멱등 처리된다. Scanner 작업의 완료 기준은 유효한 결과 요청이 2xx로 완료되는 단계까지이며,
저장 및 체크리스트 반영 검증은 서버 테스트 범위다.
