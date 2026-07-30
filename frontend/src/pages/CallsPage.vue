<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTabs from '../components/BaseTabs.vue'
import { getChatRooms } from '../api/chat'
import {
  cancelRtcCall,
  getMyRtcCalls,
  getRtcSession,
  respondRtcCall,
  updateRtcCall,
} from '../api/rtc'
import { getMyReinspectionRequests } from '../api/products'

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
const now = ref(Date.now())
let remainingTimer = null

async function load() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [loadedCalls, chatRooms] = await Promise.all([
      getMyRtcCalls(),
      getChatRooms({ size: 100 }).catch(() => ({ content: [] })),
    ])
    const rooms = chatRooms?.content || []
    calls.value = await Promise.all(loadedCalls.map(async (call) => {
      const room = rooms.find(({ roomId }) => Number(roomId) === Number(call.chatRoomId))
      let sessionExpiresAt = call.sessionExpiresAt
      if (!sessionExpiresAt && call.rtcSessionId) {
        try {
          const session = await getRtcSession(call.rtcSessionId)
          sessionExpiresAt = session.expiresAt
        } catch {
          // 통화 목록은 유지하고, 세션 만료 정보만 표시하지 않는다.
        }
      }
      return {
        ...call,
        counterpartName: call.counterpartName || room?.counterpartNickname || null,
        sessionExpiresAt,
        productId: room?.listingId || null,
        productName: room?.listingTitle || (room?.listingId ? `상품 #${room.listingId}` : '상품 정보 없음'),
        productThumbnailUrl: room?.listingThumbnailUrl || null,
      }
    }))
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

const recaptureRequests = ref([])
const isLoadingRecaptures = ref(true)
const recaptureLoadError = ref('')

async function loadRecaptures() {
  isLoadingRecaptures.value = true
  recaptureLoadError.value = ''
  try {
    const requests = await getMyReinspectionRequests()
    recaptureRequests.value = requests.map((request) => ({
      id: request.requestKey,
      requestKey: request.requestKey,
      productId: request.listingId,
      productName: `상품 #${request.listingId}`,
      categoryLabel: '재검수',
      registrationNumber: `#${request.listingId}`,
      price: null,
      specSummary: `${request.items.length}개 항목`,
      checklistItemName: request.items.map((item) => item.itemName).join(', '),
      requestedAt: request.requestedAt?.slice(0, 10) || '',
      reason: request.reason,
      status: request.status === 'REQUESTED' ? 'PENDING' : request.status,
    }))
  } catch (error) {
    recaptureLoadError.value = error.message || '재검수 요청을 불러오지 못했습니다.'
    recaptureRequests.value = []
  } finally {
    isLoadingRecaptures.value = false
  }
}

onMounted(() => {
  remainingTimer = setInterval(() => { now.value = Date.now() }, 1000)
  return Promise.all([load(), loadRecaptures()])
})

onBeforeUnmount(() => clearInterval(remainingTimer))

function isSessionExpired(call) {
  if (!call.sessionExpiresAt) return false
  return new Date(call.sessionExpiresAt).getTime() <= now.value
}

function isCallCompleted(call) {
  return call.status === 'COMPLETED' || isSessionExpired(call)
}

const callFilter = ref('전체 목록')
const callCounts = computed(() => ({
  전체: calls.value.length,
  대기: calls.value.filter((call) => call.status === 'PROPOSED').length,
  거절: calls.value.filter((call) => call.status === 'REJECTED').length,
  완료: calls.value.filter(isCallCompleted).length,
}))
const callTabs = computed(() => [
  { key: '전체 목록', label: `전체 목록 (${callCounts.value.전체})` },
  { key: '대기', label: `대기 (${callCounts.value.대기})` },
  { key: '거절', label: `거절 (${callCounts.value.거절})` },
  { key: '완료', label: `완료 (${callCounts.value.완료})` },
])
const filteredCalls = computed(() => {
  if (callFilter.value === '대기') return calls.value.filter((call) => call.status === 'PROPOSED')
  if (callFilter.value === '거절') return calls.value.filter((call) => call.status === 'REJECTED')
  if (callFilter.value === '완료') return calls.value.filter(isCallCompleted)
  return calls.value
})
const callGroups = computed(() => {
  const groups = new Map()
  filteredCalls.value.forEach((call) => {
    const groupKey = call.productId ? `listing-${call.productId}` : `room-${call.chatRoomId}`
    if (!groups.has(groupKey)) groups.set(groupKey, [])
    groups.get(groupKey).push(call)
  })
  return Array.from(groups.values())
})

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

function formatScheduledAt(value) {
  if (!value) return '일정 미정'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function remainingTime(expiresAt) {
  if (!expiresAt) return null
  const remainingSeconds = Math.max(
    0,
    Math.floor((new Date(expiresAt).getTime() - now.value) / 1000),
  )
  const hours = Math.floor(remainingSeconds / 3600)
  const minutes = Math.floor((remainingSeconds % 3600) / 60)
  const seconds = remainingSeconds % 60
  if (hours > 0) return `${hours}시간 ${minutes}분 ${seconds}초`
  return `${minutes}분 ${seconds}초`
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
        <template v-else>
          <p class="mb-5 text-sm text-text-sub">
            상품별 실시간 확인 요청과 진행 상태를 확인하세요.
          </p>
          <div class="mb-5 flex flex-wrap gap-2">
            <button
              v-for="tab in callTabs"
              :key="tab.key"
              type="button"
              class="rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors"
              :class="callFilter === tab.key
                ? 'border-primary bg-accent text-primary-dark'
                : 'border-border text-text-sub hover:border-primary'"
              @click="callFilter = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <div
            v-for="group in callGroups"
            :key="group[0].productId || `room-${group[0].chatRoomId}`"
            class="mb-6"
          >
            <BaseCard class="mb-3 flex items-center gap-4 p-5">
              <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md bg-bg text-text-sub">
                <img
                  v-if="group[0].productThumbnailUrl"
                  :src="group[0].productThumbnailUrl"
                  :alt="group[0].productName"
                  class="h-full w-full object-cover"
                >
                <span
                  v-else
                  class="text-2xl"
                >▣</span>
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <BaseBadge variant="gray">
                    실시간 확인
                  </BaseBadge>
                  <span class="text-xs text-text-sub">
                    상품 번호 #{{ group[0].productId || '-' }}
                  </span>
                </div>
                <p class="mt-1 truncate font-bold text-text-main">
                  {{ group[0].productName }}
                </p>
                <p class="mt-1 text-sm text-text-sub">
                  요청 {{ group.length }}건
                </p>
              </div>
              <div class="shrink-0 text-right">
                <p class="mb-1 text-xs text-text-sub">
                  확인 현황
                </p>
                <BaseBadge :variant="group.every(isCallCompleted) ? 'success' : 'primary'">
                  {{ group.every(isCallCompleted) ? '확인 완료' : '확인 진행 중' }}
                </BaseBadge>
              </div>
            </BaseCard>

            <BaseCard
              v-for="call in group"
              :key="call.callId"
              class="mb-3 p-5"
              :class="['PROPOSED', 'ACCEPTED'].includes(call.status) && !isSessionExpired(call)
                ? 'border-l-2 border-l-primary'
                : ''"
            >
              <div class="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <p class="font-semibold text-text-main">
                    영상 확인 요청 #{{ call.callId }}
                  </p>
                  <p
                    v-if="call.memo"
                    class="mt-1 text-sm text-text-sub"
                  >
                    {{ call.memo }}
                  </p>
                  <p class="mt-2 text-xs text-text-sub">
                    상태: {{ call.status }} · {{ call.incoming ? '받은 요청' : '보낸 요청' }}
                  </p>
                  <dl class="mt-3 grid gap-1 text-sm text-text-sub">
                    <div class="flex gap-2">
                      <dt class="font-semibold text-text-main">
                        상대방
                      </dt>
                      <dd>{{ call.counterpartName || `회원 #${call.incoming ? call.proposerId : call.respondentId}` }}</dd>
                    </div>
                    <div
                      class="flex flex-wrap items-center justify-between gap-x-6 gap-y-1"
                    >
                      <div class="flex gap-2">
                        <dt class="font-semibold text-text-main">
                          검증 일정
                        </dt>
                        <dd>{{ formatScheduledAt(call.scheduledAt) }}</dd>
                      </div>
                      <div
                        v-if="call.sessionExpiresAt"
                        class="flex gap-2"
                        :class="isSessionExpired(call) ? 'font-semibold text-red-600' : ''"
                      >
                        <dt>{{ isSessionExpired(call) ? '세션 만료' : '세션 만료까지' }}</dt>
                        <dd v-if="!isSessionExpired(call)">
                          {{ remainingTime(call.sessionExpiresAt) }}
                        </dd>
                      </div>
                    </div>
                  </dl>
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
                    v-if="call.rtcSessionId && call.status === 'ACCEPTED' && !isSessionExpired(call)"
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
          </div>
          <BaseCard
            v-if="!callGroups.length"
            class="py-14 text-center text-text-sub"
          >
            선택한 상태의 영상 확인 요청이 없습니다.
          </BaseCard>
        </template>
      </template>

      <template v-else>
        <p
          v-if="recaptureLoadError"
          role="alert"
          class="mb-5 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {{ recaptureLoadError }}
        </p>
        <p
          v-else-if="isLoadingRecaptures"
          class="mb-5 text-sm text-text-sub"
        >
          재검수 요청을 불러오는 중입니다.
        </p>
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
                    :to="{
                      name: 'seller-product-edit',
                      params: { productId: item.productId },
                      query: { reinspectionRequestKey: item.requestKey },
                    }"
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
