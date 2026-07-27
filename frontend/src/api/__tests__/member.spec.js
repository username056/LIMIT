import { afterEach, describe, expect, it, vi } from 'vitest'
import { changeMyPassword, getMyProfile, updateMyProfile } from '../member'
import { clearAuthSession, setAuthSession } from '../../auth/session'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data) {
  return {
    ok: true,
    json: vi.fn().mockResolvedValue({ data, meta: null }),
  }
}

describe('member api', () => {
  afterEach(() => {
    clearAuthSession()
    vi.unstubAllGlobals()
  })

  it('인증 헤더로 내 회원 정보를 조회한다', async () => {
    setAuthSession({ accessToken: 'member-token' })
    const fetchMock = vi.fn().mockResolvedValue(ok({ memberId: 1, nickname: 'limit' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getMyProfile()).resolves.toMatchObject({ memberId: 1 })
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/members/me`, expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({ Authorization: 'Bearer member-token' }),
    }))
  })

  it('변경한 프로필 필드와 비밀번호를 각각 계약 경로로 전송한다', async () => {
    const currentValue = 'Password123!'
    const nextValue = 'NewPassword456!'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(ok({ memberId: 1, nickname: 'new-name' }))
      .mockResolvedValueOnce(ok(null))
    vi.stubGlobal('fetch', fetchMock)

    await updateMyProfile({ nickname: 'new-name' })
    await changeMyPassword(currentValue, nextValue)

    expect(fetchMock).toHaveBeenNthCalledWith(1, `${API_BASE_URL}/members/me`, expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ nickname: 'new-name' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `${API_BASE_URL}/members/me/password`, expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({
        currentPassword: currentValue,
        newPassword: nextValue,
      }),
    }))
  })
})
