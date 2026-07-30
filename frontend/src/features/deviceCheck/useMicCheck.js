import { ref } from 'vue'

// 스트림이 열리는 것만으로는 무음 상태를 못 걸러내서, 실제로 입력 레벨이 움직이는지까지 확인한다.
export function useMicCheck() {
  const status = ref('idle') // idle | requesting | listening | passed | failed
  const detail = ref('')
  const level = ref(0)
  let stream = null
  let audioContext = null
  let rafId = null

  async function start() {
    status.value = 'requesting'
    detail.value = ''
    level.value = 0
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      status.value = 'failed'
      detail.value = '마이크 권한이 거부되었거나 장치를 찾을 수 없습니다.'
      return false
    }

    status.value = 'listening'
    const heard = await measurePeakLevel()
    stopStream()
    status.value = heard ? 'passed' : 'failed'
    if (!heard) {
      detail.value = '입력 레벨이 감지되지 않았습니다. 마이크에 대고 말해 주세요.'
    }
    return heard
  }

  function measurePeakLevel() {
    return new Promise((resolve) => {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext
      audioContext = new AudioContextClass()
      const source = audioContext.createMediaStreamSource(stream)
      const analyser = audioContext.createAnalyser()
      analyser.fftSize = 512
      source.connect(analyser)
      const data = new Uint8Array(analyser.frequencyBinCount)
      const deadlineMs = 4000
      const startedAt = performance.now()

      const sample = () => {
        analyser.getByteTimeDomainData(data)
        let frameMax = 0
        for (let i = 0; i < data.length; i += 1) {
          frameMax = Math.max(frameMax, Math.abs(data[i] - 128))
        }
        level.value = Math.max(level.value, frameMax)
        if (level.value > 12) {
          resolve(true)
          return
        }
        if (performance.now() - startedAt > deadlineMs) {
          resolve(false)
          return
        }
        rafId = requestAnimationFrame(sample)
      }
      sample()
    })
  }

  function stopStream() {
    if (rafId) cancelAnimationFrame(rafId)
    rafId = null
    stream?.getTracks().forEach((track) => track.stop())
    audioContext?.close().catch(() => {})
    stream = null
    audioContext = null
  }

  return { status, detail, level, start, stop: stopStream }
}
