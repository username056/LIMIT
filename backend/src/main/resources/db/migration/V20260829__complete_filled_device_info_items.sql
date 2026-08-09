-- 기기 정보 항목은 사진 없이 값만 채워질 수 있다. 판매자가 직접 입력하거나, 진단 프로그램이
-- 올린 DxDiag 파일에서 읽어 오는 경우다. 그때는 그 항목에 증빙 row가 생기지 않아 항목이
-- PENDING에 남아 있었다. 값은 상세 페이지의 '자동 인식된 사양' 카드에 떠 있는데도 상품 목록의
-- 검증 개수는 오르지 않았다.
--
-- 앞으로 들어오는 값은 DeviceInfoCompletionService가 완료로 두지만, 이미 채워 둔 매물은 다시
-- 저장하지 않는 한 그대로다. 네 값이 모두 차 있는 항목만 여기서 맞춘다.
--
-- 값의 출처는 따지지 않는다. DxDiag가 채운 값과 판매자가 직접 넣은 값을 같이 본다. OCR로 채운
-- 항목은 사진이 증빙으로 남아 이미 완료 처리되어 있으므로 여기서 다루지 않는다.
UPDATE listing_checklist_item item
SET item.completion_status = 'COMPLETED'
WHERE item.item_code IN ('LAP-SCR-013', 'SYS-003')
  AND item.completion_status <> 'COMPLETED'
  AND (
        NULLIF(TRIM(item.manual_model_name), '') IS NOT NULL
        OR EXISTS (SELECT 1 FROM dxdiag_result d JOIN evidence e ON e.id = d.evidence_id
                    WHERE e.listing_id = item.listing_id
                      AND NULLIF(TRIM(d.model_name), '') IS NOT NULL)
      )
  AND (
        NULLIF(TRIM(item.manual_storage_capacity), '') IS NOT NULL
        OR EXISTS (SELECT 1 FROM dxdiag_result d JOIN evidence e ON e.id = d.evidence_id
                    WHERE e.listing_id = item.listing_id
                      AND NULLIF(TRIM(d.storage_capacity), '') IS NOT NULL)
      )
  AND (
        NULLIF(TRIM(item.manual_os_version), '') IS NOT NULL
        OR EXISTS (SELECT 1 FROM dxdiag_result d JOIN evidence e ON e.id = d.evidence_id
                    WHERE e.listing_id = item.listing_id
                      AND NULLIF(TRIM(d.os_version), '') IS NOT NULL)
      )
  AND (
        NULLIF(TRIM(item.manual_cpu), '') IS NOT NULL
        OR EXISTS (SELECT 1 FROM dxdiag_result d JOIN evidence e ON e.id = d.evidence_id
                    WHERE e.listing_id = item.listing_id
                      AND NULLIF(TRIM(d.cpu), '') IS NOT NULL)
      );
