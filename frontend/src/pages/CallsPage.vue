<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseButton from '../components/BaseButton.vue'
import { getChatRooms } from '../api/chat'
import {
  cancelRtcCall,
  getMyRtcCalls,
  getRtcSession,
  respondRtcCall,
  updateRtcCall,
} from '../api/rtc'
import { getMyProducts, getMyReinspectionRequests } from '../api/products'

const router = useRouter()
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
    /*
      재검수 응답(ReinspectionRequestResponse)에는 listingId 하나뿐이라, 예전에는
      카드가 "상품 #101"에 빈 사진틀로 떴습니다. 재검수는 내가 올린 상품에만 오므로
      내 상품 목록을 한 번 받아 와 이름과 대표 사진을 붙입니다.
      (요청 건수만큼 상세를 부르지 않으려고 목록 한 번으로 끝냅니다.)
    */
    const [requests, myProducts] = await Promise.all([
      getMyReinspectionRequests(),
      getMyProducts({ size: 100, sort: 'updatedAt,desc' })
        .then((response) => response?.data || [])
        .catch(() => []),
    ])
    const productById = new Map(myProducts.map((product) => [Number(product.productId), product]))

    recaptureRequests.value = requests.map((request) => {
      const product = productById.get(Number(request.listingId))
      return {
        id: request.requestKey,
        requestKey: request.requestKey,
        productId: request.listingId,
        productName: product?.name || `상품 #${request.listingId}`,
        thumbnailUrl: product?.thumbnailUrl || null,
        categoryLabel: '재검수',
        registrationNumber: `#${request.listingId}`,
        price: null,
        specSummary: `${request.items.length}개 항목`,
        checklistItemName: request.items.map((item) => item.itemName).join(', '),
        requestedAt: request.requestedAt?.slice(0, 10) || '',
        reason: request.reason,
        status: request.status === 'REQUESTED' ? 'PENDING' : request.status,
      }
    })
  } catch (error) {
    recaptureLoadError.value = error.message || '재검수 요청을 불러오지 못했습니다.'
    recaptureRequests.value = []
  } finally {
    isLoadingRecaptures.value = false
  }
}

// 서버는 보낸 사람이 PROPOSED 상태일 때만 취소를 받습니다(CallAppointment.cancel).
function canCancelCall(request) {
  return request.kind === 'CALL'
    && request.call.status === 'PROPOSED'
    && !request.call.incoming
}

// 상품 페이지로 가는 길. 사진과 제목이 이 링크를 씁니다.
function productRoute(request) {
  if (!request.productId) return null
  return { name: 'product-detail', params: { productId: request.productId } }
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

function isCallCompleted(call) {
  return call.status === 'COMPLETED' || isSessionExpired(call)
}

/*
  아직 답이 없는데 약속 시각이 지나 버린 요청.
  ---------------------------------------------------------------------------
  서버는 약속 시각 +30분이 지나면 수락을 거부합니다(RtcCallService.respond →
  RTC_SESSION_EXPIRED). 그런데 화면은 PROPOSED면 무조건 '응답 대기'로 두고
  수락 버튼을 열어 둬서, 누르면 그대로 오류가 났습니다.

  일정 변경·취소는 서버에 시간 제한이 없으므로(CallAppointment.update/cancel)
  버튼을 남겨 둡니다. 아직 판매자가 처리해야 할 건이라 '완료'로 치우지 않고
  실시간 확인 탭에 그대로 둡니다.
*/
function isCallOverdue(call) {
  if (call.status !== 'PROPOSED' || !call.scheduledAt) return false
  return new Date(call.scheduledAt).getTime() + 30 * 60 * 1000 <= now.value
}

/*
  화상 확인과 재촬영을 한 줄기로 합칩니다.
  ---------------------------------------------------------------------------
  예전에는 큰 탭으로 둘을 갈라 놓고, 각 탭 안에서 다시 상품별로 묶은 머리 카드를
  얹었습니다. 그래서 요청 하나를 보려면 카드를 두 겹 지나야 했고, 두 종류를 함께
  보고 싶어도 탭을 오가야 했습니다.

  판매자가 실제로 하는 일은 "구매자가 나에게 뭘 요청했나"를 한 줄로 훑고 처리하는
  것이라, 종류를 섞어 한 목록으로 두고 탭은 걸러 보는 용도로만 씁니다.
*/
const REQUEST_TABS = [
  { id: 'ALL', label: '전체' },
  { id: 'CALL', label: '실시간 확인' },
  { id: 'RECAPTURE', label: '재촬영 요청' },
  { id: 'DONE', label: '완료' },
]
const requestFilter = ref('ALL')

// 화상 요청 한 건을 목록이 쓰는 공통 모양으로 바꿉니다.
function toCallRequest(call) {
  const completed = isCallCompleted(call)
  // 서버 enum은 AppointmentStatus.CANCELED(L 하나)입니다. 여기서 CANCELLED만 보고
  // 있던 탓에 취소된 약속이 '일정 확정'으로 떠 있었습니다. 두 철자를 모두 받습니다.
  const cancelled = ['REJECTED', 'CANCELED', 'CANCELLED'].includes(call.status)
  let tone = 'scheduled'
  if (completed) tone = 'done'
  else if (cancelled) tone = 'cancelled'

  let statusLabel = '일정 확정'
  if (completed) statusLabel = isSessionExpired(call) ? '시간 만료' : '확인 완료'
  else if (call.status === 'REJECTED') statusLabel = '거절됨'
  else if (cancelled) statusLabel = '취소됨'
  else if (isCallOverdue(call)) statusLabel = '시간 지남'
  else if (call.status === 'PROPOSED') statusLabel = call.incoming ? '응답 대기' : '상대 응답 대기'

  return {
    id: `call-${call.callId}`,
    kind: 'CALL',
    kindLabel: '실시간 확인',
    tone,
    statusLabel,
    productId: call.productId,
    productName: call.productName,
    thumbnailUrl: call.productThumbnailUrl,
    counterpartName: call.counterpartName
      || `회원 #${call.incoming ? call.proposerId : call.respondentId}`,
    timeLabel: formatScheduledAt(call.scheduledAt),
    sortAt: call.scheduledAt ? new Date(call.scheduledAt).getTime() : 0,
    memo: shouldDisplayCallMemo(call.memo) ? call.memo : '',
    call,
  }
}

/*
  재촬영 요청 한 건도 같은 모양으로 바꿉니다.
  ---------------------------------------------------------------------------
  서버 ReinspectionStatus는 REQUESTED · COMPLETED · CANCELED 셋입니다. 예전에는
  'PENDING이 아니면 완료'로 뭉뚱그려서, 구매자가 물린 요청까지 '재촬영 완료'로
  떴습니다. 셋을 따로 읽습니다.
*/
function toRecaptureRequest(item) {
  const cancelled = ['CANCELED', 'CANCELLED'].includes(item.status)
  const done = item.status !== 'PENDING'

  let tone = 'retake'
  if (cancelled) tone = 'cancelled'
  else if (done) tone = 'done'

  let statusLabel = '재촬영 대기'
  if (cancelled) statusLabel = '요청 취소'
  else if (done) statusLabel = '재촬영 완료'

  return {
    id: `recapture-${item.id}`,
    kind: 'RECAPTURE',
    kindLabel: '재촬영 요청',
    tone,
    statusLabel,
    productId: item.productId,
    productName: item.productName,
    thumbnailUrl: item.thumbnailUrl,
    counterpartName: '구매 희망자',
    timeLabel: item.requestedAt ? `${item.requestedAt} 접수` : '접수일 미상',
    sortAt: item.requestedAt ? new Date(item.requestedAt).getTime() : 0,
    memo: item.reason,
    // 어느 항목을 다시 찍어 달라는 것인지가 이 요청의 핵심입니다.
    checklistNames: (item.checklistItemName || '').split(',').map((n) => n.trim()).filter(Boolean),
    recapture: item,
  }
}

const allRequests = computed(() => [
  ...calls.value.map(toCallRequest),
  ...recaptureRequests.value.map(toRecaptureRequest),
].sort((a, b) => b.sortAt - a.sortAt))

/*
  거절·취소된 약속은 손댈 것이 없으므로 끝난 것으로 봅니다.
  예전에는 tone === 'done'만 걸러 내서, 취소된 약속이 '실시간 확인' 탭에 남아
  처리할 일처럼 세어졌습니다.
*/
function isSettled(request) {
  return request.tone === 'done' || request.tone === 'cancelled'
}

function filterRequests(tabId) {
  if (tabId === 'CALL') return allRequests.value.filter((r) => r.kind === 'CALL' && !isSettled(r))
  if (tabId === 'RECAPTURE') return allRequests.value.filter((r) => r.kind === 'RECAPTURE' && !isSettled(r))
  if (tabId === 'DONE') return allRequests.value.filter(isSettled)
  return allRequests.value
}

const visibleRequests = computed(() => filterRequests(requestFilter.value))

function requestTabCount(tabId) {
  return filterRequests(tabId).length
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

function shouldDisplayCallMemo(memo) {
  return Boolean(memo) && !memo.trim().endsWith('상태 실시간 확인 요청')
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

      <!--
        이 화면에 있는 것은 넷뿐입니다. 제목, 설명, 탭, 요청 목록.
        숫자 요약은 뺐습니다. 여기 온 사람은 수치를 들여다보러 온 게 아니라
        밀린 요청을 처리하러 왔고, 건수는 아래 탭 옆에 이미 붙어 있습니다.
      -->
      <!-- 걸러 보는 탭. 회색 홈을 깔지 않고 밑줄로만 지금 자리를 표시합니다. -->
      <div
        class="tabs"
        role="tablist"
      >
        <button
          v-for="tab in REQUEST_TABS"
          :key="tab.id"
          type="button"
          role="tab"
          :aria-selected="requestFilter === tab.id"
          class="tabs__item"
          :class="{ 'tabs__item--on': requestFilter === tab.id }"
          @click="requestFilter = tab.id"
        >
          {{ tab.label }}
          <span class="tabs__count">({{ requestTabCount(tab.id) }})</span>
        </button>
      </div>

      <p
        v-if="errorMessage"
        role="alert"
        class="mb-5 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>
      <p
        v-if="recaptureLoadError"
        role="alert"
        class="mb-5 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ recaptureLoadError }}
      </p>

      <p
        v-if="isLoading || isLoadingRecaptures"
        class="py-16 text-center text-sm text-text-sub"
      >
        요청을 불러오는 중입니다.
      </p>

      <div
        v-else-if="visibleRequests.length"
        class="list"
      >
        <article
          v-for="request in visibleRequests"
          :key="request.id"
          class="request"
          :class="`request--${request.tone}`"
        >
          <div class="request__main">
            <!-- 사진과 제목이 곧 상품 링크입니다. '상품 보기' 버튼을 따로 두지 않습니다. -->
            <component
              :is="productRoute(request) ? RouterLink : 'div'"
              class="request__thumb"
              :class="{ 'request__thumb--link': productRoute(request) }"
              :to="productRoute(request) || undefined"
              :tabindex="productRoute(request) ? -1 : undefined"
              :aria-hidden="productRoute(request) ? 'true' : undefined"
            >
              <img
                v-if="request.thumbnailUrl"
                :src="request.thumbnailUrl"
                :alt="request.productName"
              >
              <span
                v-else
                class="request__thumbEmpty"
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
            </component>

            <div class="request__body">
              <!-- 무슨 요청인지 먼저 한 줄. 종류는 색 글자로, 상태는 작은 알약으로. -->
              <p class="request__top">
                <span
                  class="kind"
                  :class="`kind--${request.tone}`"
                >{{ request.kindLabel }}</span>
                <span
                  class="pill"
                  :class="`pill--${request.tone}`"
                >{{ request.statusLabel }}</span>
                <span
                  v-if="request.kind === 'CALL' && callExpirationAt(request.call)"
                  class="pill"
                  :class="isSessionExpired(request.call) ? 'pill--expired' : 'pill--timer'"
                >
                  {{ isSessionExpired(request.call)
                    ? '세션 만료'
                    : `만료까지 ${remainingTime(callExpirationAt(request.call))}` }}
                </span>
              </p>

              <!-- 이 줄이 카드에서 가장 큽니다. 판매자가 찾는 것은 업무가 아니라 물건입니다. -->
              <h2 class="request__name">
                <RouterLink
                  v-if="productRoute(request)"
                  :to="productRoute(request)"
                  class="request__nameLink"
                >
                  {{ request.productName }}
                </RouterLink>
                <template v-else>
                  {{ request.productName }}
                </template>
              </h2>

              <!-- 재촬영은 어느 항목을 다시 찍어 달라는 것인지가 핵심이라 따로 세웁니다. -->
              <ul
                v-if="request.checklistNames?.length"
                class="chips"
              >
                <li
                  v-for="name in request.checklistNames"
                  :key="name"
                  class="chip"
                >
                  {{ name }}
                </li>
              </ul>

              <p class="request__who">
                <span>{{ request.counterpartName }}</span>
                <span aria-hidden="true">·</span>
                <span>{{ request.timeLabel }}</span>
              </p>

              <p
                v-if="request.memo"
                class="request__memo"
              >
                {{ request.memo }}
              </p>
            </div>
          </div>

          <!--
            오른쪽은 두 층입니다. 위에 지금 해야 할 일 하나, 아래에 나머지를 글자로.
            버튼을 한 줄에 세 개 늘어놓으면 무엇이 본 작업인지 알 수 없습니다.
          -->
          <div class="request__actions">
            <template v-if="request.kind === 'CALL'">
              <BaseButton
                v-if="request.call.rtcSessionId && request.call.status === 'ACCEPTED' && !isSessionExpired(request.call)"
                @click="router.push({ name: 'rtc-call', params: { callId: request.call.callId } })"
              >
                <svg
                  class="btnIcon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <rect
                    x="2.5"
                    y="6"
                    width="13"
                    height="12"
                    rx="2.5"
                  />
                  <path d="M15.5 10.5 21.5 7v10l-6-3.5z" />
                </svg>
                화상 입장
              </BaseButton>
              <!--
                시간이 지난 요청에는 수락 버튼을 두지 않습니다. 서버가 약속 시각
                +30분을 넘기면 수락을 거부해서, 누르면 오류만 났습니다.
              -->
              <BaseButton
                v-else-if="request.call.status === 'PROPOSED' && request.call.incoming && !isCallOverdue(request.call)"
                :disabled="pendingCallId === request.call.callId"
                @click="respond(request.call, true)"
              >
                수락하기
              </BaseButton>
              <BaseButton
                v-else-if="request.call.status === 'PROPOSED'"
                variant="outline"
                @click="startEdit(request.call)"
              >
                일정 변경
              </BaseButton>
            </template>

            <BaseButton
              v-else-if="request.recapture.status === 'PENDING'"
              variant="outline"
              :to="{
                name: 'seller-product-edit',
                params: { productId: request.recapture.productId },
                query: { reinspectionRequestKey: request.recapture.requestKey },
              }"
            >
              <svg
                class="btnIcon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M12 16V4.5" />
                <path d="m7.5 9 4.5-4.5L16.5 9" />
                <path d="M4 15.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2.5" />
              </svg>
              사진 업로드
            </BaseButton>

            <!--
              작은 글자 줄. 접어 두지 않고 그대로 펼칩니다.
              끝났거나 취소된 약속에도 최소한 채팅방으로 돌아갈 길은 남겨 둡니다.
            -->
            <div class="links">
              <template v-if="request.kind === 'CALL'">
                <button
                  v-if="request.call.status === 'PROPOSED' && request.call.incoming"
                  type="button"
                  class="link"
                  :disabled="pendingCallId === request.call.callId"
                  @click="respond(request.call, false)"
                >
                  거절
                </button>
                <button
                  v-if="request.call.chatRoomId"
                  type="button"
                  class="link"
                  @click="router.push({ name: 'chat', params: { roomId: request.call.chatRoomId } })"
                >
                  채팅 열기
                </button>
                <button
                  v-if="canCancelCall(request)"
                  type="button"
                  class="link link--danger"
                  @click="startCancel(request.call)"
                >
                  약속 취소
                </button>
              </template>
              <button
                v-else-if="request.recapture.status === 'PENDING'"
                type="button"
                class="link"
                @click="router.push({
                  name: 'seller-product-edit',
                  params: { productId: request.recapture.productId },
                })"
              >
                상품 관리
              </button>
            </div>
          </div>

          <!-- 일정 변경·취소 입력칸은 그 카드 안에서 펼칩니다. -->
          <form
            v-if="request.kind === 'CALL' && editingCallId === request.call.callId"
            class="request__form"
            @submit.prevent="saveEdit(request.call)"
          >
            <label class="request__field">
              통화 시간
              <input
                v-model="editScheduledAt"
                type="datetime-local"
                required
              >
            </label>
            <label class="request__field">
              메모
              <textarea
                v-model="editMemo"
                maxlength="500"
                rows="3"
              />
            </label>
            <div class="request__formActions">
              <BaseButton
                variant="ghost"
                @click="editingCallId = null"
              >
                닫기
              </BaseButton>
              <BaseButton
                type="submit"
                :disabled="pendingCallId === request.call.callId"
              >
                변경 저장
              </BaseButton>
            </div>
          </form>

          <form
            v-if="request.kind === 'CALL' && cancelingCallId === request.call.callId"
            class="request__form"
            @submit.prevent="confirmCancel(request.call)"
          >
            <label class="request__field">
              취소 사유 (선택)
              <textarea
                v-model="cancelReason"
                maxlength="500"
                rows="3"
              />
            </label>
            <div class="request__formActions">
              <BaseButton
                variant="ghost"
                @click="cancelingCallId = null"
              >
                닫기
              </BaseButton>
              <BaseButton
                type="submit"
                :disabled="pendingCallId === request.call.callId"
              >
                취소 확인
              </BaseButton>
            </div>
          </form>
        </article>

        <!-- 목록이 여기서 끝났다는 표시. 더 스크롤할지 망설이지 않게 합니다. -->
        <p class="listEnd">
          더 이상 요청이 없습니다.
        </p>
      </div>

      <div
        v-else
        class="empty"
      >
        <p class="empty__title">
          처리할 요청이 없습니다.
        </p>
        <p class="empty__desc">
          구매 희망자가 화상 확인이나 재촬영을 요청하면 여기에 모입니다.
        </p>
      </div>
    </main>
  </DefaultLayout>
</template>

<style scoped>
/*
  이 화면에서 상자는 요청 카드 하나뿐입니다.
  ---------------------------------------------------------------------------
  제목 · 설명 · 탭 · 요청 목록. 그 밖에는 아무것도 두지 않습니다.
  나머지는 활자 크기·옅은 선·작은 색 라벨로만 구분합니다.
  구역 사이는 넉넉하게, 카드 안은 촘촘하게. 간격은 8px 격자에 맞췄습니다.
*/

/* ---- 탭 (회색 홈 없음) -------------------------------------------------- */

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-bottom: 24px;
  border-bottom: 1px solid #ececec;
}

.tabs__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-sub);
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease;
}

.tabs__item:hover {
  color: var(--color-text-main);
}

.tabs__item--on {
  border-bottom-color: #6366f1;
  font-weight: 600;
  color: var(--color-text-main);
}

.tabs__count {
  font-size: 13px;
  font-weight: 500;
  color: #a5adbb;
}

.tabs__item--on .tabs__count {
  color: #6366f1;
}

/* ---- 요청 카드 (이 화면의 유일한 카드) ---------------------------------- */

.list {
  display: grid;
  gap: 14px;
}

.request {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;

  /*
    왼쪽 세로 색 띠는 두지 않습니다. 종류는 이미 색 글자와 알약이 말해 주고,
    띠까지 있으면 카드마다 색이 두 번 반복돼 목록이 어수선해집니다.
  */
  padding: 20px 24px;
  border: 1px solid #eceef3;
  border-radius: 18px;
  background: var(--color-surface);
  transition:
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

.request:hover {
  border-color: rgb(99 102 241 / 28%);
  box-shadow: 0 10px 28px -16px rgb(76 100 200 / 28%);
}

/* 끝난 건은 뒤로 물러납니다. 처리할 것만 눈에 들어오게. */
.request--done,
.request--cancelled {
  background: #fcfcfd;
}

.request--done .request__name,
.request--cancelled .request__name {
  color: var(--color-text-sub);
}

.request__main {
  display: flex;
  flex: 1 1 380px;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

/* 상품 사진을 크게 둡니다. 이 화면의 주인공은 업무가 아니라 물건입니다. */
.request__thumb {
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

.request__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

.request__thumb--link {
  cursor: pointer;
}

/* 사진이 눌린다는 것을 알리는 최소한의 신호. 틀 밖으로는 나가지 않습니다. */
.request:hover .request__thumb--link img {
  transform: scale(1.04);
}

.request__thumbEmpty {
  color: #d3d8e2;
}

.request__thumbEmpty svg {
  width: 28px;
  height: 28px;
}

.request__body {
  min-width: 0;
}

/* 종류 · 상태 줄 */

.request__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.kind {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.kind--scheduled { color: #6366f1; }
.kind--retake { color: #8b5cf6; }
.kind--done { color: #16a34a; }
.kind--cancelled { color: #94a3b8; }

.pill {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.pill--scheduled { background: #eef0fe; color: #5b5fe0; }
.pill--retake { background: #f4eefe; color: #7c3aed; }
.pill--done { background: #e9f7ee; color: #15803d; }
.pill--cancelled { background: #f2f4f7; color: #6b7280; }
.pill--timer { background: #eff6ff; color: #2f6fce; }
.pill--expired { background: #fdeeee; color: #c53030; }

.request__name {
  overflow: hidden;
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request__nameLink {
  color: inherit;
  text-decoration: none;
  transition: color 0.2s ease;
}

.request__nameLink:hover {
  color: #6366f1;
}

.request__nameLink:focus-visible {
  border-radius: 4px;
  outline: 2px solid #6366f1;
  outline-offset: 3px;
}

/* 재촬영 항목 칩 */

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.chip {
  padding: 4px 10px;
  border-radius: 8px;
  background: #f4f1fe;
  font-size: 12px;
  font-weight: 500;
  color: #7c3aed;
}

.request__who {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: #98a1b0;
}

.request__who [aria-hidden='true'] {
  color: #d3d8e2;
}

.request__memo {
  max-width: 46em;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-sub);
}

/* ---- 오른쪽 작업 (본 작업 하나 + 글자 줄) -------------------------------- */

.request__actions {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.btnIcon {
  width: 17px;
  height: 17px;
}

.links {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
}

/* 글자 버튼 사이는 세로 실선 하나로만 가릅니다. */
.links > * + *::before {
  content: '';
  position: absolute;
  top: 50%;
  left: -7px;
  width: 1px;
  height: 11px;
  background: #e2e5ec;
  transform: translateY(-50%);
}

.links > * {
  position: relative;
}

.link {
  border: 0;
  background: transparent;
  font-size: 13px;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: color 0.2s ease;
}

.link:hover {
  color: #6366f1;
}

.link:disabled {
  color: #c6ccd6;
  cursor: not-allowed;
}

/* 되돌릴 수 없는 것만 붉게. 평소에는 눈에 띄지 않다가 짚으면 드러납니다. */
.link--danger {
  color: #a5adbb;
}

.link--danger:hover {
  color: #c53030;
}

/* 목록 끝 */

.listEnd {
  padding: 28px 0 4px;
  font-size: 13px;
  text-align: center;
  color: #a5adbb;
}

/* ---- 카드 안에서 펼쳐지는 입력 ------------------------------------------ */

.request__form {
  display: grid;
  gap: 12px;
  width: 100%;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #ececec;
}

.request__field {
  display: grid;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-main);
}

.request__field input,
.request__field textarea {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-main);
}

.request__field input:focus,
.request__field textarea:focus {
  border-color: var(--color-primary);
  outline: none;
}

.request__formActions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ---- 빈 상태 (상자 없음) ------------------------------------------------ */

.empty {
  padding: 80px 24px;
  text-align: center;
}

.empty__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-main);
}

.empty__desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}

@media (max-width: 720px) {
  .tabs {
    gap: 20px;
    overflow-x: auto;
  }

  .request {
    padding: 16px;
  }

  .request__actions {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tabs__item,
  .request,
  .request__thumb img {
    transition: none;
  }

  .request:hover .request__thumb--link img {
    transform: none;
  }
}
</style>
