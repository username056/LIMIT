<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import ChatThread from '../components/ChatThread.vue'
import { getChatRooms, leaveChatRoom } from '../api/chat'
import { createChatListSocket } from '../api/chatSocket'

const route = useRoute()
const router = useRouter()
const rooms = ref([])
const isLoading = ref(true)
const errorMessage = ref('')
let listSocket = null
let listSocketRoomKey = ''
let reconnectTimer = null
let refreshTimer = null
let isUnmounted = false

function scheduleRoomsRefresh() {
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => loadRooms(false), 0)
}

function connectListSocket() {
  const roomIds = rooms.value.map((room) => Number(room.roomId)).filter(Number.isFinite)
  const roomKey = [...roomIds].sort((first, second) => first - second).join(',')
  if (roomKey === listSocketRoomKey) return

  listSocketRoomKey = roomKey
  const previousSocket = listSocket
  listSocket = null
  previousSocket?.close()
  clearTimeout(reconnectTimer)
  if (!roomIds.length) {
    listSocket = null
    return
  }

  const nextSocket = createChatListSocket({
    roomIds,
    onEvent: scheduleRoomsRefresh,
    onClose: () => {
      if (isUnmounted || listSocket !== nextSocket) return
      reconnectTimer = setTimeout(() => {
        listSocketRoomKey = ''
        connectListSocket()
      }, 1500)
    },
  })
  listSocket = nextSocket
}

async function loadRooms(showLoading = true) {
  if (showLoading) isLoading.value = true
  errorMessage.value = ''
  try {
    const result = await getChatRooms({ size: 20 })
    rooms.value = (result?.content || []).filter(
      (room) => room.lastMessageId || String(room.roomId) === String(route.params.roomId),
    )
    connectListSocket()
  } catch (error) {
    errorMessage.value = error.message || '채팅 목록을 불러오지 못했습니다.'
  } finally {
    if (showLoading) isLoading.value = false
  }
}

async function removeRoom(roomId) {
  if (!window.confirm('이 채팅방을 목록에서 삭제할까요?')) return
  try {
    await leaveChatRoom(roomId)
    rooms.value = rooms.value.filter((room) => Number(room.roomId) !== Number(roomId))
    if (String(selectedRoomId.value) === String(roomId)) await router.push({ name: 'chat' })
  } catch (error) {
    errorMessage.value = error.message || '채팅방을 삭제하지 못했습니다.'
  }
}

onMounted(loadRooms)
onBeforeUnmount(() => {
  isUnmounted = true
  clearTimeout(refreshTimer)
  clearTimeout(reconnectTimer)
  const currentSocket = listSocket
  listSocket = null
  currentSocket?.close()
})

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
    <!--
      폭·좌우 여백은 상품 목록·상세와 같은 값(max-w-[1200px], px-4 → sm:px-6 → lg:px-10)입니다.
      화면을 꽉 채우는 대화창이라 위아래만 조금 좁게 둡니다.
    -->
    <div class="mx-auto w-full max-w-[1200px] px-4 py-8 sm:px-6 lg:h-[calc(100dvh-72px)] lg:min-h-0 lg:px-10 lg:py-8">
      <div class="grid min-h-[560px] grid-cols-1 overflow-hidden rounded-lg border border-border bg-surface shadow-card lg:h-full lg:min-h-0 lg:grid-cols-[340px_minmax(0,1fr)]">
        <BaseCard
          :padded="false"
          class="rounded-none border-0 border-b shadow-none lg:flex lg:min-h-0 lg:flex-col lg:border-b-0 lg:border-r lg:border-r-slate-100"
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
            class="divide-y divide-border lg:min-h-0 lg:flex-1 lg:overflow-y-auto"
          >
            <RouterLink
              v-for="room in rooms"
              :key="room.roomId"
              :to="{ name: 'chat', params: { roomId: room.roomId } }"
              class="group block border-l-2 p-4 transition-colors"
              :class="String(selectedRoomId) === String(room.roomId) ? 'border-primary bg-accent/60' : 'border-transparent hover:bg-slate-50'"
            >
              <div class="flex items-center justify-between gap-2">
                <div class="flex min-w-0 items-center gap-3">
                  <img
                    v-if="room.listingThumbnailUrl"
                    :src="room.listingThumbnailUrl"
                    :alt="room.listingTitle || '상품 이미지'"
                    class="h-10 w-10 shrink-0 rounded-md object-cover"
                  >
                  <div
                    v-else
                    class="h-10 w-10 shrink-0 rounded-md bg-primary-gradient"
                  />
                  <span class="truncate text-sm font-bold text-text-main">
                    {{ room.counterpartNickname || `회원 #${room.counterpartId}` }}
                  </span>
                </div>
                <div class="flex shrink-0 items-center gap-2">
                  <span
                    v-if="room.unreadCount"
                    :aria-label="`읽지 않은 메시지 ${room.unreadCount}개`"
                    class="unread-badge flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold text-white"
                  >
                    {{ room.unreadCount }}
                  </span>
                  <!--
                    삭제 버튼은 마우스를 올렸을 때만 드러냅니다. 목록에 늘 ×가 떠 있으면
                    지우는 일이 대화를 여는 일만큼 눈에 띄어 잘못 누르기 쉽습니다.
                    키보드로 옮겨 다닐 때는 호버가 없으므로 포커스에도 함께 나타납니다.
                  -->
                  <button
                    type="button"
                    aria-label="채팅방 삭제"
                    class="rounded px-1 text-sm text-text-sub opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus-visible:opacity-100 group-hover:opacity-100"
                    @click.prevent.stop="removeRoom(room.roomId)"
                  >
                    ×
                  </button>
                </div>
              </div>
              <p class="mt-1 truncate text-xs font-semibold text-text-sub">
                {{ room.listingTitle || `상품 #${room.listingId}` }}
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
          @room-updated="scheduleRoomsRefresh"
        />
        <BaseCard
          v-else
          class="flex min-h-[520px] flex-col items-center justify-center rounded-none border-0 bg-[#f8fafc] shadow-none lg:min-h-0"
        >
          <div class="max-w-sm text-center">
            <!-- 80px 원형. 아무것도 없는 넓은 면에 시선이 멈출 자리를 하나 둡니다. -->
            <div class="empty-icon mx-auto flex h-20 w-20 items-center justify-center rounded-full text-primary">
              <svg
                class="h-9 w-9"
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
            <p class="mt-5 text-base font-semibold text-[#1e293b]">
              아직 채팅이 선택되지 않았습니다
            </p>
            <p class="mt-2 text-sm leading-relaxed text-[#94a3b8]">
              왼쪽의 채팅방 목록을 선택하여 대화를 계속하거나, 상품 상세 페이지에서 판매자에게 문의해 보세요.
            </p>
          </div>
        </BaseCard>
      </div>
    </div>
  </DefaultLayout>
</template>

<style scoped>
/*
  읽지 않은 개수 배지와 Empty 아이콘 배경.
  받아온 시안은 보라(#8b5cf6, #a855f7)로 흐르는데, 서비스 그라데이션이
  #6366F1 → #93C5FD라 보라를 섞으면 헤더·버튼과 색이 따로 놉니다.
  같은 브랜드 색 안에서 흐르게 두었습니다.
*/
.unread-badge {
  background: linear-gradient(135deg, #6366f1, #93c5fd);
}

.empty-icon {
  background: linear-gradient(135deg, rgb(99 102 241 / 10%), rgb(147 197 253 / 25%));
}
</style>
