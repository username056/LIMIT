<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTabs from '../components/BaseTabs.vue'
import { cancelRtcCall, getMyRtcCalls, respondRtcCall, updateRtcCall } from '../api/rtc'

const router = useRouter()
const activeTab = ref('실시간 확인')

const calls = ref([])
const isLoading = ref(true)
const pendingCallId = ref(null)
const editingCallId = ref(null)
const cancelingCallId = ref(null)
const editScheduledAt = ref('')
const editMemo = ref('')
const cancelReason = ref('')
const errorMessage = ref('')

async function load() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    calls.value = await getMyRtcCalls()
  } catch (error) {
    errorMessage.value = error.message || '영상 확인 요청을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function respond(call, accepted) {
  pendingCallId.value = call.callId
  try {
    const updated = await respondRtcCall(call.callId, accepted, accepted ? null : '요청 거절')
    await load()
    if (accepted && updated.rtcSessionId) {
      await router.push({ name: 'rtc-call', params: { callId: call.callId } })
    }
  } catch (error) {
    errorMessage.value = error.message || '요청을 처리하지 못했습니다.'
  } finally {
    pendingCallId.value = null
  }
}

function startEdit(call) {
  cancelingCallId.value = null
  editingCallId.value = call.callId
  editScheduledAt.value = call.scheduledAt?.slice(0, 16) || ''
  editMemo.value = call.memo || ''
}

async function saveEdit(call) {
  if (!editScheduledAt.value) return
  pendingCallId.value = call.callId
  errorMessage.value = ''
  try {
    await updateRtcCall(call.callId, {
      scheduledAt: `${editScheduledAt.value}:00`,
      memo: editMemo.value.trim() || null,
    })
    editingCallId.value = null
    await load()
  } catch (error) {
    errorMessage.value = error.message || '통화 약속을 변경하지 못했습니다.'
  } finally {
    pendingCallId.value = null
  }
}

function startCancel(call) {
  editingCallId.value = null
  cancelingCallId.value = call.callId
  cancelReason.value = ''
}

async function confirmCancel(call) {
  pendingCallId.value = call.callId
  errorMessage.value = ''
  try {
    await cancelRtcCall(call.callId, cancelReason.value.trim())
    cancelingCallId.value = null
    await load()
  } catch (error) {
    errorMessage.value = error.message || '통화 약속을 취소하지 못했습니다.'
  } finally {
    pendingCallId.value = null
  }
}

onMounted(load)

// 재촬영 요청 목록 조회 API가 아직 없어(판매자가 자기 상품 전체에 걸린 요청을 한 번에 보는 API 없음)
// 예시 데이터로 화면만 먼저 구성합니다. API가 준비되면 이 배열 대신 응답 데이터를 사용하세요.
const recaptureRequests = ref([
  {
    id: 1,
    productId: 1042,
    productName: 'Galaxy Book4 Pro (Space Black)',
    categoryLabel: '노트북',
    registrationNumber: '#10842',
    price: 1680000,
    specSummary: '배터리 효율 94% · RAM 18GB · SSD 512GB',
    checklistItemName: '화면 상태 (디스플레이)',
    requestedAt: '2024-03-11',
    reason: '화면 좌측 하단 백라이트 밝기 차이가 있는 것 같습니다. 불을 끄고 완전히 어두운 어둠 속에서 흰색 단일 배경을 띄우고 다시 한번 정밀 촬영해 주세요.',
    status: 'PENDING',
  },
  {
    id: 2,
    productId: 1042,
    productName: 'Galaxy Book4 Pro (Space Black)',
    categoryLabel: '노트북',
    registrationNumber: '#10842',
    price: 1680000,
    specSummary: '배터리 효율 94% · RAM 18GB · SSD 512GB',
    checklistItemName: '외관 후면',
    requestedAt: '2024-03-09',
    reason: '후면 바닥 고무 패드의 마모 상태 및 좌측 하단 나사 결합 부품이 분해 이력에 의해 뭉개져 있는지 확인을 요청하셨습니다.',
    status: 'COMPLETED',
  },
])

const recaptureFilter = ref('전체 목록')
const recaptureCounts = computed(() => ({
  전체: recaptureRequests.value.length,
  미처리: recaptureRequests.value.filter((item) => item.status === 'PENDING').length,
  완료: recaptureRequests.value.filter((item) => item.status === 'COMPLETED').length,
}))
const recaptureTabs = computed(() => [
  { key: '전체 목록', label: `전체 목록 (${recaptureCounts.value.전체})` },
  { key: '미처리 요청', label: `미처리 요청 (${recaptureCounts.value.미처리})` },
  { key: '재촬영 완료', label: `재촬영 완료 (${recaptureCounts.value.완료})` },
])
const filteredRecaptureRequests = computed(() => {
  if (recaptureFilter.value === '미처리 요청') return recaptureRequests.value.filter((item) => item.status === 'PENDING')
  if (recaptureFilter.value === '재촬영 완료') return recaptureRequests.value.filter((item) => item.status === 'COMPLETED')
  return recaptureRequests.value
})
const recaptureGroups = computed(() => {
  const groups = new Map()
  filteredRecaptureRequests.value.forEach((item) => {
    if (!groups.has(item.productId)) groups.set(item.productId, [])
    groups.get(item.productId).push(item)
  })
  return Array.from(groups.values())
})

function formatPrice(price) {
  return Number(price || 0).toLocaleString('ko-KR')
}
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[900px] px-6 py-10">
      <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
        LIVE VERIFICATION
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        1:1 실시간 확인
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        구매 희망자가 요청한 실시간 영상 확인과 재촬영 요청을 한곳에서 확인하세요.
      </p>

      <BaseTabs
        v-model="activeTab"
        :tabs="['실시간 확인', '재촬영 요청']"
        class="mt-6 mb-6"
      />

      <template v-if="activeTab === '실시간 확인'">
        <p
          v-if="errorMessage"
          role="alert"
          class="mb-5 rounded-md bg-red-50 p-3 text-red-700"
        >
          {{ errorMessage }}
        </p>
        <p
          v-if="isLoading"
          class="py-12 text-center text-text-sub"
        >
          요청을 불러오는 중입니다.
        </p>
        <div
          v-else
          class="grid gap-4"
        >
          <BaseCard
            v-for="call in calls"
            :key="call.callId"
            class="p-5"
          >
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p class="font-semibold text-text-main">
                  영상 확인 요청 #{{ call.callId }}
                </p>
                <p class="mt-1 text-sm text-text-sub">
                  {{ call.memo || '등록된 상품 상태를 실시간으로 확인합니다.' }}
                </p>
                <p class="mt-2 text-xs text-text-sub">
                  상태: {{ call.status }} · {{ call.incoming ? '받은 요청' : '보낸 요청' }}
                </p>
              </div>
              <div class="flex gap-2">
                <template v-if="call.status === 'PROPOSED' && call.incoming">
                  <BaseButton @click="respond(call, true)">
                    수락
                  </BaseButton>
                  <BaseButton
                    variant="outline"
                    @click="respond(call, false)"
                  >
                    거절
                  </BaseButton>
                </template>
                <template v-if="call.status === 'PROPOSED' && !call.incoming">
                  <BaseButton
                    variant="outline"
                    @click="startEdit(call)"
                  >
                    약속 변경
                  </BaseButton>
                  <BaseButton
                    variant="ghost"
                    @click="startCancel(call)"
                  >
                    약속 취소
                  </BaseButton>
                </template>
                <BaseButton
                  v-if="call.rtcSessionId && ['ACCEPTED', 'COMPLETED'].includes(call.status)"
                  :disabled="call.status === 'COMPLETED'"
                  @click="router.push({ name: 'rtc-call', params: { callId: call.callId } })"
                >
                  통화 입장
                </BaseButton>
              </div>
            </div>

            <form
              v-if="editingCallId === call.callId"
              class="mt-5 grid gap-3 border-t border-border pt-5"
              @submit.prevent="saveEdit(call)"
            >
              <label class="grid gap-1 text-sm font-medium text-text-main">
                통화 시간
                <input
                  v-model="editScheduledAt"
                  type="datetime-local"
                  required
                  class="rounded-md border border-border px-3 py-2"
                >
              </label>
              <label class="grid gap-1 text-sm font-medium text-text-main">
                메모
                <textarea
                  v-model="editMemo"
                  maxlength="500"
                  rows="3"
                  class="rounded-md border border-border px-3 py-2"
                />
              </label>
              <div class="flex justify-end gap-2">
                <BaseButton
                  variant="ghost"
                  @click="editingCallId = null"
                >
                  닫기
                </BaseButton>
                <BaseButton
                  type="submit"
                  :disabled="pendingCallId === call.callId"
                >
                  변경 저장
                </BaseButton>
              </div>
            </form>

            <form
              v-if="cancelingCallId === call.callId"
              class="mt-5 grid gap-3 border-t border-border pt-5"
              @submit.prevent="confirmCancel(call)"
            >
              <label class="grid gap-1 text-sm font-medium text-text-main">
                취소 사유 (선택)
                <textarea
                  v-model="cancelReason"
                  maxlength="500"
                  rows="3"
                  class="rounded-md border border-border px-3 py-2"
                />
              </label>
              <div class="flex justify-end gap-2">
                <BaseButton
                  variant="ghost"
                  @click="cancelingCallId = null"
                >
                  닫기
                </BaseButton>
                <BaseButton
                  type="submit"
                  :disabled="pendingCallId === call.callId"
                >
                  취소 확인
                </BaseButton>
              </div>
            </form>
          </BaseCard>
          <BaseCard
            v-if="!calls.length"
            class="py-14 text-center text-text-sub"
          >
            영상 확인 요청이 없습니다.
          </BaseCard>
        </div>
      </template>

      <template v-else>
        <p class="mb-5 text-sm text-text-sub">
          구매 희망자가 실시간 검수 전 특정 부위에 대한 재확인을 요청한 내역입니다.
        </p>

        <div class="mb-5 flex flex-wrap gap-2">
          <button
            v-for="tab in recaptureTabs"
            :key="tab.key"
            type="button"
            class="rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors"
            :class="recaptureFilter === tab.key
              ? 'border-primary bg-accent text-primary-dark'
              : 'border-border text-text-sub hover:border-primary'"
            @click="recaptureFilter = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div
          v-for="group in recaptureGroups"
          :key="group[0].productId"
          class="mb-6"
        >
          <BaseCard class="mb-3 flex items-center gap-4 p-5">
            <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md bg-bg text-text-sub">
              <span class="text-3xl">▣</span>
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <BaseBadge variant="gray">
                  {{ group[0].categoryLabel }}
                </BaseBadge>
                <span class="text-xs text-text-sub">기기 등록번호 {{ group[0].registrationNumber }}</span>
              </div>
              <p class="mt-1 truncate font-bold text-text-main">
                {{ group[0].productName }}
              </p>
              <p class="mt-1 text-sm text-text-sub">
                ₩{{ formatPrice(group[0].price) }} · {{ group[0].specSummary }}
              </p>
            </div>
            <div class="shrink-0 text-right">
              <p class="mb-1 text-xs text-text-sub">
                검수 현황
              </p>
              <BaseBadge :variant="group.some((item) => item.status === 'PENDING') ? 'primary' : 'success'">
                {{ group.some((item) => item.status === 'PENDING') ? '재촬영 대기 중' : '재촬영 완료' }}
              </BaseBadge>
            </div>
          </BaseCard>

          <div class="space-y-3">
            <BaseCard
              v-for="item in group"
              :key="item.id"
              class="p-5"
              :class="item.status === 'PENDING' ? 'border-l-2 border-l-primary' : ''"
            >
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <BaseBadge variant="gray">
                      {{ item.checklistItemName }}
                    </BaseBadge>
                    <span class="text-xs text-text-sub">요청 접수일: {{ item.requestedAt }}</span>
                  </div>
                  <p class="mt-2 text-sm leading-6 text-text-main">
                    {{ item.reason }}
                  </p>
                </div>
                <div class="flex shrink-0 items-center gap-3">
                  <BaseBadge :variant="item.status === 'PENDING' ? 'primary' : 'success'">
                    {{ item.status === 'PENDING' ? '미처리' : '재촬영 완료' }}
                  </BaseBadge>
                  <BaseButton
                    v-if="item.status === 'PENDING'"
                    :to="{ name: 'seller-products' }"
                  >
                    재촬영 진행하기
                  </BaseButton>
                  <BaseButton
                    v-else
                    variant="outline"
                    :to="{ name: 'product-detail', params: { productId: item.productId } }"
                  >
                    촬영 완료본 보기
                  </BaseButton>
                </div>
              </div>
            </BaseCard>
          </div>
        </div>

        <BaseCard
          v-if="!recaptureGroups.length"
          class="py-14 text-center text-text-sub"
        >
          해당하는 재촬영 요청이 없습니다.
        </BaseCard>
      </template>
    </main>
  </DefaultLayout>
</template>
