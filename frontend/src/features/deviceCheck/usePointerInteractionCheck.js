import { computed, reactive, ref } from 'vue'

// 마우스/트랙패드/터치스크린/스타일러스가 브라우저 입장에서는 전부 Pointer Events로만 구분된다.
// pointerType 필터 하나로 네 가지 항목(POINTER/TOUCHSCREEN/STYLUS)을 전부 커버한다.
export function usePointerInteractionCheck({ pointerTypes, requirePressure = false } = {}) {
  const status = ref('idle') // idle | listening | passed | failed
  const seen = reactive({ down: false, move: false, wheel: false, pressure: false })
  let moveSamples = 0
  let target = null

  const passed = computed(
    () => seen.down && (seen.move || seen.wheel) && (!requirePressure || seen.pressure),
  )

  function matches(event) {
    return !pointerTypes || pointerTypes.includes(event.pointerType)
  }

  function onPointerDown(event) {
    if (!matches(event)) return
    seen.down = true
    if (event.pressure > 0) seen.pressure = true
  }

  function onPointerMove(event) {
    if (!matches(event)) return
    moveSamples += 1
    if (moveSamples > 3) seen.move = true
    if (event.pressure > 0) seen.pressure = true
  }

  function onWheel() {
    seen.wheel = true
  }

  function start(el = window) {
    target = el
    moveSamples = 0
    Object.assign(seen, { down: false, move: false, wheel: false, pressure: false })
    status.value = 'listening'
    target.addEventListener('pointerdown', onPointerDown)
    target.addEventListener('pointermove', onPointerMove)
    target.addEventListener('wheel', onWheel, { passive: true })
  }

  function finish() {
    stop()
    status.value = passed.value ? 'passed' : 'failed'
    return passed.value
  }

  function stop() {
    target?.removeEventListener('pointerdown', onPointerDown)
    target?.removeEventListener('pointermove', onPointerMove)
    target?.removeEventListener('wheel', onWheel)
    target = null
  }

  return { status, seen, passed, start, finish, stop }
}
