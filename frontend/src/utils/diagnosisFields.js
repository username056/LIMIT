// 자동 인식된 사양 카드에서 쓰는 필드 라벨·그룹·배터리 등급 기준을 한 곳에 모아 둡니다.
// ProductDetailPage.vue와 DiagnosisSpecList.vue가 함께 씁니다.

export const DIAGNOSIS_FIELD_LABELS = {
  MODEL_NAME: '모델명',
  CPU: 'CPU',
  RAM: 'RAM',
  GPU: 'GPU',
  STORAGE_CAPACITY: '저장 용량',
  OS_VERSION: 'OS 버전',
  GPU_MEMORY: 'GPU 메모리',
  DRIVER_VERSION: '그래픽 드라이버 버전',
  SOUND_DEVICE: '사운드 장치',
  DESIGN_CAPACITY: '출고 시 용량',
  FULL_CHARGE_CAPACITY: '현재 최대 충전 용량',
  CYCLE_COUNT: '충전 사이클',
  BATTERY_MANUFACTURER: '배터리 제조사',
  CAPACITY_RATIO: '배터리 용량 비율',
}

export function diagnosisFieldLabel(fieldName) {
  return DIAGNOSIS_FIELD_LABELS[fieldName] || fieldName
}

// 필드값이 길어도 그대로 두되, 충전 사이클만 숫자 뒤에 단위를 붙여 줍니다.
export function diagnosisFieldValue(fieldName, value) {
  if (fieldName === 'CYCLE_COUNT' && value) return `${value}회`
  return value
}

export const BASIC_INFO_FIELDS = ['MODEL_NAME', 'CPU', 'RAM', 'STORAGE_CAPACITY', 'OS_VERSION']
export const GRAPHICS_DEVICE_FIELDS = ['GPU', 'GPU_MEMORY', 'DRIVER_VERSION', 'SOUND_DEVICE']
export const BATTERY_DETAIL_FIELDS = ['DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY', 'BATTERY_MANUFACTURER']
// 건강도(CAPACITY_RATIO)를 계산하지 못했을 때, 그나마 확보된 배터리 정보를 참고용으로 보여주는 순서입니다.
export const BATTERY_FALLBACK_FIELDS = ['CYCLE_COUNT', 'FULL_CHARGE_CAPACITY', 'DESIGN_CAPACITY', 'BATTERY_MANUFACTURER']
export const ALL_BATTERY_FIELDS = ['DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY', 'CYCLE_COUNT', 'BATTERY_MANUFACTURER', 'CAPACITY_RATIO']

/*
  배터리 건강도(SoH) 등급 구간입니다.
  ---------------------------------------------------------------------------
  제조사 진단 화면(Dell 등)에서 흔히 쓰는 4단계 표현을 참고하되, 실제 구간(85/70/50)은
  리튬이온 배터리 문헌에서 70~80%를 성능 저하·교체 검토 기준으로 보는 사례를 바탕으로
  서비스 자체 기준으로 정했습니다. 노트북은 제조사·모델·사용 환경별 편차가 커서 절대적인
  판정이 아니라 구매 판단을 돕는 참고 등급입니다.
*/
export const BATTERY_GRADE_TIERS = [
  {
    min: 85,
    label: '우수',
    tone: 'excellent',
    scenario: '배터리 상태가 우수해 이동 중 사용에도 무리가 없어요.',
  },
  {
    min: 70,
    label: '양호',
    tone: 'good',
    scenario: '어댑터 없이도 사용할 수 있지만, 새 배터리보다 사용 시간은 줄어들 수 있어요.',
  },
  {
    min: 50,
    label: '확인 필요',
    tone: 'caution',
    scenario: '문서 작업처럼 가벼운 사용은 가능할 수 있지만, 외부에서 오래 쓰려면 사용 시간이 짧게 느껴질 수 있어요. 주로 전원 연결해서 쓴다면 큰 문제가 아닐 수 있습니다.',
  },
  {
    min: 0,
    label: '교체 검토',
    tone: 'replace',
    scenario: '배터리 사용 시간이 짧을 가능성이 높아요. 전원 연결 위주로 쓰는 용도라면 구매 전 가격과 교체 비용을 같이 확인해 보세요.',
  },
]

export function batteryGradeFor(ratio) {
  if (typeof ratio !== 'number' || Number.isNaN(ratio)) return null
  return BATTERY_GRADE_TIERS.find((tier) => ratio >= tier.min) || null
}

export const BATTERY_GRADE_DISCLAIMER = '배터리 건강도(SoH)는 설계 용량 대비 현재 완전 충전 용량의 비율입니다. 노트북은 제조사·모델·사용 환경별 편차가 있어 본 등급은 구매 판단을 돕는 참고 정보입니다.'

export const BATTERY_GRADE_BASIS = '등급 체계는 제조사 진단 화면에서 흔히 쓰는 4단계 표현을 참고하되, 구간은 리튬이온 배터리 문헌에서 70~80%를 성능 저하·교체 검토 기준으로 보는 사례를 바탕으로 서비스 자체 기준으로 설정했습니다.'

export const CYCLE_COUNT_DISCLAIMER = '충전 사이클은 사용 이력을 보여주는 참고값입니다. 모델별 정격 수명이 달라 등급 산정에는 사용하지 않습니다.'

export const BATTERY_UNMEASURABLE_TITLE = '배터리 건강도를 측정할 수 없습니다'
export const BATTERY_UNMEASURABLE_DESCRIPTION = '업로드된 배터리 리포트에서 건강도 계산에 필요한 값을 모두 확인하지 못했습니다.'

export const BATTERY_UNAVAILABLE_TITLE = '배터리 정보를 확인할 수 없습니다'
export const BATTERY_UNAVAILABLE_DESCRIPTION = '판매자가 배터리 리포트를 등록하지 않았거나 자동 인식에 실패했습니다.'
