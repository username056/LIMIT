import { describe, expect, it } from 'vitest'

import { usePointerInteractionCheck } from '../usePointerInteractionCheck'

// jsdom에는 PointerEvent 생성자가 없어서, 우리 코드가 실제로 읽는 두 속성(pointerType/pressure)만
// 얹은 일반 Event로 대체한다.
function pointerEvent(type, pointerType, pressure = 0) {
  const event = new Event(type)
  Object.defineProperty(event, 'pointerType', { value: pointerType })
  Object.defineProperty(event, 'pressure', { value: pressure })
  return event
}

describe('usePointerInteractionCheck', () => {
  it('마우스 down + move가 감지되면 passed', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['mouse'] })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'mouse'))
    for (let i = 0; i < 4; i += 1) window.dispatchEvent(pointerEvent('pointermove', 'mouse'))

    expect(check.finish()).toBe(true)
    expect(check.status.value).toBe('passed')
  })

  it('down만 있고 move·wheel이 전혀 없으면 failed', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['mouse'] })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'mouse'))

    expect(check.finish()).toBe(false)
    expect(check.status.value).toBe('failed')
  })

  it('down 이후 wheel만 있어도 passed', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['mouse'] })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'mouse'))
    window.dispatchEvent(new WheelEvent('wheel'))

    expect(check.finish()).toBe(true)
  })

  it('pointerType이 다르면 무시한다 (터치스크린 점검 중 마우스 이벤트)', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['touch'] })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'mouse'))
    window.dispatchEvent(pointerEvent('pointermove', 'mouse'))

    expect(check.finish()).toBe(false)
  })

  it('requirePressure가 true면 필압 없는 입력은 실패 처리한다 (스타일러스)', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['pen'], requirePressure: true })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'pen', 0))
    for (let i = 0; i < 4; i += 1) window.dispatchEvent(pointerEvent('pointermove', 'pen', 0))

    expect(check.finish()).toBe(false)
  })

  it('requirePressure가 true여도 필압이 감지되면 passed', () => {
    const check = usePointerInteractionCheck({ pointerTypes: ['pen'], requirePressure: true })
    check.start(window)

    window.dispatchEvent(pointerEvent('pointerdown', 'pen', 0.5))
    for (let i = 0; i < 4; i += 1) window.dispatchEvent(pointerEvent('pointermove', 'pen', 0.5))

    expect(check.finish()).toBe(true)
  })
})
