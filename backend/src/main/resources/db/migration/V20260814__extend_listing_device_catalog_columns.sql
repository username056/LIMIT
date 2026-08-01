-- 매물에 카탈로그 참조와 사양 스냅샷을 추가한다.
--
-- 지금 매물은 color/storage_gb만 자유롭게 들고 있어서, 카탈로그가 나중에 수정되면 그 매물이
-- 어떤 사양으로 팔렸는지 되짚을 방법이 없다. 선택한 사양을 등록 시점에 JSON으로 얼려 두고
-- 카탈로그 변경이 기존 매물에 소급되지 않게 한다.
--
-- 새 열은 전부 NULL 허용이다. 기존 매물에는 화면 크기·RAM·통신 방식 정보가 아예 없고,
-- 등록 화면 개편(4단계) 전까지는 신규 매물도 이 값을 채우지 못한다. NOT NULL로 두면
-- 이관 자체가 불가능하다.

ALTER TABLE listing
    ADD COLUMN device_model_id BIGINT NULL AFTER category_id,
    ADD COLUMN device_variant_id BIGINT NULL AFTER device_model_id,
    ADD COLUMN screen_size_inches DECIMAL(4,2) NULL AFTER storage_gb,
    ADD COLUMN memory_gb INT NULL AFTER screen_size_inches,
    ADD COLUMN connectivity VARCHAR(20) NULL AFTER memory_gb,
    ADD COLUMN spec_snapshot JSON NULL AFTER connectivity,
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 AFTER spec_snapshot;

-- 기존 매물의 카탈로그 참조 backfill.
--
-- listing.category_id는 리프 모델 행을 가리키고 device_model.model_id는 그 리프의 id를 그대로
-- 물려받았으므로(V20260813) 값을 그대로 옮기면 된다. JOIN이 매칭되지 않는 행(있다면 최상위
-- 카테고리를 직접 참조하는 비정상 데이터)은 NULL로 남겨 FK 추가 시 드러나게 한다.
UPDATE listing l
  JOIN device_model m ON m.model_id = l.category_id
   SET l.device_model_id = m.model_id
 WHERE l.device_model_id IS NULL;

-- variant는 backfill하지 않는다. 기존 매물의 color/storage_gb는 자유 입력이라 실제 조합과
-- 일치한다는 보장이 없다. 추정해서 채우면 '실조합만 선택 가능' 이라는 규칙의 근거가 무너진다.

ALTER TABLE listing
    ADD CONSTRAINT fk_listing_device_model
        FOREIGN KEY (device_model_id) REFERENCES device_model (model_id),
    ADD CONSTRAINT fk_listing_device_variant
        FOREIGN KEY (device_variant_id) REFERENCES device_variant (variant_id);

-- 조회수 정렬용 인덱스. 공개 피드 조건(status, deleted_at)을 앞에 두어야 정렬 전에 후보가
-- 좁혀진다.
CREATE INDEX idx_listing_view_count_feed ON listing (status, deleted_at, view_count DESC);
