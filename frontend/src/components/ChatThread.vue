<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getChatMessages } from '../api/chat'
import { createChatSocket } from '../api/chatSocket'
import { getProduct } from '../api/products'
import { useAuthSession } from '../auth/session'

const props = defineProps({
  room: {
    type: Object,
    required: true, // ChatRoomSummaryResponse: roomId, listingId, counterpartId, status, unreadCount, lastMessageAt ...
  },
})

const session = useAuthSession()
const myMemberId = computed(() => session.value?.member?.memberId ?? null)

const product = ref(null)
const messages = ref([])
const isLoadingMessages = ref(true)
const messagesError = ref('')
const socketStatus = ref('connecting')
let chatSocket = null
let nextPendingMessageId = -1

async function loadMessages(roomId) {
  isLoadingMessages.value = true
  messagesError.value = ''
  try {
    const result = await getChatMessages(roomId, { size: 50 })
    messages.value = (result?.content || []).slice().reverse()
    markRead()
  } catch (error) {
    messagesError.value = error.message || '메시지를 불러오지 못했습니다.'
  } finally {
    isLoadingMessages.value = false
  }
}

function upsertMessage(message) {
  const indexByClientId = messages.value.findIndex((item) => item.clientMessageId === message.clientMessageId)
  if (indexByClientId >= 0) {
    messages.value[indexByClientId] = { ...messages.value[indexByClientId], ...message, isPending: false }
    return
  }
  if (!messages.value.some((item) => item.messageId === message.messageId)) {
    messages.value.push(message)
  }
}

function latestSequence() {
  return messages.value.reduce((max, message) => Math.max(max, Number(message.roomSequence || 0)), 0)
}

function markRead() {
  const lastReadSeq = latestSequence()
  if (lastReadSeq > 0) chatSocket?.markRead(lastReadSeq)
}

function connectSocket(roomId) {
  chatSocket?.close()
  socketStatus.value = 'connecting'
  chatSocket = createChatSocket({
    roomId,
    onOpen: () => {
      socketStatus.value = 'connected'
      messagesError.value = ''
      markRead()
    },
    onEvent: (event) => {
      if (event.type === 'MESSAGE' && event.message) {
        upsertMessage(event.message)
        markRead()
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
      messagesError.value = error?.error?.message || '채팅 처리 중 오류가 발생했습니다.'
      socketStatus.value = 'error'
    },
    onClose: () => {
      if (socketStatus.value !== 'closed' && socketStatus.value !== 'error') socketStatus.value = 'disconnected'
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

watch(
  () => props.room.roomId,
  (roomId) => {
    loadMessages(roomId)
    loadProduct(props.room.listingId)
    connectSocket(roomId)
  },
  { immediate: true },
)

const messageInput = ref('')

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
  })
  chatSocket?.sendMessage({ clientMessageId, type: 'TEXT', content: text, mediaIds: [] })
  messageInput.value = ''
}

function formatTime(isoString) {
  if (!isoString) return ''
  return new Date(isoString).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onBeforeUnmount(() => {
  socketStatus.value = 'closed'
  chatSocket?.close()
})
</script>

<template>
  <div class="flex h-full min-w-0 min-h-[520px] flex-col bg-surface">
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
          상대 회원 #{{ room.counterpartId }}
        </p>
        <span
          class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
          :class="socketStatus === 'connected' ? 'bg-green-50 text-green-700' : 'bg-yellow-50 text-yellow-700'"
        >
          {{ socketStatus === 'connected' ? '실시간 연결됨' : '연결 확인 중' }}
        </span>
      </div>
      <RouterLink
        :to="{ name: 'calls' }"
        class="rounded-md border border-primary px-3 py-1.5 text-xs font-semibold text-primary hover:bg-accent"
      >
        실시간 검증 요청하기
      </RouterLink>
    </div>

    <div
      v-if="product"
      class="flex items-center gap-3 border-b border-border px-5 py-3"
    >
      <div class="h-10 w-10 shrink-0 bg-primary-gradient" />
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-bold text-text-main">
          {{ product.name }}
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

    <div class="flex-1 space-y-3 overflow-y-auto px-5 py-5">
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
        v-else-if="!messages.length"
        class="py-10 text-center text-sm text-text-sub"
      >
        아직 주고받은 메시지가 없습니다.
      </p>

      <TransitionGroup
        v-else
        name="msg"
        tag="div"
        class="space-y-3"
      >
        <div
          v-for="message in messages"
          :key="message.messageId"
          class="flex flex-col"
          :class="message.senderId === myMemberId ? 'items-end' : 'items-start'"
        >
          <div
            class="max-w-[75%] rounded-lg px-4 py-2.5 text-sm leading-6"
            :class="message.senderId === myMemberId ? 'bg-primary-deep text-white' : 'bg-bg text-text-main'"
          >
            {{ message.type === 'TEXT' || message.type === 'SYSTEM' ? message.content : `[${message.type}] ${message.content}` }}
          </div>
          <span class="mt-1 text-[11px] text-text-sub">
            {{ formatTime(message.sentAt) }}
            <template v-if="message.isPending">· 전송 중</template>
          </span>
        </div>
      </TransitionGroup>
    </div>

    <div class="border-t border-border p-4">
      <form
        class="flex gap-2"
        @submit.prevent="sendMessage"
      >
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
