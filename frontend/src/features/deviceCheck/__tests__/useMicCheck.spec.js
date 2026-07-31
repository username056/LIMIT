import { afterEach, describe, expect, it, vi } from 'vitest'

import { useMicCheck } from '../useMicCheck'

function mockGetUserMedia(impl) {
  vi.stubGlobal('navigator', {
    ...navigator,
    mediaDevices: { getUserMedia: impl },
  })
}

function fakeStream() {
  return { getTracks: () => [{ stop: vi.fn() }] }
}

// getByteTimeDomainData가 채우는 값만 우리 코드가 읽으므로, 그 값으로 입력 레벨을 흉내낸다.
// 128을 기준으로 편차(|value - 128|)가 커질수록 소리가 큰 것으로 해석된다.
function mockAudioContext(fillValue) {
  const analyser = {
    fftSize: 0,
    frequencyBinCount: 32,
    connect: vi.fn(),
    getByteTimeDomainData: (data) => data.fill(fillValue),
  }
  const context = {
    createMediaStreamSource: vi.fn(() => ({ connect: vi.fn() })),
    createAnalyser: vi.fn(() => analyser),
    close: vi.fn().mockResolvedValue(undefined),
  }
  vi.stubGlobal('AudioContext', vi.fn(() => context))
  return { context, analyser }
}

describe('useMicCheck', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('마이크 권한이 거부되면 failed로 끝난다', async () => {
    mockGetUserMedia(vi.fn().mockRejectedValue(new Error('denied')))
    const check = useMicCheck()

    const result = await check.start()

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('권한')
  })

  it('입력 레벨이 임계값을 넘으면 즉시 passed가 된다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    mockAudioContext(200) // |200-128| = 72 > 12
    const check = useMicCheck()

    const result = await check.start()

    expect(result).toBe(true)
    expect(check.status.value).toBe('passed')
    expect(check.level.value).toBeGreaterThan(12)
  })

  it('입력이 감지되지 않고 타임아웃되면 failed가 된다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    mockAudioContext(128) // 편차 0 → 무음

    // 가짜 타이머 없이 실제 rAF 몇 프레임만 흘려보내되, deadline(4000ms) 판정에 쓰이는
    // performance.now()만 호출마다 큰 폭으로 앞당겨서 짧은 시간 안에 타임아웃을 재현한다.
    let elapsed = 0
    vi.spyOn(performance, 'now').mockImplementation(() => {
      elapsed += 1500
      return elapsed
    })
    const check = useMicCheck()

    const result = await check.start()

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('감지되지 않았습니다')
  })

  it('이 브라우저에서 오디오 분석을 지원하지 않으면 failed와 사유를 남긴다', async () => {
    mockGetUserMedia(vi.fn().mockResolvedValue(fakeStream()))
    vi.stubGlobal('AudioContext', vi.fn(() => {
      throw new Error('unsupported')
    }))
    const check = useMicCheck()

    const result = await check.start()

    expect(result).toBe(false)
    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('지원하지 않습니다')
  })
})
