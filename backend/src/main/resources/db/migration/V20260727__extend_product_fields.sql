-- P-01~P-11 상품 등록·카탈로그 실제 저장 필드 확장

ALTER TABLE listing
    ADD COLUMN color VARCHAR(50) NULL AFTER price,
    ADD COLUMN storage_gb INT NULL AFTER color,
    ADD COLUMN trade_region VARCHAR(100) NULL AFTER storage_gb,
    ADD INDEX idx_listing_public_feed (status, deleted_at, created_at),
    ADD INDEX idx_listing_seller_feed (seller_id, status, deleted_at, updated_at),
    ADD INDEX idx_listing_seller_all_feed (seller_id, deleted_at, updated_at);

ALTER TABLE category
    ADD COLUMN manufacturer_id BIGINT NULL AFTER manufacturer,
    ADD COLUMN supported_storage_gb VARCHAR(100) NULL AFTER model_code,
    ADD INDEX idx_category_manufacturer (manufacturer_id);

UPDATE category
   SET manufacturer_id = CRC32(LOWER(TRIM(manufacturer)))
 WHERE manufacturer IS NOT NULL
   AND TRIM(manufacturer) <> '';

ALTER TABLE wishlist
    ADD INDEX idx_wishlist_user_created (user_id, created_at);

ALTER TABLE listing_image
    ADD INDEX idx_listing_image_thumbnail (listing_id, image_type, id);
