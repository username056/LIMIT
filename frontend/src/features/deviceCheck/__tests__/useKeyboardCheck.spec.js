import { afterEach, describe, expect, it } from 'vitest'

import { AMBIGUOUS_CODES, KEYBOARD_ROWS, NUMPAD_ROWS, OS_RESERVED_CODES, useKeyboardCheck } from '../useKeyboardCheck'

const UNRELIABLE_CODES = [...OS_RESERVED_CODES, ...AMBIGUOUS_CODES]
const REQUIRED_CODES = KEYBOARD_ROWS.flat().filter((code) => !UNRELIABLE_CODES.includes(code))

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
    expect(result.missingCodes.length).toBe(REQUIRED_CODES.length - 2)
  })

  it('OS 예약 키(Win/Alt)와 좌우 구분 불가 키(오른쪽 Shift)는 눌리지 않아도 응답 없음으로 취급하지 않고 passed가 된다', () => {
    check = useKeyboardCheck()
    check.start()

    REQUIRED_CODES.forEach(press)
    const result = check.finish()

    expect(check.status.value).toBe('passed')
    expect(result.missingCodes).toHaveLength(0)
    expect(result.missingUnreliableCodes).toEqual(expect.arrayContaining(UNRELIABLE_CODES))
  })

  it('오른쪽 Shift는 code·location이 모두 무정보로 와도(브라우저 한계) 응답 없음으로 취급하지 않는다', () => {
    check = useKeyboardCheck()
    check.start()

    REQUIRED_CODES.forEach(press)
    // 실측된 실패 케이스: code가 빈 문자열, location이 0(표준)으로만 와서 좌우 구분이 불가능한 경우
    pressWithOptions({ code: '', key: 'Shift', location: 0 })
    const result = check.finish()

    expect(check.status.value).toBe('passed')
    expect(result.missingUnreliableCodes).toContain('ShiftRight')
  })

  it('OS 예약 키를 누르면 preventDefault를 호출해 기본 동작을 막는다', () => {
    check = useKeyboardCheck()
    check.start()

    const event = new KeyboardEvent('keydown', { code: 'AltLeft', cancelable: true })
    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(check.pressed.has('AltLeft')).toBe(true)
  })

  it('Tab·Enter를 누르면 기본 동작(포커스 이동, 버튼 클릭)을 막는다', () => {
    check = useKeyboardCheck()
    check.start()

    const tabDown = new KeyboardEvent('keydown', { code: 'Tab', cancelable: true })
    const enterDown = new KeyboardEvent('keydown', { code: 'Enter', cancelable: true })
    const spaceUp = new KeyboardEvent('keyup', { code: 'Space', cancelable: true })
    window.dispatchEvent(tabDown)
    window.dispatchEvent(enterDown)
    window.dispatchEvent(spaceUp)

    expect(tabDown.defaultPrevented).toBe(true)
    expect(enterDown.defaultPrevented).toBe(true)
    expect(spaceUp.defaultPrevented).toBe(true)
  })

  it('시작하면 다른 곳에 있던 포커스를 비워 Enter/Space가 그 버튼을 누르지 못하게 한다', () => {
    const button = document.createElement('button')
    document.body.appendChild(button)
    button.focus()
    expect(document.activeElement).toBe(button)

    check = useKeyboardCheck()
    check.start()

    expect(document.activeElement).not.toBe(button)
    document.body.removeChild(button)
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

  it('브라우저가 code를 정상적으로 보고하는 표준 케이스에서도 왼쪽·오른쪽 Shift를 각각 구분한다', () => {
    check = useKeyboardCheck()
    check.start()

    pressWithOptions({ code: 'ShiftRight', key: 'Shift', location: 2 })
    pressWithOptions({ code: 'ShiftLeft', key: 'Shift', location: 1 })

    expect(check.pressed.has('ShiftRight')).toBe(true)
    expect(check.pressed.has('ShiftLeft')).toBe(true)
  })

  it('code가 ShiftRight로 잘못 보고돼도 location이 왼쪽이면 ShiftLeft로 바로잡는다', () => {
    check = useKeyboardCheck()
    check.start()

    pressWithOptions({ code: 'ShiftRight', key: 'Shift', location: 1 })

    expect(check.pressed.has('ShiftLeft')).toBe(true)
    expect(check.pressed.has('ShiftRight')).toBe(false)
  })

  it('없는 키로 표시하면 판정과 total에서 빠진다', () => {
    check = useKeyboardCheck()
    check.start()

    check.toggleExcluded('CapsLock')
    REQUIRED_CODES.filter((code) => code !== 'CapsLock').forEach(press)
    const result = check.finish()

    expect(check.status.value).toBe('passed')
    expect(result.missingCodes).toHaveLength(0)
    expect(result.excludedCodes).toContain('CapsLock')
    expect(check.total.value).toBe(REQUIRED_CODES.length - 1)
  })

  it('없음으로 표시한 키를 실제로 누르면 표시가 자동으로 풀린다', () => {
    check = useKeyboardCheck()
    check.start()

    check.toggleExcluded('KeyA')
    expect(check.excludedCodes.has('KeyA')).toBe(true)

    press('KeyA')

    expect(check.excludedCodes.has('KeyA')).toBe(false)
    expect(check.pressed.has('KeyA')).toBe(true)
  })

  it('이미 눌린 키는 없음으로 표시할 수 없다', () => {
    check = useKeyboardCheck()
    check.start()

    press('KeyA')
    check.toggleExcluded('KeyA')

    expect(check.excludedCodes.has('KeyA')).toBe(false)
  })

})
