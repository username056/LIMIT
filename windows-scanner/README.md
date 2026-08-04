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

출력 파일은 `src/LimitScanner/bin/Release/net8.0-windows/win-x64/publish/LimitScanner.exe`다.

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

진단 파일 업로드 후 다음 검사를 순서대로 실행한다.

- 스피커: Windows 출력 장치를 선택해 전체·왼쪽·오른쪽 채널 테스트음을 재생한다.
- 디스플레이: 선택한 화면에 검정·흰색·빨강·초록·파랑을 전체화면으로 표시한다.
- 충전: Windows가 보고하는 AC 연결 상태와 실행 중 연결·분리 이벤트를 기록한다.

각 단계에서 사용자가 정상·이상 또는 건너뜀을 선택하면 검사 결과를 `test-results` API로
전송한다. 충전 검사는 Windows의 AC 상태 감지만 확인하므로 충전기 출력, 케이블, 충전 단자
전체의 정상 여부를 보증하지 않는다.
