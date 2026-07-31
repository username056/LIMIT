import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiClient } from '../client'
import { captureApiException } from '../../monitoring/sentry'
import { getAccessToken, restoreAuthSession } from '../../auth/session'

vi.mock('../../monitoring/sentry', () => ({
  captureApiException: vi.fn(),
}))

vi.mock('../../auth/session', () => ({
  getAccessToken: vi.fn(),
  restoreAuthSession: vi.fn(),
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

describe('apiClient 401 자동 재발급', () => {
  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('accessToken 만료(401) 시 refresh 후 원요청을 한 번 재시도한다', async () => {
    getAccessToken.mockReturnValue('expired-token')
    restoreAuthSession.mockResolvedValue(true)

    const successBody = { ok: true, status: 200, json: vi.fn().mockResolvedValue({ data: { id: 1 } }) }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(errorResponse(401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(successBody)
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiClient.get('/listings/1')).resolves.toEqual({ id: 1 })
    expect(restoreAuthSession).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('refresh 실패 시 원래 401 에러를 그대로 던진다', async () => {
    getAccessToken.mockReturnValue('expired-token')
    restoreAuthSession.mockResolvedValue(false)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(401, 'TOKEN_EXPIRED')))

    await expect(apiClient.get('/listings/1')).rejects.toBeInstanceOf(ApiError)
    expect(restoreAuthSession).toHaveBeenCalledTimes(1)
  })

  it('accessToken 없이 받은 401(로그인 실패 등)에는 refresh를 시도하지 않는다', async () => {
    getAccessToken.mockReturnValue(null)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(401, 'INVALID_CREDENTIALS')))

    await expect(apiClient.post('/auth/logins', {})).rejects.toBeInstanceOf(ApiError)
    expect(restoreAuthSession).not.toHaveBeenCalled()
  })

  it('동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출된다', async () => {
    getAccessToken.mockReturnValue('expired-token')
    let resolveRefresh
    restoreAuthSession.mockReturnValue(new Promise((resolve) => { resolveRefresh = resolve }))

    const successBody = () => ({ ok: true, status: 200, json: vi.fn().mockResolvedValue({ data: { id: 1 } }) })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(errorResponse(401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(errorResponse(401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(successBody())
      .mockResolvedValueOnce(successBody())
    vi.stubGlobal('fetch', fetchMock)

    const p1 = apiClient.get('/listings/1')
    const p2 = apiClient.get('/listings/2')
    await Promise.resolve()
    resolveRefresh(true)

    await expect(Promise.all([p1, p2])).resolves.toEqual([{ id: 1 }, { id: 1 }])
    expect(restoreAuthSession).toHaveBeenCalledTimes(1)
  })
})
