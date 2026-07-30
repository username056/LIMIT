# 기기별 체크리스트 자동 생성

## 지원 범위

- 카탈로그에 등록된 스마트폰, 폴더블, 태블릿, Windows·Linux 노트북 모델을 지원한다.
- 모든 기기는 해당 모델의 최신 `PUBLISHED` 템플릿을 기본 체크리스트로 사용한다.
- 노트북에 게시 템플릿이 없는 예외 상황에서는 서버의 전용 기본 정책을 사용한다.
- 목록에 없는 모델은 직접 입력 후 모델 검토 요청으로 접수한다. 승인 전에는 상품에 사용할 수 없다.

## 모델별 조사와 생성 규칙

검증된 서버 기본 정책이나 게시 템플릿을 항상 먼저 적용한다. AI는 기본 항목을 삭제하거나 필수 여부를 바꿀 수 없다.

- AI 조사는 `(deviceModelId, researchVersion)`별로 한 번만 수행하고 결과를 저장한다.
- 조사 상태는 `PROCESSING`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `FAILED`다.
- 동일 모델을 다시 선택하면 저장된 상태를 반환하므로 매물이나 판매자마다 AI를 다시 호출하지 않는다.
- `PENDING_REVIEW` 결과는 판매자 체크리스트에 섞지 않고 관리자 화면에서만 근거와 함께 보여준다.
- 관리자가 기능 코드를 승인하면 새 버전의 `PUBLISHED` 템플릿을 발행한다.
- 이후 같은 모델의 상품은 해당 공용 템플릿을 사용하고, 상품 등록 시점에는 항목을 `listing_checklist_item`으로 복사해 스냅샷을 고정한다.
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

응답의 `researchId`, `researchStatus`로 조사 상태를 확인한다. `aiApplied`는 관리자 승인본이 현재 게시 템플릿에 반영됐는지를 나타낸다. `false`여도 `items`에는 게시된 기본 항목이 포함된다.

판매자 응답에는 승인 전 `aiSuggestions`와 `reviewCandidates`를 노출하지 않는다. 관리자 조사 응답의 기능 후보는 다음 정보를 포함한다.

- `featureCode`: 상품 등록에 다시 전달할 기기 유형별 기능 코드
- `featureName`: 사용자에게 표시할 한국어 기능명
- `reason`: 공식 자료를 근거로 후보를 선정한 이유
- `checkGuide`: 판매자가 실제 기기에서 기능을 확인할 방법
- `evidenceStatus`: `VERIFIED`, `LIKELY`, `UNKNOWN`, `CONFLICTED`
- `sourceUrl`, `sourceTitle`: 확인한 공식 자료

### 관리자 조사 검토

- `GET /api/v1/admin/checklist-researches?status=PENDING_REVIEW`
- `POST /api/v1/admin/checklist-researches/{researchId}/approval`
- `POST /api/v1/admin/checklist-researches/{researchId}/rejection`

승인 요청에서 `approvedFeatureCodes`를 생략하면 검증된 전체 후보를 승인한다. 배열을 보내면 해당 코드만 새 템플릿에 포함한다. 승인·반려는 관리자 작업 로그에 기록된다.

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

관리자는 다음 API로 요청을 처리한다.

- `GET /api/v1/admin/device-model-requests?status=PENDING`
- `POST /api/v1/admin/device-model-requests/{requestId}/approval`
- `POST /api/v1/admin/device-model-requests/{requestId}/rejection`

승인하면 카테고리에 모델을 추가하고 같은 기기 분류의 기존 `PUBLISHED` 템플릿을 초기 버전으로 복제한다. 이후 판매자가 새 모델을 처음 선택할 때 모델별 AI 조사가 한 번 수행된다.

### 상품 등록과 스냅샷

`POST /api/v1/products`

`confirmedFeatures`는 비워 전달해야 하며 판매자가 임의 기능을 보내면 요청을 거절한다. 서버는 최신 `PUBLISHED` 템플릿을 재사용하고 상품 등록 시 항목을 매물별 스냅샷으로 복사한다. 따라서 같은 모델을 다시 등록해도 새 템플릿이나 새 AI 조사가 생기지 않는다.

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
