-- 웹 실동작 점검(SELLER_CONFIRMATION)은 지금까지 completion_status(PENDING/SUBMITTED/COMPLETED)
-- 하나로만 기록해서, "점검을 시도했다"와 "장치가 정상이었다"를 구분하지 못했다. 이 컬럼에 실제 판정
-- (SUCCESS/FAILED/SKIPPED)을 따로 남겨 completion_status와 별개로 결과를 조회할 수 있게 한다.

ALTER TABLE listing_checklist_item
    ADD COLUMN device_check_result VARCHAR(20) NULL AFTER completion_status;
