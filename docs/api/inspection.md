# 검수 증거 자동 진단 (OCR·DxDiag·배터리 리포트)

## 구현 범위

- 판매자가 체크리스트 항목에 업로드한 증거(스크린샷/DxDiag 파일/배터리 리포트 파일)를 자동으로 구조화해
  기기 사양을 추출하고, 값이 서로 다르면 상충 여부를 함께 보여준다.
- 판매자는 취합된 값을 항목별 필드 단위로 확정·수정할 수 있고, 구매자는 상품 상세에서 확정(또는 자동
  추출) 최종 값만 확인한다.
- 코드 위치: 컨트롤러·서비스·리포지토리·엔티티·DTO·enum 모두 `domain.inspection` 아래 평평하게 있다
  (`controller`, `service`, `entity`, `repository`, `dto/{request,response}`, `enums`, `parser`).

### 체크리스트 항목과의 관계

체크리스트 항목(`ListingChecklistItem`)에는 `evidenceType`·`automationType`·`parserType` 세 필드가 있고,
이 문서의 API들은 이 필드 조합으로 어떤 항목이 자동 진단 대상인지 판단한다.

| automationType | parserType | evidenceType | 트리거하는 API | 노트북 체크리스트 항목 |
| --- | --- | --- | --- | --- |
| `OCR` | (없음) | `PHOTO` | `POST .../ocr-results` | `LAP-SCR-013`(설정 정보 화면) |
| `FILE_PARSE` | `DXDIAG` | `DIAGNOSTIC_FILE` | `POST .../dxdiag-results` | `LAP-SYS-011`(Windows 시스템 진단) |
| `FILE_PARSE` | `BATTERY_REPORT` | `DIAGNOSTIC_FILE` | `POST .../battery-report-results` | `LAP-BAT-010`(배터리 상태) |
| `NONE` | (없음) | 무관 | 없음 | 나머지 항목 전체 |

`automationType`/`parserType`은 `ProductChecklistItemResponse`(`GET /products/{productId}/checklist-items`)에
노출되어 있어, 프런트는 이 값으로 업로드 완료 직후 어떤 파싱 API를 호출할지 정한다. 업로드 자체는 이
파싱을 자동으로 트리거하지 않는다 — 클라이언트가 명시적으로 호출해야 한다. 프런트 로직은 항목 코드가
아니라 `automationType`/`parserType`만 보고 동작하므로, 아래 표의 노트북 항목 외에도 스마트폰·태블릿의
시딩 템플릿 항목 `SYS-003`(기기 정보 화면, OCR)처럼 같은 조합을 가진 항목이면 동일하게 동작한다
(자세한 시딩 데이터는 [product-catalog-rtc-completion.md](product-catalog-rtc-completion.md) 참고).

## API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/v1/inspections/evidence/{evidenceId}/ocr-results` | 증거 스크린샷 OCR 자동 구조화 | 판매자 본인 |
| POST | `/api/v1/inspections/evidence/{evidenceId}/dxdiag-results` | DxDiag.txt/xml 파싱 | 판매자 본인 |
| POST | `/api/v1/inspections/evidence/{evidenceId}/battery-report-results` | battery-report.html 파싱 | 판매자 본인 |
| GET | `/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis` | 항목 하나의 필드별 취합·상충 조회 | 판매자 본인 |
| PATCH | `/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis-values` | 필드 하나를 확정값으로 저장 | 판매자 본인 |
| GET | `/api/v1/inspections/products/{productId}/diagnosis-summary` | 구매자용 최종 요약 조회 | 없음(공개) |

OCR이 인식하는 필드는 `MODEL_NAME`/`CPU`/`RAM`/`GPU`/`STORAGE_CAPACITY`/`OS_VERSION` 6종이다(`OcrFieldType`).
스크린샷 파서([SystemInfoScreenshotParser.java](../../backend/src/main/java/com/c203/limit/domain/inspection/parser/SystemInfoScreenshotParser.java))는
상단 카드 4개(저장소/그래픽카드/설치된RAM/프로세서)에서 CPU/RAM/GPU/저장용량을 1차로 뽑고, 화면에 "장치
사양" 표(장치 이름 → 프로세서 → 설치된 RAM → 장치 ID → 제품 ID → 시스템 종류 → 펜 및 터치, 항상 이
순서인 Windows 표준 패널)가 펼쳐져 있으면 그 표의 프로세서/설치된 RAM 값(클럭 속도·사용 가능 용량까지
포함해 더 상세함)으로 카드 값을 덮어쓴다. 이 표 라벨 7개를 순서대로 다 못 찾으면(표를 안 펼친 화면 등)
조용히 카드 값을 그대로 둔다. **이 표 파싱은 아직 실제 Naver Clova 응답으로 검증되지 않았다** — 라이브
자격증명이 있는 환경에서 `SystemInfoScreenshotLiveManualTests`/`NaverClovaOcrClientLiveIntegrationTests`로
실제 좌표를 확인해 `SystemInfoScreenshotParserTests`의 픽스처를 교체하기 전까지는 신뢰도를 낮게 봐야 한다.
DxDiag는 `CPU`/`RAM`(`memory`)/`GPU`/`GPU_MEMORY`/`DRIVER_VERSION`/`SOUND_DEVICE`를, 배터리 리포트는
`DESIGN_CAPACITY`/`FULL_CHARGE_CAPACITY`/`CYCLE_COUNT`/`BATTERY_MANUFACTURER`/`CAPACITY_RATIO`를 추출한다.
이 필드 이름들은 `DiagnosisFieldName` enum으로 통일되어 있어, OCR과 DxDiag가 겹치는 필드(`CPU`/`RAM`/`GPU`
등)는 같은 이름으로 취합·비교된다.

### 취합 조회 (`GET .../diagnosis`)

같은 체크리스트 항목에 딸린 증거들 중 `PHOTO` 타입은 OCR 결과에서, `DIAGNOSTIC_FILE` 타입은 DxDiag/배터리
리포트 파싱 결과에서 값을 모아 필드 이름 기준으로 합친다. 한 필드에 OCR 값과 파일 파싱 값이 둘 다 있고
서로 다르면 `conflict: true`를 반환한다.

```json
{
  "data": {
    "itemId": 7003,
    "fields": [
      {
        "fieldName": "CPU",
        "ocrValue": "13th Gen Intel(R) Core(TM) i7-13700H",
        "fileParseValue": "13th Gen Intel(R) Core(TM) i7-13700H (20 CPUs), ~2.4GHz",
        "conflict": true,
        "confirmedValue": null
      }
    ]
  },
  "meta": null
}
```

### 확정/수정 (`PATCH .../diagnosis-values`)

```json
{ "fieldName": "CPU", "confirmedValue": "13th Gen Intel(R) Core(TM) i7-13700H" }
```

이 필드를 처음 수정하는 경우 OCR/진단파일에서 취합된 당시 원본값을 이력에 먼저 자동 기록한 뒤 확정값을
저장한다. 자동으로 감지되지 않은 필드(OCR/파싱이 실패했거나 부분 인식된 경우)도 `fieldName`만 유효한
`DiagnosisFieldName`이면 그대로 확정값으로 저장할 수 있다 — 반드시 먼저 감지된 값이 있어야 하는 건 아니다.

### 구매자용 요약 (`GET .../diagnosis-summary`)

인증 없이 조회 가능. 매물의 체크리스트 항목마다 필드별로 `value`(판매자가 고쳤으면 고친 값, 아니면 자동
추출값)와 `status`(`AVAILABLE`/`EXTRACTION_FAILED`)를 반환하고, "자동 추출값은 참고 정보이며 상품의 정상
여부를 보증하지 않습니다" 같은 `disclaimer`를 함께 준다.

## 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `INS001` EVIDENCE_NOT_FOUND | 404 | evidenceId에 해당하는 증거가 없음 |
| `INS002` EVIDENCE_NOT_READY | 409 | 증거가 아직 업로드 처리(READY) 되지 않음 |
| `INS007` INVALID_EVIDENCE_TYPE | 400 | OCR에 DIAGNOSTIC_FILE을, 파일 파싱에 PHOTO를 넘기는 등 타입 불일치 |
| `INS010` PARSING_FAILED | 422 | OCR/파일 파싱 자체가 실패(형식 인식 불가 등) |
| `INS012` ITEM_NOT_FOUND | 404 | itemId에 해당하는 체크리스트 항목이 없음 |
| `INS013` FIELD_NOT_EDITABLE | 400 | 해당 항목의 자동화 유형에 속하지 않는 fieldName을 확정하려 함 |
| `INS015` ALREADY_PARSED | 409 | 같은 evidenceId로 이미 파싱을 수행함(재호출 불가) |
| `AUTH005` FORBIDDEN | 403 | 증거·항목이 속한 매물의 판매자 본인이 아님 |

## 프런트 연동

`frontend/src/api/inspection.js`가 위 6개 API를 그대로 감싼다. `ProductRegisterPage.vue`의 체크리스트
촬영 단계에서, 업로드가 끝나는 즉시(`handleCaptureFile` 안에서) 항목의 `automationType`/`parserType`을 보고
해당 파싱 API를 호출한 뒤 취합 조회로 결과를 받아 "촬영 또는 파일 업로드" 버튼 아래에 필드별 입력창으로
보여준다. 자동 인식에 실패했거나 일부 필드만 인식된 경우에도, 그 항목이 다룰 수 있는 필드 전체(예: OCR이면
`MODEL_NAME`/`CPU`/`RAM`/`GPU`/`OS_VERSION`/`STORAGE_CAPACITY`)를 빈 입력칸으로 함께 보여줘 드롭다운 없이
바로 타이핑해 저장할 수 있다. 상품 상세 페이지에서 구매자용 최종 요약(`diagnosis-summary`)을 보여주는 연동은
아직 없다.

## 검증

- `./gradlew test`, `./gradlew bootJar` 통과
- `npm run lint`, `npm run test`, `npm run build` 통과 (`ProductRegisterPage.spec.js`)
- `docs/api/openapi.json` 최신 반영
