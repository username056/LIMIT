<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseCard from '../components/BaseCard.vue'
import ChatAvatar from '../components/ChatAvatar.vue'
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

/*
  고른 대화를 알아보게 하는 조건. 바탕색과 사진이 같이 씁니다.
  ---------------------------------------------------------------------------
  예전에는 왼쪽에 2px 파란 띠를 세웠습니다. 바탕색(bg-accent/60)이 흰색과 거의
  구분되지 않아 띠에 의지했던 것인데, 바탕을 #F5F7FF로 올린 뒤에는 띠가 한 칸만
  왼쪽이 두꺼워 보이게 만들 뿐이었습니다. 바탕색만으로 충분합니다.
*/
function isSelectedRoom(room) {
  return String(selectedRoomId.value) === String(room.roomId)
}

function formatTime(isoString) {
  if (!isoString) return '대화 없음'
  return new Date(isoString).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <DefaultLayout>
    <!--
      폭과 좌우 여백은 index.css의 .page-shell과 같은 값입니다. 여기만 클래스를 못 쓰는
      이유는 화면 높이를 꽉 채워야 해서(lg:h-[calc(...)]) 자체 컨테이너가 필요하기 때문입니다.

      위아래 여백은 .page-shell(48px)보다 작습니다. 이 화면에 온 사람은 머리말이 아니라
      대화를 읽으러 왔고, 위에서 덜어 낸 픽셀은 그대로 대화 목록 높이가 됩니다.
      .page-shell의 폭을 바꾸면 이 값도 같이 맞춰 주세요.
    -->
    <div class="mx-auto flex w-full max-w-[1080px] flex-col px-4 py-6 sm:px-6 lg:h-[calc(100dvh-72px)] lg:min-h-0 lg:px-10 lg:pb-2 lg:pt-5">
      <!--
        페이지 이름은 다른 화면과 같이 맨 위에 둡니다. 예전에는 왼쪽 목록 카드 안에
        들어 있어서, 채팅만 제목이 화면 구석에 박혀 있는 꼴이었습니다.
        글자 크기는 그대로 두고 사이 여백만 좁힌 dense를 씁니다.
      -->
      <PageHeader
        dense
        eyebrow="CHAT"
        title="채팅"
        description="상품에 대해 판매자와 직접 이야기하고, 실시간 확인 일정을 잡아 보세요."
      />

      <!-- 대화 영역. 머리말이 쓰고 남은 높이를 전부 차지합니다. -->
      <!-- 상품 등록과 같이 테두리 없이 흰 판만 얹습니다. -->
      <!-- 목록은 이름과 상품명만 읽으면 되니 좁혀 두고, 대화가 넓게 씁니다. -->
      <div class="grid min-h-[560px] grid-cols-1 overflow-hidden rounded-lg bg-surface shadow-card lg:min-h-0 lg:flex-1 lg:grid-cols-[300px_minmax(0,1fr)]">
        <BaseCard
          :padded="false"
          class="rounded-none border-0 border-b shadow-none lg:flex lg:min-h-0 lg:flex-col lg:border-b-0 lg:border-r lg:border-r-slate-100"
        >
          <!--
            '대화 목록' 이름표는 두지 않고 첫 대화가 바로 보이게 합니다.
            위에 페이지 이름이 '채팅'이라고 적혀 있어 이 칸이 목록임은 보면 압니다.

            선이 어긋나지 않는 이유: 대화 한 칸과 오른쪽 머리말이 둘 다 64px입니다
            (사진 40 + 위아래 12). 한쪽 높이를 바꾸면 다른 쪽도 같이 맞춰 주세요.
            그러지 않으면 가운데 구분선이 어긋나 두 칸이 이어지지 않아 보입니다.
          -->
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
            <!--
              한 칸은 이름·상품명·마지막 메시지 세 줄입니다.
              -----------------------------------------------------------------
              시각은 이름과 같은 줄 오른쪽에 붙여 줄 수를 아꼈습니다. 예전에는
              시각이 따로 한 줄을 써서 한 칸이 112px이었습니다.

              오른쪽 대화창 머리말(64px)보다 이 칸이 높습니다. 두 칸의 첫 선이
              어긋나 보이지만, 마지막 메시지를 보여 주려면 줄이 하나 더 필요합니다.
            -->
            <RouterLink
              v-for="room in rooms"
              :key="room.roomId"
              :to="{ name: 'chat', params: { roomId: room.roomId } }"
              class="group relative flex items-center gap-3 px-4 py-2.5 transition-colors"
              :class="isSelectedRoom(room) ? 'bg-[#F5F7FF]' : 'hover:bg-slate-50'"
            >
              <ChatAvatar
                :src="room.listingThumbnailUrl"
                :alt="room.listingTitle || '상품 이미지'"
                :vivid="isSelectedRoom(room)"
              />

              <div class="min-w-0 flex-1">
                <!-- 오른쪽 여백은 위에 뜨는 삭제 버튼 자리입니다. -->
                <div class="flex items-center gap-2 pr-6">
                  <span class="truncate text-sm font-bold text-text-main">
                    {{ room.counterpartNickname || `회원 #${room.counterpartId}` }}
                  </span>
                  <span
                    v-if="room.unreadCount"
                    :aria-label="`읽지 않은 메시지 ${room.unreadCount}개`"
                    class="unread-badge flex h-5 min-w-[20px] shrink-0 items-center justify-center rounded-full px-1 text-[11px] font-bold text-white"
                  >
                    {{ room.unreadCount }}
                  </span>
                </div>
                <!--
                  상품명은 작게 위로, 마지막 메시지가 본문 자리를 가져갑니다.
                  --------------------------------------------------------------
                  예전에는 두 번째 줄이 상품명이었습니다. 서버 응답에 마지막 메시지
                  본문이 없었기 때문인데, 목록을 훑는 사람이 알고 싶은 것은 "무슨
                  물건인가"보다 "무슨 말이 왔는가"입니다.

                  시각은 아래 줄 오른쪽에 둡니다. 위 오른쪽은 삭제 버튼 자리라,
                  둘을 같은 자리에 두면 호버할 때마다 날짜가 가려집니다.
                -->
                <p class="mt-0.5 truncate text-[11px] text-[#98a1b0]">
                  {{ room.listingTitle || `상품 #${room.listingId}` }}
                </p>
                <div class="mt-0.5 flex items-baseline gap-2">
                  <p class="min-w-0 flex-1 truncate text-[13px] text-text-sub">
                    {{ room.lastMessagePreview || '대화를 시작해 보세요.' }}
                  </p>
                  <span class="shrink-0 text-xs text-text-sub">
                    {{ formatTime(room.lastMessageAt) }}
                  </span>
                </div>
              </div>

              <!--
                삭제 버튼은 마우스를 올렸을 때만 드러냅니다. 목록에 늘 ×가 떠 있으면
                지우는 일이 대화를 여는 일만큼 눈에 띄어 잘못 누르기 쉽습니다.
                키보드로 옮겨 다닐 때는 호버가 없으므로 포커스에도 함께 나타납니다.
              -->
              <button
                type="button"
                aria-label="채팅방 삭제"
                class="absolute right-3 top-2 rounded px-1 text-sm leading-none text-text-sub opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus-visible:opacity-100 group-hover:opacity-100"
                @click.prevent.stop="removeRoom(room.roomId)"
              >
                ×
              </button>
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
