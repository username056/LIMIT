import { afterEach, describe, expect, it } from 'vitest'

import { KEYBOARD_ROWS, NUMPAD_ROWS, useKeyboardCheck } from '../useKeyboardCheck'

function press(code) {
  window.dispatchEvent(new KeyboardEvent('keydown', { code }))
}

function pressWithOptions(options) {
  window.dispatchEvent(new KeyboardEvent('keydown', options))
}

describe('useKeyboardCheck', () => {
  let check

  afterEach(() => {
    check?.stop()
  })

  it('대상 키를 모두 누르면 passed가 된다', () => {
    check = useKeyboardCheck()
    check.start()

    KEYBOARD_ROWS.flat().forEach(press)
    const result = check.finish()

    expect(check.status.value).toBe('passed')
    expect(result.missingCodes).toHaveLength(0)
  })

  it('일부만 눌러도 진행을 막지 않고 응답 없음 키를 기록한다', () => {
    check = useKeyboardCheck()
    check.start()

    press('KeyA')
    press('KeyB')
    const result = check.finish()

    expect(check.status.value).toBe('passedWithMissing')
    expect(result.pressedCodes).toEqual(expect.arrayContaining(['KeyA', 'KeyB']))
    expect(result.missingCodes).not.toContain('KeyA')
    expect(result.missingCodes.length).toBe(KEYBOARD_ROWS.flat().length - 2)
  })

  it('대상 목록에 없는 키 입력은 무시한다', () => {
    check = useKeyboardCheck()
    check.start()

    press('F13')
    expect(check.pressedCount.value).toBe(0)
  })

  it('includeNumpad가 true면 숫자패드 키만 대상으로 삼는다', () => {
    check = useKeyboardCheck({ includeNumpad: true })
    check.start()

    NUMPAD_ROWS.flat().forEach(press)
    const result = check.finish()

    expect(check.status.value).toBe('passed')
    expect(result.missingCodes).toHaveLength(0)
  })

  it('stop 이후에는 키 입력을 더 이상 반영하지 않는다', () => {
    check = useKeyboardCheck()
    check.start()
    check.stop()

    press('KeyA')

    expect(check.pressedCount.value).toBe(0)
  })

  it('오른쪽 Shift 위치값과 한영·한자 별칭을 표준 코드로 표시한다', () => {
    check = useKeyboardCheck()
    check.start()

    pressWithOptions({ code: 'ShiftLeft', key: 'Shift', location: 2 })
    pressWithOptions({ code: 'AltRight', key: 'HangulMode' })
    pressWithOptions({ code: 'ControlRight', key: 'HanjaMode' })

    expect(check.pressed.has('ShiftRight')).toBe(true)
    expect(check.pressed.has('Lang1')).toBe(true)
    expect(check.pressed.has('Lang2')).toBe(true)
  })

})
