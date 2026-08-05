import { describe, expect, it } from 'vitest'

import { CHECK_KIND, toCheckableItems, toUniversalCheckItems } from '../checkableItemCodes'

describe('toCheckableItems', () => {
  it('7개 웹 점검 itemCode를 evidenceType과 무관하게 골라 checkKind를 붙인다', () => {
    const items = [
      { checklistItemId: 1, itemCode: 'LAP-FTR-CAM', evidenceType: 'SELLER_CONFIRMATION' },
      { checklistItemId: 2, itemCode: 'LAP-ID-001', evidenceType: 'PHOTO' },
      { checklistItemId: 3, itemCode: 'LAP-KBD-005', evidenceType: 'SELLER_CONFIRMATION' },
      { checklistItemId: 4, itemCode: 'LAP-DSP-003', evidenceType: 'VIDEO' },
      { checklistItemId: 5, itemCode: 'LAP-CHG-007', evidenceType: 'VIDEO' },
    ]

    const result = toCheckableItems(items)

    expect(result).toHaveLength(4)
    expect(result[0]).toMatchObject({ checklistItemId: 1, checkKind: CHECK_KIND.CAMERA })
    expect(result[1]).toMatchObject({ checklistItemId: 3, checkKind: CHECK_KIND.KEYBOARD })
    expect(result[2]).toMatchObject({ checklistItemId: 4, checkKind: CHECK_KIND.DISPLAY })
    expect(result[3]).toMatchObject({ checklistItemId: 5, checkKind: CHECK_KIND.CHARGING })
  })

  it('기존 PHOTO VIDEO 스냅샷도 코드가 지원 대상이면 직접 점검에 포함한다', () => {
    const items = [
      { checklistItemId: 1, itemCode: 'LAP-FTR-CAM', evidenceType: 'PHOTO' },
      { checklistItemId: 2, itemCode: 'LAP-KBD-005', evidenceType: 'VIDEO' },
    ]

    expect(toCheckableItems(items)).toHaveLength(2)
  })

  it('대상 목록이 비어 있으면 빈 배열을 반환한다', () => {
    expect(toCheckableItems([])).toEqual([])
  })
})

describe('toUniversalCheckItems', () => {
  it('체크리스트가 비어 있어도 키보드와 포인터 점검을 만든다', () => {
    const result = toUniversalCheckItems([])

    expect(result).toHaveLength(2)
    expect(result.map((item) => item.testType)).toEqual([
      'KEYBOARD', 'TOUCHPAD',
    ])
  })
})
