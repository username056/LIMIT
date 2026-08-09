import { describe, expect, it } from 'vitest'
import { diagnosisCompletionFieldNames, isDeviceInfoItem, isDiagnosisComplete } from '../diagnosisCompletion'

// 값이 채워진 필드 이름만 모아 hasValue 함수를 만듭니다.
function filled(...fieldNames) {
  return (fieldName) => fieldNames.includes(fieldName)
}

describe('diagnosisCompletion', () => {
  it('기기 정보 항목은 OCR 필드를 본다', () => {
    expect(isDeviceInfoItem({ itemCode: 'LAP-SCR-013' })).toBe(true)
    expect(isDeviceInfoItem({ itemCode: 'SYS-003' })).toBe(true)
    expect(isDeviceInfoItem({ itemCode: 'LAP-BAT-001' })).toBe(false)
    expect(diagnosisCompletionFieldNames({ itemCode: 'SYS-003' }))
      .toEqual(['MODEL_NAME', 'STORAGE_CAPACITY', 'OS_VERSION', 'CPU'])
  })

  it('필요한 값이 모두 채워지면 완료로 본다', () => {
    const item = { parserType: 'DXDIAG' }
    expect(isDiagnosisComplete(item, filled('RAM', 'GPU'))).toBe(true)
    // 전부가 아니라 최소값만 봅니다. 기기에 따라 안 나오는 값이 있어서입니다.
    expect(isDiagnosisComplete(item, filled('RAM', 'GPU', 'SOUND_DEVICE'))).toBe(true)
  })

  it('하나라도 비면 완료가 아니다', () => {
    expect(isDiagnosisComplete({ parserType: 'DXDIAG' }, filled('RAM'))).toBe(false)
    expect(isDiagnosisComplete(
      { parserType: 'BATTERY_REPORT' },
      filled('DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY'),
    )).toBe(false)
  })

  it('자동 인식이 없는 항목은 값이 있어도 완료로 보지 않는다', () => {
    // 사진·영상으로 확인하는 항목입니다. 진단값으로 대신할 수 없습니다.
    expect(isDiagnosisComplete({ itemCode: 'LAP-SCR-001' }, () => true)).toBe(false)
    expect(isDiagnosisComplete(undefined, () => true)).toBe(false)
  })
})
