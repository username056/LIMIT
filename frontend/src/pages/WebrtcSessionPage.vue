<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import { getChatRoom } from '../mock/chat'

// WIREFRAME MOCK: 실제 WebRTC 연결/시그널링이 없어 화면 흐름만 먼저 확인할 수 있도록
// 카메라 영상은 placeholder로, 체크리스트·채팅은 mock 데이터로 구현했습니다.

const route = useRoute()
const router = useRouter()
const room = computed(() => getChatRoom(route.params.roomId))

const CHECKLIST_ITEMS = [
  '제품 외관 전면 상태 스캔',
  '제품 외관 후면 힌지 균열 여부',
  'LCD 화면 잔상 및 디스플레이 결점',
  '키보드 동시 입력 및 트랙패드 터치 감도',
  '좌우측 데이터/충전 포트 연결성 체크',
  '스테레오 내장 스피커 및 마이크 성능',
]
const selectedChecklistIndex = ref(3)

const isMuted = ref(false)
const isSpeakerOn = ref(true)

const elapsedSeconds = ref(0)
let timer = null
onMounted(() => {
  timer = setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
})
onBeforeUnmount(() => {
  clearInterval(timer)
})
const elapsedLabel = computed(() => {
  const minutes = String(Math.floor(elapsedSeconds.value / 60)).padStart(2, '0')
  const seconds = String(elapsedSeconds.value % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
})

const messages = ref([
  { id: 1, from: 'seller', text: '화면 테두리 힌지 부위를 다시 보여주시면 확인이 더 용이합니다.', time: '12:03' },
  { id: 2, from: 'me', text: '네, 지금 화면 테두리 힌지 부분을 자세히 보여드릴게요.', time: '12:04' },
  { id: 3, from: 'seller', text: '확인했습니다. 다음은 키보드 동시 입력 및 트랙패드 터치 감도를 확인해 주세요.', time: '12:05' },
])
const messageInput = ref('')
let nextMessageId = 100

function sendMessage() {
  const text = messageInput.value.trim()
  if (!text) return
  messages.value.push({ id: nextMessageId++, from: 'me', text, time: elapsedLabel.value })
  messageInput.value = ''
}

function endSession() {
  router.push({ name: 'chat', params: { roomId: route.params.roomId } })
}
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-8 lg:px-10">
      <p class="mb-6 rounded-md bg-accent px-4 py-2 text-xs font-semibold text-primary-dark">
        1대1 화상 검증 페이지 · {{ room?.productName }} · WIREFRAME MOCK (실제 WebRTC 연결 전까지 화면 흐름만 보여줍니다)
      </p>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div>
          <div class="relative aspect-video overflow-hidden rounded-lg bg-gray-900">
            <div class="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-gray-700 to-gray-900 text-sm text-white/50">
              라이브 카메라 영상 자리 (mock)
            </div>

            <div class="absolute left-3 top-3 flex items-center gap-2">
              <span class="rounded-md bg-black/60 px-2.5 py-1 text-xs font-semibold text-white">라이브 비디오 스트림</span>
              <span class="rounded-md bg-green-500 px-2.5 py-1 text-xs font-bold text-white">{{ elapsedLabel }}</span>
            </div>

            <button
              type="button"
              class="absolute right-3 top-3 rounded-md border border-white/30 bg-black/40 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-red-500/80"
              @click="endSession"
            >
              세션 종료
            </button>

            <div class="absolute bottom-3 left-3 flex gap-2">
              <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-full bg-black/50 text-white hover:bg-black/70"
                :aria-label="isMuted ? '음소거 해제' : '음소거'"
                @click="isMuted = !isMuted"
              >
                <svg
                  v-if="!isMuted"
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M12 3a3 3 0 00-3 3v6a3 3 0 006 0V6a3 3 0 00-3-3zM5 11a1 1 0 10-2 0 9 9 0 008 8.94V21H8a1 1 0 100 2h8a1 1 0 100-2h-3v-1.06A9 9 0 0021 11a1 1 0 10-2 0 7 7 0 01-14 0z" />
                </svg>
                <svg
                  v-else
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M16.5 12c0-.4-.05-.78-.13-1.15l-1.55 1.55c0 .53-.43.96-.96.96h-.13l1.83 1.83A4.983 4.983 0 0016.5 12zM19 11h-1.7c0 .74-.16 1.43-.43 2.06l1.23 1.23A6.943 6.943 0 0019 11zM4.27 3L3 4.27l6 6V11a3 3 0 003 3c.13 0 .25-.02.37-.04l1.2 1.2c-.51.24-1.07.34-1.57.34-2.21 0-4-1.79-4-4H5c0 2.76 2.24 5 5 5v3h4v-3c.6-.08 1.17-.24 1.7-.49L18.73 21 20 19.73 4.27 3z" />
                </svg>
              </button>
              <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-full bg-black/50 text-white hover:bg-black/70"
                :aria-label="isSpeakerOn ? '스피커 끄기' : '스피커 켜기'"
                @click="isSpeakerOn = !isSpeakerOn"
              >
                <svg
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M4 9v6h4l5 5V4L8 9H4z" />
                  <path
                    v-if="isSpeakerOn"
                    d="M16.5 12a4.5 4.5 0 00-2.5-4.03v8.06A4.5 4.5 0 0016.5 12z"
                  />
                </svg>
              </button>
            </div>
          </div>

          <p class="mt-3 rounded-md bg-accent px-4 py-3 text-xs text-primary-dark">
            안내: 모든 체크리스트 점검 완료 또는 확인 거절 시 세션 종료 단계를 밟을 수 있습니다.
          </p>
        </div>

        <div class="space-y-6">
          <BaseCard>
            <h2 class="mb-3 text-sm font-bold text-text-main">
              노트북 검증 체크 리스트
            </h2>
            <div class="space-y-2">
              <button
                v-for="(item, index) in CHECKLIST_ITEMS"
                :key="item"
                type="button"
                class="w-full rounded-md border px-3 py-2.5 text-left text-sm transition-colors"
                :class="selectedChecklistIndex === index
                  ? 'border-primary font-semibold text-text-main'
                  : 'border-border text-text-sub hover:border-primary'"
                @click="selectedChecklistIndex = index"
              >
                {{ item }}
              </button>
            </div>
          </BaseCard>

          <BaseCard
            :padded="false"
            class="flex h-[360px] flex-col overflow-hidden"
          >
            <h2 class="border-b border-border px-4 py-3 text-sm font-bold text-text-main">
              실시간 1:1 채팅
            </h2>
            <TransitionGroup
              name="msg"
              tag="div"
              class="flex-1 space-y-3 overflow-y-auto px-4 py-3"
            >
              <div
                v-for="message in messages"
                :key="message.id"
                class="flex items-end gap-2"
                :class="message.from === 'me' ? 'flex-row-reverse' : ''"
              >
                <span
                  class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold text-white"
                  :class="message.from === 'me' ? 'bg-primary' : 'bg-text-sub'"
                >
                  {{ message.from === 'me' ? 'B' : 'S' }}
                </span>
                <div class="max-w-[75%]">
                  <div
                    class="rounded-lg px-3 py-2 text-sm leading-5"
                    :class="message.from === 'me' ? 'bg-primary-gradient text-white' : 'bg-bg text-text-main'"
                  >
                    {{ message.text }}
                  </div>
                  <p
                    class="mt-0.5 text-[11px] text-text-sub"
                    :class="message.from === 'me' ? 'text-right' : ''"
                  >
                    {{ message.time }}
                  </p>
                </div>
              </div>
            </TransitionGroup>
            <form
              class="flex gap-2 border-t border-border p-3"
              @submit.prevent="sendMessage"
            >
              <input
                v-model="messageInput"
                type="text"
                placeholder="메시지를 입력하세요..."
                class="w-full min-w-0 rounded-md border border-border px-3 py-2 text-sm text-text-main outline-none focus:border-primary"
              >
              <button
                type="submit"
                aria-label="전송"
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary-gradient text-white"
              >
                <svg
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M3 20l18-8L3 4v6l12 2-12 2z" />
                </svg>
              </button>
            </form>
          </BaseCard>
        </div>
      </div>
    </div>
  </DefaultLayout>
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
