<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import { getChatMessages, getChatRooms } from '../api/chat'
import { createChatSocket } from '../api/chatSocket'
import {
  endRtcSession,
  getRtcCall,
  getRtcSession,
  issueRtcJoinToken,
  markRtcConnected,
  respondRtcCall,
  signalingSocketUrl,
} from '../api/rtc'
// 재촬영 요청을 되살릴 때 함께 풉니다.
// import { createReinspectionRequest } from '../api/products'
import { useAuthSession } from '../auth/session'

const route = useRoute()
const router = useRouter()
const authSession = useAuthSession()
const call = ref(null)
const rtcSession = ref(null)
const sellerVideo = ref(null)
const status = ref('loading')
const errorMessage = ref('')
const requestMessage = ref('')
const messages = ref([])
const chatMessagesContainer = ref(null)
const chatRoom = ref(null)
const messageInput = ref('')
const chatStatus = ref('connecting')
const chatError = ref('')
const elapsedSeconds = ref(0)
let signalingSocket
let chatSocket
let peer
let localStream
let remoteStream
let joinInfo
let connectedRecorded = false
let reconnectTimer
let elapsedTimer
let nextPendingMessageId = -1
let pendingSignals = []
const MAX_PENDING_SIGNALS = 50

const myMemberId = computed(() => authSession.value?.member?.memberId ?? null)
const visibleMessages = computed(() => messages.value.filter((message) => message.type !== 'SYSTEM'))
const isSeller = computed(() => Number(rtcSession.value?.sellerId) === Number(myMemberId.value))
const counterpartId = computed(() => (
  isSeller.value ? rtcSession.value?.buyerId : rtcSession.value?.sellerId
))
const counterpartName = computed(() => {
  return chatRoom.value?.counterpartNickname || `회원 #${counterpartId.value}`
})
const counterpartInitial = computed(() => counterpartName.value.trim().charAt(0) || '회')
const connectionLabel = computed(() => {
  if (status.value === 'connected') return '연결 양호 - HD 1080p'
  if (status.value === 'reconnecting') return '재연결 중'
  if (status.value === 'waiting-peer') return '상대방 접속 대기 중'
  return '연결 확인 중'
})
const formattedElapsed = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60).toString().padStart(2, '0')
  const seconds = (elapsedSeconds.value % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
})

function sendSignal(type, payload = {}) {
  const message = JSON.stringify({ type, payload })
  if (signalingSocket?.readyState === WebSocket.OPEN) {
    signalingSocket.send(message)
    return
  }
  if (pendingSignals.length >= MAX_PENDING_SIGNALS) pendingSignals.shift()
  pendingSignals.push(message)
}

function flushPendingSignals() {
  if (signalingSocket?.readyState !== WebSocket.OPEN) return
  pendingSignals.forEach((message) => signalingSocket.send(message))
  pendingSignals = []
}

async function attachSellerStream() {
  await nextTick()
  if (!sellerVideo.value || !('srcObject' in sellerVideo.value)) return
  sellerVideo.value.srcObject = isSeller.value ? localStream : remoteStream
}

async function createOffer(iceRestart = false) {
  const offer = await peer.createOffer({ iceRestart })
  await peer.setLocalDescription(offer)
  sendSignal('offer', offer)
}

async function handleSignal(event) {
  let message
  try {
    message = JSON.parse(event.data)
  } catch {
    return
  }
  const peerSignalTypes = ['peer-ready', 'offer', 'answer', 'ice-candidate', 'ice-restart']
  if (peerSignalTypes.includes(message.type) && !peer) return
  try {
    if (message.type === 'peer-ready' && joinInfo?.offerer) await createOffer()
    if (message.type === 'offer') {
      await peer.setRemoteDescription(message.payload)
      const answer = await peer.createAnswer()
      await peer.setLocalDescription(answer)
      sendSignal('answer', answer)
    }
    if (message.type === 'answer') await peer.setRemoteDescription(message.payload)
    if (message.type === 'ice-candidate' && message.payload?.candidate) {
      await peer.addIceCandidate(message.payload)
    }
    if (message.type === 'ice-restart' && joinInfo?.offerer) await createOffer(true)
    if (message.type === 'inspection-request') {
      requestMessage.value = message.payload?.instruction || ''
    }
    if (message.type === 'peer-left') status.value = 'reconnecting'
    if (message.type === 'hangup') status.value = 'peer-ended'
  } catch (error) {
    showError(error)
  }
}

function cleanupRtc() {
  if (signalingSocket) {
    signalingSocket.onopen = null
    signalingSocket.onmessage = null
    signalingSocket.onerror = null
    signalingSocket.onclose = null
    signalingSocket.close()
  }
  if (peer) {
    peer.ontrack = null
    peer.onicecandidate = null
    peer.onconnectionstatechange = null
    peer.close()
  }
  localStream?.getTracks().forEach((track) => track.stop())
  remoteStream?.getTracks().forEach((track) => track.stop())
  signalingSocket = null
  peer = null
  localStream = null
  remoteStream = null
  pendingSignals = []
}

async function connectRtc() {
  cleanupRtc()
  status.value = 'media-request'
  errorMessage.value = ''
  try {
    joinInfo = await issueRtcJoinToken(rtcSession.value.sessionId)
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    peer = new RTCPeerConnection({
      iceServers: joinInfo.iceServers.map((server) => ({
        urls: server.urls,
        username: server.username || undefined,
        credential: server.credential || undefined,
      })),
    })
    localStream.getTracks().forEach((track) => peer.addTrack(track, localStream))
    peer.ontrack = async (event) => {
      remoteStream = event.streams[0]
      await attachSellerStream()
    }
    peer.onicecandidate = (event) => {
      if (event.candidate) sendSignal('ice-candidate', event.candidate)
    }
    peer.onconnectionstatechange = async () => {
      status.value = peer.connectionState
      if (peer.connectionState === 'connected' && !connectedRecorded) {
        connectedRecorded = true
        try {
          await markRtcConnected(rtcSession.value.sessionId, 'P2P')
        } catch (error) {
          connectedRecorded = false
          showError(error)
        }
      }
      if (peer.connectionState === 'failed') {
        status.value = 'reconnecting'
        try {
          peer.restartIce()
          sendSignal('ice-restart')
          if (joinInfo?.offerer) await createOffer(true)
        } catch (error) {
          status.value = 'connection-failed'
          showError(error)
        }
      }
    }
    await attachSellerStream()
    signalingSocket = new WebSocket(signalingSocketUrl(joinInfo))
    signalingSocket.onopen = () => {
      status.value = 'waiting-peer'
      flushPendingSignals()
    }
    signalingSocket.onmessage = (event) => { handleSignal(event).catch(showError) }
    signalingSocket.onerror = () => { status.value = 'connection-failed' }
    signalingSocket.onclose = () => {
      if (!['ended', 'peer-ended'].includes(status.value)) status.value = 'reconnecting'
    }
  } catch (error) {
    showError(error)
    status.value = 'connection-failed'
  }
}

function latestSequence() {
  return messages.value.reduce(
    (max, message) => Math.max(max, Number(message.roomSequence || 0)),
    0,
  )
}

async function scrollChatToLatest() {
  await nextTick()
  const container = chatMessagesContainer.value
  if (!container) return
  container.scrollTop = container.scrollHeight
}

async function upsertMessage(message) {
  const clientIndex = messages.value.findIndex(
    (item) => item.clientMessageId === message.clientMessageId,
  )
  if (clientIndex >= 0) {
    messages.value[clientIndex] = { ...messages.value[clientIndex], ...message, isPending: false }
    if (message.type !== 'SYSTEM') await scrollChatToLatest()
    return
  }
  if (!messages.value.some((item) => item.messageId === message.messageId)) {
    messages.value.push(message)
    messages.value.sort(
      (first, second) => Number(first.roomSequence || Infinity) - Number(second.roomSequence || Infinity),
    )
    if (message.type !== 'SYSTEM') await scrollChatToLatest()
  }
}

async function loadMessages() {
  try {
    const [result, roomResult] = await Promise.all([
      getChatMessages(call.value.chatRoomId, { size: 50 }),
      getChatRooms({ size: 100 }).catch(() => null),
    ])
    messages.value = (result?.content || []).slice().reverse()
    chatRoom.value = (roomResult?.content || []).find(
      (room) => Number(room.roomId) === Number(call.value.chatRoomId),
    ) || null
    await scrollChatToLatest()
  } catch (error) {
    chatError.value = error.message || '채팅 내역을 불러오지 못했습니다.'
  }
}

function connectChat() {
  chatSocket?.close()
  clearTimeout(reconnectTimer)
  chatStatus.value = 'connecting'
  chatSocket = createChatSocket({
    roomId: call.value.chatRoomId,
    onOpen: () => {
      chatStatus.value = 'connected'
      chatError.value = ''
      const lastReadSeq = latestSequence()
      if (lastReadSeq > 0) chatSocket?.markRead(lastReadSeq)
    },
    onEvent: async (event) => {
      if (event.type !== 'MESSAGE' || !event.message) return
      await upsertMessage(event.message)
      const lastReadSeq = latestSequence()
      if (lastReadSeq > 0) chatSocket?.markRead(lastReadSeq)
    },
    onAck: (ack) => { upsertMessage(ack) },
    onError: (error) => {
      chatError.value = error?.error?.message || '채팅 처리 중 오류가 발생했습니다.'
      chatStatus.value = 'error'
    },
    onClose: () => {
      if (chatStatus.value === 'closed') return
      chatStatus.value = 'disconnected'
      reconnectTimer = setTimeout(connectChat, 1500)
    },
  })
}

function createClientMessageId() {
  if (crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function sendMessage() {
  const content = messageInput.value.trim()
  if (!content || chatStatus.value !== 'connected' || myMemberId.value == null) return
  const clientMessageId = createClientMessageId()
  const optimisticMessage = {
    messageId: nextPendingMessageId--,
    roomSequence: null,
    senderId: myMemberId.value,
    clientMessageId,
    type: 'TEXT',
    content,
    sentAt: new Date().toISOString(),
    isPending: true,
  }
  messages.value.push(optimisticMessage)
  await scrollChatToLatest()
  try {
    if (!chatSocket || typeof chatSocket.sendMessage !== 'function') {
      throw new Error('채팅 연결이 없습니다.')
    }
    await chatSocket.sendMessage({ clientMessageId, type: 'TEXT', content, mediaIds: [] })
    messageInput.value = ''
  } catch (error) {
    messages.value = messages.value.filter(
      (message) => message.clientMessageId !== clientMessageId,
    )
    chatError.value = error?.message || '메시지를 전송하지 못했습니다. 연결 상태를 확인해 주세요.'
  }
}

function formatTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function goToChat() {
  router.push({ name: 'chat', params: { roomId: call.value.chatRoomId } })
}

// 통화 중 재촬영 요청은 잠시 내려 두었습니다. 이미 얼굴을 보고 이야기하는 중이라 채팅으로
// 말하는 편이 빠르고, 같은 화면에 요청 폼까지 두면 체크리스트를 읽기 어려워집니다.
// 되살릴 때는 아래 주석과 템플릿의 대응 블록을 함께 풀고, api/products의
// createReinspectionRequest import를 다시 추가하세요.
//
// const recaptureItemIds = ref([])
// const recaptureReason = ref('')
// const recaptureError = ref('')
// const recaptureNotice = ref('')
// const isSubmittingRecapture = ref(false)
// const canRequestRecapture = computed(
//   () => !isSeller.value && Boolean(rtcSession.value?.listingId),
// )
//
// function toggleRecaptureItem(checklistItemId) {
//   const index = recaptureItemIds.value.indexOf(checklistItemId)
//   if (index === -1) recaptureItemIds.value.push(checklistItemId)
//   else recaptureItemIds.value.splice(index, 1)
// }
//
// async function submitRecaptureRequest() {
//   recaptureError.value = ''
//   recaptureNotice.value = ''
//   if (!recaptureItemIds.value.length) {
//     recaptureError.value = '재촬영을 요청할 항목을 하나 이상 선택해 주세요.'
//     return
//   }
//   const reason = recaptureReason.value.trim()
//   if (!reason) {
//     recaptureError.value = '어떤 부분을 다시 보고 싶은지 적어 주세요.'
//     return
//   }
//   isSubmittingRecapture.value = true
//   try {
//     await createReinspectionRequest(rtcSession.value.listingId, {
//       reason,
//       items: recaptureItemIds.value.map((checklistItemId) => ({
//         checklistItemId,
//         requestContent: reason,
//       })),
//     })
//     recaptureNotice.value = '재촬영 요청을 보냈습니다. 판매자가 새 자료를 올리면 알려드립니다.'
//     recaptureItemIds.value = []
//     recaptureReason.value = ''
//   } catch (error) {
//     recaptureError.value = error.message || '재촬영 요청을 보내지 못했습니다.'
//   } finally {
//     isSubmittingRecapture.value = false
//   }
// }

function showError(error) {
  errorMessage.value = error.message || '통화 연결 중 오류가 발생했습니다.'
}

async function finish() {
  if (!rtcSession.value) return
  try {
    rtcSession.value = await endRtcSession(rtcSession.value.sessionId, {
      endReason: 'COMPLETED',
      memo: null,
      checklistResults: rtcSession.value.checklistItems.map((item) => ({
        checklistItemId: item.checklistItemId,
        confirmed: item.confirmed,
        note: item.note || null,
      })),
    })
    sendSignal('hangup')
    status.value = 'ended'
    cleanupRtc()
  } catch (error) {
    showError(error)
  }
}

async function load() {
  try {
    call.value = await getRtcCall(route.params.callId)
    if (call.value.status === 'PROPOSED' && call.value.incoming) {
      call.value = await respondRtcCall(call.value.callId, true)
    }
    if (!call.value.rtcSessionId) throw new Error('상대방의 수락을 기다리고 있습니다.')
    rtcSession.value = await getRtcSession(call.value.rtcSessionId)
    await loadMessages()
    connectChat()
    if (rtcSession.value.status === 'ENDED') status.value = 'ended'
    else await connectRtc()
  } catch (error) {
    showError(error)
    status.value = 'connection-failed'
  }
}

onMounted(() => {
  elapsedTimer = setInterval(() => { elapsedSeconds.value += 1 }, 1000)
  load()
})

onBeforeUnmount(() => {
  cleanupRtc()
  clearInterval(elapsedTimer)
  clearTimeout(reconnectTimer)
  chatStatus.value = 'closed'
  chatSocket?.close()
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto w-full max-w-[1440px] px-4 py-4 sm:px-6 sm:py-6">
      <p
        v-if="errorMessage && !rtcSession"
        role="alert"
        class="rounded-lg bg-red-50 p-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>

      <div
        v-if="rtcSession"
        class="grid items-start gap-6 xl:grid-cols-[minmax(0,2.15fr)_minmax(320px,1fr)]"
      >
        <section class="min-w-0">
          <div class="mb-5 flex min-h-11 items-center">
            <p
              v-if="errorMessage"
              role="alert"
              class="w-full rounded-lg bg-red-50 p-3 text-sm text-red-700"
            >
              {{ errorMessage }}
            </p>
            <div
              v-else
              data-testid="counterpart-profile"
              class="flex items-center gap-3"
            >
              <span class="flex h-11 w-11 items-center justify-center rounded-full bg-accent text-sm font-bold text-primary">
                {{ counterpartInitial }}
              </span>
              <p
                data-testid="counterpart-nickname"
                class="font-bold text-text-main"
              >
                {{ counterpartName }}
              </p>
            </div>
          </div>
          <div class="relative aspect-video overflow-hidden rounded-xl bg-slate-950">
            <video
              ref="sellerVideo"
              autoplay
              :muted="isSeller"
              playsinline
              class="h-full w-full object-cover"
            />
            <div class="absolute left-4 top-4 flex items-center gap-2">
              <span class="rounded-md bg-black/60 px-3 py-2 text-xs font-bold text-white">
                판매자 라이브 화면
              </span>
              <span class="rounded-md bg-primary px-3 py-2 text-xs font-bold text-white">
                {{ formattedElapsed }}
              </span>
            </div>
            <div class="absolute bottom-4 left-4 flex rounded-full bg-black/50 p-2 text-white">
              <svg
                class="h-5 w-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M11 5 6 9H2v6h4l5 4V5Z" />
                <path d="M15.5 8.5a5 5 0 0 1 0 7" />
                <path d="M18 6a9 9 0 0 1 0 12" />
              </svg>
            </div>
          </div>
          <div class="mt-4 rounded-lg border border-primary/30 bg-accent/60 px-4 py-3 text-sm text-primary">
            안내: 체크리스트를 참고해 상품 상태를 확인하고, 세부 요청은 오른쪽 채팅으로 전달해 주세요.
          </div>
        </section>

        <aside class="flex min-w-0 flex-col gap-5">
          <div class="flex min-h-11 flex-wrap items-center justify-end gap-4">
            <div class="flex items-center gap-2 text-sm font-semibold text-text-main">
              <span
                class="h-2 w-2 rounded-full"
                :class="status === 'connected' ? 'bg-emerald-500' : 'bg-amber-400'"
              />
              {{ connectionLabel }}
            </div>
            <button
              v-if="!['ended', 'peer-ended'].includes(status)"
              type="button"
              class="rounded-lg bg-red-50 px-5 py-2.5 text-sm font-bold text-red-500 hover:bg-red-100"
              @click="finish"
            >
              세션 종료
            </button>
            <button
              v-else
              type="button"
              class="rounded-lg border border-border px-5 py-2.5 text-sm font-bold text-text-sub"
              @click="goToChat"
            >
              채팅으로 돌아가기
            </button>
          </div>
          <section class="min-w-0 rounded-xl border border-border bg-white p-5">
            <h2 class="text-lg font-bold text-text-main">
              상품 검증 체크리스트
            </h2>
            <p class="mt-1 text-sm text-text-sub">
              판매글에 등록된 검증 항목과 같은 목록입니다. 더 보고 싶은 부분은 아래 채팅으로 말씀하세요.
            </p>
            <ul class="mt-4 max-h-[320px] space-y-3 overflow-y-auto pr-1">
              <li
                v-for="item in rtcSession.checklistItems"
                :key="item.checklistItemId"
                class="flex gap-3 rounded-lg border border-border px-4 py-3 text-sm text-text-sub"
              >
                <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
                <span>
                  <strong class="block font-semibold text-text-main">{{ item.name }}</strong>
                  <small
                    v-if="item.captureGuide"
                    class="mt-1 block leading-5 text-text-sub"
                  >{{ item.captureGuide }}</small>
                </span>
              </li>
              <li
                v-if="!rtcSession.checklistItems.length"
                class="text-sm text-text-sub"
              >
                등록된 체크리스트가 없습니다.
              </li>
            </ul>

            <!--
              재촬영 요청은 잠시 내려 두었습니다(채팅으로 말하는 편이 빠릅니다).
              되살릴 때는 script의 recapture 주석과 이 블록을 함께 풀고, 위 목록의 항목을
              체크박스로 되돌리세요(항목마다 toggleRecaptureItem 연결).

            <div
              v-if="canRequestRecapture && rtcSession.checklistItems.length"
              class="mt-4 border-t border-border pt-4"
            >
              <label class="block text-sm font-semibold text-text-main">
                재촬영 요청 내용
                <textarea
                  v-model="recaptureReason"
                  rows="3"
                  maxlength="500"
                  placeholder="예) 화면 하단 왼쪽이 잘 안 보여서 조금 더 가까이 보여 주실 수 있을까요?"
                  class="mt-2 w-full rounded-md border border-border px-3 py-2.5 text-sm font-normal outline-none focus:border-primary"
                />
              </label>
              <p
                v-if="recaptureError"
                role="alert"
                class="mt-2 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700"
              >
                {{ recaptureError }}
              </p>
              <p
                v-if="recaptureNotice"
                role="status"
                class="mt-2 rounded-md bg-accent px-3 py-2 text-xs text-primary-dark"
              >
                {{ recaptureNotice }}
              </p>
              <button
                type="button"
                class="mt-3 w-full rounded-lg border border-primary px-4 py-2.5 text-sm font-bold text-primary disabled:opacity-60"
                :disabled="isSubmittingRecapture"
                @click="submitRecaptureRequest"
              >
                재촬영 요청 보내기
              </button>
            </div>
            -->
          </section>

          <section class="flex h-[330px] flex-col overflow-hidden rounded-xl border border-border bg-white sm:h-[350px]">
            <div class="border-b border-border px-5 py-4">
              <h2 class="font-bold text-text-main">
                실시간 1:1 채팅
              </h2>
            </div>
            <div
              ref="chatMessagesContainer"
              data-testid="rtc-chat-messages"
              class="flex-1 space-y-3 overflow-y-auto px-5 py-4"
            >
              <p
                v-if="chatError"
                role="alert"
                class="text-center text-xs text-red-600"
              >
                {{ chatError }}
              </p>
              <p
                v-else-if="!visibleMessages.length"
                class="py-8 text-center text-sm text-text-sub"
              >
                채팅으로 확인할 내용을 요청해 보세요.
              </p>
              <div
                v-for="message in visibleMessages"
                :key="message.messageId"
                class="flex"
                :class="Number(message.senderId) === Number(myMemberId) ? 'justify-end' : 'justify-start'"
              >
                <div class="max-w-[82%]">
                  <div
                    class="rounded-xl px-3 py-2 text-sm leading-5"
                    :class="Number(message.senderId) === Number(myMemberId)
                      ? 'rounded-br-none bg-primary-gradient text-white'
                      : 'rounded-bl-none bg-bg text-text-main'"
                  >
                    {{ message.content }}
                  </div>
                  <p
                    class="mt-1 text-[10px] text-text-sub"
                    :class="Number(message.senderId) === Number(myMemberId) ? 'text-right' : 'text-left'"
                  >
                    {{ formatTime(message.sentAt) }}
                    <span v-if="message.isPending"> · 전송 중</span>
                  </p>
                </div>
              </div>
            </div>
            <form
              class="flex gap-2 border-t border-border p-4"
              @submit.prevent="sendMessage"
            >
              <input
                v-model="messageInput"
                type="text"
                maxlength="2000"
                placeholder="메시지를 입력하세요..."
                class="min-w-0 flex-1 rounded-lg border border-border px-3 py-2.5 text-sm outline-none focus:border-primary"
              >
              <button
                type="submit"
                :disabled="chatStatus !== 'connected' || !messageInput.trim()"
                aria-label="메시지 전송"
                class="inline-flex h-11 w-11 items-center justify-center rounded-lg bg-primary text-white disabled:opacity-40"
              >
                <svg
                  class="h-5 w-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="m22 2-7 20-4-9-9-4 20-7Z"
                  />
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M22 2 11 13"
                  />
                </svg>
              </button>
            </form>
          </section>
        </aside>
      </div>
    </main>
  </DefaultLayout>
</template>
