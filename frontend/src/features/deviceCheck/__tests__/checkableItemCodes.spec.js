import { describe, expect, it } from 'vitest'

import { CHECK_KIND, toCheckableItems } from '../checkableItemCodes'

describe('toCheckableItems', () => {
  it('itemCode가 일치하고 evidenceType이 SELLER_CONFIRMATION인 항목만 골라 checkKind를 붙인다', () => {
    const items = [
      { checklistItemId: 1, itemCode: 'LAP-FTR-CAM', evidenceType: 'SELLER_CONFIRMATION' },
      { checklistItemId: 2, itemCode: 'LAP-ID-001', evidenceType: 'PHOTO' },
      { checklistItemId: 3, itemCode: 'LAP-KBD-005', evidenceType: 'SELLER_CONFIRMATION' },
    ]

    const result = toCheckableItems(items)

    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({ checklistItemId: 1, checkKind: CHECK_KIND.CAMERA })
    expect(result[1]).toMatchObject({ checklistItemId: 3, checkKind: CHECK_KIND.KEYBOARD })
  })

  it('itemCode는 일치해도 evidenceType이 아직 PHOTO/VIDEO인 기존 상품 항목은 제외한다', () => {
    // 이 기능 배포 전에 스냅샷된 기존 상품은 itemCode가 같아도 evidenceType이 옛날 값(PHOTO/VIDEO)일 수 있다.
    const items = [
      { checklistItemId: 1, itemCode: 'LAP-FTR-CAM', evidenceType: 'PHOTO' },
      { checklistItemId: 2, itemCode: 'LAP-KBD-005', evidenceType: 'VIDEO' },
    ]

    expect(toCheckableItems(items)).toEqual([])
  })

  it('대상 목록이 비어 있으면 빈 배열을 반환한다', () => {
    expect(toCheckableItems([])).toEqual([])
  })
})
