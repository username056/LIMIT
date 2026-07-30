<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { getProductChecklist, getProductDraftProgress, updateProductDraftProgress } from '../api/products'
import { CHECK_KIND, toCheckableItems } from '../features/deviceCheck/checkableItemCodes'
import { useCameraCheck } from '../features/deviceCheck/useCameraCheck'
import { useMicCheck } from '../features/deviceCheck/useMicCheck'
import { useSpeakerCheck } from '../features/deviceCheck/useSpeakerCheck'
import { useKeyboardCheck } from '../features/deviceCheck/useKeyboardCheck'
import { usePointerInteractionCheck } from '../features/deviceCheck/usePointerInteractionCheck'

const CHECK_KIND_LABEL = {
  [CHECK_KIND.CAMERA]: '카메라',
  [CHECK_KIND.MIC]: '마이크',
  [CHECK_KIND.SPEAKER]: '스피커',
  [CHECK_KIND.KEYBOARD]: '키보드',
  [CHECK_KIND.NUMPAD]: '숫자 키패드',
  [CHECK_KIND.POINTER]: '마우스/터치패드',
  [CHECK_KIND.TOUCHSCREEN]: '터치스크린',
  [CHECK_KIND.STYLUS]: '스타일러스',
}

const route = useRoute()
const router = useRouter()
const productId = route.params.productId

const loading = ref(true)
const loadError = ref('')
const saveError = ref('')
const saving = ref(false)
const items = ref([])
const stepIndex = ref(0)
const passedItemIds = ref(new Set())
const existingConfirmedIds = ref(new Set())
const draftStep = ref(1)
const finished = ref(false)
const videoEl = ref(null)
const pointerAreaEl = ref(null)
const keyboardMissing = ref([])

const camera = useCameraCheck()
const mic = useMicCheck()
const speaker = useSpeakerCheck()
let keyboard = null
let pointer = null

const currentItem = computed(() => items.value[stepIndex.value] || null)
const currentLabel = computed(() =>
  currentItem.value ? CHECK_KIND_LABEL[currentItem.value.checkKind] : '',
)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [checklist, progress] = await Promise.all([
      getProductChecklist(productId),
      getProductDraftProgress(productId),
    ])
    items.value = toCheckableItems(checklist)
    existingConfirmedIds.value = new Set(progress.confirmedChecklistItemIds || [])
    draftStep.value = progress.step
    if (items.value.length === 0) finished.value = true
  } catch {
    loadError.value = '점검 대상 체크리스트를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
load()

function markPassed(itemId, passed) {
  if (passed) passedItemIds.value.add(itemId)
  else passedItemIds.value.delete(itemId)
}

async function runCurrent() {
  const item = currentItem.value
  if (!item) return
  keyboardMissing.value = []

  if (item.checkKind === CHECK_KIND.CAMERA) {
    const passed = await camera.start(videoEl.value)
    markPassed(item.checklistItemId, passed)
  } else if (item.checkKind === CHECK_KIND.MIC) {
    const passed = await mic.start()
    markPassed(item.checklistItemId, passed)
  } else if (item.checkKind === CHECK_KIND.SPEAKER) {
    speaker.playTone()
  } else if (item.checkKind === CHECK_KIND.KEYBOARD) {
    keyboard = useKeyboardCheck({ includeNumpad: false })
    keyboard.start()
  } else if (item.checkKind === CHECK_KIND.NUMPAD) {
    keyboard = useKeyboardCheck({ includeNumpad: true })
    keyboard.start()
  } else if (item.checkKind === CHECK_KIND.POINTER) {
    pointer = usePointerInteractionCheck({ pointerTypes: ['mouse'] })
    pointer.start(pointerAreaEl.value)
  } else if (item.checkKind === CHECK_KIND.TOUCHSCREEN) {
    pointer = usePointerInteractionCheck({ pointerTypes: ['touch'] })
    pointer.start(pointerAreaEl.value)
  } else if (item.checkKind === CHECK_KIND.STYLUS) {
    pointer = usePointerInteractionCheck({ pointerTypes: ['pen'], requirePressure: true })
    pointer.start(pointerAreaEl.value)
  }
}

function confirmSpeakerHeard(heard) {
  speaker.confirmHeard(heard)
  markPassed(currentItem.value.checklistItemId, heard)
}

function finishKeyboard() {
  const result = keyboard.finish()
  keyboardMissing.value = result.missingCodes
  // 죽은 키가 있어도 점검을 시도했다는 사실 자체는 완료로 인정한다 — 상세(어떤 키가 안 눌렸는지)는
  // 서버에 저장할 곳이 없어 이 화면에서만 보여준다.
  markPassed(currentItem.value.checklistItemId, true)
}

function finishPointer() {
  const passed = pointer.finish()
  markPassed(currentItem.value.checklistItemId, passed)
}

function retryCurrent() {
  runCurrent()
}

function next() {
  camera.stop()
  mic.stop()
  keyboard?.stop()
  keyboard = null
  pointer = null
  stepIndex.value += 1
  if (stepIndex.value >= items.value.length) finished.value = true
}

async function save() {
  saving.value = true
  saveError.value = ''
  try {
    const merged = new Set(existingConfirmedIds.value)
    items.value.forEach((item) => {
      if (passedItemIds.value.has(item.checklistItemId)) merged.add(item.checklistItemId)
    })
    await updateProductDraftProgress(productId, {
      step: draftStep.value,
      confirmedChecklistItemIds: [...merged],
    })
    router.push({ name: 'seller-product-edit', params: { productId } })
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
})
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-2xl px-6 py-12">
      <h1 class="text-xl font-bold text-text-main">
        장치 실동작 점검
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        카메라·마이크·키보드 등이 실제로 동작하는지 이 화면에서 바로 확인합니다.
      </p>

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
            :disabled="saving"
            @click="save"
          >
            저장하고 돌아가기
          </BaseButton>
          <BaseButton
            variant="outline"
            :to="{ name: 'seller-product-edit', params: { productId } }"
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

        <!-- 카메라 -->
        <div
          v-if="currentItem.checkKind === 'CAMERA'"
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
              v-if="speaker.status.value === 'passed' || speaker.status.value === 'failed'"
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
            v-if="!keyboard"
            class="text-sm text-text-sub"
          >
            시작을 누르고 표시되는 키를 차례로 눌러 주세요.
          </p>
          <div
            v-else
            class="space-y-2"
          >
            <div
              v-for="(row, rowIndex) in keyboard.rows"
              :key="rowIndex"
              class="flex flex-wrap gap-1"
            >
              <span
                v-for="code in row"
                :key="code"
                class="rounded border px-2 py-1 text-xs"
                :class="keyboard.pressed.has(code) ? 'border-primary bg-primary-gradient text-white' : 'border-border text-text-sub'"
              >
                {{ code }}
              </span>
            </div>
            <p class="text-sm text-text-sub">
              {{ keyboard.pressedCount.value }} / {{ keyboard.total }}
            </p>
          </div>
          <p
            v-if="keyboardMissing.length"
            class="mt-2 text-sm text-amber-600"
          >
            응답 없음: {{ keyboardMissing.join(', ') }}
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
            <BaseButton
              v-else
              @click="next"
            >
              다음
            </BaseButton>
          </div>
        </div>

        <!-- 마우스/트랙패드, 터치스크린, 스타일러스 -->
        <div
          v-else
          class="mt-4"
        >
          <div
            ref="pointerAreaEl"
            class="flex h-40 items-center justify-center rounded-md border border-dashed border-border text-sm text-text-sub"
          >
            여기에서 클릭·드래그·스크롤(또는 터치·펜)을 해보세요.
          </div>
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
  </DefaultLayout>
</template>
