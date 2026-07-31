import { ref } from 'vue'

// 카메라 스트림이 열리는 것(PERMISSION_TEST)과 실제로 화면이 살아있는지(USER_ACTION_VERIFY)를
// 분리한다. 렌즈가 막혀 있어도 스트림 자체는 정상 발급되므로, 프레임 변화가 실제로 있는지까지 봐야
// "작동한다"고 말할 수 있다.
export function useCameraCheck() {
  const status = ref('idle') // idle | requesting | live | passed | failed
  const detail = ref('')
  let stream = null

  async function start(videoEl) {
    status.value = 'requesting'
    detail.value = ''
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: true })
    } catch {
      status.value = 'failed'
      detail.value = '카메라 권한이 거부되었거나 장치를 찾을 수 없습니다.'
      return false
    }

    if (videoEl) {
      videoEl.srcObject = stream
      await videoEl.play().catch(() => {})
    }
    status.value = 'live'

    const hasVariance = await detectFrameVariance(videoEl)
    stopStream()
    status.value = hasVariance ? 'passed' : 'failed'
    if (!hasVariance) {
      detail.value = '화면 변화가 감지되지 않았습니다. 렌즈 가림을 확인하고 다시 시도해 주세요.'
    }
    return hasVariance
  }

  function detectFrameVariance(videoEl) {
    return new Promise((resolve) => {
      if (!videoEl) {
        resolve(false)
        return
      }
      const canvas = document.createElement('canvas')
      canvas.width = 64
      canvas.height = 48
      const ctx = canvas.getContext('2d', { willReadFrequently: true })
      let previous = null
      let varianceSeen = false
      let sampleCount = 0
      const maxSamples = 6

      const timer = setInterval(() => {
        sampleCount += 1
        try {
          ctx.drawImage(videoEl, 0, 0, canvas.width, canvas.height)
          const frame = ctx.getImageData(0, 0, canvas.width, canvas.height).data
          if (previous) {
            let diff = 0
            for (let i = 0; i < frame.length; i += 40) diff += Math.abs(frame[i] - previous[i])
            if (diff > 200) varianceSeen = true
          }
          previous = frame
        } catch {
          // 디코딩 전 첫 프레임 등 일시적 오류는 무시하고 다음 샘플에서 재시도한다.
        }
        if (sampleCount >= maxSamples) {
          clearInterval(timer)
          resolve(varianceSeen)
        }
      }, 500)
    })
  }

  function stopStream() {
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
  }

  return { status, detail, start, stop: stopStream }
}
