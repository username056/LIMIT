import { computed, reactive, ref } from 'vue'

// 죽은 키는 중고 노트북에서 흔한 하자라 전수검사가 필요하지만, 100% 통과를 강제하면 등록 자체를
// 막아버릴 위험이 있다. 그래서 실패한 키는 "응답 없음"으로만 기록하고 완료 여부는 판매자가 직접
// 끝낼 수 있게 한다(finish는 언제든 호출 가능, 완료 여부는 isComplete로만 노출).
export const KEYBOARD_ROWS = [
  ['Backquote', 'Digit1', 'Digit2', 'Digit3', 'Digit4', 'Digit5', 'Digit6', 'Digit7', 'Digit8', 'Digit9', 'Digit0', 'Minus', 'Equal', 'Backspace'],
  ['Tab', 'KeyQ', 'KeyW', 'KeyE', 'KeyR', 'KeyT', 'KeyY', 'KeyU', 'KeyI', 'KeyO', 'KeyP', 'BracketLeft', 'BracketRight', 'Backslash'],
  ['CapsLock', 'KeyA', 'KeyS', 'KeyD', 'KeyF', 'KeyG', 'KeyH', 'KeyJ', 'KeyK', 'KeyL', 'Semicolon', 'Quote', 'Enter'],
  ['ShiftLeft', 'KeyZ', 'KeyX', 'KeyC', 'KeyV', 'KeyB', 'KeyN', 'KeyM', 'Comma', 'Period', 'Slash', 'ShiftRight'],
  ['ControlLeft', 'MetaLeft', 'AltLeft', 'Lang2', 'Space', 'Lang1', 'AltRight', 'ArrowLeft', 'ArrowUp', 'ArrowDown', 'ArrowRight'],
]

export const NUMPAD_ROWS = [
  ['NumLock', 'NumpadDivide', 'NumpadMultiply', 'NumpadSubtract'],
  ['Numpad7', 'Numpad8', 'Numpad9', 'NumpadAdd'],
  ['Numpad4', 'Numpad5', 'Numpad6'],
  ['Numpad1', 'Numpad2', 'Numpad3', 'NumpadEnter'],
  ['Numpad0', 'NumpadDecimal'],
]

export function useKeyboardCheck({ includeNumpad = false } = {}) {
  const rows = includeNumpad ? NUMPAD_ROWS : KEYBOARD_ROWS
  const targetCodes = rows.flat()
  const pressed = reactive(new Set())
  const status = ref('idle') // idle | listening | passed | passedWithMissing

  const pressedCount = computed(() => pressed.size)
  const total = targetCodes.length
  const missingCodes = computed(() => targetCodes.filter((code) => !pressed.has(code)))
  const isComplete = computed(() => pressedCount.value >= total)

  function handleKeydown(event) {
    if (!event.isTrusted) return
    if (!targetCodes.includes(event.code)) return
    pressed.add(event.code)
  }

  function start() {
    pressed.clear()
    status.value = 'listening'
    window.addEventListener('keydown', handleKeydown)
  }

  function finish() {
    window.removeEventListener('keydown', handleKeydown)
    status.value = isComplete.value ? 'passed' : 'passedWithMissing'
    return { pressedCodes: [...pressed], missingCodes: missingCodes.value }
  }

  function stop() {
    window.removeEventListener('keydown', handleKeydown)
  }

  return { status, rows, pressed, pressedCount, total, missingCodes, isComplete, start, finish, stop }
}
