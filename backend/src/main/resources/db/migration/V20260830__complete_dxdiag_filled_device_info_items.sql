-- V20260829는 손으로 채운 값만 봤다. 그런데 진단 프로그램이 넣은 값은 manual 컬럼이 아니라
-- DxDiag 결과에 들어간다. 그래서 프로그램으로 다 채운 매물이 여전히 PENDING에 남아 있었다.
--
-- V20260829는 이미 적용된 파일이라 고치지 않는다. Flyway는 적용된 마이그레이션의 내용이 바뀌면
-- 체크섬이 어긋나 부팅을 거부한다. 빠뜨린 경우만 여기서 따로 맞춘다.
--
-- DxDiag가 채운 값과 판매자가 직접 넣은 값을 같이 본다. OCR로 채운 항목은 사진이 증빙으로 남아
-- 이미 완료 처리되어 있으므로 여기서 다루지 않는다.
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
