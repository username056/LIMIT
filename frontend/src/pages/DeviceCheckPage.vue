<script setup>
import { computed, onBeforeUnmount, reactive, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { getProductChecklist, getProductDraftProgress, updateProductDraftProgress } from '../api/products'
import { CHECK_KIND, toUniversalCheckItems } from '../features/deviceCheck/checkableItemCodes'
import { useCameraCheck } from '../features/deviceCheck/useCameraCheck'
import { useMicCheck } from '../features/deviceCheck/useMicCheck'
import { useSpeakerCheck } from '../features/deviceCheck/useSpeakerCheck'
import { AMBIGUOUS_CODES, OS_RESERVED_CODES, useKeyboardCheck } from '../features/deviceCheck/useKeyboardCheck'
import { usePointerInteractionCheck } from '../features/deviceCheck/usePointerInteractionCheck'

const POINTER_TIMEOUT_MS = 10000

const CHECK_KIND_LABEL = {
  [CHECK_KIND.CAMERA]: '카메라',
  [CHECK_KIND.MIC]: '마이크',
  [CHECK_KIND.SPEAKER]: '스피커',
  [CHECK_KIND.DISPLAY]: '디스플레이',
  [CHECK_KIND.CHARGING]: '충전',
  [CHECK_KIND.KEYBOARD]: '키보드',
  [CHECK_KIND.NUMPAD]: '숫자 키패드',
  [CHECK_KIND.POINTER]: '마우스/터치패드',
  [CHECK_KIND.TOUCHSCREEN]: '터치스크린',
  [CHECK_KIND.STYLUS]: '스타일러스',
}

// 특정 기종의 실제 배열이 아니라 일반적인 표준 노트북 자판처럼 보이도록 하는 표시용 값입니다.
// 점검 로직(useKeyboardCheck)의 키 코드 목록은 그대로 두고, 라벨과 상대 너비만 덧입힙니다.
const KEY_LABEL_OVERRIDES = {
  Backquote: '`', Minus: '-', Equal: '=', Backspace: '⌫', Tab: 'Tab',
  BracketLeft: '[', BracketRight: ']', Backslash: '\\', CapsLock: 'Caps Lock',
  Semicolon: ';', Quote: "'", Enter: 'Enter', ShiftLeft: 'Shift', ShiftRight: 'Shift',
  Comma: ',', Period: '.', Slash: '/', ControlLeft: 'Ctrl', MetaLeft: 'Win',
  AltLeft: 'Alt', AltRight: 'Alt', Lang1: '한/영', Lang2: '한자', Space: 'Space',
  ArrowLeft: '←', ArrowUp: '↑', ArrowDown: '↓', ArrowRight: '→',
  NumLock: 'Num Lock', NumpadDivide: '/', NumpadMultiply: '*', NumpadSubtract: '-',
  NumpadAdd: '+', NumpadEnter: 'Enter', NumpadDecimal: '.',
}
const KEY_WIDTH_OVERRIDES = {
  Backspace: 2, Tab: 1.5, Backslash: 1.5, CapsLock: 1.75, Enter: 2.25,
  ShiftLeft: 2.25, ShiftRight: 2.25, ControlLeft: 1.25, MetaLeft: 1.25,
  AltLeft: 1.25, AltRight: 1.25, Space: 6.25,
}

function keyLabel(code) {
  if (KEY_LABEL_OVERRIDES[code]) return KEY_LABEL_OVERRIDES[code]
  if (code.startsWith('Digit')) return code.slice(5)
  if (code.startsWith('Key')) return code.slice(3)
  if (code.startsWith('Numpad')) return code.slice(6)
  return code
}

// 감지가 보장되지 않는 키 목록엔 왼쪽·오른쪽이 라벨만 보면 구분 안 되는 키(Alt, Shift)가 섞여 있어
// keyLabel 그대로 쓰면 "Alt, Alt"처럼 뭐가 뭔지 모르게 나온다. 이 목록 전용으로만 좌우를 밝힌다.
const UNRELIABLE_KEY_LABEL_OVERRIDES = {
  AltLeft: '왼쪽 Alt', AltRight: '오른쪽 Alt', ShiftRight: '오른쪽 Shift',
}
function unreliableKeyLabel(code) {
  return UNRELIABLE_KEY_LABEL_OVERRIDES[code] || keyLabel(code)
}
const KEYBOARD_UNRELIABLE_CODES = [...OS_RESERVED_CODES, ...AMBIGUOUS_CODES]

function keyWidth(code) {
  return KEY_WIDTH_OVERRIDES[code] || 1
}

const route = useRoute()
const router = useRouter()
const productId = route.params.productId

const RESULT = { SUCCESS: 'SUCCESS', FAILED: 'FAILED' }

const loading = ref(true)
const loadError = ref('')
const saveError = ref('')
const saving = ref(false)
const items = ref([])
const stepIndex = ref(0)
const itemResults = ref(new Map())
const existingResults = ref(new Map())
const existingDeviceResults = ref(new Map())
const draftStep = ref(1)
const finished = ref(false)
const videoEl = ref(null)
const pointerAreaEl = ref(null)
const keyboardMissing = ref([])
const keyboardMissingUnreliable = ref([])
const displayStarted = ref(false)
const chargingStarted = ref(false)
const forceRecheckIds = reactive(new Set())

/*
  돌아갈 곳은 2단계입니다.
  ---------------------------------------------------------------------------
  이 화면은 등록 2단계의 '직접 점검하기'로 들어옵니다. 그런데 돌아가면 늘
  1단계가 열렸습니다. 등록 화면의 startEdit이 "수정은 기기 정보부터 훑는다"는 뜻으로
  activeStep을 1로 못 박고 있어서입니다.

  점검을 마치고 온 사람은 고치러 온 것이 아니라 하던 일을 이어서 합니다. 주소에
  단계를 실어 보내 그 자리로 돌아가게 합니다(재검수 요청도 같은 방식으로 2단계를
  엽니다).
*/
const backToRegister = {
  name: 'seller-product-new',
  query: { step: '2', resumeProductId: String(productId) },
}

const camera = useCameraCheck()
const mic = useMicCheck()
const speaker = useSpeakerCheck()
let keyboard = null
const pointer = shallowRef(null)
let pointerTimeoutId = null

const currentItem = computed(() => items.value[stepIndex.value] || null)
const currentLabel = computed(() =>
  currentItem.value ? CHECK_KIND_LABEL[currentItem.value.checkKind] : '',
)
const currentAlreadyCompleted = computed(() => {
  const item = currentItem.value
  if (!item) return false
  if (forceRecheckIds.has(item.testType)) return false
  return item.status === 'COMPLETED'
    || existingResults.value.get(item.checklistItemId) === RESULT.SUCCESS
    || existingDeviceResults.value.get(item.testType) === RESULT.SUCCESS
})
const currentCompletionSource = computed(() => {
  const item = currentItem.value
  if (!item) return null
  if (existingDeviceResults.value.get(item.testType) === RESULT.SUCCESS) return 'WEB'
  if (item.status === 'COMPLETED'
    || existingResults.value.get(item.checklistItemId) === RESULT.SUCCESS) return 'AUTO'
  return null
})
const isNumpadCheck = computed(() => currentItem.value?.checkKind === CHECK_KIND.NUMPAD)
const canMoveNext = computed(() => currentAlreadyCompleted.value
  || itemResults.value.has(currentItem.value?.testType))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [checklist, progress] = await Promise.all([
      getProductChecklist(productId),
      getProductDraftProgress(productId),
    ])
    items.value = toUniversalCheckItems(checklist)
    existingResults.value = new Map(
      Object.entries(progress.results || {}).map(([itemId, result]) => [Number(itemId), result]),
    )
    existingDeviceResults.value = new Map(Object.entries(progress.deviceResults || {}))
    draftStep.value = progress.step
  } catch {
    loadError.value = '점검 대상 체크리스트를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
load()

function markResult(testType, passed) {
  itemResults.value.set(testType, passed ? RESULT.SUCCESS : RESULT.FAILED)
}

function startPointerTimeout() {
  clearTimeout(pointerTimeoutId)
  pointerTimeoutId = setTimeout(() => {
    if (pointer.value && pointer.value.status.value === 'listening') finishPointer()
  }, POINTER_TIMEOUT_MS)
}

async function runCurrent() {
  const item = currentItem.value
  if (!item) return
  keyboardMissing.value = []
  keyboardMissingUnreliable.value = []
  displayStarted.value = false
  chargingStarted.value = false
  clearTimeout(pointerTimeoutId)

  if (item.checkKind === CHECK_KIND.CAMERA) {
    const passed = await camera.start(videoEl.value)
    markResult(item.testType, passed)
  } else if (item.checkKind === CHECK_KIND.MIC) {
    const passed = await mic.start()
    markResult(item.testType, passed)
  } else if (item.checkKind === CHECK_KIND.SPEAKER) {
    speaker.playTone()
  } else if (item.checkKind === CHECK_KIND.DISPLAY) {
    displayStarted.value = true
  } else if (item.checkKind === CHECK_KIND.CHARGING) {
    chargingStarted.value = true
  } else if (item.checkKind === CHECK_KIND.KEYBOARD) {
    keyboard = useKeyboardCheck({ includeNumpad: false })
    keyboard.start()
  } else if (item.checkKind === CHECK_KIND.NUMPAD) {
    keyboard = useKeyboardCheck({ includeNumpad: true })
    keyboard.start()
  } else if (item.checkKind === CHECK_KIND.POINTER) {
    pointer.value = usePointerInteractionCheck({ pointerTypes: ['mouse', 'touch', 'pen'] })
    pointer.value.start(pointerAreaEl.value)
    startPointerTimeout()
  } else if (item.checkKind === CHECK_KIND.TOUCHSCREEN) {
    pointer.value = usePointerInteractionCheck({ pointerTypes: ['touch'] })
    pointer.value.start(pointerAreaEl.value)
    startPointerTimeout()
  } else if (item.checkKind === CHECK_KIND.STYLUS) {
    pointer.value = usePointerInteractionCheck({ pointerTypes: ['pen'], requirePressure: true })
    pointer.value.start(pointerAreaEl.value)
    startPointerTimeout()
  }
}

function confirmManualCheck(passed) {
  markResult(currentItem.value.testType, passed)
}

function forceRecheckCurrent() {
  forceRecheckIds.add(currentItem.value.testType)
}

function confirmSpeakerHeard(heard) {
  speaker.confirmHeard(heard)
  markResult(currentItem.value.testType, heard)
}

function finishKeyboard() {
  const result = keyboard.finish()
  keyboardMissing.value = result.missingCodes
  keyboardMissingUnreliable.value = result.missingUnreliableCodes
  markResult(currentItem.value.testType, result.missingCodes.length === 0)
}

function finishPointer() {
  clearTimeout(pointerTimeoutId)
  const passed = pointer.value.finish()
  markResult(currentItem.value.testType, passed)
}

function retryCurrent() {
  runCurrent()
}

function next() {
  camera.stop()
  mic.stop()
  keyboard?.stop()
  keyboard = null
  clearTimeout(pointerTimeoutId)
  pointer.value?.stop()
  pointer.value = null
  stepIndex.value += 1
  if (stepIndex.value >= items.value.length) finished.value = true
}

function previous() {
  camera.stop()
  mic.stop()
  keyboard?.stop()
  keyboard = null
  pointer.value?.stop()
  pointer.value = null
  finished.value = false
  stepIndex.value = Math.max(0, stepIndex.value - 1)
}

function skipCurrent() {
  if (currentItem.value) itemResults.value.delete(currentItem.value.testType)
  next()
}

async function save() {
  saving.value = true
  saveError.value = ''
  try {
    // 이번 세션에서 다시 점검한 항목은 이전 결과를 덮어쓴다 — 예전에 성공했던 항목이라도
    // 재점검에서 실패하면 그 실패가 최종값이 되도록, existingResults 위에 itemResults를 얹는다.
    const merged = new Map(existingDeviceResults.value)
    itemResults.value.forEach((result, testType) => merged.set(testType, result))
    const deviceResults = [...merged.entries()].map(([testType, result]) => ({
      testType,
      result,
    }))
    const results = items.value
      .filter((item) => item.checklistItemId && merged.has(item.testType))
      .map((item) => ({
        checklistItemId: item.checklistItemId,
        result: merged.get(item.testType),
      }))
    await updateProductDraftProgress(productId, {
      step: draftStep.value,
      results,
      deviceResults,
    })
    router.push(backToRegister)
  } catch {
    saveError.value = '점검 결과를 저장하지 못했습니다. 다시 시도해 주세요.'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  camera.stop()
  mic.stop()
  keyboard?.stop()
  clearTimeout(pointerTimeoutId)
  pointer.value?.stop()
})
</script>

<template>
  <DefaultLayout>
    <!--
      점검 항목은 좁게 읽는 편이 낫지만, 바깥 틀은 다른 화면과 같은 값을 씁니다.
      그래야 제목이 시작하는 자리가 어디서나 같습니다. 좁히는 건 안쪽에서 합니다.
    -->
    <div class="page-shell">
      <PageHeader
        eyebrow="DEVICE CHECK"
        title="장치 실동작 점검"
        description="키보드와 포인터 입력이 실제로 감지되는지 이 화면에서 바로 확인합니다."
      />

      <!-- 점검 항목은 한 줄이 길면 읽기 어렵습니다. 안쪽만 좁게 둡니다. -->
      <div class="max-w-2xl">
        <BaseCard
          v-if="loading"
          class="mt-6 p-8 text-center text-text-sub"
        >
          불러오는 중...
        </BaseCard>
        <BaseCard
          v-else-if="loadError"
          class="mt-6 p-8 text-center text-red-600"
        >
          {{ loadError }}
        </BaseCard>

        <BaseCard
          v-else-if="finished"
          class="mt-6 p-8"
        >
          <p class="text-text-main">
            점검이 끝났습니다. 결과를 저장하고 돌아갈까요?
          </p>
          <p
            v-if="saveError"
            class="mt-2 text-sm text-red-600"
          >
            {{ saveError }}
          </p>
          <div class="mt-6 flex gap-3">
            <BaseButton
              variant="outline"
              @click="previous"
            >
              이전
            </BaseButton>
            <BaseButton
              :disabled="saving"
              @click="save"
            >
              저장하고 돌아가기
            </BaseButton>
            <BaseButton
              variant="outline"
              :to="backToRegister"
            >
              건너뛰기
            </BaseButton>
          </div>
        </BaseCard>

        <BaseCard
          v-else
          class="mt-6 p-8"
        >
          <p class="text-sm text-text-sub">
            {{ stepIndex + 1 }} / {{ items.length }}
          </p>
          <h2 class="mt-1 text-lg font-semibold text-text-main">
            {{ currentLabel }}
          </h2>

          <div class="mt-4 flex flex-wrap gap-3 border-b border-border pb-4">
            <BaseButton
              variant="outline"
              :disabled="stepIndex === 0"
              @click="previous"
            >
              이전
            </BaseButton>
            <BaseButton
              variant="outline"
              @click="skipCurrent"
            >
              건너뛰기
            </BaseButton>
            <BaseButton
              :disabled="!canMoveNext"
              @click="next"
            >
              다음
            </BaseButton>
          </div>

          <div
            v-if="currentAlreadyCompleted"
            class="mt-4 rounded-md border border-primary/30 bg-accent p-4"
          >
            <p class="font-semibold text-primary-dark">
              {{ currentCompletionSource === 'WEB' ? '웹 점검 완료' : '자동 점검 완료' }}
            </p>
            <p class="mt-1 text-sm text-text-sub">
              {{ currentCompletionSource === 'WEB'
                ? '이전에 웹에서 정상 점검한 결과가 저장되어 있습니다.'
                : 'Limit 진단 프로그램에서 정상 결과를 받아 웹 점검을 생략할 수 있습니다.' }}
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton @click="next">
                다음
              </BaseButton>
              <BaseButton
                variant="outline"
                @click="forceRecheckCurrent"
              >
                다시 점검
              </BaseButton>
            </div>
          </div>

          <!-- 카메라 -->
          <div
            v-else-if="currentItem.checkKind === 'CAMERA'"
            class="mt-4"
          >
            <video
              ref="videoEl"
              class="w-full rounded-md bg-black"
              muted
              playsinline
            />
            <p class="mt-3 text-sm text-text-sub">
              {{ camera.detail.value }}
            </p>
            <p class="mt-2 text-sm leading-6 text-text-sub">
              카메라가 켜지면 약 3초 동안 손을 흔들거나 기기를 조금 움직여 주세요.
              화면 변화를 감지하면 자동으로 점검이 완료됩니다.
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton
                v-if="camera.status.value === 'idle'"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <BaseButton
                v-else-if="camera.status.value === 'failed'"
                variant="outline"
                @click="retryCurrent"
              >
                다시 시도
              </BaseButton>
              <BaseButton
                v-if="camera.status.value === 'passed' || camera.status.value === 'failed'"
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>

          <!-- 마이크 -->
          <div
            v-else-if="currentItem.checkKind === 'MIC'"
            class="mt-4"
          >
            <p class="text-sm text-text-sub">
              마이크에 대고 말해 주세요. (입력 레벨: {{ mic.level.value }})
            </p>
            <p
              v-if="mic.detail.value"
              class="mt-2 text-sm text-red-600"
            >
              {{ mic.detail.value }}
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton
                v-if="mic.status.value === 'idle'"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <BaseButton
                v-else-if="mic.status.value === 'failed'"
                variant="outline"
                @click="retryCurrent"
              >
                다시 시도
              </BaseButton>
              <BaseButton
                v-if="mic.status.value === 'passed' || mic.status.value === 'failed'"
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>

          <!-- 스피커 -->
          <div
            v-else-if="currentItem.checkKind === 'SPEAKER'"
            class="mt-4"
          >
            <p class="text-sm text-text-sub">
              테스트음이 재생됩니다. 소리가 들렸는지 확인해 주세요.
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton
                v-if="speaker.status.value === 'idle'"
                @click="runCurrent"
              >
                테스트음 재생
              </BaseButton>
              <template v-else-if="speaker.status.value === 'awaitingConfirmation'">
                <BaseButton @click="confirmSpeakerHeard(true)">
                  들렸어요
                </BaseButton>
                <BaseButton
                  variant="outline"
                  @click="confirmSpeakerHeard(false)"
                >
                  안 들렸어요
                </BaseButton>
              </template>
              <BaseButton
                v-if="speaker.status.value === 'failed'"
                variant="outline"
                @click="retryCurrent"
              >
                다시 시도
              </BaseButton>
              <BaseButton
                v-if="speaker.status.value === 'passed' || speaker.status.value === 'failed'"
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>

          <!-- 디스플레이 -->
          <div
            v-else-if="currentItem.checkKind === 'DISPLAY'"
            class="mt-4"
          >
            <p class="text-sm text-text-sub">
              점검을 시작한 뒤 흰색·검은색·빨강·초록·파랑 영역에서 멍, 줄, 깜빡임과 불량 화소를 확인하세요.
            </p>
            <div
              v-if="displayStarted"
              class="mt-4 grid h-48 grid-cols-5 overflow-hidden rounded-md border border-border"
            >
              <span class="bg-white" /><span class="bg-black" /><span class="bg-red-600" />
              <span class="bg-green-600" /><span class="bg-blue-600" />
            </div>
            <div class="mt-4 flex flex-wrap gap-3">
              <BaseButton
                v-if="!displayStarted"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <template v-else-if="!itemResults.has(currentItem.testType)">
                <BaseButton @click="confirmManualCheck(true)">
                  정상이에요
                </BaseButton>
                <BaseButton
                  variant="outline"
                  @click="confirmManualCheck(false)"
                >
                  이상이 있어요
                </BaseButton>
              </template>
              <BaseButton
                v-else
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>

          <!-- 충전 -->
          <div
            v-else-if="currentItem.checkKind === 'CHARGING'"
            class="mt-4"
          >
            <p class="text-sm text-text-sub">
              충전기를 연결하거나 분리하고 운영체제의 배터리 아이콘과 충전 표시가 바뀌는지 확인하세요.
            </p>
            <div class="mt-4 flex flex-wrap gap-3">
              <BaseButton
                v-if="!chargingStarted"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <template v-else-if="!itemResults.has(currentItem.testType)">
                <BaseButton @click="confirmManualCheck(true)">
                  정상이에요
                </BaseButton>
                <BaseButton
                  variant="outline"
                  @click="confirmManualCheck(false)"
                >
                  인식되지 않아요
                </BaseButton>
              </template>
              <BaseButton
                v-else
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>

          <!-- 키보드 / 숫자패드 -->
          <div
            v-else-if="currentItem.checkKind === 'KEYBOARD' || currentItem.checkKind === 'NUMPAD'"
            class="mt-4"
          >
            <p
              v-if="currentItem.checkKind === 'KEYBOARD'"
              class="mb-2 text-sm text-text-sub"
            >
              {{ KEYBOARD_UNRELIABLE_CODES.map(unreliableKeyLabel).join(', ') }}는 브라우저·OS가 가로채거나
              좌우를 구분할 정보를 주지 않아 정상 키여도 감지가 보장되지 않습니다.
            </p>
            <p
              v-if="!keyboard"
              class="text-sm text-text-sub"
            >
              시작을 누르고 표시되는 키를 차례로 눌러 주세요.
            </p>
            <div
              v-else
              class="space-y-1.5"
            >
              <p class="text-xs text-text-sub">
                이 노트북에 물리적으로 없는 키는 회색 칸을 눌러 "없음"으로 표시할 수 있습니다.
              </p>
              <div
                v-for="(row, rowIndex) in keyboard.rows"
                :key="rowIndex"
                :class="isNumpadCheck ? 'grid max-w-[220px] grid-cols-4 gap-1' : 'flex gap-1'"
              >
                <span
                  v-for="code in row"
                  :key="code"
                  class="flex h-9 min-w-0 cursor-pointer items-center justify-center overflow-hidden whitespace-nowrap rounded border px-1 text-[11px] font-medium sm:h-10 sm:text-xs"
                  :class="keyboard.pressed.has(code)
                    ? 'border-primary bg-primary-gradient text-white'
                    : keyboard.excludedCodes.has(code)
                      ? 'border-dashed border-border text-text-sub/50 line-through'
                      : 'border-border text-text-sub'"
                  :style="isNumpadCheck
                    ? { gridColumn: code === 'Numpad0' ? 'span 2' : undefined }
                    : { flex: `${keyWidth(code)} 0 0%` }"
                  @click="keyboard.toggleExcluded(code)"
                >
                  {{ keyLabel(code) }}
                </span>
              </div>
              <p class="text-sm text-text-sub">
                {{ keyboard.pressedCount.value }} / {{ keyboard.total.value }}
              </p>
            </div>
            <p
              v-if="keyboardMissing.length"
              class="mt-2 text-sm text-amber-600"
            >
              응답 없음: {{ keyboardMissing.join(', ') }}
            </p>
            <p
              v-if="keyboardMissingUnreliable.length"
              class="mt-2 text-sm text-text-sub"
            >
              확인 불가: {{ keyboardMissingUnreliable.map(unreliableKeyLabel).join(', ') }}
            </p>
            <p
              v-if="keyboard && keyboard.excludedCodes.size"
              class="mt-2 text-sm text-text-sub"
            >
              없는 키로 표시함: {{ [...keyboard.excludedCodes].map(keyLabel).join(', ') }}
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton
                v-if="!keyboard"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <BaseButton
                v-else-if="keyboard.status.value !== 'passed' && keyboard.status.value !== 'passedWithMissing'"
                @click="finishKeyboard"
              >
                완료
              </BaseButton>
              <template v-else>
                <BaseButton
                  v-if="keyboard.status.value === 'passedWithMissing'"
                  variant="outline"
                  @click="retryCurrent"
                >
                  다시 시도
                </BaseButton>
                <BaseButton @click="next">
                  다음
                </BaseButton>
              </template>
            </div>
          </div>

          <!-- 마우스/트랙패드, 터치스크린, 스타일러스 -->
          <div
            v-else
            class="mt-4"
          >
            <p class="mb-2 text-sm text-text-sub">
              점검 시작 후 10초 안에 클릭·드래그·스크롤(또는 터치·펜)을 확인해 주세요. 시간 안에 확인되지
              않으면 자동으로 실패 처리됩니다.
            </p>
            <div
              ref="pointerAreaEl"
              class="flex h-40 items-center justify-center rounded-md border border-dashed border-border text-sm text-text-sub"
            >
              여기에서 클릭·드래그·스크롤(또는 터치·펜)을 해보세요.
            </div>
            <p
              v-if="pointer"
              class="mt-3 text-sm text-text-sub"
            >
              누르기 {{ pointer.seen.down ? '감지' : '대기' }} ·
              이동/스크롤 {{ pointer.seen.move || pointer.seen.wheel ? '감지' : '대기' }}
            </p>
            <div class="mt-4 flex gap-3">
              <BaseButton
                v-if="!pointer"
                @click="runCurrent"
              >
                점검 시작
              </BaseButton>
              <BaseButton
                v-else-if="pointer.status.value === 'listening'"
                @click="finishPointer"
              >
                완료
              </BaseButton>
              <BaseButton
                v-else-if="pointer.status.value === 'failed'"
                variant="outline"
                @click="retryCurrent"
              >
                다시 시도
              </BaseButton>
              <BaseButton
                v-if="pointer && pointer.status.value === 'passed'"
                @click="next"
              >
                다음
              </BaseButton>
            </div>
          </div>
        </BaseCard>
      </div>
    </div>
  </DefaultLayout>
</template>
