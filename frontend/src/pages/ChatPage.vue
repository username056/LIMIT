<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import ChatThread from '../components/ChatThread.vue'
import { getChatRooms } from '../api/chat'

// 방 목록/지난 메시지 조회, 메시지 전송·실시간 수신 모두 실제 채팅 API/WebSocket을 사용합니다.
// TODO(채팅 API 연동): 상대 회원 닉네임·상품 미리보기를 주는 API가 아직 없어 목록에는
// 회원 ID/상품 ID만 표시합니다.

const route = useRoute()
const rooms = ref([])
const isLoading = ref(true)
const errorMessage = ref('')

async function loadRooms() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const result = await getChatRooms({ size: 20 })
    rooms.value = result?.content || []
  } catch (error) {
    errorMessage.value = error.message || '채팅 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

onMounted(loadRooms)

const selectedRoomId = computed(() => route.params.roomId || null)
const selectedRoom = computed(
  () => rooms.value.find((room) => String(room.roomId) === String(selectedRoomId.value)) || null,
)

function formatTime(isoString) {
  if (!isoString) return '대화 없음'
  return new Date(isoString).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1280px] px-4 py-7 sm:px-6 lg:px-10 lg:py-10">
      <p class="mb-4 text-xs font-bold uppercase tracking-[0.16em] text-primary">
        CHAT
      </p>
      <div class="grid min-h-[560px] grid-cols-1 overflow-hidden border-y border-border bg-surface lg:grid-cols-[320px_minmax(0,1fr)]">
        <BaseCard
          :padded="false"
          class="rounded-none border-0 border-b shadow-none lg:border-b-0 lg:border-r"
        >
          <h1 class="border-b border-border px-5 py-4 text-lg font-bold text-text-main">
            채팅
          </h1>

          <p
            v-if="isLoading"
            class="px-5 py-16 text-center text-sm text-text-sub"
          >
            불러오는 중...
          </p>
          <p
            v-else-if="errorMessage"
            role="alert"
            class="px-5 py-16 text-center text-sm text-red-600"
          >
            {{ errorMessage }}
          </p>

          <div
            v-else-if="rooms.length"
            class="divide-y divide-border"
          >
            <RouterLink
              v-for="room in rooms"
              :key="room.roomId"
              :to="{ name: 'chat', params: { roomId: room.roomId } }"
              class="block border-l-2 px-5 py-4 transition-colors"
              :class="String(selectedRoomId) === String(room.roomId) ? 'border-primary bg-accent/60' : 'border-transparent hover:bg-bg'"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="truncate text-sm font-bold text-text-main">상대 회원 #{{ room.counterpartId }}</span>
                <span
                  v-if="room.unreadCount"
                  class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-white"
                >
                  {{ room.unreadCount }}
                </span>
              </div>
              <p class="mt-1 truncate text-xs font-semibold text-text-sub">
                상품 #{{ room.listingId }}
              </p>
              <p class="mt-1 text-xs text-text-sub">
                {{ formatTime(room.lastMessageAt) }}
              </p>
            </RouterLink>
          </div>

          <div
            v-else
            class="px-5 py-16 text-center"
          >
            <p class="text-sm font-semibold text-text-sub">
              채팅 목록이 비어있어요
            </p>
          </div>
        </BaseCard>

        <ChatThread
          v-if="selectedRoom"
          :room="selectedRoom"
        />
        <BaseCard
          v-else
          class="flex min-h-[520px] items-center justify-center rounded-none border-0 shadow-none"
        >
          <div class="max-w-sm text-center">
            <div class="mx-auto flex h-10 w-10 items-center justify-center text-primary">
              <svg
                class="h-7 w-7"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                />
              </svg>
            </div>
            <p class="mt-3 text-base font-bold text-text-main">
              아직 채팅이 선택되지 않았습니다
            </p>
            <p class="mt-2 text-sm text-text-sub">
              왼쪽의 채팅방 목록을 선택하여 대화를 계속하거나, 상품 상세 페이지에서 판매자에게 문의해 보세요.
            </p>
          </div>
        </BaseCard>
      </div>
    </div>
  </DefaultLayout>
</template>
