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

// Windows 키·Alt는 OS/브라우저가 자체 동작(시작 메뉴, 메뉴 포커스 이동)으로 먼저 가로채는 경우가 많아
// 실제 키보드는 멀쩡해도 keyup이 페이지까지 도달하지 않을 수 있다.
export const OS_RESERVED_CODES = ['MetaLeft', 'AltLeft', 'AltRight']

// 일부 노트북은 오른쪽 Shift를 눌러도 code와 location이 둘 다 빈 값/표준값으로만 보고돼(실측 확인됨)
// 왼쪽과 구분할 정보 자체가 없다. code·location 어느 쪽으로도 판단이 불가능한 브라우저 한계라
// 코드로 고칠 수 없다.
export const AMBIGUOUS_CODES = ['ShiftRight']

// 위 두 부류 모두 "안 눌렸다고 해서 키가 고장났다고 볼 수 없는" 키다. 그래서 "응답 없음"에 넣어
// 재시도를 강제하지 않고, 별도로 "감지 보장 안 됨"으로만 안내한다.
const UNRELIABLE_CODES = [...OS_RESERVED_CODES, ...AMBIGUOUS_CODES]

export function useKeyboardCheck({ includeNumpad = false } = {}) {
  const rows = includeNumpad ? NUMPAD_ROWS : KEYBOARD_ROWS
  const targetCodes = rows.flat()
  const pressed = reactive(new Set())
  // 오른쪽 Alt처럼 일부 노트북(한글 자판 등)엔 물리적으로 없는 키가 있다. 안 눌렸다고 자동으로
  // "없는 키"로 단정할 수는 없으니(진짜 고장일 수도 있음), 판매자가 직접 표시한 키만 판정에서 뺀다.
  const excludedCodes = reactive(new Set())
  const status = ref('idle') // idle | listening | passed | passedWithMissing

  const activeCodes = computed(() => targetCodes.filter((code) => !excludedCodes.has(code)))
  const requiredCodes = computed(() => activeCodes.value.filter((code) => !UNRELIABLE_CODES.includes(code)))
  const unreliableTargetCodes = computed(() => activeCodes.value.filter((code) => UNRELIABLE_CODES.includes(code)))

  const pressedCount = computed(() => requiredCodes.value.filter((code) => pressed.has(code)).length)
  const total = computed(() => requiredCodes.value.length)
  const missingCodes = computed(() => requiredCodes.value.filter((code) => !pressed.has(code)))
  const missingUnreliableCodes = computed(() => unreliableTargetCodes.value.filter((code) => !pressed.has(code)))
  const isComplete = computed(() => missingCodes.value.length === 0)

  function toggleExcluded(code) {
    if (!targetCodes.includes(code) || pressed.has(code)) return
    if (excludedCodes.has(code)) excludedCodes.delete(code)
    else excludedCodes.add(code)
  }

  function normalizedCode(event) {
    const { code, location, keyCode } = event
    const pressedKey = event['key']
    // 일부 브라우저·드라이버는 왼쪽/오른쪽 Shift의 code를 서로 바꿔 보고하는 경우가 있어(둘 다
    // 'Shift'로만 나오는 key 값과 달리 code는 뒤바뀔 수 있음), location이 좌/우를 명확히 가리키면
    // code보다 location을 우선 신뢰한다. location이 표준값(0)이면 code를 그대로 믿는다.
    if (pressedKey === 'Shift' || code === 'ShiftLeft' || code === 'ShiftRight') {
      if (location === 2) return 'ShiftRight'
      if (location === 1) return 'ShiftLeft'
    }
    if (['HangulMode', 'KoreanMode'].includes(pressedKey) || keyCode === 21) {
      return 'Lang1'
    }
    if (pressedKey === 'HanjaMode' || keyCode === 25) {
      return 'Lang2'
    }
    return code
  }

  function markPressed(code) {
    if (!targetCodes.includes(code)) return
    pressed.add(code)
    excludedCodes.delete(code)
  }

  function handleKey(event) {
    const code = normalizedCode(event)
    // 대상 키의 기본 동작(Tab 포커스 이동, Enter/Space의 포커스된 버튼 클릭, 화살표/Space 스크롤,
    // Windows·Alt의 메뉴 포커스 이동 등)을 막는다. 안 막으면 예를 들어 Tab으로 "완료" 버튼에 포커스가
    // 넘어간 뒤 Enter를 누르는 순간 검사 중인데도 완료가 눌려버린다.
    if (targetCodes.includes(code)) event.preventDefault()
    markPressed(code)
  }

  function start() {
    pressed.clear()
    status.value = 'listening'
    // 시작 시점에 다른 버튼이 포커스를 쥐고 있으면 그 버튼이 Enter/Space에 반응할 수 있어 미리 비운다.
    document.activeElement?.blur()
    // capture phase로 등록해 브라우저 자체 단축키 처리보다 먼저 이벤트를 받는다.
    window.addEventListener('keydown', handleKey, true)
    window.addEventListener('keyup', handleKey, true)
  }

  function finish() {
    window.removeEventListener('keydown', handleKey, true)
    window.removeEventListener('keyup', handleKey, true)
    status.value = isComplete.value ? 'passed' : 'passedWithMissing'
    return {
      pressedCodes: [...pressed],
      missingCodes: missingCodes.value,
      missingUnreliableCodes: missingUnreliableCodes.value,
      excludedCodes: [...excludedCodes],
    }
  }

  function stop() {
    window.removeEventListener('keydown', handleKey, true)
    window.removeEventListener('keyup', handleKey, true)
  }

  return {
    status, rows, pressed, pressedCount, total, missingCodes, missingUnreliableCodes, isComplete,
    excludedCodes, toggleExcluded,
    start, finish, stop,
  }
}
