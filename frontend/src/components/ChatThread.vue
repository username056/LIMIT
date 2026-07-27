<script setup>
import { ref } from 'vue'
import { addProposalMessage, addTextMessage, respondToProposal as respondToProposalInStore } from '../mock/chat'

// WIREFRAME MOCK: 채팅/실시간 화상 검증 API가 아직 없어 메시지 전송·일정 제안은
// src/mock/chat.js의 공용 mock 저장소를 통해서만 바뀝니다 (prop을 직접 mutate하지 않습니다).

const props = defineProps({
  room: {
    type: Object,
    required: true,
  },
})

const messageInput = ref('')

function respondToProposal(message, status) {
  respondToProposalInStore(props.room.id, message.id, status)
}

const isScheduleModalOpen = ref(false)
const scheduleDate = ref('')
const scheduleTime = ref('')
const WEEKDAYS_KO = ['일', '월', '화', '수', '목', '금', '토']

function openScheduleModal() {
  scheduleDate.value = ''
  scheduleTime.value = ''
  isScheduleModalOpen.value = true
}

function closeScheduleModal() {
  isScheduleModalOpen.value = false
}

function formatProposedAt(dateStr, timeStr) {
  const [year, month, day] = dateStr.split('-').map(Number)
  const [hour, minute] = timeStr.split(':').map(Number)
  const weekday = WEEKDAYS_KO[new Date(year, month - 1, day).getDay()]
  const period = hour < 12 ? '오전' : '오후'
  const hour12 = hour % 12 === 0 ? 12 : hour % 12
  return `${year}.${String(month).padStart(2, '0')}.${String(day).padStart(2, '0')} (${weekday}) ${period} ${hour12}:${String(minute).padStart(2, '0')}`
}

function confirmSchedule() {
  if (!scheduleDate.value || !scheduleTime.value) return
  addProposalMessage(props.room.id, formatProposedAt(scheduleDate.value, scheduleTime.value))
  closeScheduleModal()
}

function sendMessage() {
  const text = messageInput.value.trim()
  if (!text) return
  addTextMessage(props.room.id, 'me', text)
  messageInput.value = ''
}
</script>

<template>
  <div class="flex h-full min-w-0 min-h-[520px] flex-col rounded-lg border border-border bg-surface">
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
        <div>
          <p class="text-sm font-bold text-text-main">
            {{ room.name }}
          </p>
          <p class="flex items-center gap-1 text-xs text-text-sub">
            <span
              class="h-1.5 w-1.5 rounded-full"
              :class="room.online ? 'bg-green-500' : 'bg-text-sub'"
            />
            {{ room.online ? '온라인' : '오프라인' }}
          </p>
        </div>
      </div>
      <RouterLink
        :to="{ name: 'coming-soon', params: { feature: 'chat-report' }, query: { name: room.name } }"
        class="rounded-md border border-border px-3 py-1.5 text-xs font-semibold text-text-sub hover:border-primary hover:text-primary"
      >
        신고하기
      </RouterLink>
    </div>

    <div class="flex items-center gap-3 border-b border-border px-5 py-3">
      <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary-gradient text-lg">
        {{ room.productIcon }}
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-bold text-text-main">
          {{ room.productName }}
        </p>
        <p class="truncate text-xs text-text-sub">
          ₩{{ room.productPrice.toLocaleString('ko-KR') }} · {{ room.productSpec }}
        </p>
      </div>
      <RouterLink
        :to="{ name: 'product-detail', params: { productId: room.productId } }"
        class="whitespace-nowrap rounded-md border border-primary px-3 py-1.5 text-xs font-semibold text-primary hover:bg-accent"
      >
        상품 보기
      </RouterLink>
    </div>

    <div class="flex-1 overflow-y-auto px-5 py-5">
      <p class="mb-4 text-center text-xs text-text-sub">
        {{ room.dateLabel }}
      </p>

      <TransitionGroup
        name="msg"
        tag="div"
        class="space-y-4"
      >
        <template
          v-for="message in room.messages"
          :key="message.id"
        >
          <!-- 일반 텍스트 메시지 -->
          <div
            v-if="message.type === 'text'"
            class="flex flex-col"
            :class="message.from === 'me' ? 'items-end' : 'items-start'"
          >
            <span
              v-if="message.from === 'seller'"
              class="mb-1 text-xs font-semibold text-text-sub"
            >{{ room.name }}</span>
            <div
              class="max-w-[75%] rounded-lg px-4 py-2.5 text-sm leading-6"
              :class="message.from === 'me' ? 'bg-primary-gradient text-white' : 'bg-bg text-text-main'"
            >
              {{ message.text }}
            </div>
            <span class="mt-1 text-[11px] text-text-sub">{{ message.time }}</span>
          </div>

          <!-- 실시간 화상 검증 일정 제안 -->
          <div
            v-else-if="message.type === 'proposal'"
            class="flex flex-col"
            :class="message.from === 'me' ? 'items-end' : 'items-start'"
          >
            <span
              v-if="message.from === 'seller'"
              class="mb-1 text-xs font-semibold text-text-sub"
            >{{ room.name }}</span>
            <div class="w-full max-w-sm rounded-lg border border-border bg-surface p-4 shadow-card">
              <p class="flex items-center gap-1.5 text-sm font-bold text-text-main">
                📅 실시간 화상 검증 일정 제안
              </p>
              <p class="mt-3 text-xs text-text-sub">
                제안 시간
              </p>
              <p class="mt-1 text-sm font-bold text-text-main">
                {{ message.proposedAt }}
              </p>
              <div
                v-if="message.status === 'pending'"
                class="mt-3 flex gap-2"
              >
                <button
                  type="button"
                  class="flex-1 rounded-md border border-border py-2 text-sm font-semibold text-text-main hover:border-primary"
                  @click="respondToProposal(message, 'rejected')"
                >
                  거절
                </button>
                <button
                  type="button"
                  class="flex-1 rounded-md bg-primary-gradient py-2 text-sm font-semibold text-white"
                  @click="respondToProposal(message, 'accepted')"
                >
                  수락하기
                </button>
              </div>
              <p
                v-else
                class="mt-3 text-sm font-semibold"
                :class="message.status === 'accepted' ? 'text-green-600' : 'text-text-sub'"
              >
                {{ message.status === 'accepted' ? '✓ 수락한 일정입니다' : '거절한 일정입니다' }}
              </p>
            </div>
            <span class="mt-1 text-[11px] text-text-sub">{{ message.time }}</span>
          </div>

          <!-- 실시간 화상 검증 일정 확정 -->
          <div
            v-else-if="message.type === 'confirmed'"
            class="flex flex-col items-start"
          >
            <span class="mb-1 text-xs font-semibold text-text-sub">{{ room.name }}</span>
            <div class="w-full max-w-sm rounded-lg border border-border bg-surface p-4 shadow-card">
              <p class="flex items-center gap-1.5 text-sm font-bold text-text-main">
                📅 실시간 화상 검증 일정 확정
              </p>
              <p class="mt-3 text-xs text-text-sub">
                확정된 일정
              </p>
              <p class="mt-1 text-sm font-bold text-text-main">
                {{ message.confirmedAt }}
              </p>
              <RouterLink
                :to="{ name: 'webrtc-session', params: { roomId: room.id } }"
                class="mt-3 block rounded-md bg-primary-gradient py-2 text-center text-sm font-semibold text-white"
              >
                실시간 검증 입장하기
              </RouterLink>
            </div>
            <span class="mt-1 text-[11px] text-text-sub">{{ message.time }}</span>
          </div>

          <!-- 일정 확정 배너 -->
          <div
            v-else-if="message.type === 'banner'"
            class="mx-auto w-full max-w-md rounded-lg border border-green-200 bg-green-50 p-4"
          >
            <div class="flex items-center justify-between">
              <p class="text-sm font-bold text-green-700">
                ✓ 실시간 검증 일정이 확정되었습니다!
              </p>
              <span class="rounded-full bg-surface px-2 py-0.5 text-[11px] font-semibold text-green-600">예약 확정</span>
            </div>
            <p class="mt-2 text-sm text-green-700">
              {{ message.confirmedAt }} 시작
            </p>
            <p class="mt-1 text-xs text-green-600">
              검증 시작 5분 전부터 아래 입장 버튼이 활성화됩니다.
            </p>
            <RouterLink
              :to="{ name: 'webrtc-session', params: { roomId: room.id } }"
              class="mt-3 block rounded-md bg-green-600 py-2 text-center text-sm font-semibold text-white hover:bg-green-700"
            >
              실시간 화상 검증 방 입장하기
            </RouterLink>
          </div>
        </template>
      </TransitionGroup>
    </div>

    <div class="border-t border-border p-4">
      <div class="mb-2 flex flex-wrap items-center gap-2">
        <button
          type="button"
          class="shrink-0 rounded-full border border-primary px-3 py-1.5 text-xs font-semibold text-primary hover:bg-accent"
          @click="openScheduleModal"
        >
          📅 실시간 검증 일정 잡기
        </button>
        <span class="text-xs text-text-sub">* 상호 조율 하에 라이브 WebRTC 성능 테스트 시간대를 제안해보세요.</span>
      </div>
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
          class="whitespace-nowrap rounded-md bg-primary-gradient px-4 py-2.5 text-sm font-semibold text-white"
        >
          전송
        </button>
      </form>
    </div>

    <div
      v-if="isScheduleModalOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="closeScheduleModal"
    >
      <div class="w-full max-w-sm rounded-lg bg-surface p-6 shadow-elevated">
        <h3 class="text-base font-bold text-text-main">
          실시간 검증 일정 제안
        </h3>
        <p class="mt-1 text-xs text-text-sub">
          날짜와 시간을 선택해 상대방에게 제안하세요.
        </p>
        <div class="mt-4 space-y-3">
          <label class="block">
            <span class="mb-1 block text-sm font-medium text-text-main">날짜</span>
            <input
              v-model="scheduleDate"
              type="date"
              class="w-full rounded-md border border-border px-3 py-2 text-sm text-text-main outline-none focus:border-primary"
            >
          </label>
          <label class="block">
            <span class="mb-1 block text-sm font-medium text-text-main">시간</span>
            <input
              v-model="scheduleTime"
              type="time"
              class="w-full rounded-md border border-border px-3 py-2 text-sm text-text-main outline-none focus:border-primary"
            >
          </label>
        </div>
        <div class="mt-5 flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-md border border-border py-2 text-sm font-semibold text-text-main"
            @click="closeScheduleModal"
          >
            취소
          </button>
          <button
            type="button"
            class="flex-1 rounded-md bg-primary-gradient py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!scheduleDate || !scheduleTime"
            @click="confirmSchedule"
          >
            제안하기
          </button>
        </div>
      </div>
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
