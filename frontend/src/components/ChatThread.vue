<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getChatMediaBlob, getChatMessages, uploadChatMedia } from '../api/chat'
import { createChatSocket } from '../api/chatSocket'
import { getMyReinspectionRequests, getProduct, getProductChecklist } from '../api/products'
import { getMyRtcCalls, requestRtcCall, respondRtcCall } from '../api/rtc'
import { useAuthSession } from '../auth/session'

const props = defineProps({
  room: {
    type: Object,
    required: true, // ChatRoomSummaryResponse: roomId, listingId, counterpartId, status, unreadCount, lastMessageAt ...
  },
})
const emit = defineEmits(['room-updated'])

const session = useAuthSession()
const myMemberId = computed(() => session.value?.member?.memberId ?? null)

const product = ref(null)
const isSeller = computed(() => Number(product.value?.sellerId) === Number(myMemberId.value))
const checklistItems = ref([])
const isChecklistOpen = ref(false)
const messages = ref([])
const isLoadingMessages = ref(true)
const messagesError = ref('')
const socketStatus = ref('connecting')
const isUploading = ref(false)
const counterpartLastReadSeq = ref(0)
const isCallFormOpen = ref(false)
const isRequestingCall = ref(false)
const callScheduledAt = ref('')
const callMemo = ref('')
const callMessage = ref('')
const appointments = ref([])
const pendingAppointmentId = ref(null)
const appointmentAnchorSequence = ref(0)
const anchoredAppointmentId = ref(null)
const fileInput = ref(null)
const messageList = ref(null)
const expandedImage = ref(null)
let previousBodyOverflow = null
let chatSocket = null
let nextPendingMessageId = -1
let connectionVersion = 0
let reconnectTimer = null
const mediaObjectUrls = new Set()
const LAST_TIMELINE_ORDER = 2147483647
const now = ref(Date.now())
const countdownTimer = setInterval(() => { now.value = Date.now() }, 1000)

async function scrollToLatest() {
  await nextTick()
  const element = messageList.value
  if (element) element.scrollTop = element.scrollHeight
}

function openImage(image) {
  if (previousBodyOverflow === null) {
    previousBodyOverflow = document.body.style.overflow
  }
  document.body.style.overflow = 'hidden'
  expandedImage.value = image
}

function closeImage() {
  expandedImage.value = null
  if (previousBodyOverflow !== null) {
    document.body.style.overflow = previousBodyOverflow
    previousBodyOverflow = null
  }
}

function handleImageDialogKeydown(event) {
  if (event.key === 'Escape' && expandedImage.value) closeImage()
}

async function attachMediaUrls(message) {
  if (!message.media?.length) return message
  const media = await Promise.all(message.media.map(async (item) => {
    if (item.displayUrl) return item
    try {
      const blob = await getChatMediaBlob(item.mediaId)
      if (typeof URL.createObjectURL !== 'function') return item
      const displayUrl = URL.createObjectURL(blob)
      mediaObjectUrls.add(displayUrl)
      return { ...item, displayUrl }
    } catch {
      return item
    }
  }))
  return { ...message, media }
}

function matchesReinspectionRequest(message, request) {
  if (Number(request.listingId) !== Number(props.room.listingId)) return false
  const content = message.content || ''
  if (request.reason && content.includes(request.reason)) return true
  const itemNames = request.items?.map((item) => item.itemName).filter(Boolean) || []
  return itemNames.length > 0 && itemNames.every((name) => content.includes(name))
}

function restoreReinspectionFromContent(message) {
  if (message.type !== 'SYSTEM' || message.reinspection) return message
  const content = String(message.content || '')
  if (content.includes('재검수가 완료되었습니다')) {
    return {
      ...message,
      notificationType: 'REINSPECTION_COMPLETED',
      reinspection: {
        listingId: props.room.listingId,
        items: [],
      },
    }
  }
  if (!content.includes('재검수 요청')) return message

  const lines = content.split('\n').map((line) => line.trim()).filter(Boolean)
  const reasonLine = lines.find((line) => line.startsWith('사유:'))
  const items = lines
    .filter((line) => line.startsWith('- '))
    .map((line) => ({
      name: line.slice(2).split(':')[0].trim(),
      requestContent: null,
    }))
    .filter((item) => item.name)
  return {
    ...message,
    notificationType: 'REINSPECTION_REQUESTED',
    reinspection: {
      listingId: props.room.listingId,
      reason: reasonLine?.slice('사유:'.length).trim() || null,
      items,
    },
  }
}

async function restoreReinspectionCards(loadedMessages) {
  const withCompletedCards = loadedMessages.map(restoreReinspectionFromContent)
  const plainNotifications = loadedMessages.filter(
    (message) => message.type === 'SYSTEM'
      && !message.reinspection
      && message.content?.includes('재검수 요청'),
  )
  if (!plainNotifications.length) return withCompletedCards

  try {
    const requests = await getMyReinspectionRequests()
    return withCompletedCards.map((message) => {
      const isPlainNotification = plainNotifications.some(
        (candidate) => candidate.clientMessageId === message.clientMessageId,
      )
      if (!isPlainNotification) return message
      const request = requests.find((candidate) => matchesReinspectionRequest(message, candidate))
      if (!request) return restoreReinspectionFromContent(message)
      return {
        ...message,
        notificationType: 'REINSPECTION_REQUESTED',
        reinspection: {
          requestKey: request.requestKey,
          listingId: request.listingId,
          reason: request.reason,
          items: (request.items || []).map((item) => ({
            name: item.itemName,
            requestContent: item.requestContent,
          })),
        },
      }
    })
  } catch {
    return loadedMessages.map(restoreReinspectionFromContent)
  }
}

async function loadMessages(roomId) {
  isLoadingMessages.value = true
  messagesError.value = ''
  try {
    const result = await getChatMessages(roomId, { size: 50 })
    const loadedMessages = await Promise.all(
      (result?.content || []).slice().reverse().map(attachMediaUrls),
    )
    messages.value = await restoreReinspectionCards(loadedMessages)
    await scrollToLatest()
    markRead()
  } catch (error) {
    messagesError.value = error.message || '메시지를 불러오지 못했습니다.'
  } finally {
    isLoadingMessages.value = false
  }
}

async function upsertMessage(message) {
  const hydrated = await attachMediaUrls(restoreReinspectionFromContent(message))
  const indexByClientId = messages.value.findIndex((item) => item.clientMessageId === message.clientMessageId)
  if (indexByClientId >= 0) {
    messages.value[indexByClientId] = { ...messages.value[indexByClientId], ...hydrated, isPending: false }
    await scrollToLatest()
    return
  }
  if (!messages.value.some((item) => item.messageId === message.messageId)) {
    messages.value.push(hydrated)
    messages.value.sort((first, second) => Number(first.roomSequence || Infinity) - Number(second.roomSequence || Infinity))
    await scrollToLatest()
  }
}

function latestSequence() {
  return messages.value.reduce((max, message) => Math.max(max, Number(message.roomSequence || 0)), 0)
}

function messageTimelineOrder(roomSequence) {
  const sequence = Number(roomSequence)
  if (!Number.isSafeInteger(sequence) || sequence < 1) return LAST_TIMELINE_ORDER
  return Math.min(sequence * 2, LAST_TIMELINE_ORDER - 1)
}

function isMessageOnMySide(message) {
  if (message.notificationType === 'REINSPECTION_REQUESTED') return isSeller.value
  if (message.notificationType === 'REINSPECTION_COMPLETED') return !isSeller.value
  return Number(message.senderId) === Number(myMemberId.value)
}

function isAppointmentNotification(message) {
  const content = String(message.content || '')
  return message.type === 'SYSTEM'
    && (/^.+ 님이 실시간 검증 약속을 (설정|변경|취소)했습니다\.$/.test(content)
      || content.startsWith('검증 약속이 변경됐어요!')
      || content.startsWith('검증 약속이 등록·변경됐어요!'))
}

function appointmentNotificationText(message) {
  const content = String(message.content || '')
  if (content.startsWith('검증 약속이 변경됐어요!')
    || content.startsWith('검증 약속이 등록·변경됐어요!')) {
    return '실시간 검증 약속이 변경되었습니다.'
  }
  return content
}

function appointmentTimelineOrder() {
  const sequence = Number(appointmentAnchorSequence.value)
  if (!Number.isSafeInteger(sequence) || sequence < 0) return LAST_TIMELINE_ORDER - 2
  return Math.min(sequence * 2 + 1, LAST_TIMELINE_ORDER - 1)
}

function reanchorAppointment() {
  anchoredAppointmentId.value = null
}

function markRead() {
  const lastReadSeq = latestSequence()
  if (lastReadSeq > 0) chatSocket?.markRead(lastReadSeq)
}

async function recoverMissingMessages(roomId) {
  const afterSeq = latestSequence()
  if (afterSeq < 1) return
  const result = await getChatMessages(roomId, { afterSeq, size: 100 })
  for (const message of result?.content || []) await upsertMessage(message)
}

function connectSocket(roomId) {
  const version = ++connectionVersion
  chatSocket?.close()
  clearTimeout(reconnectTimer)
  socketStatus.value = 'connecting'
  chatSocket = createChatSocket({
    roomId,
    onOpen: async () => {
      if (version !== connectionVersion) return
      socketStatus.value = 'connected'
      messagesError.value = ''
      try {
        await recoverMissingMessages(roomId)
      } catch {
        messagesError.value = '재접속 중 누락 메시지를 확인하지 못했습니다.'
      }
      markRead()
      emit('room-updated')
    },
    onEvent: async (event) => {
      if (version !== connectionVersion) return
      if (event.type === 'MESSAGE' && event.message) {
        await upsertMessage(event.message)
        markRead()
        emit('room-updated')
      } else if (event.type === 'CALL_APPOINTMENT_UPDATED') {
        reanchorAppointment()
        await loadAppointments(roomId)
      } else if (event.type?.startsWith('REINSPECTION_') && event.message) {
        await upsertMessage({
          ...event.message,
          reinspection: event.reinspection,
          notificationType: event.type,
        })
        markRead()
        emit('room-updated')
      } else if (event.type === 'READ' && event.readerId !== myMemberId.value) {
        counterpartLastReadSeq.value = Math.max(
          counterpartLastReadSeq.value,
          Number(event.lastReadSeq || 0),
        )
      }
    },
    onAck: (ack) => {
      const index = messages.value.findIndex((message) => message.clientMessageId === ack.clientMessageId)
      if (index >= 0) {
        messages.value[index] = {
          ...messages.value[index],
          messageId: ack.messageId,
          roomSequence: ack.roomSequence,
          status: ack.status,
          sentAt: ack.sentAt,
          isPending: false,
        }
      }
    },
    onError: (error) => {
      if (version !== connectionVersion) return
      messagesError.value = error?.error?.message || '채팅 처리 중 오류가 발생했습니다.'
      socketStatus.value = 'error'
    },
    onClose: () => {
      if (version !== connectionVersion || socketStatus.value === 'closed') return
      socketStatus.value = 'disconnected'
      reconnectTimer = setTimeout(() => connectSocket(roomId), 1500)
    },
  })
}

async function loadProduct(listingId) {
  product.value = null
  try {
    product.value = await getProduct(listingId)
  } catch {
    product.value = null
  }
}

function evidenceTypeLabel(type) {
  return {
    PHOTO: '사진',
    VIDEO: '영상',
    DIAGNOSTIC_FILE: '진단파일',
    SELLER_CONFIRMATION: '확인',
  }[type] || type
}

function isCheckedChecklistItem(item) {
  return item.status === 'COMPLETED' || Boolean(item.latestEvidenceId)
}

// 이 방에서 이야기하는 상품의 체크리스트입니다. 항목 구성은 기기 모델마다 다르고,
// 상품 생성 시 고정된 스냅샷을 그대로 조회합니다.
async function loadChecklist(listingId) {
  checklistItems.value = []
  try {
    checklistItems.value = await getProductChecklist(listingId)
  } catch {
    checklistItems.value = []
  }
}

async function loadAppointments(roomId) {
  try {
    const result = await getMyRtcCalls()
    appointments.value = (result || [])
      .filter((appointment) => Number(appointment.chatRoomId) === Number(roomId))
      .sort((first, second) => Number(second.callId) - Number(first.callId))
    const appointmentId = appointments.value[0]?.callId ?? null
    if (appointmentId !== anchoredAppointmentId.value) {
      anchoredAppointmentId.value = appointmentId
      appointmentAnchorSequence.value = messages.value
        .filter(isAppointmentNotification)
        .reduce((max, message) => Math.max(max, Number(message.roomSequence || 0)), 0)
    }
  } catch (error) {
    callMessage.value = error.message || '통화 약속을 불러오지 못했습니다.'
  }
}

function isAppointmentExpired(appointment) {
  if (!appointment?.scheduledAt) return false
  return new Date(appointment.scheduledAt).getTime() + 30 * 60 * 1000 <= now.value
}

function appointmentRemainingTime(appointment) {
  if (!appointment?.scheduledAt) return null
  const scheduledAt = new Date(appointment.scheduledAt).getTime()
  if (now.value < scheduledAt) return null
  return remainingSessionTime(new Date(scheduledAt + 30 * 60 * 1000).toISOString())
}

function shouldDisplayAppointmentMemo(memo) {
  return Boolean(memo) && !memo.trim().endsWith('상태 실시간 확인 요청')
}

const latestAppointment = computed(
  () => appointments.value.find(
    (appointment) => ['PROPOSED', 'ACCEPTED'].includes(appointment.status)
      && !isAppointmentExpired(appointment),
  ) || null,
)
const checkedChecklistCount = computed(
  () => checklistItems.value.filter((item) => isCheckedChecklistItem(item)).length,
)

watch(
  () => props.room.roomId,
  async (roomId) => {
    counterpartLastReadSeq.value = Number(props.room.counterpartLastReadSequence || 0)
    anchoredAppointmentId.value = null
    appointmentAnchorSequence.value = 0
    await loadMessages(roomId)
    await loadAppointments(roomId)
    isChecklistOpen.value = false
    loadProduct(props.room.listingId)
    loadChecklist(props.room.listingId)
    connectSocket(roomId)
  },
  { immediate: true },
)

watch(
  () => props.room.counterpartLastReadSequence,
  (sequence) => {
    counterpartLastReadSeq.value = Math.max(counterpartLastReadSeq.value, Number(sequence || 0))
  },
)

const messageInput = ref('')

async function requestCallAppointment() {
  if (!callScheduledAt.value || isRequestingCall.value) return
  isRequestingCall.value = true
  callMessage.value = ''
  try {
    await requestRtcCall(props.room.roomId, {
      scheduledAt: `${callScheduledAt.value}:00`,
      memo: callMemo.value.trim() || null,
    })
    reanchorAppointment()
    await loadAppointments(props.room.roomId)
    callMessage.value = '통화 약속을 요청했습니다.'
    isCallFormOpen.value = false
    callScheduledAt.value = ''
    callMemo.value = ''
  } catch (error) {
    callMessage.value = error.code === 'RTC006'
      ? '이미 확정되었거나 응답을 기다리는 검증 일정이 있어요.'
      : error.message || '검증 일정을 요청하지 못했습니다.'
  } finally {
    isRequestingCall.value = false
  }
}

async function respondAppointment(accepted) {
  const appointment = latestAppointment.value
  if (!appointment || pendingAppointmentId.value) return
  pendingAppointmentId.value = appointment.callId
  callMessage.value = ''
  try {
    await respondRtcCall(appointment.callId, accepted, accepted ? null : '요청 거절')
    reanchorAppointment()
    await loadAppointments(props.room.roomId)
    callMessage.value = accepted ? '통화 약속을 수락했습니다.' : '통화 약속을 거절했습니다.'
  } catch (error) {
    callMessage.value = error.message || '통화 약속을 처리하지 못했습니다.'
  } finally {
    pendingAppointmentId.value = null
  }
}

function createClientMessageId() {
  if (crypto.randomUUID) return crypto.randomUUID()
  return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (digit) => (
    Number(digit) ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> Number(digit) / 4
  ).toString(16))
}

function sendMessage() {
  const text = messageInput.value.trim()
  if (!text || myMemberId.value == null || socketStatus.value !== 'connected') return
  const clientMessageId = createClientMessageId()
  messages.value.push({
    messageId: nextPendingMessageId--,
    roomSequence: null,
    senderId: myMemberId.value,
    clientMessageId,
    type: 'TEXT',
    content: text,
    status: 'SENDING',
    sentAt: new Date().toISOString(),
    isPending: true,
    media: [],
  })
  scrollToLatest()
  chatSocket?.sendMessage({ clientMessageId, type: 'TEXT', content: text, mediaIds: [] })
  messageInput.value = ''
}

async function selectMedia(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || socketStatus.value !== 'connected' || myMemberId.value == null) return
  isUploading.value = true
  messagesError.value = ''
  try {
    const media = await uploadChatMedia(props.room.roomId, file)
    const clientMessageId = createClientMessageId()
    const type = media.type
    const optimistic = await attachMediaUrls({
      messageId: nextPendingMessageId--,
      roomSequence: null,
      senderId: myMemberId.value,
      clientMessageId,
      type,
      content: file.name,
      status: 'SENDING',
      sentAt: new Date().toISOString(),
      isPending: true,
      media: [media],
    })
    messages.value.push(optimistic)
    scrollToLatest()
    chatSocket?.sendMessage({ clientMessageId, type, content: file.name, mediaIds: [media.mediaId] })
  } catch (error) {
    messagesError.value = error.message || '파일을 전송하지 못했습니다.'
  } finally {
    isUploading.value = false
  }
}

function openMediaPicker() {
  fileInput.value?.click()
}

function formatTime(isoString) {
  if (!isoString) return ''
  return new Date(isoString).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatAppointmentTime(isoString) {
  if (!isoString) return '시간 미정'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(isoString))
}

function remainingSessionTime(expiresAt) {
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

onMounted(() => {
  window.addEventListener('keydown', handleImageDialogKeydown)
})

onBeforeUnmount(() => {
  if (previousBodyOverflow !== null) {
    document.body.style.overflow = previousBodyOverflow
    previousBodyOverflow = null
  }
  connectionVersion += 1
  clearTimeout(reconnectTimer)
  clearInterval(countdownTimer)
  socketStatus.value = 'closed'
  chatSocket?.close()
  mediaObjectUrls.forEach((url) => URL.revokeObjectURL(url))
  window.removeEventListener('keydown', handleImageDialogKeydown)
})
</script>

<template>
  <div class="flex h-full min-w-0 min-h-[520px] flex-col bg-surface lg:min-h-0">
    <div class="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
      <div class="flex items-center gap-3">
        <RouterLink
          to="/chat"
          aria-label="채팅 목록으로"
          class="text-text-sub hover:text-primary"
        >
          <svg
            class="h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M15 19l-7-7 7-7"
            />
          </svg>
        </RouterLink>
        <p class="text-sm font-bold text-text-main">
          {{ room.counterpartNickname || `회원 #${room.counterpartId}` }}
        </p>
        <span
          class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
          :class="socketStatus === 'connected' ? 'bg-green-50 text-green-700' : 'bg-yellow-50 text-yellow-700'"
        >
          {{ socketStatus === 'connected' ? '실시간 연결됨' : '연결 확인 중' }}
        </span>
      </div>
    </div>

    <div
      v-if="product"
      class="flex items-center gap-3 border-b border-border px-5 py-3"
    >
      <img
        v-if="room.listingThumbnailUrl"
        :src="room.listingThumbnailUrl"
        :alt="room.listingTitle || product?.name || '상품 이미지'"
        class="h-10 w-10 shrink-0 rounded-md object-cover"
      >
      <div
        v-else
        class="h-10 w-10 shrink-0 rounded-md bg-primary-gradient"
      />
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-bold text-text-main">
          {{ room.listingTitle || product.name }}
        </p>
        <p class="truncate text-xs text-text-sub">
          ₩{{ Number(product.price).toLocaleString('ko-KR') }}
        </p>
      </div>
      <RouterLink
        :to="{ name: 'product-detail', params: { productId: room.listingId } }"
        class="whitespace-nowrap text-xs font-semibold text-primary hover:text-primary-dark"
      >
        상품 보기
      </RouterLink>
    </div>

    <!-- 이 상품의 검증 체크리스트. 기기 모델마다 항목이 달라 상품별 스냅샷을 그대로 보여줍니다.
         채팅 영역을 좁히지 않도록 접어두고, 펼쳐도 4개 정도만 보이고 나머지는 스크롤합니다. -->
    <div
      v-if="checklistItems.length"
      class="border-b border-border px-5 py-2"
    >
      <button
        type="button"
        class="flex w-full items-center justify-between gap-2 py-1 text-left"
        :aria-expanded="isChecklistOpen"
        @click="isChecklistOpen = !isChecklistOpen"
      >
        <span class="text-xs font-bold text-text-main">
          검증 체크리스트
          <span class="ml-1 font-semibold text-primary">
            {{ checkedChecklistCount }} / {{ checklistItems.length }}
          </span>
        </span>
        <span class="text-xs text-text-sub">{{ isChecklistOpen ? '접기' : '펼치기' }}</span>
      </button>

      <ul
        v-if="isChecklistOpen"
        class="mt-1 max-h-40 space-y-1.5 overflow-y-auto pr-1 pb-1"
      >
        <li
          v-for="item in checklistItems"
          :key="item.checklistItemId"
          class="flex items-start gap-2 rounded-md bg-bg px-2.5 py-1.5"
        >
          <span
            class="mt-0.5 shrink-0 text-xs font-bold"
            :class="isCheckedChecklistItem(item) ? 'text-primary' : 'text-text-sub'"
            aria-hidden="true"
          >{{ isCheckedChecklistItem(item) ? '✓' : '·' }}</span>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-xs font-semibold text-text-main">
              {{ item.name }}<span
                v-if="item.required ?? item.isRequired"
                class="ml-1 text-red-500"
              >*</span>
            </span>
            <span class="mt-0.5 block text-[11px] text-text-sub">
              {{ evidenceTypeLabel(item.evidenceType) }}
              · {{ isCheckedChecklistItem(item) ? '자료 확인' : '미등록' }}
            </span>
          </span>
        </li>
      </ul>
    </div>

    <div
      ref="messageList"
      class="flex-1 space-y-3 overflow-y-auto px-5 py-5"
    >
      <p
        v-if="isLoadingMessages"
        class="py-10 text-center text-sm text-text-sub"
      >
        불러오는 중...
      </p>
      <p
        v-else-if="messagesError"
        role="alert"
        class="py-10 text-center text-sm text-red-600"
      >
        {{ messagesError }}
      </p>
      <p
        v-else-if="!messages.length && !latestAppointment"
        class="py-10 text-center text-sm text-text-sub"
      >
        아직 주고받은 메시지가 없습니다.
      </p>

      <TransitionGroup
        v-if="messages.length || latestAppointment"
        name="msg"
        tag="div"
        class="flex flex-col gap-3"
      >
        <div
          v-for="message in messages"
          :key="message.messageId"
          :data-message-sequence="message.roomSequence"
          :data-testid="message.isPending ? 'pending-message' : undefined"
          class="flex flex-col"
          :class="isAppointmentNotification(message)
            ? 'w-full items-center'
            : isMessageOnMySide(message) ? 'items-end' : 'items-start'"
          :style="{ order: messageTimelineOrder(message.roomSequence) }"
        >
          <div
            class="max-w-[75%] rounded-lg px-4 py-2.5 text-sm leading-6"
            :class="message.type === 'SYSTEM' && (message.reinspection || isAppointmentNotification(message))
              ? isAppointmentNotification(message)
                ? 'w-full max-w-none bg-transparent p-0 text-text-main'
                : 'bg-transparent p-0 text-text-main'
              : message.senderId === myMemberId
                ? 'bg-primary-deep text-white'
                : 'bg-bg text-text-main'"
          >
            <template v-if="message.type === 'SYSTEM' && message.reinspection">
              <div
                data-testid="system-notification-card"
                class="min-w-[280px] rounded-xl border border-primary/25 p-4 text-text-main shadow-sm sm:min-w-[360px]"
              >
                <p class="mb-2 text-xs font-bold text-primary">
                  재검수 요청
                </p>
                <p class="font-bold text-text-main">
                  {{ message.notificationType === 'REINSPECTION_COMPLETED'
                    ? '재검수를 완료했어요!'
                    : '재검수 요청이 들어왔어요!' }}
                </p>
                <details
                  v-if="message.notificationType === 'REINSPECTION_REQUESTED'"
                  class="group mt-3"
                  open
                >
                  <summary class="cursor-pointer list-none text-sm font-semibold text-primary">
                    <span class="group-open:hidden">펼쳐보기</span>
                    <span class="hidden group-open:inline">접기</span>
                  </summary>
                  <div class="mt-3 border-t border-border pt-3">
                    <div class="rounded-xl border border-primary/15 bg-accent/50 px-3.5 py-3">
                      <p class="text-xs font-bold text-primary-dark">
                        요청 내용
                      </p>
                      <p class="mt-1 text-sm leading-5 text-text-main">
                        {{ message.reinspection.reason || '별도 요청 내용이 없습니다.' }}
                      </p>
                    </div>
                    <p class="mt-4 text-xs font-bold text-text-sub">
                      선택한 체크리스트
                    </p>
                    <ul class="mt-2 grid gap-2 sm:grid-cols-2">
                      <li
                        v-for="item in message.reinspection.items || []"
                        :key="`${message.messageId}-${item.name}`"
                        class="flex min-w-0 items-center gap-2.5 rounded-lg border border-primary/15 bg-white px-3 py-2.5"
                      >
                        <span
                          aria-hidden="true"
                          class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-white"
                        >
                          ✓
                        </span>
                        <span class="truncate text-sm font-semibold text-text-main">
                          {{ item.name }}
                        </span>
                      </li>
                    </ul>
                    <RouterLink
                      v-if="isSeller && message.reinspection.requestKey && message.notificationType === 'REINSPECTION_REQUESTED'"
                      :to="{
                        name: 'seller-product-edit',
                        params: { productId: message.reinspection.listingId || room.listingId },
                        query: { reinspectionRequestKey: message.reinspection.requestKey },
                      }"
                      class="mt-4 block rounded-lg bg-primary-gradient px-4 py-2.5 text-center text-sm font-bold text-white"
                    >
                      바로 재촬영하기
                    </RouterLink>
                  </div>
                </details>
                <RouterLink
                  v-else
                  :to="{
                    name: 'product-detail',
                    params: { productId: message.reinspection.listingId || room.listingId },
                  }"
                  class="mt-4 block rounded-lg bg-primary-gradient px-4 py-2.5 text-center text-sm font-bold text-white"
                >
                  확인하러 가기
                </RouterLink>
              </div>
            </template>
            <template
              v-else-if="isAppointmentNotification(message)"
            >
              <div
                data-testid="appointment-notification-divider"
                class="flex w-full items-center gap-3 py-1 text-xs font-medium text-text-sub"
              >
                <span class="h-px flex-1 bg-border" />
                <p class="max-w-[72%] text-center leading-5">
                  {{ appointmentNotificationText(message) }}
                </p>
                <span class="h-px flex-1 bg-border" />
              </div>
            </template>
            <template v-else-if="message.type === 'SYSTEM'">
              {{ message.content }}
            </template>
            <template v-else-if="message.type === 'TEXT'">
              {{ message.content }}
            </template>
            <template v-else>
              <button
                v-if="message.type === 'IMAGE' && message.media?.[0]?.displayUrl"
                type="button"
                class="group relative block cursor-zoom-in overflow-hidden rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
                :aria-label="`${message.content || '채팅 이미지'} 확대 보기`"
                @click="openImage({
                  src: message.media[0].displayUrl,
                  alt: message.content || '채팅 이미지',
                })"
              >
                <img
                  :src="message.media[0].displayUrl"
                  :alt="message.content || '채팅 이미지'"
                  class="max-h-72 object-contain transition-transform duration-200 group-hover:scale-[1.02]"
                  @load="scrollToLatest"
                >
                <span class="pointer-events-none absolute bottom-2 right-2 rounded-full bg-black/60 px-2 py-1 text-[11px] font-semibold text-white">
                  확대
                </span>
              </button>
              <video
                v-else-if="message.type === 'VIDEO' && message.media?.[0]?.displayUrl"
                :src="message.media[0].displayUrl"
                controls
                preload="metadata"
                class="max-h-72 rounded-md"
                @loadedmetadata="scrollToLatest"
              />
              <span v-else>{{ message.content || '미디어 파일' }}</span>
            </template>
          </div>
          <span
            v-if="!isAppointmentNotification(message)"
            class="mt-1 text-[11px] text-text-sub"
          >
            {{ formatTime(message.sentAt) }}
            <template v-if="message.isPending"> · 전송 중</template>
            <template
              v-else-if="
                message.senderId === myMemberId
                  && message.roomSequence
                  && Number(message.roomSequence) <= counterpartLastReadSeq
              "
            >
              · 읽음
            </template>
          </span>
        </div>

        <div
          v-if="latestAppointment"
          :key="`appointment-${latestAppointment.callId}`"
          data-testid="appointment-card"
          class="mx-auto w-full max-w-sm rounded-xl bg-white p-4 shadow-sm"
          :class="latestAppointment.status === 'ACCEPTED' ? 'border-2 border-primary' : 'border border-primary'"
          :style="{ order: appointmentTimelineOrder() }"
        >
          <div class="flex items-center gap-2">
            <svg
              class="h-5 w-5 shrink-0 text-primary"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <rect
                x="3"
                y="5"
                width="18"
                height="16"
                rx="2"
              />
              <path d="M16 3v4M8 3v4M3 10h18" />
              <path
                v-if="latestAppointment.status === 'ACCEPTED'"
                d="m8 15 2.5 2.5L16 12"
              />
            </svg>
            <p class="font-bold text-text-main">
              {{ latestAppointment.status === 'ACCEPTED' ? '실시간 화상 검증 일정 확정' : '실시간 화상 검증 일정 제안' }}
            </p>
          </div>
          <div class="mt-3 rounded-lg bg-bg px-3 py-3">
            <p class="text-xs text-text-sub">
              {{ latestAppointment.status === 'ACCEPTED' ? '확정된 일정' : '제안 시간' }}
            </p>
            <p class="mt-1 text-sm font-bold text-text-main">
              {{ formatAppointmentTime(latestAppointment.scheduledAt) }}
            </p>
            <p
              v-if="appointmentRemainingTime(latestAppointment)"
              data-testid="appointment-expiration"
              class="mt-2 inline-flex rounded-full bg-accent px-2.5 py-1 text-xs font-bold text-primary-dark"
            >
              약속 만료까지 {{ appointmentRemainingTime(latestAppointment) }}
            </p>
            <p
              v-if="shouldDisplayAppointmentMemo(latestAppointment.memo)"
              class="mt-1 text-xs text-text-sub"
            >
              {{ latestAppointment.memo }}
            </p>
          </div>
          <div
            v-if="latestAppointment.status === 'PROPOSED' && latestAppointment.incoming"
            class="mt-3 grid grid-cols-2 gap-3"
          >
            <button
              type="button"
              :disabled="pendingAppointmentId === latestAppointment.callId"
              class="rounded-lg border border-border px-4 py-2.5 text-sm font-semibold text-text-sub disabled:opacity-50"
              @click="respondAppointment(false)"
            >
              거절
            </button>
            <button
              type="button"
              :disabled="pendingAppointmentId === latestAppointment.callId"
              class="rounded-lg bg-primary-gradient px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
              @click="respondAppointment(true)"
            >
              수락하기
            </button>
          </div>
          <p
            v-else-if="latestAppointment.status === 'PROPOSED'"
            class="mt-3 text-center text-xs font-semibold text-primary"
          >
            상대방의 응답을 기다리고 있습니다.
          </p>
          <RouterLink
            v-else
            :to="{ name: 'rtc-call', params: { callId: latestAppointment.callId } }"
            class="mt-3 block rounded-lg bg-primary-gradient px-4 py-2.5 text-center text-sm font-semibold text-white"
          >
            실시간 검증 입장하기
          </RouterLink>
        </div>
      </TransitionGroup>
    </div>

    <div class="border-t border-border p-4">
      <div class="mb-3">
        <div class="flex flex-wrap items-center gap-3">
          <button
            type="button"
            class="inline-flex items-center gap-2 rounded-lg border border-primary px-3 py-2 text-sm font-semibold text-primary hover:bg-accent"
            @click="isCallFormOpen = !isCallFormOpen"
          >
            <svg
              class="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <rect
                x="3"
                y="5"
                width="18"
                height="16"
                rx="2"
              />
              <path d="M16 3v4M8 3v4M3 10h18" />
            </svg>
            실시간 검증 일정 잡기
          </button>
          <p class="text-xs text-text-sub">
            * 상호 조율 하에 라이브 WebRTC 성능 테스트 시간대를 제안해보세요.
          </p>
        </div>

        <form
          v-if="isCallFormOpen"
          class="mt-3 grid gap-3 rounded-xl border border-primary bg-white p-4 sm:grid-cols-2"
          @submit.prevent="requestCallAppointment"
        >
          <label class="grid gap-1 text-xs font-semibold text-text-main">
            제안 시간
            <input
              v-model="callScheduledAt"
              type="datetime-local"
              required
              class="rounded-md border border-border bg-surface px-3 py-2 text-sm font-normal"
            >
          </label>
          <label class="grid gap-1 text-xs font-semibold text-text-main">
            메모
            <input
              v-model="callMemo"
              type="text"
              maxlength="500"
              placeholder="확인할 내용을 입력하세요."
              class="rounded-md border border-border bg-surface px-3 py-2 text-sm font-normal"
            >
          </label>
          <div class="flex justify-end gap-2 sm:col-span-2">
            <button
              type="button"
              class="rounded-md border border-border px-4 py-2 text-sm font-semibold text-text-sub"
              @click="isCallFormOpen = false"
            >
              닫기
            </button>
            <button
              type="submit"
              :disabled="isRequestingCall"
              class="rounded-md bg-primary-gradient px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            >
              {{ isRequestingCall ? '요청 중…' : '일정 제안하기' }}
            </button>
          </div>
        </form>
        <p
          v-if="callMessage"
          role="status"
          class="mt-2 text-xs text-text-sub"
        >
          {{ callMessage }}
        </p>
      </div>
      <form
        class="flex gap-2"
        @submit.prevent="sendMessage"
      >
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime"
          class="hidden"
          @change="selectMedia"
        >
        <button
          type="button"
          :disabled="socketStatus !== 'connected' || isUploading"
          :aria-label="isUploading ? '사진 또는 영상 업로드 중' : '사진 또는 영상 첨부'"
          :title="isUploading ? '업로드 중' : '사진 또는 영상 첨부'"
          class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-md border border-border text-text-sub transition-colors hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
          @click="openMediaPicker"
        >
          <svg
            class="h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
          >
            <rect
              x="3"
              y="5"
              width="18"
              height="14"
              rx="2"
            />
            <circle
              cx="8.5"
              cy="10"
              r="1.5"
            />
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m5 17 4.5-4.5 3 3 2-2L19 17"
            />
          </svg>
        </button>
        <input
          v-model="messageInput"
          type="text"
          placeholder="메시지를 입력하세요..."
          class="w-full min-w-0 rounded-md border border-border bg-surface px-4 py-2.5 text-sm text-text-main outline-none focus:border-primary"
        >
        <button
          type="submit"
          :disabled="socketStatus !== 'connected'"
          class="whitespace-nowrap rounded-md bg-primary-gradient px-4 py-2.5 text-sm font-semibold text-white transition-all hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:brightness-100"
        >
          전송
        </button>
      </form>
    </div>

    <div
      v-if="expandedImage"
      role="dialog"
      aria-modal="true"
      aria-label="채팅 이미지 확대 보기"
      class="fixed inset-0 z-[100] flex items-center justify-center bg-black/85 p-4 sm:p-8"
      @click.self="closeImage"
    >
      <button
        type="button"
        class="absolute right-4 top-4 flex h-11 w-11 items-center justify-center rounded-full bg-white/15 text-2xl text-white transition hover:bg-white/25 focus:outline-none focus-visible:ring-2 focus-visible:ring-white"
        aria-label="확대 이미지 닫기"
        @click="closeImage"
      >
        ×
      </button>
      <img
        :src="expandedImage.src"
        :alt="expandedImage.alt"
        class="max-h-full max-w-full rounded-lg object-contain shadow-2xl"
      >
    </div>
  </div>
</template>

<style scoped>
.msg-enter-active {
  transition: all 0.25s ease;
}

.msg-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
</style>
