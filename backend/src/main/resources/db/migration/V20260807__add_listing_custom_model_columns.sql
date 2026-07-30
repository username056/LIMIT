-- 판매자가 직접 입력한 기기 모델 정보를 매물에 보관한다.
--
-- 기기 모델은 category 테이블의 실제 행이고 checklist_template이 그 행에 묶여 있어, 카탈로그에
-- 없는 기기는 '기타 (직접 입력)' 행(V20260806)을 통해 등록한다. 그 행 하나에 여러 기기가 매달리므로
-- 실제 제조사와 모델명은 매물마다 따로 남겨야 한다.
--
-- 이 값이 있으면 상세·목록에서 카탈로그 모델명 대신 판매자가 입력한 값을 노출한다.
-- 세부 모델명을 글제목에 적게 하는 우회를 없애기 위한 열이다.

ALTER TABLE listing
    ADD COLUMN custom_manufacturer VARCHAR(50) NULL AFTER category_id,
    ADD COLUMN custom_model_name VARCHAR(100) NULL AFTER custom_manufacturer;
