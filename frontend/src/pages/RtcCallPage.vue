<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import {
  endRtcSession, getRtcCall, getRtcSession, issueRtcJoinToken,
  markRtcConnected, respondRtcCall, signalingSocketUrl,
} from '../api/rtc'

const route = useRoute()
const call = ref(null)
const session = ref(null)
const localVideo = ref(null)
const remoteVideo = ref(null)
const status = ref('loading')
const errorMessage = ref('')
const requestMessage = ref('')
const overallMemo = ref('')
const checklist = reactive([])
let socket
let peer
let localStream
let joinInfo
let connectedRecorded = false

function send(type, payload = {}) {
  if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type, payload }))
}

async function createOffer(iceRestart = false) {
  const offer = await peer.createOffer({ iceRestart })
  await peer.setLocalDescription(offer)
  send('offer', offer)
}

async function handleSignal(event) {
  const message = JSON.parse(event.data)
  if (message.type === 'peer-ready' && joinInfo.offerer) await createOffer()
  if (message.type === 'offer') {
    await peer.setRemoteDescription(message.payload)
    const answer = await peer.createAnswer()
    await peer.setLocalDescription(answer)
    send('answer', answer)
  }
  if (message.type === 'answer') await peer.setRemoteDescription(message.payload)
  if (message.type === 'ice-candidate' && message.payload?.candidate) {
    await peer.addIceCandidate(message.payload)
  }
  if (message.type === 'ice-restart' && joinInfo.offerer) await createOffer(true)
  if (message.type === 'inspection-request') requestMessage.value = message.payload?.instruction || ''
  if (message.type === 'peer-left') status.value = 'reconnecting'
  if (message.type === 'hangup') status.value = 'peer-ended'
}

function cleanup() {
  socket?.close()
  peer?.close()
  localStream?.getTracks().forEach((track) => track.stop())
  socket = null
  peer = null
  localStream = null
}

async function connect() {
  cleanup()
  status.value = 'media-request'
  errorMessage.value = ''
  try {
    joinInfo = await issueRtcJoinToken(session.value.sessionId)
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    await nextTick()
    localVideo.value.srcObject = localStream
    peer = new RTCPeerConnection({
      iceServers: joinInfo.iceServers.map((server) => ({
        urls: server.urls, username: server.username || undefined, credential: server.credential || undefined,
      })),
    })
    localStream.getTracks().forEach((track) => peer.addTrack(track, localStream))
    peer.ontrack = (event) => { remoteVideo.value.srcObject = event.streams[0] }
    peer.onicecandidate = (event) => { if (event.candidate) send('ice-candidate', event.candidate) }
    peer.onconnectionstatechange = async () => {
      status.value = peer.connectionState
      if (peer.connectionState === 'connected' && !connectedRecorded) {
        connectedRecorded = true
        await markRtcConnected(session.value.sessionId, 'P2P')
      }
      if (peer.connectionState === 'failed') {
        status.value = 'reconnecting'
        peer.restartIce()
        send('ice-restart')
        if (joinInfo.offerer) await createOffer(true)
      }
    }
    socket = new WebSocket(signalingSocketUrl(joinInfo))
    socket.onopen = () => { status.value = 'waiting-peer' }
    socket.onmessage = (event) => { handleSignal(event).catch(showError) }
    socket.onerror = () => { status.value = 'connection-failed' }
    socket.onclose = () => { if (!['ended', 'peer-ended'].includes(status.value)) status.value = 'reconnecting' }
  } catch (error) {
    showError(error)
    status.value = 'connection-failed'
  }
}

function showError(error) {
  errorMessage.value = error.message || '통화 연결 중 오류가 발생했습니다.'
}

function requestInspection(item) {
  const instruction = `${item.name} 부위 또는 작동 과정을 화면 가까이에서 보여 주세요.`
  requestMessage.value = instruction
  send('inspection-request', { checklistItemId: item.checklistItemId, instruction })
}

async function finish() {
  try {
    session.value = await endRtcSession(session.value.sessionId, {
      endReason: 'COMPLETED',
      memo: overallMemo.value || null,
      checklistResults: checklist.map((item) => ({
        checklistItemId: item.checklistItemId,
        confirmed: item.confirmed,
        note: item.note || null,
      })),
    })
    send('hangup')
    status.value = 'ended'
    cleanup()
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
    session.value = await getRtcSession(call.value.rtcSessionId)
    checklist.splice(0, checklist.length, ...session.value.checklistItems.map((item) => ({ ...item })))
    overallMemo.value = session.value.memo || ''
    if (session.value.status === 'ENDED') status.value = 'ended'
    else await connect()
  } catch (error) {
    showError(error)
    status.value = 'connection-failed'
  }
}

onMounted(load)
onBeforeUnmount(cleanup)
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1200px] px-5 py-8">
      <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
            LIVE CALL
          </p>
          <h1 class="mt-2 text-2xl font-bold text-text-main">
            1:1 상품 실시간 확인
          </h1><p class="text-sm text-text-sub">
            상태: {{ status }} · 전체 통화는 녹화되지 않습니다.
          </p>
        </div>
        <div class="flex gap-2">
          <BaseButton
            v-if="status === 'connection-failed' || status === 'reconnecting'"
            variant="outline"
            @click="connect"
          >
            재입장
          </BaseButton><BaseButton
            v-if="session && status !== 'ended'"
            @click="finish"
          >
            확인 저장 후 종료
          </BaseButton>
        </div>
      </div>
      <p
        v-if="errorMessage"
        role="alert"
        class="mb-4 rounded-md bg-red-50 p-3 text-red-700"
      >
        {{ errorMessage }}
      </p>
      <p
        v-if="requestMessage"
        class="mb-4 rounded-md bg-blue-50 p-3 font-medium text-blue-800"
      >
        실시간 요청: {{ requestMessage }}
      </p>
      <div class="grid gap-5 lg:grid-cols-[1.5fr_1fr]">
        <section class="grid gap-4 sm:grid-cols-2">
          <div class="relative aspect-video overflow-hidden rounded-xl bg-black">
            <video
              ref="remoteVideo"
              autoplay
              playsinline
              class="h-full w-full object-cover"
            /><span class="absolute bottom-2 left-2 rounded bg-black/60 px-2 py-1 text-xs text-white">상대 화면</span>
          </div>
          <div class="relative aspect-video overflow-hidden rounded-xl bg-black">
            <video
              ref="localVideo"
              autoplay
              muted
              playsinline
              class="h-full w-full object-cover"
            /><span class="absolute bottom-2 left-2 rounded bg-black/60 px-2 py-1 text-xs text-white">내 화면</span>
          </div>
        </section>
        <BaseCard class="p-5">
          <h2 class="font-bold text-text-main">
            확인 체크리스트
          </h2>
          <div class="mt-4 max-h-[520px] space-y-4 overflow-y-auto">
            <div
              v-for="item in checklist"
              :key="item.checklistItemId"
              class="rounded-lg border border-border p-3"
            >
              <label class="flex items-start gap-2"><input
                v-model="item.confirmed"
                type="checkbox"
                class="mt-1"
              ><span><strong>{{ item.name }}</strong><small class="mt-1 block text-text-sub">{{ item.captureGuide }}</small></span></label>
              <input
                v-model.trim="item.note"
                maxlength="500"
                placeholder="확인 메모"
                class="mt-3 w-full rounded border border-border px-3 py-2 text-sm"
              >
              <BaseButton
                class="mt-2"
                variant="outline"
                @click="requestInspection(item)"
              >
                이 부위 실시간 요청
              </BaseButton>
            </div>
          </div>
          <textarea
            v-model.trim="overallMemo"
            maxlength="1000"
            rows="3"
            placeholder="통화 전체 메모"
            class="mt-4 w-full rounded border border-border p-3 text-sm"
          />
        </BaseCard>
      </div>
    </main>
  </DefaultLayout>
</template>
