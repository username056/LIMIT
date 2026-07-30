import { describe, expect, it } from 'vitest'

import { CHECK_KIND, toCheckableItems } from '../checkableItemCodes'

describe('toCheckableItems', () => {
  it('점검 대상 itemCode만 골라 checkKind를 붙인다', () => {
    const items = [
      { checklistItemId: 1, itemCode: 'LAP-FTR-CAM' },
      { checklistItemId: 2, itemCode: 'LAP-ID-001' },
      { checklistItemId: 3, itemCode: 'LAP-KBD-005' },
    ]

    const result = toCheckableItems(items)

    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({ checklistItemId: 1, checkKind: CHECK_KIND.CAMERA })
    expect(result[1]).toMatchObject({ checklistItemId: 3, checkKind: CHECK_KIND.KEYBOARD })
  })

  it('대상 목록이 비어 있으면 빈 배열을 반환한다', () => {
    expect(toCheckableItems([])).toEqual([])
  })
})
