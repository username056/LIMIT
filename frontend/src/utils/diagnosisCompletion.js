/*
  진단으로 채워진 항목을 '완료'로 볼 기준.
  ---------------------------------------------------------------------------
  진단 프로그램이 값을 채워도 증빙 파일(evidence) 행은 생기지 않습니다. 그래서 파일이
  있느냐만 보던 상품 상세는 판매자가 다 채운 항목을 '자료 준비 중'으로 보여 줬습니다.
  판매하기 화면은 진단값을 보고 '자동 입력 완료'라고 하던 터라, 같은 항목이 화면마다
  다르게 보였습니다.

  기준을 여기 한 곳에 둡니다. 화면마다 각자 판단하면 세 번째 기준이 생기고, 그때부터는
  어느 화면 말이 맞는지 아무도 모르게 됩니다.
*/

// 기기 정보 항목은 사진에서 글자를 읽어(OCR) 채웁니다.
export const OCR_FIELD_NAMES = ['MODEL_NAME', 'STORAGE_CAPACITY', 'OS_VERSION', 'CPU']

// 파서가 만든 파일에서 읽는 항목은 종류마다 필요한 값이 다릅니다. 전부가 아니라 '이것만
// 있으면 확인된 것으로 본다'는 최소값입니다. 기기에 따라 안 나오는 값이 있어서입니다.
const COMPLETION_FIELD_NAMES_BY_PARSER = {
  DXDIAG: ['RAM', 'GPU'],
  BATTERY_REPORT: ['DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY', 'CAPACITY_RATIO'],
}

const DEVICE_INFO_ITEM_CODES = ['LAP-SCR-013', 'SYS-003']

export function isDeviceInfoItem(item) {
  return DEVICE_INFO_ITEM_CODES.includes(item?.itemCode)
}

export function diagnosisCompletionFieldNames(item) {
  if (isDeviceInfoItem(item)) return OCR_FIELD_NAMES
  return COMPLETION_FIELD_NAMES_BY_PARSER[item?.parserType] || []
}

/**
 * 항목에 필요한 진단값이 모두 채워졌는지 봅니다.
 *
 * @param item 체크리스트 항목
 * @param hasValue (fieldName) => boolean — 그 값이 채워졌는지 답하는 함수.
 *   화면마다 값을 들고 있는 모양이 달라(등록은 항목별 fields, 상세는 사양 요약) 판단만
 *   여기서 하고 값 찾기는 부르는 쪽에 맡깁니다.
 */
export function isDiagnosisComplete(item, hasValue) {
  const requiredFieldNames = diagnosisCompletionFieldNames(item)
  if (!requiredFieldNames.length) return false
  return requiredFieldNames.every((fieldName) => hasValue(fieldName))
}
