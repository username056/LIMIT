# 기기별 체크리스트 자동 생성

## 지원 범위

- 카탈로그에 등록된 스마트폰, 폴더블, 태블릿, Windows·Linux 노트북 모델을 지원한다.
- 모든 기기는 해당 모델의 최신 `PUBLISHED` 템플릿을 기본 체크리스트로 사용한다.
- 노트북에 게시 템플릿이 없는 예외 상황에서는 서버의 전용 기본 정책을 사용한다.
- 목록에 없는 모델은 직접 입력하면 사후 검토 요청과 함께 즉시 카탈로그에 등록된다. 같은 요청에서
  검증된 기본 템플릿을 발행하므로 관리자 검토를 기다리지 않고 상품 등록에 사용할 수 있다.

## 모델별 조사와 생성 규칙

검증된 서버 기본 정책이나 게시 템플릿을 항상 먼저 적용한다. AI는 기본 항목을 삭제하거나 필수 여부를 바꿀 수 없다.

- AI 조사는 `(deviceModelId, researchVersion)`별로 한 번만 수행하고 결과를 저장한다.
- 조사 상태는 `PROCESSING`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `FAILED`다.
- 동일 모델을 다시 선택하면 저장된 상태를 반환하므로 매물이나 판매자마다 AI를 다시 호출하지 않는다.
- `FAILED` 상태에는 민감 정보를 제외한 오류 코드와 사용자 안내 문구를 저장한다.
- `PENDING_REVIEW` 조사 후보도 판매자에게 즉시 보여주며, 판매자가 실제 기기에 해당한다고 선택한
  기능만 해당 매물 체크리스트에 추가한다. 기본 항목은 선택 해제할 수 없다.
- 관리자의 승인·반려는 판매 등록을 잠그는 절차가 아니라 사후 검토 기록이다. 승인하더라도 AI
  항목을 전역 기본 템플릿으로 승격하지 않는다.
- 실패한 조사뿐 아니라 모든 모델을 관리자가 재조사할 수 있다. 재조사는 기존 결과를 덮지 않고
  `researchVersion + 1`의 새 조사 이력을 만든다.
- 상품 등록 시 기본 항목과 판매자가 선택한 AI 항목을 매물별로 복사해 스냅샷을 고정한다. 이후
  관리자 모델 수정이나 재조사 결과는 새 매물부터 적용되고 기존 매물은 바뀌지 않는다.
- AI는 선택한 기기 유형에 등록된 기능 코드만 후보로 반환한다.
- 기능 후보는 공식 제조사 제품·지원 문서에서 확인된 경우에만 반환한다.
- 서비스에 점검 방법이 정의되지 않은 공식 기능은 관리자용 `reviewCandidates`로만 저장하며 체크리스트에는 자동 추가하지 않는다.
- AI 호출이 비활성화되거나 실패해도 기본 체크리스트 생성은 성공한다.

기기별 후보 예시는 다음과 같다.

- 스마트폰: 무선 충전, eSIM, 망원 카메라, NFC, 지문·얼굴 인식
- 폴더블: 커버 디스플레이, 플렉스 모드, 무선 충전, eSIM, 스타일러스
- 태블릿: 스타일러스, 키보드 커넥터, 셀룰러, OLED, 데스크톱 모드
- 노트북: 포트, 카메라, 무선 연결, 터치스크린, 전용 GPU 등 기존 노트북 기능

## API

### 체크리스트 미리 생성

`POST /api/v1/checklist-generations`

ACTIVE 판매자만 호출할 수 있다.

카탈로그 모델 요청:

```json
{
  "deviceModelId": 101,
  "confirmedFeatures": []
}
```

응답의 `researchId`, `researchStatus`로 조사 상태를 확인한다. `items`에는 해제할 수 없는 기본
항목, `aiSuggestions`에는 판매자가 선택할 수 있는 AI 추가 후보가 들어간다. `researchStatus`가
`FAILED`이면 판매 화면은 AI 조사 실패와 기본 정책 적용을 구분해 안내한다.

판매자와 관리자 조사 응답의 기능 후보는 다음 정보를 포함한다.

- `featureCode`: 상품 등록에 다시 전달할 기기 유형별 기능 코드
- `featureName`: 사용자에게 표시할 한국어 기능명
- `reason`: 공식 자료를 근거로 후보를 선정한 이유
- `checkGuide`: 판매자가 실제 기기에서 기능을 확인할 방법
- `evidenceStatus`: `VERIFIED`, `LIKELY`, `UNKNOWN`, `CONFLICTED`
- `sourceUrl`, `sourceTitle`: 확인한 공식 자료

### 관리자 조사 검토

- `GET /api/v1/admin/checklist-researches?status=PENDING_REVIEW`
- `GET /api/v1/admin/checklist-researches?status=FAILED`
- `POST /api/v1/admin/checklist-researches/{researchId}/approval`
- `POST /api/v1/admin/checklist-researches/{researchId}/rejection`
- `POST /api/v1/admin/checklist-researches/{researchId}/retry`

승인 요청에서 `approvedFeatureCodes`를 생략하면 검증된 전체 후보를 검토 완료로 기록한다. 배열을
보내면 해당 코드만 확인한 것으로 기록한다. 승인·반려는 관리자 작업 로그에 기록되지만 판매자가
선택할 수 있는 기능 후보나 기존 매물 스냅샷을 변경하지 않는다.

실패 목록 응답에는 `failureCode`, `failureMessage`가 포함된다. 기존 retry API는 실패 조사와의
호환 경로이며 새 모델 관리 화면은 `POST /api/v1/admin/device-models/{modelId}/researches`를
사용한다. 새 조사가 실패해도 HTTP 요청 자체는 성공하고 새 버전의 `FAILED` 결과를 반환한다.

### 직접 입력 모델 요청

`POST /api/v1/device-model-requests`

```json
{
  "categoryId": 1,
  "manufacturer": "Samsung",
  "modelName": "Galaxy S25",
  "modelCode": "SM-S931N",
  "osFamily": "ANDROID"
}
```

동일 카테고리·제조사·모델명의 `PENDING` 요청은 중복 접수하지 않는다.

응답에는 즉시 사용할 `resolvedModelId`가 포함된다. 관리자는 기존 요청 API와 모델 관리 API로
사후 검토를 처리한다.

- `GET /api/v1/admin/device-model-requests?status=PENDING`
- `PATCH /api/v1/admin/device-model-requests/{requestId}`
- `POST /api/v1/admin/device-model-requests/{requestId}/approval`
- `POST /api/v1/admin/device-model-requests/{requestId}/rejection`
- `GET /api/v1/admin/device-models?reviewStatus=PENDING_REVIEW`
- `GET /api/v1/admin/device-models/{modelId}`
- `PATCH /api/v1/admin/device-models/{modelId}`
- `POST /api/v1/admin/device-models/{modelId}/researches`

관리자는 모델 상세에서 카테고리, 제조사, 모델명, 모델 코드와 OS를 수정 완료한 뒤 같은 값으로
재조사할 수 있다. 변경할 카테고리는 활성 최상위 기기 카테고리여야 하며 수정은 관리자 작업
로그에 기록된다. 카테고리가 바뀌면 해당 분류의 검증된 기본 템플릿을 새 모델 버전으로 발행한다.

요청 생성 트랜잭션에서 카테고리와 정식 `device_model`에 모델을 함께 추가하고 같은 기기 분류의
기존 `PUBLISHED` 템플릿을 초기 버전으로 복제한다. 이후 판매자가 새 모델을 선택하면 AI 조사가
수행된다. 등록된 모델은 판매하기 화면의 해당 카테고리 모델 목록에 활성 상태로 노출된다. 모델 목록
검색은 제조사, 모델명과 모델 코드를 대상으로 하며, 목록 첫 페이지 밖의 모델도 검색어로
서버에서 다시 조회할 수 있다.

### 상품 등록과 스냅샷

`POST /api/v1/products`

`confirmedFeatures`에는 체크리스트 생성 응답의 `aiSuggestions.featureCode` 중 판매자가 선택한
코드만 전달한다. 서버는 저장된 해당 모델의 최신 조사 후보와 다시 대조하고, 임의 기능 코드는
거절한다. 선택이 없으면 최신 `PUBLISHED` 기본 템플릿을 재사용하고, 선택이 있으면 기본 항목과
선택 항목으로 매물 전용 템플릿을 만든 뒤 `listing_checklist_item` 스냅샷을 생성한다.

## AI 설정

기본값은 AI 비활성화다.

```dotenv
AI_CHECKLIST_ENABLED=true
OPENAI_API_KEY=
OPENAI_RESPONSES_ENDPOINT=https://api.openai.com/v1/responses
OPENAI_CHECKLIST_MODEL=gpt-5.6-luna
AI_CHECKLIST_ALLOWED_DOMAINS=samsung.com,apple.com,google.com,lg.com,microsoft.com,lenovo.com,dell.com,hp.com,asus.com,acer.com,msi.com
AI_CHECKLIST_CONNECT_TIMEOUT=5s
AI_CHECKLIST_READ_TIMEOUT=30s
```

AI 연동은 Responses API, 공식 도메인으로 제한한 Web Search, strict JSON Schema 응답을 사용한다. API 키나 전체 프롬프트를 로그에 남기지 않는다.
