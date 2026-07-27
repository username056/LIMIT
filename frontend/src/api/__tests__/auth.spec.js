import { afterEach, describe, expect, it, vi } from 'vitest'
import { requestPasswordReset, resetPassword } from '../auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function noContent() {
  return {
    ok: true,
    status: 204,
    json: vi.fn().mockRejectedValue(new SyntaxError('No content')),
  }
}

describe('password reset api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('메일 요청과 비밀번호 재설정을 인증 계약 경로로 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(noContent())
    vi.stubGlobal('fetch', fetchMock)

    await requestPasswordReset('member@example.com')
    await resetPassword('one-time-token', 'NewPassword456!')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      `${API_BASE_URL}/auth/password-reset-requests`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'member@example.com' }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      `${API_BASE_URL}/auth/password-resets`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          token: 'one-time-token',
          newPassword: 'NewPassword456!',
        }),
      }),
    )
  })
})
