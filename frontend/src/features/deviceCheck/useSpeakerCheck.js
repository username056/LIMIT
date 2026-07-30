import { ref } from 'vue'

// 브라우저는 소리가 실제로 났는지 읽을 방법이 없어서, 재생까지는 자동으로 하고 마지막 판정만
// 판매자의 자기 응답("들렸어요?")으로 받는다. 다른 실동작 항목보다 신뢰도가 낮음을 인지하고 쓴다.
export function useSpeakerCheck() {
  const status = ref('idle') // idle | playing | awaitingConfirmation | passed | failed
  const detail = ref('')
  let audioContext = null

  function playTone() {
    status.value = 'playing'
    detail.value = ''
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    audioContext = new AudioContextClass()
    const oscillator = audioContext.createOscillator()
    oscillator.type = 'sine'
    oscillator.frequency.value = 440
    oscillator.connect(audioContext.destination)
    oscillator.start()
    oscillator.stop(audioContext.currentTime + 1.2)
    oscillator.onended = () => {
      status.value = 'awaitingConfirmation'
    }
  }

  function confirmHeard(heard) {
    status.value = heard ? 'passed' : 'failed'
    if (!heard) detail.value = '판매자가 소리를 듣지 못했다고 응답했습니다.'
    audioContext?.close().catch(() => {})
  }

  return { status, detail, playTone, confirmHeard }
}
