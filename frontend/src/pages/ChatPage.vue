<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import ChatThread from '../components/ChatThread.vue'
import { chatRooms, getChatRoom, markRoomRead } from '../mock/chat'

// WIREFRAME MOCK: 채팅 API/실시간 연결이 아직 없어 화면 흐름만 먼저 확인할 수 있도록
// mock 데이터로 구현했습니다. 방 목록/대화 내용은 src/mock/chat.js에서 관리합니다.
// 방 선택 상태는 /chat/:roomId URL로 표현되어 새로고침·공유해도 유지됩니다.

const route = useRoute()
const selectedRoom = computed(() => (route.params.roomId ? getChatRoom(route.params.roomId) : null))

watch(
  () => route.params.roomId,
  (roomId) => {
    if (roomId) markRoomRead(roomId)
  },
  { immediate: true },
)
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-10 lg:px-10">
      <p class="mb-6 rounded-md bg-accent px-4 py-2 text-xs font-semibold text-primary-dark">
        WIREFRAME MOCK · 실시간 채팅·화상 검증 연결 전까지 화면 흐름을 확인하기 위한 mock 데이터입니다.
      </p>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
        <BaseCard :padded="false">
          <h1 class="border-b border-border px-5 py-4 text-lg font-bold text-text-main">
            채팅
          </h1>

          <div
            v-if="chatRooms.length"
            class="divide-y divide-border"
          >
            <RouterLink
              v-for="room in chatRooms"
              :key="room.id"
              :to="{ name: 'chat', params: { roomId: room.id } }"
              class="flex w-full items-start gap-3 px-5 py-4 text-left transition-colors"
              :class="String(route.params.roomId) === String(room.id) ? 'bg-accent' : 'hover:bg-bg'"
            >
              <span class="h-11 w-11 shrink-0 rounded-full bg-primary-gradient" />
              <span class="min-w-0 flex-1">
                <span class="flex items-center justify-between gap-2">
                  <span class="truncate text-sm font-bold text-text-main">{{ room.name }}</span>
                  <span class="flex shrink-0 items-center gap-1">
                    <span class="text-xs text-text-sub">{{ room.time }}</span>
                    <span
                      v-if="room.unreadCount"
                      class="flex h-5 w-5 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-white"
                    >
                      {{ room.unreadCount }}
                    </span>
                  </span>
                </span>
                <span class="mt-1 flex items-center gap-1 text-xs text-text-sub">
                  <span aria-hidden="true">{{ room.productIcon }}</span>
                  <span class="truncate font-semibold">{{ room.productName }}</span>
                </span>
                <p class="mt-1 truncate text-xs text-text-sub">
                  {{ room.lastMessage }}
                </p>
                <span
                  v-if="room.statusBadge"
                  class="mt-2 inline-flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-[11px] font-semibold text-green-600"
                >
                  ✓ {{ room.statusBadge }}
                </span>
              </span>
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
          class="flex min-h-[520px] items-center justify-center"
        >
          <div class="max-w-sm text-center">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-bg text-text-sub">
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
            <p class="mt-4 text-base font-bold text-text-main">
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
