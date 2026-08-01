-- 기존 category 트리를 새 카탈로그 테이블로 복제 이관한다.
--
-- 핵심 규칙은 **ID 보존**이다. device_category.category_id는 최상위 category.id를,
-- device_model.model_id는 리프 category.id를 그대로 받는다. 프론트가 쓰는 deviceModelId와
-- 이미 저장된 listing.category_id가 전부 그 값이라, 새로 채번하면 이관 시점에 기존 매물과
-- 클라이언트 상태가 한꺼번에 깨진다. AUTO_INCREMENT 열에 명시 INSERT를 하면 카운터는
-- max+1로 맞춰지므로 이후 신규 채번도 정상 동작한다.
--
-- 모든 INSERT는 WHERE NOT EXISTS로 멱등하게 둔다(기존 시드 마이그레이션과 동일한 방식).

-- 1) 최상위 카테고리
INSERT INTO device_category (category_id, code, name, display_order, is_active)
SELECT c.id, c.device_type, c.name, c.display_order, c.is_active
FROM category c
WHERE c.parent_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM device_category existing WHERE existing.category_id = c.id
  );

-- 2) 제조사
--
-- manufacturer_id는 category.manufacturer_id를 읽지 않고 CRC32를 다시 계산한다. 그 열은
-- V20260727에서 한 번 backfill된 뒤 이후 INSERT마다 각자 채워 넣은 값이라 신뢰 근거가
-- 한 곳에 없다. 계산식은 V20260727 및 Category.manufacturerId()와 동일하다.
INSERT INTO manufacturer (manufacturer_id, name, normalized_name, is_active)
SELECT CRC32(grouped.normalized_name), grouped.name, grouped.normalized_name, b'1'
FROM (
    SELECT LOWER(TRIM(c.manufacturer)) AS normalized_name,
           MIN(TRIM(c.manufacturer)) AS name
    FROM category c
    WHERE c.parent_id IS NOT NULL
      AND c.manufacturer IS NOT NULL
      AND TRIM(c.manufacturer) <> ''
    GROUP BY LOWER(TRIM(c.manufacturer))
) grouped
WHERE NOT EXISTS (
    SELECT 1
    FROM manufacturer existing
    WHERE existing.normalized_name = grouped.normalized_name
);

-- 3) 기기 모델
--
-- 리프 판별은 model_code IS NOT NULL이다. DeviceModelRequestService.approve()가 모델 코드가
-- 없는 요청도 'REQUEST-{id}'로 채워 넣기 때문에 승인된 사용자 요청 모델도 빠짐없이 걸린다.
-- parent_id IS NOT NULL 조건을 함께 두어 최상위 행이 섞이지 않게 한다.
INSERT INTO device_model (
    model_id, category_id, manufacturer_id, model_name, normalized_model_name,
    model_code, os_family, release_year, is_active, display_order, created_at, updated_at
)
SELECT c.id,
       c.parent_id,
       CASE
           WHEN c.manufacturer IS NULL OR TRIM(c.manufacturer) = '' THEN NULL
           ELSE CRC32(LOWER(TRIM(c.manufacturer)))
       END,
       c.name,
       LOWER(REPLACE(TRIM(c.name), ' ', '')),
       c.model_code,
       c.os_family,
       NULL,
       c.is_active,
       c.display_order,
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM category c
WHERE c.parent_id IS NOT NULL
  AND c.model_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM device_model existing WHERE existing.model_id = c.id
  );

-- 4) 판매 옵션 조합 — 저장 용량 축
--
-- supported_storage_gb는 "128,256,512" 콤마 문자열이라 행으로 전개해야 한다. 값이 최대 8개라고
-- 보고 1..8 숫자 목록과 조인해 n번째 토큰을 잘라낸다(순수 SQL에서 문자열을 행으로 푸는 표준 관용구).
--
-- 색상 축은 여기서 채우지 않는다. 현재 DB에 모델별 색상 정보가 아예 없다. 색상x용량 실조합은
-- 제조사 수집(3단계)이 채운 뒤에야 의미가 생기므로, 그때까지 variant는 용량 축만 갖는다.
INSERT INTO device_variant (
    model_id, variant_key, display_name, storage_gb, is_active, created_at, updated_at
)
SELECT m.model_id,
       CONCAT(m.model_code, '-', expanded.storage_gb),
       CONCAT(m.model_name, ' ', expanded.storage_gb, 'GB'),
       expanded.storage_gb,
       b'1',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM device_model m
JOIN (
    SELECT DISTINCT
           c.id AS model_id,
           CAST(
               TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(c.supported_storage_gb, ',', seq.n), ',', -1))
               AS UNSIGNED
           ) AS storage_gb
    FROM category c
    JOIN (
        SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
    ) seq
      ON seq.n <= CHAR_LENGTH(c.supported_storage_gb)
                  - CHAR_LENGTH(REPLACE(c.supported_storage_gb, ',', '')) + 1
    WHERE c.supported_storage_gb IS NOT NULL
      AND TRIM(c.supported_storage_gb) <> ''
) expanded ON expanded.model_id = m.model_id
WHERE expanded.storage_gb > 0
  AND NOT EXISTS (
      SELECT 1
      FROM device_variant existing
      WHERE existing.model_id = m.model_id
        AND existing.variant_key = CONCAT(m.model_code, '-', expanded.storage_gb)
  );

-- 5) 용량 정보가 없는 모델의 기본 조합
--
-- '기타 (직접 입력)' 모델과 승인된 사용자 요청 모델은 supported_storage_gb가 비어 있어 위에서
-- variant가 하나도 만들어지지 않는다. 기획의 '선택 가능한 variant가 없는 모델은 검색 결과에서
-- 제외' 규칙을 그대로 적용하면 이 모델들이 통째로 사라져 카탈로그에 없는 기기의 등록 경로가
-- 막힌다. 모든 모델이 최소 1개의 조합을 갖도록 기본 조합을 만들어 규칙을 단일하게 유지한다.
INSERT INTO device_variant (
    model_id, variant_key, display_name, is_active, created_at, updated_at
)
SELECT m.model_id,
       CONCAT(m.model_code, '-BASE'),
       m.model_name,
       b'1',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM device_model m
WHERE NOT EXISTS (
    SELECT 1 FROM device_variant existing WHERE existing.model_id = m.model_id
);
