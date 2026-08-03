# 노트북 체크리스트 자동 생성

## 범위

- 1차 지원 기기는 노트북이다.
- 운영체제는 Windows를 정식 지원하고 Linux는 공통 체크리스트로 지원한다.
- 등록된 모델은 내부 카탈로그 정보를 사용한다.
- 카탈로그에 없는 모델은 제조사, 모델명, 모델 코드, 운영체제를 직접 입력해 미리보기 체크리스트를 생성할 수 있다.
- 스마트폰과 태블릿을 추가할 수 있도록 생성 정책을 노트북 정책 클래스로 분리했다.

## 생성 규칙

등록된 모델은 최신 `PUBLISHED` 템플릿을 기본으로 적용한다. 게시 템플릿이 없는 예외 상황과 직접
입력 호환 경로에서만 서버의 노트북 기본 정책을 사용한다. AI는 기본 항목을 삭제하거나 필수 여부를
바꾸지 못한다.

서버 노트북 기본 정책은 기기 식별, 외관, 힌지, 디스플레이, 전원·부팅, 키보드, 터치패드, 충전,
실제 사양 확인, 계정 로그아웃·초기화로 구성한다.

- 힌지: 모든 노트북에서 유격, 소음, 파손, 들뜸과 화면 고정력을 영상으로 확인한다.

- Windows: `battery-report.html`과 `DxDiag.txt` 등록 항목에 더해, 설정 > 시스템 > 정보 화면을 캡처해 OCR로 자동 인식하는 별도 항목(`LAP-SCR-013`)을 추가한다. 이 항목은 `evidenceType=PHOTO`, `automationType=OCR`이며 DxDiag/배터리 리포트 파싱 결과와 별개로 취합·상충 확인된다. 화면 한 장이면 충분해 `maxCount=1`로 제한한다(다른 PHOTO 항목의 기본값은 5).
- Linux: 배터리 상태와 시스템 정보 화면 캡처 항목을 추가한다.
- 추가 기능: 판매자가 확인한 승인 기능 코드만 체크리스트에 추가하며 최대 5개다.
- 유선 LAN(RJ45) 포트와 microSD 카드 슬롯은 각각 독립된 승인 기능과 점검 항목으로 취급한다.
- AI 후보: 공식 제조사 자료에서 발견한 기능을 판매자 확인 후보로 반환한다. 판매자가 `confirmedFeatures`로 다시 전달해야 실제 항목이 된다.
- AI 후보는 한국어 기능명, 선정 이유, 실제 점검 방법을 함께 반환한다. 모델이 영어 설명을 반환하면 서버의 기능별 한국어 안내로 대체한다.
- 승인 목록 밖의 기능은 사용자용 한국어 이름으로 `reviewCandidates`에 반환하며, 전용 점검 방법이 정의되기 전까지 체크리스트에 넣지 않는다.
- 승인된 기능 코드가 잘못 `reviewCandidates`에 포함되더라도 서버가 제거해 내부 코드를 사용자에게 노출하지 않는다.

AI 호출이 비활성화되거나 실패하거나 응답 검증에 실패해도 기본 체크리스트 생성은 성공한다.

## API

### 체크리스트 미리 생성

`POST /api/v1/checklist-generations`

ACTIVE 판매자만 호출할 수 있다.

등록 모델 요청:

```json
{
  "deviceModelId": 201,
  "confirmedFeatures": ["CAMERA", "WIFI"]
}
```

직접 입력 요청:

```json
{
  "manufacturer": "Samsung",
  "modelName": "Galaxy Book4 Pro",
  "modelCode": "NT960XGK-KC51G",
  "osFamily": "WINDOWS",
  "confirmedFeatures": []
}
```

응답의 `aiApplied`는 AI 보강 호출과 응답 검증이 성공했는지를 나타낸다. `false`여도 `items`에는 검증된 기본 항목이 들어간다.

`aiSuggestions` 항목은 다음 설명을 포함한다.

- `featureName`: 사용자에게 표시할 한국어 기능명
- `reason`: 공식 자료를 근거로 이 기능을 후보로 선정한 이유
- `checkGuide`: 판매자가 실제 기기에서 확인할 기능과 점검 방법
- `evidenceType`: 선택 시 생성될 항목의 증빙 유형. 판매 화면은 이 값으로 촬영·업로드 항목과
  판매자 직접 확인 항목을 나눠 표시한다.

`evidenceStatus` 값:

- `VERIFIED`: 공식 자료가 정확한 모델을 명시한다.
- `LIKELY`: 공식 자료가 제품군을 지원하지만 세부 모델이 불명확하다.
- `UNKNOWN`: 공식 근거가 부족하다.
- `CONFLICTED`: 공식 자료끼리 충돌한다.

### 매물 등록과 스냅샷

`POST /api/v1/products`

노트북 등록 요청에 `confirmedFeatures`를 최대 5개까지 전달할 수 있다. 서버는 최신
`PUBLISHED` 모델 템플릿의 기본 항목을 그대로 유지하고 선택한 기능 항목만 추가해 등록 시점의
DRAFT 체크리스트 템플릿과 매물 체크리스트 항목을 생성한다. 따라서 이후 공통 정책이 변경되어도
이미 등록한 매물의 항목은 바뀌지 않는다.

다른 기기 유형도 같은 방식으로 최신 `PUBLISHED` 기본 템플릿과 판매자가 선택한 추가 항목을
합성해 스냅샷으로 사용한다.

## AI 설정

기본값은 AI 비활성화다.

```dotenv
AI_CHECKLIST_ENABLED=true
OPENAI_API_KEY=
OPENAI_RESPONSES_ENDPOINT=https://api.openai.com/v1/responses
OPENAI_CHECKLIST_MODEL=gpt-5.6-luna
AI_CHECKLIST_ALLOWED_DOMAINS=samsung.com,lg.com,microsoft.com,lenovo.com,dell.com,hp.com,asus.com,acer.com,msi.com
AI_CHECKLIST_CONNECT_TIMEOUT=5s
AI_CHECKLIST_READ_TIMEOUT=30s
```

AI 연동은 Responses API, 공식 도메인 제한 Web Search, strict JSON Schema 응답을 사용한다. API 키나 프롬프트 전체를 로그에 남기지 않는다.

## 현재 제외 범위

- 직접 입력한 모델을 카탈로그에 영구 등록하는 기능
- 공식 자료의 90일 영구 캐시와 관리자 후보 승인 화면
- 증빙 파일 업로드, AI 증빙 매핑, 첫 증빙 제출 이후 재생성 잠금
- 스마트폰·태블릿 정책

위 기능은 체크리스트 생성 이후의 별도 도메인 작업으로 분리한다.
