# ADR-0003: 기기 카탈로그를 category 트리에서 분리

- 상태: Accepted
- 결정일: 2026-08-01

## 배경

`category` 한 테이블이 두 가지 역할을 겸하고 있다. `parent_id IS NULL`인 행은 카테고리이고,
`model_code IS NOT NULL`인 리프 행은 기기 모델이다. 이 구조에서 세 가지 문제가 생겼다.

- 모델에만 필요한 열(`model_code`, `supported_storage_gb`)이 카테고리 행에서는 항상 NULL이다.
- 색상은 `listing`의 자유 입력이고 저장 용량은 `supported_storage_gb`의 콤마 문자열이라, 실제로
  판매되지 않는 색상x용량 조합을 막을 방법이 없다.
- 노트북의 화면 크기·CPU·GPU·RAM처럼 축이 여러 개인 사양을 표현할 자리가 없다.

또한 `listing`은 등록 시점의 사양을 보존하지 않아, 카탈로그를 수정하면 이미 등록된 매물이 어떤
사양으로 팔렸는지 되짚을 수 없다.

## 결정

카테고리·제조사·모델·판매옵션을 각자의 테이블로 분리한다.

- `device_category` — 승인된 최상위 카테고리
- `manufacturer` — 제조사
- `device_model` — 승인된 기기 모델
- `device_variant` — 실제 판매되는 SKU 조합

`listing`에는 `device_model_id`, `device_variant_id`, `spec_snapshot`(JSON), `view_count`와
사양 열(`screen_size_inches`, `memory_gb`, `connectivity`)을 추가한다.

### ID를 새로 채번하지 않고 보존한다

`device_category.category_id`는 기존 최상위 `category.id`를, `device_model.model_id`는 기존 리프
`category.id`를 그대로 물려받는다.

프론트가 쓰는 `deviceModelId`와 이미 저장된 `listing.category_id`가 전부 그 값이다. 새 시퀀스를
쓰면 이관 시점에 클라이언트 상태와 기존 매물 참조가 한꺼번에 깨지고, 매핑 테이블을 따로 들고
다녀야 한다. AUTO_INCREMENT 열에 명시 INSERT를 하면 카운터가 max+1로 맞춰지므로 이후 신규
채번에는 영향이 없다.

### manufacturer_id는 채번하지 않고 CRC32를 쓴다

`manufacturer.manufacturer_id = CRC32(LOWER(TRIM(name)))`이다. 기존 `category.manufacturer_id`가
이미 같은 방식이고 `GET /api/v1/device-models`의 `manufacturerId` 파라미터로 프론트에 노출돼
있다. 새 시퀀스를 도입하면 그 필터가 조용히 깨진다.

대가는 CRC32 충돌 가능성이다. 서로 다른 제조사명이 같은 값을 내면 PK 충돌로 INSERT가 실패한다.
조용히 병합되는 것보다는 낫고, 그 시점에 별도 채번으로 교체한다.

### device_variant에 선택 축을 열로 함께 둔다

기획서의 `device_variant`는 `variant_key`와 `display_name`만 갖지만, 등록 화면의 단계별 옵션
노출("색상을 고르면 그 색상에서 가능한 용량만")은 조합을 축별로 조회할 수 있어야 성립한다.
축을 열로 두고 `variant_key`는 조합의 식별자로만 쓴다.

축 열은 전부 nullable이다. 카테고리마다 의미 있는 축이 다르고(노트북은 cpu/gpu/memory_gb,
스마트폰은 color/storage_gb), 카테고리별로 테이블을 쪼개면 조인이 카테고리 수만큼 늘어난다.

### 옵션 후보는 자기 축의 선택을 제외하고 계산한다

`GET /api/v1/device-models/{modelId}/options`는 각 축의 후보를 **그 축의 선택을 제외한** 나머지
조건으로 계산한다. 자기 선택으로도 좁히면 이미 고른 값 하나만 남아 사용자가 앞 단계 선택을
되돌릴 수 없다.

### category 테이블은 이번에 제거하지 않는다

`checklist_template.category_id`와 `listing.category_id`가 그 행을 물고 있어 한 번에 끊을 수 없다.
두 구조를 병행하다가 프론트 전환이 끝난 뒤 별도 작업으로 제거한다.

## 결과와 제한

- 색상 축은 아직 비어 있다. 현재 DB에 모델별 색상 정보가 없어, 이관으로 만들어지는 variant는
  용량 축만 갖는다. 색상x용량 실조합은 제조사 수집 단계가 채워야 의미가 생긴다. 즉 **이 ADR만으로는
  "잘못된 옵션 조합 방지"가 아직 동작하지 않는다.**
- 용량 정보가 없는 모델('기타 (직접 입력)', 승인된 사용자 요청 모델)에는 `-BASE` 기본 조합을
  만든다. 만들지 않으면 "선택 가능한 variant가 없는 모델은 검색에서 제외" 규칙에 걸려 카탈로그에
  없는 기기의 등록 경로가 통째로 막힌다.
- 기존 매물의 `device_variant_id`는 backfill하지 않는다. 기존 `color`/`storage_gb`는 자유 입력이라
  실제 조합과 일치한다는 보장이 없고, 추정해서 채우면 "실조합만 선택 가능"이라는 규칙의 근거가
  무너진다.
- `spec_snapshot`은 등록 시점에만 쓰고 판매자 수정 경로에서는 갱신하지 않는다.
- 두 구조를 병행하는 동안 모델 데이터가 두 곳에 존재한다. 이관 이후 `category`에 직접 INSERT하는
  경로(`DeviceModelRequestService.approve`)는 새 테이블에도 함께 써야 하며, 이는 후속 단계에서
  처리한다.

## 관련 마이그레이션

- `V20260812__create_device_catalog_tables.sql`
- `V20260813__migrate_category_to_device_catalog.sql`
- `V20260814__extend_listing_device_catalog_columns.sql`
