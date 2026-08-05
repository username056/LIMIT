// LaptopChecklistPolicy(backend)에서 SELLER_CONFIRMATION으로 전환된 실동작 점검 대상 항목 코드.
// 여기 없는 itemCode는 이 기능이 관여하지 않고 기존 방식(사진/영상 증빙 등) 그대로 둔다.
export const CHECK_KIND = {
  CAMERA: 'CAMERA',
  MIC: 'MIC',
  SPEAKER: 'SPEAKER',
  DISPLAY: 'DISPLAY',
  CHARGING: 'CHARGING',
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
  'LAP-DSP-003': CHECK_KIND.DISPLAY,
  'LAP-CHG-007': CHECK_KIND.CHARGING,
  'LAP-KBD-005': CHECK_KIND.KEYBOARD,
  'LAP-FTR-NUM': CHECK_KIND.NUMPAD,
  'LAP-PAD-006': CHECK_KIND.POINTER,
  'LAP-FTR-TOUCH': CHECK_KIND.TOUCHSCREEN,
  'LAP-FTR-PEN': CHECK_KIND.STYLUS,
}

export const WEB_DEVICE_CHECKS = [
  { testType: 'KEYBOARD', checkKind: CHECK_KIND.KEYBOARD, itemCode: 'LAP-KBD-005', name: '키보드' },
  { testType: 'TOUCHPAD', checkKind: CHECK_KIND.POINTER, itemCode: 'LAP-PAD-006', name: '포인터' },
]

export function toCheckableItems(checklistItems) {
  return checklistItems
    .filter((item) => CHECKABLE_ITEM_CODES[item.itemCode])
    .map((item) => ({ ...item, checkKind: CHECKABLE_ITEM_CODES[item.itemCode] }))
}

export function toUniversalCheckItems(checklistItems = []) {
  const byCode = new Map(checklistItems.map((item) => [item.itemCode, item]))
  return WEB_DEVICE_CHECKS.map((definition) => ({
    ...definition,
    ...(byCode.get(definition.itemCode) || {}),
  })).sort((left, right) => Number(Boolean(right.checklistItemId)) - Number(Boolean(left.checklistItemId)))
}
