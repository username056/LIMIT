<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseTabs from '../components/BaseTabs.vue'
import { useAuthSession } from '../auth/session'
import { getChatRooms } from '../api/chat'
import {
  cancelRtcCall,
  getMyRtcCalls,
  getRtcSession,
  respondRtcCall,
  updateRtcCall,
} from '../api/rtc'
import { getMyReinspectionRequests, getProduct, getProductImages } from '../api/products'

const router = useRouter()
const session = useAuthSession()
const myMemberId = computed(() => session.value?.member?.memberId ?? null)
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
    const missingThumbnailProductIds = [...new Set(
      rooms
        .filter((room) => room.listingId && !room.listingThumbnailUrl)
        .map((room) => Number(room.listingId)),
    )]
    const productThumbnailEntries = await Promise.all(
      missingThumbnailProductIds.map(async (productId) => {
        try {
          const images = await getProductImages(productId)
          const thumbnailUrl = images.find((image) => image.imageType === 'THUMBNAIL')?.imageUrl
            || images[0]?.imageUrl
            || null
          return [productId, thumbnailUrl]
        } catch {
          return [productId, null]
        }
      }),
    )
    const productThumbnailUrls = new Map(productThumbnailEntries)
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
        productThumbnailUrl: room?.listingThumbnailUrl
          || productThumbnailUrls.get(Number(room?.listingId))
          || null,
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
    const productIds = [...new Set(requests.map((request) => Number(request.listingId)))]
    const productSummaryEntries = await Promise.all(productIds.map(async (productId) => {
      const [product, images] = await Promise.all([
        getProduct(productId).catch(() => null),
        getProductImages(productId).catch(() => []),
      ])
      const thumbnailUrl = product?.thumbnailUrl
        || images.find((image) => image.imageType === 'THUMBNAIL')?.imageUrl
        || images[0]?.imageUrl
        || null
      return [productId, {
        name: product?.name || `상품 #${productId}`,
        price: product?.price ?? null,
        thumbnailUrl,
        sellerId: product?.sellerId ?? null,
      }]
    }))
    const productSummaries = new Map(productSummaryEntries)
    recaptureRequests.value = requests.map((request) => ({
      id: request.requestKey,
      requestKey: request.requestKey,
      productId: request.listingId,
      productName: productSummaries.get(Number(request.listingId))?.name
        || `상품 #${request.listingId}`,
      productThumbnailUrl: productSummaries.get(Number(request.listingId))?.thumbnailUrl || null,
      isSeller: productSummaries.get(Number(request.listingId))?.sellerId != null
        && myMemberId.value != null
        && String(productSummaries.get(Number(request.listingId)).sellerId)
          === String(myMemberId.value),
      categoryLabel: '재검수',
      registrationNumber: `#${request.listingId}`,
      price: productSummaries.get(Number(request.listingId))?.price ?? null,
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
  const expiresAt = callExpirationAt(call)
  return expiresAt ? expiresAt.getTime() <= now.value : false
}

function callExpirationAt(call) {
  if (!call.scheduledAt || !['ACCEPTED', 'COMPLETED'].includes(call.status)) return null
  return new Date(new Date(call.scheduledAt).getTime() + 30 * 60 * 1000)
}

function isCallPending(call) {
  return call.status === 'PROPOSED'
}

function isCallInProgress(call) {
  return call.status === 'ACCEPTED' && !isSessionExpired(call)
}

function isCallCompleted(call) {
  return call.status === 'COMPLETED'
}

function isCallEnded(call) {
  return ['REJECTED', 'CANCELED'].includes(call.status)
    || (call.status === 'ACCEPTED' && isSessionExpired(call))
}

const callFilter = ref('전체 목록')
const callCounts = computed(() => ({
  전체: calls.value.length,
  대기: calls.value.filter(isCallPending).length,
  진행중: calls.value.filter(isCallInProgress).length,
  완료: calls.value.filter(isCallCompleted).length,
  종료: calls.value.filter(isCallEnded).length,
}))
const callTabs = computed(() => [
  { key: '전체 목록', label: `전체 목록 (${callCounts.value.전체})` },
  { key: '대기', label: `대기 (${callCounts.value.대기})` },
  { key: '진행 중', label: `진행 중 (${callCounts.value.진행중})` },
  { key: '완료', label: `완료 (${callCounts.value.완료})` },
  { key: '종료', label: `종료 (${callCounts.value.종료})` },
])
const filteredCalls = computed(() => {
  if (callFilter.value === '대기') return calls.value.filter(isCallPending)
  if (callFilter.value === '진행 중') return calls.value.filter(isCallInProgress)
  if (callFilter.value === '완료') return calls.value.filter(isCallCompleted)
  if (callFilter.value === '종료') return calls.value.filter(isCallEnded)
  return calls.value
})

function callTone(call) {
  if (isCallCompleted(call)) return 'done'
  if (isCallEnded(call)) return 'cancelled'
  return 'scheduled'
}

function callStatusLabel(call) {
  if (isCallCompleted(call)) return '확인 완료'
  if (call.status === 'REJECTED') return '거절됨'
  if (call.status === 'CANCELED') return '취소됨'
  if (call.status === 'ACCEPTED' && isSessionExpired(call)) return null
  if (call.status === 'ACCEPTED') return '일정 확정'
  return call.incoming ? '응답 대기' : '상대 응답 대기'
}

function callCounterpartName(call) {
  return call.counterpartName
    || `회원 #${call.incoming ? call.proposerId : call.respondentId}`
}

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

function shouldDisplayCallMemo(memo) {
  return Boolean(memo)
    && !memo.trim().endsWith('상태 실시간 확인 요청')
    && !memo.trim().endsWith('상태 실시간 검수 요청')
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
    <main class="page-shell">
      <PageHeader
        eyebrow="LIVE VERIFICATION"
        title="1:1 실시간 확인"
        description="구매 희망자가 보낸 화상 확인 및 재촬영 요청을 확인할 수 있습니다."
      />

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
          <div
            class="request-tabs"
            role="tablist"
          >
            <button
              v-for="tab in callTabs"
              :key="tab.key"
              type="button"
              role="tab"
              :aria-selected="callFilter === tab.key"
              class="request-tabs__item"
              :class="{ 'request-tabs__item--active': callFilter === tab.key }"
              @click="callFilter = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <div
            v-if="filteredCalls.length"
            class="request-list"
          >
            <article
              v-for="call in filteredCalls"
              :key="call.callId"
              data-testid="rtc-request-card"
              class="request-card"
              :class="`request-card--${callTone(call)}`"
            >
              <div class="request-card__main">
                <div class="request-card__thumb">
                  <img
                    v-if="call.productThumbnailUrl"
                    :src="call.productThumbnailUrl"
                    :alt="call.productName"
                  >
                  <span
                    v-else
                    class="request-card__thumb-empty"
                    aria-hidden="true"
                  >
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <rect
                        x="3.5"
                        y="5"
                        width="17"
                        height="14"
                        rx="2.5"
                      />
                      <path d="m4 16 4.5-4.5 3 3 3.5-3.5 5 5" />
                    </svg>
                  </span>
                </div>
                <div class="request-card__body">
                  <p class="request-card__top">
                    <span
                      class="request-kind"
                      :class="`request-kind--${callTone(call)}`"
                    >실시간 확인</span>
                    <span
                      v-if="callStatusLabel(call)"
                      class="request-pill"
                      :class="`request-pill--${callTone(call)}`"
                    >{{ callStatusLabel(call) }}</span>
                    <span
                      v-if="callExpirationAt(call)"
                      class="request-pill"
                      :class="isSessionExpired(call) ? 'request-pill--expired' : 'request-pill--timer'"
                    >
                      {{ isSessionExpired(call)
                        ? '세션 만료'
                        : `만료까지 ${remainingTime(callExpirationAt(call))}` }}
                    </span>
                  </p>
                  <h2 class="request-card__name">
                    {{ call.productName }}
                  </h2>
                  <p class="request-card__meta">
                    <span>{{ callCounterpartName(call) }}</span>
                    <span aria-hidden="true">·</span>
                    <span>{{ formatScheduledAt(call.scheduledAt) }}</span>
                  </p>
                  <p
                    v-if="shouldDisplayCallMemo(call.memo)"
                    class="request-card__memo"
                  >
                    {{ call.memo }}
                  </p>
                </div>
              </div>

              <div class="request-card__actions">
                <div class="request-card__primary-actions">
                  <template v-if="call.status === 'PROPOSED' && call.incoming">
                    <BaseButton @click="respond(call, true)">
                      수락하기
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
                      일정 변경
                    </BaseButton>
                  </template>
                  <BaseButton
                    v-if="call.rtcSessionId && call.status === 'ACCEPTED' && !isSessionExpired(call)"
                    @click="router.push({ name: 'rtc-call', params: { callId: call.callId } })"
                  >
                    화상 입장
                  </BaseButton>
                </div>
                <button
                  v-if="call.status === 'PROPOSED' && !call.incoming"
                  type="button"
                  class="request-link request-link--danger"
                  @click="startCancel(call)"
                >
                  약속 취소
                </button>
              </div>

              <form
                v-if="editingCallId === call.callId"
                class="request-card__form"
                @submit.prevent="saveEdit(call)"
              >
                <label class="request-card__field">
                  통화 시간
                  <input
                    v-model="editScheduledAt"
                    type="datetime-local"
                    required
                  >
                </label>
                <label class="request-card__field">
                  메모
                  <textarea
                    v-model="editMemo"
                    maxlength="500"
                    rows="3"
                  />
                </label>
                <div class="request-card__form-actions">
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
                class="request-card__form"
                @submit.prevent="confirmCancel(call)"
              >
                <label class="request-card__field">
                  취소 사유 (선택)
                  <textarea
                    v-model="cancelReason"
                    maxlength="500"
                    rows="3"
                  />
                </label>
                <div class="request-card__form-actions">
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
            </article>

            <p class="request-list__end">
              더 이상 요청이 없습니다.
            </p>
          </div>
          <div
            v-else
            class="request-empty"
          >
            <p class="request-empty__title">
              처리할 요청이 없습니다.
            </p>
            <p class="request-empty__description">
              구매 희망자가 화상 확인을 요청하면 여기에 표시됩니다.
            </p>
          </div>
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
        <div
          class="request-tabs"
          role="tablist"
        >
          <button
            v-for="tab in recaptureTabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="recaptureFilter === tab.key"
            class="request-tabs__item"
            :class="{ 'request-tabs__item--active': recaptureFilter === tab.key }"
            @click="recaptureFilter = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div
          v-if="filteredRecaptureRequests.length"
          class="request-list"
        >
          <article
            v-for="item in filteredRecaptureRequests"
            :key="item.id"
            data-testid="recapture-request-card"
            class="request-card"
            :class="item.status === 'PENDING' ? 'request-card--retake' : 'request-card--done'"
          >
            <div class="request-card__main">
              <div class="request-card__thumb">
                <img
                  v-if="item.productThumbnailUrl"
                  :src="item.productThumbnailUrl"
                  :alt="item.productName"
                >
                <span
                  v-else
                  class="request-card__thumb-empty"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect
                      x="3.5"
                      y="5"
                      width="17"
                      height="14"
                      rx="2.5"
                    />
                    <path d="m4 16 4.5-4.5 3 3 3.5-3.5 5 5" />
                  </svg>
                </span>
              </div>
              <div class="request-card__body">
                <p class="request-card__top">
                  <span
                    class="request-kind request-kind--retake"
                  >재촬영 요청</span>
                  <span
                    class="request-pill"
                    :class="item.status === 'PENDING' ? 'request-pill--retake' : 'request-pill--done'"
                  >{{ item.status === 'PENDING' ? '재촬영 대기' : '재촬영 완료' }}</span>
                </p>
                <h2 class="request-card__name">
                  {{ item.productName }}
                </h2>
                <div
                  v-if="item.checklistItemName"
                  class="request-chips"
                >
                  <span class="request-chip">{{ item.checklistItemName }}</span>
                </div>
                <p class="request-card__meta">
                  <span>구매 희망자</span>
                  <span aria-hidden="true">·</span>
                  <span>{{ item.requestedAt ? `${item.requestedAt} 접수` : '접수일 미상' }}</span>
                </p>
                <p class="request-card__memo">
                  {{ item.reason }}
                </p>
              </div>
            </div>

            <div class="request-card__actions">
              <BaseButton
                v-if="item.status === 'PENDING' && item.isSeller"
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
          </article>

          <p class="request-list__end">
            더 이상 요청이 없습니다.
          </p>
        </div>

        <div
          v-else-if="!isLoadingRecaptures"
          class="request-empty"
        >
          <p class="request-empty__title">
            처리할 요청이 없습니다.
          </p>
          <p class="request-empty__description">
            구매 희망자가 재촬영을 요청하면 여기에 표시됩니다.
          </p>
        </div>
      </template>
    </main>
  </DefaultLayout>
</template>

<style scoped>
.request-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-bottom: 24px;
  border-bottom: 1px solid #ececec;
}

.request-tabs__item {
  padding: 0 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.request-tabs__item:hover {
  color: var(--color-text-main);
}

.request-tabs__item--active {
  border-bottom-color: #6366f1;
  font-weight: 600;
  color: var(--color-text-main);
}

.request-list {
  display: grid;
  gap: 14px;
}

.request-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  border: 1px solid #eceef3;
  border-radius: 18px;
  background: var(--color-surface);
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
}

.request-card:hover {
  border-color: rgb(99 102 241 / 28%);
  box-shadow: 0 10px 28px -16px rgb(76 100 200 / 28%);
}

.request-card--done,
.request-card--cancelled {
  background: #fcfcfd;
}

.request-card--done .request-card__name,
.request-card--cancelled .request-card__name {
  color: var(--color-text-sub);
}

.request-card__main {
  display: flex;
  flex: 1 1 380px;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.request-card__thumb {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 124px;
  height: 96px;
  overflow: hidden;
  border-radius: 12px;
  background: #f6f7fb;
}

.request-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

.request-card:hover .request-card__thumb img {
  transform: scale(1.04);
}

.request-card__thumb-empty {
  color: #d3d8e2;
}

.request-card__thumb-empty svg {
  width: 28px;
  height: 28px;
}

.request-card__body {
  min-width: 0;
}

.request-card__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.request-kind {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.request-kind--scheduled { color: #6366f1; }
.request-kind--retake { color: #8b5cf6; }
.request-kind--done { color: #16a34a; }
.request-kind--cancelled { color: #94a3b8; }

.request-pill {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.request-pill--scheduled { background: #eef0fe; color: #5b5fe0; }
.request-pill--retake { background: #f4eefe; color: #7c3aed; }
.request-pill--done { background: #e9f7ee; color: #15803d; }
.request-pill--cancelled { background: #f2f4f7; color: #6b7280; }
.request-pill--timer { background: #eff6ff; color: #2f6fce; }
.request-pill--expired { background: #fdeeee; color: #c53030; }

.request-card__name {
  overflow: hidden;
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: #98a1b0;
}

.request-card__meta [aria-hidden='true'] {
  color: #d3d8e2;
}

.request-card__memo {
  max-width: 46em;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-sub);
}

.request-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.request-chip {
  padding: 4px 10px;
  border-radius: 8px;
  background: #f4f1fe;
  font-size: 12px;
  font-weight: 500;
  color: #7c3aed;
}

.request-card__actions {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.request-card__primary-actions {
  display: flex;
  gap: 8px;
}

.request-link {
  border: 0;
  background: transparent;
  font-size: 13px;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: color 0.2s ease;
}

.request-link:hover {
  color: #6366f1;
}

.request-link--danger {
  color: #a5adbb;
}

.request-link--danger:hover {
  color: #c53030;
}

.request-list__end {
  padding: 28px 0 4px;
  font-size: 13px;
  text-align: center;
  color: #a5adbb;
}

.request-card__form {
  display: grid;
  gap: 12px;
  width: 100%;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #ececec;
}

.request-card__field {
  display: grid;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-main);
}

.request-card__field input,
.request-card__field textarea {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-main);
}

.request-card__field input:focus,
.request-card__field textarea:focus {
  border-color: var(--color-primary);
  outline: none;
}

.request-card__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.request-empty {
  padding: 80px 24px;
  text-align: center;
}

.request-empty__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-main);
}

.request-empty__description {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}

@media (max-width: 720px) {
  .request-tabs {
    gap: 20px;
    overflow-x: auto;
  }

  .request-card {
    padding: 16px;
  }

  .request-card__actions {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .request-tabs__item,
  .request-card,
  .request-card__thumb img {
    transition: none;
  }

  .request-card:hover .request-card__thumb img {
    transform: none;
  }
}
</style>
