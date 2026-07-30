// LaptopChecklistPolicy(backend)에서 SELLER_CONFIRMATION으로 전환된 실동작 점검 대상 항목 코드.
// 여기 없는 itemCode는 이 기능이 관여하지 않고 기존 방식(사진/영상 증빙 등) 그대로 둔다.
export const CHECK_KIND = {
  CAMERA: 'CAMERA',
  MIC: 'MIC',
  SPEAKER: 'SPEAKER',
  KEYBOARD: 'KEYBOARD',
  NUMPAD: 'NUMPAD',
  POINTER: 'POINTER',
  TOUCHSCREEN: 'TOUCHSCREEN',
  STYLUS: 'STYLUS',
}

export const CHECKABLE_ITEM_CODES = {
  'LAP-FTR-CAM': CHECK_KIND.CAMERA,
  'LAP-FTR-MIC': CHECK_KIND.MIC,
  'LAP-FTR-SPK': CHECK_KIND.SPEAKER,
  'LAP-KBD-005': CHECK_KIND.KEYBOARD,
  'LAP-FTR-NUM': CHECK_KIND.NUMPAD,
  'LAP-PAD-006': CHECK_KIND.POINTER,
  'LAP-FTR-TOUCH': CHECK_KIND.TOUCHSCREEN,
  'LAP-FTR-PEN': CHECK_KIND.STYLUS,
}

export function toCheckableItems(checklistItems) {
  // itemCode가 일치해도, 이 기능 배포 전에 스냅샷된 기존 상품은 evidenceType이 여전히
  // PHOTO/VIDEO일 수 있다 — 그런 항목까지 포함하면 저장 시 백엔드가 SELLER_CONFIRMATION만
  // 허용해서 ITEM_NOT_FOUND로 거부한다.
  return checklistItems
    .filter(
      (item) => CHECKABLE_ITEM_CODES[item.itemCode] && item.evidenceType === 'SELLER_CONFIRMATION',
    )
    .map((item) => ({ ...item, checkKind: CHECKABLE_ITEM_CODES[item.itemCode] }))
}
