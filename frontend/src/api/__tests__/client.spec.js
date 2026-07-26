import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiClient } from '../client'
import { captureApiException } from '../../monitoring/sentry'

vi.mock('../../monitoring/sentry', () => ({
  captureApiException: vi.fn(),
}))

function errorResponse(status, code = 'SERVER_ERROR') {
  return {
    ok: false,
    status,
    headers: new Headers({ 'X-Trace-Id': 'trace-123' }),
    json: vi.fn().mockResolvedValue({
      error: {
        code,
        message: '요청 실패',
        fieldErrors: null,
      },
      traceId: 'trace-123',
    }),
  }
}

describe('apiClient Sentry reporting', () => {
  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('네트워크 오류를 요청 본문이나 인증정보 없이 보고한다', async () => {
    const networkError = new TypeError('Failed to fetch')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(networkError))

    await expect(apiClient.get('/listings?keyword=private')).rejects.toBe(networkError)
    expect(captureApiException).toHaveBeenCalledWith(networkError, {
      method: 'GET',
      path: '/listings?keyword=private',
    })
  })

  it('서버 5xx 오류에 traceId를 붙여 보고한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(503)))

    await expect(apiClient.post('/listings', { title: 'private' })).rejects.toBeInstanceOf(ApiError)
    expect(captureApiException).toHaveBeenCalledWith(
      expect.any(ApiError),
      {
        method: 'POST',
        path: '/listings',
        status: 503,
        code: 'SERVER_ERROR',
        traceId: 'trace-123',
      },
    )
  })

  it('사용자 입력 오류인 4xx는 Sentry에 보고하지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(400, 'INVALID_INPUT')))

    await expect(apiClient.post('/listings', {})).rejects.toBeInstanceOf(ApiError)
    expect(captureApiException).not.toHaveBeenCalled()
  })
})
