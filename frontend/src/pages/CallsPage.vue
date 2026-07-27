<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import { getMyRtcCalls, respondRtcCall } from '../api/rtc'

const router = useRouter()
const calls = ref([])
const isLoading = ref(true)
const errorMessage = ref('')

async function load() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    calls.value = await getMyRtcCalls()
  } catch (error) {
    errorMessage.value = error.message || '영상 확인 요청을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function respond(call, accepted) {
  try {
    const updated = await respondRtcCall(call.callId, accepted, accepted ? null : '요청 거절')
    await load()
    if (accepted && updated.rtcSessionId) {
      await router.push({ name: 'rtc-call', params: { callId: call.callId } })
    }
  } catch (error) {
    errorMessage.value = error.message || '요청을 처리하지 못했습니다.'
  }
}

onMounted(load)
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[900px] px-6 py-10">
      <h1 class="text-2xl font-bold text-text-main">
        1:1 실시간 확인
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        판매자와 구매자만 통화에 참여할 수 있으며 통화는 녹화되지 않습니다.
      </p>
      <p
        v-if="errorMessage"
        role="alert"
        class="mt-5 rounded-md bg-red-50 p-3 text-red-700"
      >
        {{ errorMessage }}
      </p>
      <p
        v-if="isLoading"
        class="py-12 text-center text-text-sub"
      >
        요청을 불러오는 중입니다.
      </p>
      <div
        v-else
        class="mt-6 grid gap-4"
      >
        <BaseCard
          v-for="call in calls"
          :key="call.callId"
          class="p-5"
        >
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p class="font-semibold text-text-main">
                영상 확인 요청 #{{ call.callId }}
              </p>
              <p class="mt-1 text-sm text-text-sub">
                {{ call.memo || '등록된 상품 상태를 실시간으로 확인합니다.' }}
              </p>
              <p class="mt-2 text-xs text-text-sub">
                상태: {{ call.status }} · {{ call.incoming ? '받은 요청' : '보낸 요청' }}
              </p>
            </div>
            <div class="flex gap-2">
              <template v-if="call.status === 'PROPOSED' && call.incoming">
                <BaseButton @click="respond(call, true)">
                  수락
                </BaseButton>
                <BaseButton
                  variant="outline"
                  @click="respond(call, false)"
                >
                  거절
                </BaseButton>
              </template>
              <BaseButton
                v-if="call.rtcSessionId && ['ACCEPTED', 'COMPLETED'].includes(call.status)"
                :disabled="call.status === 'COMPLETED'"
                @click="router.push({ name: 'rtc-call', params: { callId: call.callId } })"
              >
                통화 입장
              </BaseButton>
            </div>
          </div>
        </BaseCard>
        <BaseCard
          v-if="!calls.length"
          class="py-14 text-center text-text-sub"
        >
          영상 확인 요청이 없습니다.
        </BaseCard>
      </div>
    </main>
  </DefaultLayout>
</template>
