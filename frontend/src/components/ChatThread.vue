<script setup>
import { computed, ref, watch } from 'vue'
import { getChatMessages } from '../api/chat'
import { getProduct } from '../api/products'
import { useAuthSession } from '../auth/session'

// TODO(채팅 전송 API 연동): 백엔드에 메시지 전송/실시간 수신(WebSocket) API가 아직 없습니다.
// 방·지난 메시지 조회는 실제 API(getChatMessages)를 쓰고, 이 화면에서 새로 작성한 메시지는
// 화면에만 보이고 저장되지 않는 mock입니다(로컬 메시지는 messageId를 음수로 부여해 구분).

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

async function loadMessages(roomId) {
  isLoadingMessages.value = true
  messagesError.value = ''
  try {
    const result = await getChatMessages(roomId, { size: 50 })
    messages.value = (result?.content || []).slice().reverse()
  } catch (error) {
    messagesError.value = error.message || '메시지를 불러오지 못했습니다.'
  } finally {
    isLoadingMessages.value = false
  }
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
  },
  { immediate: true },
)

const messageInput = ref('')
let nextMockMessageId = -1

function sendMessage() {
  const text = messageInput.value.trim()
  if (!text || myMemberId.value == null) return
  messages.value.push({
    messageId: nextMockMessageId--,
    senderId: myMemberId.value,
    type: 'TEXT',
    content: text,
    status: 'SENT',
    sentAt: new Date().toISOString(),
    isMock: true,
  })
  messageInput.value = ''
}

function formatTime(isoString) {
  if (!isoString) return ''
  return new Date(isoString).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
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
            <template v-if="message.isMock">· 저장 안 됨 (mock)</template>
          </span>
        </div>
      </TransitionGroup>
    </div>

    <div class="border-t border-border p-4">
      <p class="mb-2 text-xs text-text-sub">
        메시지 전송 API가 아직 없어, 여기서 보낸 메시지는 화면에만 표시되고 저장되지 않습니다.
      </p>
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
          class="whitespace-nowrap rounded-md bg-primary-gradient px-4 py-2.5 text-sm font-semibold text-white transition-all hover:brightness-110"
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
