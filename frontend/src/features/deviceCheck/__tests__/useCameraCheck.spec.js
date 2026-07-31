import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useCameraCheck } from '../useCameraCheck'

function mockGetUserMedia(impl) {
  vi.stubGlobal('navigator', {
    ...navigator,
    mediaDevices: { getUserMedia: impl },
  })
}

function fakeStream() {
  return { getTracks: () => [{ stop: vi.fn() }] }
}

// jsdom은 실제 캔버스 렌더링을 지원하지 않아 getContext가 null을 반환한다.
// drawImage/getImageData만 우리 코드가 읽는 값이므로 그만큼만 가짜로 채운다.
function mockCanvasContext(getImageDataImpl) {
  return vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    drawImage: vi.fn(),
    getImageData: getImageDataImpl,
  })
}

function fakeVideoEl() {
  return { play: vi.fn().mockResolvedValue(undefined), srcObject: null }
}

describe('useCameraCheck', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('카메라 권한이 거부되면 failed로 끝난다', async () => {
    mockGetUserMedia(vi.fn().mockRejectedValue(new Error('denied')))
    const check = useCameraCheck()

    const result = await check.start(fakeVideoEl())

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('권한')
  })

  it('프레임 변화가 감지되면 passed가 된다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    let call = 0
    mockCanvasContext(() => {
      call += 1
      const value = call % 2 === 0 ? 255 : 0
      return { data: new Uint8ClampedArray(64 * 48 * 4).fill(value) }
    })
    const check = useCameraCheck()

    const pending = check.start(fakeVideoEl())
    await vi.advanceTimersByTimeAsync(500 * 6)
    const result = await pending

    expect(result).toBe(true)
    expect(check.status.value).toBe('passed')
  })

  it('프레임 변화가 없으면 failed와 안내 문구를 남긴다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    mockCanvasContext(() => ({ data: new Uint8ClampedArray(64 * 48 * 4).fill(128) }))
    const check = useCameraCheck()

    const pending = check.start(fakeVideoEl())
    await vi.advanceTimersByTimeAsync(500 * 6)
    const result = await pending

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('렌즈')
  })

  it('videoEl이 없으면 프레임을 확인할 수 없어 즉시 failed 처리한다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    const check = useCameraCheck()

    const result = await check.start(undefined)

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
  })
})
