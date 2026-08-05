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

// 카메라·마이크·스피커·디스플레이·충전은 실제 동작 여부를 브라우저에서 확인할 방법이 없거나
// (스피커/디스플레이) 이미 별도 흐름(영상 증빙, 스펙 확인)으로 검증되어 여기서는 다루지 않는다.
// 모든 매물에 항상 노출하는 항목은 키보드·포인터뿐이다.
export const WEB_DEVICE_CHECKS = [
  { testType: 'KEYBOARD', checkKind: CHECK_KIND.KEYBOARD, itemCode: 'LAP-KBD-005', name: '키보드' },
  { testType: 'TOUCHPAD', checkKind: CHECK_KIND.POINTER, itemCode: 'LAP-PAD-006', name: '포인터' },
]

// 숫자 키패드는 매물마다 있고 없고가 달라(선택 기능) 모든 상품에 항상 붙이지 않고,
// 이 매물의 체크리스트에 실제로 있을 때만 목록에 추가한다.
const OPTIONAL_WEB_CHECKS = [
  { testType: 'NUMPAD', checkKind: CHECK_KIND.NUMPAD, itemCode: 'LAP-FTR-NUM' },
]

export function toCheckableItems(checklistItems) {
  return checklistItems
    .filter((item) => CHECKABLE_ITEM_CODES[item.itemCode])
    .map((item) => ({ ...item, checkKind: CHECKABLE_ITEM_CODES[item.itemCode] }))
}

export function toUniversalCheckItems(checklistItems = []) {
  const byCode = new Map(checklistItems.map((item) => [item.itemCode, item]))
  const universal = WEB_DEVICE_CHECKS.map((definition) => ({
    ...definition,
    ...(byCode.get(definition.itemCode) || {}),
  }))
  const optional = OPTIONAL_WEB_CHECKS
    .filter((definition) => byCode.has(definition.itemCode))
    .map((definition) => ({ ...definition, ...byCode.get(definition.itemCode) }))
  return [...universal, ...optional]
    .sort((left, right) => Number(Boolean(right.checklistItemId)) - Number(Boolean(left.checklistItemId)))
}
