# Limit Windows Scanner

Windows가 제공하는 `dxdiag.exe`와 `powercfg.exe`를 실행해 진단 파일을 만들고,
웹에서 발급한 일회용 연결 코드로 Limit 검사 세션에 업로드하는 무설치 WinForms 앱이다.

SDK는 `global.json`에서 .NET 8.0.415로 고정한다.

## 로컬 실행

```powershell
$env:LIMIT_API_BASE_URL='http://localhost:18080/'
dotnet run --project .\src\LimitScanner\LimitScanner.csproj
```

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

- 키보드: 웹 검사와 동일한 60개 판정 대상 키를 표시한다. 모든 대상 키가 감지되면 성공,
  하나라도 누락되면 누락 목록을 포함한 부분 실패 결과를 만든다. `Fn`, Win, 좌·우 Alt,
  오른쪽 Shift처럼 OS가 선점하거나 구분을 보장하지 않는 키는 자동 판정 대상에서 제외한다.
- 포인터: 기존과 동일하게 이동, 좌클릭, 우클릭, 휠 스크롤을 각각 감지한다.

각 단계의 완료 또는 건너뛰기 결과를 `test-results` API로 전송한다. 키보드·포인터 검사가 끝나도
세션을 바로 완료 처리하지 않는다. 백엔드의 검사 세션 상태는
`PAIRED`/`UPLOADING`일 때만 `test-results` 재제출을 허용하고 `complete` 호출 이후에는
거부하므로(`INS039`), 메인 화면에서 키보드·포인터를 원하는 만큼 재검사하고 그 결과만 다시
전송할 수 있게 하려면 완료 시점을 사용자가 직접 눌러야
한다. 재검사가 더 필요 없으면 "최종 제출" 버튼을 눌러야 세션이 `complete` API로 종료되고
웹 쪽에서 검사 완료 상태를 확인할 수 있다.

## 검증 범위

공통 검사 화면에서는 키보드·포인터 검사를 이전/다음으로 이동하며 개별 실행하거나 재실행할 수 있다. 건너뛴 항목도 다음 검사 진행을 막지 않으며, 미완료 상태에서 창을 닫으면 중간 종료 경고를 표시한다. 키보드는 대상 키별 감지·누락 목록을 기록하고 포인터는 이동, 좌우 클릭, 휠 스크롤을 각각 감지한다.

각 실행 및 재실행은 새 `clientResultId` UUID가 포함된 `ModuleResult`를 만들며, 최종 결과는 서버 `test-results` 요청 DTO로 변환되어 전송된다.

자동 테스트는 키보드 전체 성공·누락 부분 실패, 포인터 필수 입력과 `ModuleResult` 및
`test-results` 요청 DTO의 JSON 계약을 검증한다.

실제 하드웨어는 Windows 실장비에서 다음 smoke test를 수행한다.

1. 키보드 판정 대상 60개 키를 모두 눌렀을 때 성공하는지 확인한다.
2. 일부 키를 누르지 않고 완료했을 때 누락 목록과 부분 실패 결과가 생성되는지 확인한다.
3. 포인터 이동, 좌클릭, 우클릭, 휠 스크롤 감지가 기존과 동일한지 확인한다.
4. 각 검사 결과의 `test-results` API 요청이 성공하는지 확인한다.

현재 서버는 성공한 요청을 영속화하고, 매핑된 체크리스트 항목이 있으면 결과를 반영한다.
동일한 `clientResultId`와 payload의 재전송은 멱등 처리된다. Scanner 작업의 완료 기준은
유효한 결과 요청이 2xx로 완료되는 단계까지이며, 저장 및 체크리스트 반영 검증은 서버
테스트 범위다.
