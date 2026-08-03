import { afterEach, describe, expect, it, vi } from 'vitest'
import { createInspectionSession, getInspectionSession } from '../inspectionSessions'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data = null) {
  return { ok: true, json: vi.fn().mockResolvedValue({ data, meta: null }) }
}

describe('inspection sessions api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Windows 자동 검사 세션을 생성하고 상태를 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ sessionKey: 'session-1' }))
    vi.stubGlobal('fetch', fetchMock)

    await createInspectionSession(1001)
    await getInspectionSession('session-1')

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      [`${API_BASE_URL}/inspection-sessions`, 'POST'],
      [`${API_BASE_URL}/inspection-sessions/session-1`, 'GET'],
    ])
  })
})
