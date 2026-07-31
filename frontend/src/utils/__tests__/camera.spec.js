import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cameraErrorMessage,
  captureFrameToFile,
  hasCameraDevice,
  isCameraSupported,
} from '../camera'

const originalNavigator = globalThis.navigator
const originalSecureContext = globalThis.isSecureContext

function stubNavigator(mediaDevices) {
  Object.defineProperty(globalThis, 'navigator', {
    value: { mediaDevices },
    configurable: true,
    writable: true,
  })
}

afterEach(() => {
  Object.defineProperty(globalThis, 'navigator', {
    value: originalNavigator,
    configurable: true,
    writable: true,
  })
  Object.defineProperty(globalThis, 'isSecureContext', {
    value: originalSecureContext,
    configurable: true,
    writable: true,
  })
  vi.restoreAllMocks()
})

describe('isCameraSupported', () => {
  it('getUserMedia가 있으면 지원한다고 본다', () => {
    stubNavigator({ getUserMedia: vi.fn() })
    expect(isCameraSupported()).toBe(true)
  })

  it('getUserMedia가 없으면 지원하지 않는다', () => {
    stubNavigator({})
    expect(isCameraSupported()).toBe(false)
  })

  // getUserMedia는 HTTPS나 localhost에서만 동작합니다. 그렇지 않으면 눌러도 실패만 합니다.
  it('보안 컨텍스트가 아니면 지원하지 않는다', () => {
    stubNavigator({ getUserMedia: vi.fn() })
    Object.defineProperty(globalThis, 'isSecureContext', {
      value: false,
      configurable: true,
      writable: true,
    })
    expect(isCameraSupported()).toBe(false)
  })
})

describe('hasCameraDevice', () => {
  it('카메라가 달려 있으면 true', async () => {
    stubNavigator({
      getUserMedia: vi.fn(),
      enumerateDevices: vi.fn().mockResolvedValue([
        { kind: 'audioinput' },
        { kind: 'videoinput' },
      ]),
    })
    await expect(hasCameraDevice()).resolves.toBe(true)
  })

  it('카메라가 없으면 false — 촬영 버튼을 감추기 위한 판단이다', async () => {
    stubNavigator({
      getUserMedia: vi.fn(),
      enumerateDevices: vi.fn().mockResolvedValue([{ kind: 'audioinput' }]),
    })
    await expect(hasCameraDevice()).resolves.toBe(false)
  })

  // 확인 자체가 실패했을 때 버튼을 감추면 카메라가 있어도 못 쓰게 됩니다.
  it('기기 목록 조회가 실패하면 버튼을 감추지 않는다', async () => {
    stubNavigator({
      getUserMedia: vi.fn(),
      enumerateDevices: vi.fn().mockRejectedValue(new Error('보안 제한')),
    })
    await expect(hasCameraDevice()).resolves.toBe(true)
  })

  it('지원하지 않는 브라우저면 false', async () => {
    stubNavigator({})
    await expect(hasCameraDevice()).resolves.toBe(false)
  })
})

// 브라우저가 주는 오류 이름만으로는 무엇을 고쳐야 할지 알 수 없습니다.
describe('cameraErrorMessage', () => {
  it('권한 거부는 허용 방법을 알려준다', () => {
    expect(cameraErrorMessage({ name: 'NotAllowedError' })).toContain('허용')
  })

  it('카메라가 없으면 파일 업로드로 안내한다', () => {
    expect(cameraErrorMessage({ name: 'NotFoundError' })).toContain('파일 업로드')
  })

  it('다른 앱이 쓰고 있으면 그 사실을 알려준다', () => {
    expect(cameraErrorMessage({ name: 'NotReadableError' })).toContain('다른 앱')
  })

  it('모르는 오류도 다음 행동을 알려준다', () => {
    expect(cameraErrorMessage({ name: 'WeirdError' })).toContain('파일 업로드')
  })
})

describe('captureFrameToFile', () => {
  // 업로드 경로가 File을 받으므로, 촬영 결과도 File이어야 압축·검증·업로드를 그대로 씁니다.
  it('비디오 화면을 jpeg File로 만든다', async () => {
    const drawImage = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      width: 0,
      height: 0,
      getContext: () => ({ drawImage }),
      toBlob: (callback) => callback(new Blob(['x'], { type: 'image/jpeg' })),
    })

    const file = await captureFrameToFile({ videoWidth: 640, videoHeight: 480 }, 'shot.jpg')

    expect(file).toBeInstanceOf(File)
    expect(file.name).toBe('shot.jpg')
    expect(file.type).toBe('image/jpeg')
    expect(drawImage).toHaveBeenCalled()
  })

  it('카메라 화면이 아직 준비되지 않으면 알려준다', async () => {
    await expect(captureFrameToFile({ videoWidth: 0, videoHeight: 0 }))
      .rejects.toThrow('아직 준비되지 않았습니다')
  })

  it('사진을 만들지 못하면 다시 찍도록 알린다', async () => {
    vi.spyOn(document, 'createElement').mockReturnValue({
      width: 0,
      height: 0,
      getContext: () => ({ drawImage: vi.fn() }),
      toBlob: (callback) => callback(null),
    })

    await expect(captureFrameToFile({ videoWidth: 640, videoHeight: 480 }))
      .rejects.toThrow('다시 촬영해 주세요')
  })
})
