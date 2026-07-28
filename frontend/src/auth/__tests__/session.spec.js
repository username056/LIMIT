import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  clearAuthSession,
  getAccessToken,
  getSessionMember,
  hasRole,
  restoreAuthSession,
  setAuthSession,
} from '../session'

describe('auth session', () => {
  afterEach(() => {
    clearAuthSession()
    vi.unstubAllGlobals()
  })

  it('토큰 갱신 응답의 DB 역할과 판매자 상태를 세션에 복원한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        data: {
          accessToken: 'renewed-token',
          member: {
            memberId: 1,
            nickname: 'seller',
            roles: ['MEMBER', 'SELLER'],
            sellerStatus: 'ACTIVE',
          },
        },
      }),
    }))

    expect(await restoreAuthSession()).toBe(true)
    expect(getAccessToken()).toBe('renewed-token')
    expect(hasRole('SELLER')).toBe(true)
    expect(getSessionMember().sellerStatus).toBe('ACTIVE')
  })

  it('토큰 갱신 실패 시 이전 세션을 제거한다', async () => {
    setAuthSession({ accessToken: 'stale-token', member: { roles: ['MEMBER'] } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))

    expect(await restoreAuthSession()).toBe(false)
    expect(getAccessToken()).toBeNull()
  })
})
