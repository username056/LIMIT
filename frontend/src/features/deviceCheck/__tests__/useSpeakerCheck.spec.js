import { afterEach, describe, expect, it, vi } from 'vitest'

import { useSpeakerCheck } from '../useSpeakerCheck'

function mockAudioContext({ throwOnConstruct = false } = {}) {
  const oscillator = {
    type: '',
    frequency: { value: 0 },
    connect: vi.fn(),
    start: vi.fn(),
    stop: vi.fn(),
    onended: null,
  }
  const context = {
    currentTime: 0,
    destination: {},
    createOscillator: vi.fn(() => oscillator),
    close: vi.fn().mockResolvedValue(undefined),
  }
  vi.stubGlobal('AudioContext', vi.fn(() => {
    if (throwOnConstruct) throw new Error('unsupported')
    return context
  }))
  return { context, oscillator }
}

describe('useSpeakerCheck', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('톤 재생을 시작하면 playing 상태가 되고, 재생이 끝나면 응답 대기 상태로 넘어간다', () => {
    const { oscillator } = mockAudioContext()
    const check = useSpeakerCheck()

    check.playTone()
    expect(check.status.value).toBe('playing')

    oscillator.onended()
    expect(check.status.value).toBe('awaitingConfirmation')
  })

  it('들렸다고 응답하면 passed로 끝난다', () => {
    mockAudioContext()
    const check = useSpeakerCheck()
    check.playTone()

    check.confirmHeard(true)

    expect(check.status.value).toBe('passed')
  })

  it('못 들었다고 응답하면 failed와 사유를 남긴다', () => {
    mockAudioContext()
    const check = useSpeakerCheck()
    check.playTone()

    check.confirmHeard(false)

    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('듣지 못했다')
  })

  it('브라우저가 AudioContext를 지원하지 않으면 즉시 failed 처리한다', () => {
    mockAudioContext({ throwOnConstruct: true })
    const check = useSpeakerCheck()

    check.playTone()

    expect(check.status.value).toBe('failed')
    expect(check.detail.value).toContain('지원하지 않습니다')
  })
})
