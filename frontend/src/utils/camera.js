// 상품 등록 2단계에서 그 자리에서 사진을 찍을 수 있게 하는 도우미입니다.
// 촬영은 카메라가 있는 기기에서만 되므로, 쓸 수 있는지 먼저 확인한 뒤 버튼을 보여줍니다.

// getUserMedia는 HTTPS나 localhost에서만 동작합니다(브라우저 보안 정책).
// 로컬 개발과 배포(https://l1mit.shop) 모두 해당되지만, 그렇지 않은 환경에서는 촬영을 감춥니다.
export function isCameraSupported() {
  if (typeof navigator === 'undefined') return false
  if (typeof window !== 'undefined' && window.isSecureContext === false) return false
  return typeof navigator.mediaDevices?.getUserMedia === 'function'
}

/**
 * 카메라가 실제로 달려 있는지 확인합니다.
 *
 * 권한을 주기 전에는 기기 목록의 label이 비어 있을 수 있지만 kind는 알 수 있어서,
 * 촬영 버튼을 보여줄지 판단하는 데는 충분합니다. 확인에 실패하면 버튼을 감추지 않고
 * true로 둡니다 — 눌렀을 때 권한 요청으로 이어지는 편이 낫습니다.
 */
export async function hasCameraDevice() {
  if (!isCameraSupported()) return false
  if (typeof navigator.mediaDevices.enumerateDevices !== 'function') return true
  try {
    const devices = await navigator.mediaDevices.enumerateDevices()
    return devices.some((device) => device.kind === 'videoinput')
  } catch {
    return true
  }
}

// 뒷면 카메라를 먼저 요청합니다. 기기 상태를 찍는 일이라 폰에서는 뒷면이 맞고,
// 노트북처럼 뒷면이 없으면 브라우저가 있는 카메라로 알아서 넘어갑니다.
export function openCameraStream() {
  return navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' } },
    audio: false,
  })
}

export function stopCameraStream(stream) {
  stream?.getTracks?.().forEach((track) => track.stop())
}

/**
 * 카메라 권한 실패를 사용자가 무엇을 해야 하는지 아는 말로 바꿉니다.
 * 브라우저가 주는 NotAllowedError 같은 이름만으로는 무엇을 고쳐야 할지 알 수 없습니다.
 */
export function cameraErrorMessage(error) {
  switch (error?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return '카메라 사용이 차단되어 있습니다. 브라우저 주소창의 카메라 아이콘에서 허용으로 바꿔 주세요.'
    case 'NotFoundError':
    case 'OverconstrainedError':
      return '사용할 수 있는 카메라를 찾지 못했습니다. 파일 업로드를 이용해 주세요.'
    case 'NotReadableError':
      return '다른 앱이 카메라를 쓰고 있습니다. 그 앱을 닫고 다시 시도해 주세요.'
    default:
      return '카메라를 열지 못했습니다. 파일 업로드를 이용해 주세요.'
  }
}

/**
 * 비디오 화면을 캔버스에 그려 사진 File로 만듭니다.
 *
 * 업로드 경로가 File을 받도록 되어 있어서, 촬영도 파일 선택과 같은 흐름을 타게 맞춥니다.
 * 그래야 압축·검증·S3 업로드를 그대로 재사용할 수 있습니다.
 */
export function captureFrameToFile(videoElement, filename = 'capture.jpg') {
  const width = videoElement?.videoWidth
  const height = videoElement?.videoHeight
  if (!width || !height) {
    return Promise.reject(new Error('카메라 화면이 아직 준비되지 않았습니다. 잠시 후 다시 눌러 주세요.'))
  }

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  canvas.getContext('2d').drawImage(videoElement, 0, 0, width, height)

  return new Promise((resolve, reject) => {
    if (typeof canvas.toBlob !== 'function') {
      reject(new Error('이 브라우저에서는 촬영을 지원하지 않습니다. 파일 업로드를 이용해 주세요.'))
      return
    }
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('사진을 만들지 못했습니다. 다시 촬영해 주세요.'))
          return
        }
        resolve(new File([blob], filename, { type: 'image/jpeg' }))
      },
      'image/jpeg',
      0.92,
    )
  })
}
