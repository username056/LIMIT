-- 기기 정보 항목은 사진 없이 값만 채워질 수 있다. 진단 프로그램이 넣어 주거나 판매자가 직접
-- 입력하는 경우인데, 그때는 증빙 row가 생기지 않아 항목이 PENDING에 남아 있었다. 판매자는 다
-- 채웠는데 상품 목록의 검증 개수는 오르지 않고 구매자에게는 미완료로 보였다.
--
-- 앞으로 들어오는 값은 DiagnosisValueConfirmationService가 완료로 두지만, 이미 채워 둔 매물은
-- 다시 저장하지 않는 한 그대로 남는다. 네 값이 모두 차 있는 항목만 여기서 맞춘다.
--
-- 손으로 채운 값만 본다. OCR로 채운 항목은 사진이 증빙으로 남아 이미 완료 처리되어 있다.
UPDATE listing_checklist_item
SET completion_status = 'COMPLETED'
WHERE item_code IN ('LAP-SCR-013', 'SYS-003')
  AND completion_status <> 'COMPLETED'
  AND manual_model_name IS NOT NULL AND manual_model_name <> ''
  AND manual_storage_capacity IS NOT NULL AND manual_storage_capacity <> ''
  AND manual_os_version IS NOT NULL AND manual_os_version <> ''
  AND manual_cpu IS NOT NULL AND manual_cpu <> '';
