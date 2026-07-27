import { afterEach, describe, expect, it, vi } from 'vitest'
import { addFavorite, getFavoriteStatus, getMyFavorites, removeFavorite } from '../favorites'
import { clearAuthSession, setAuthSession } from '../../auth/session'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data, meta = null) {
  return {
    ok: true,
    json: vi.fn().mockResolvedValue({ data, meta }),
  }
}

describe('favorites api', () => {
  afterEach(() => {
    clearAuthSession()
    vi.unstubAllGlobals()
  })

  it('인증 헤더로 관심 상품 목록을 조회한다', async () => {
    setAuthSession({ accessToken: 'test-token' })
    const fetchMock = vi.fn().mockResolvedValue(ok([{ favoriteId: 501, productId: 1001 }]))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getMyFavorites()).resolves.toEqual({
      data: [{ favoriteId: 501, productId: 1001 }],
      meta: null,
    })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/members/me/favorites?page=0&size=20`,
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
      }),
    )
  })

  it('현재 회원의 관심 상품 상태를 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ favorite: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getFavoriteStatus(1001)).resolves.toEqual({ favorite: true })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/products/1001/favorites/me`,
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('관심 상품 등록과 해제를 상품 리소스 경로로 요청한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(ok({ favoriteId: 501, productId: 1001 }))
      .mockResolvedValueOnce({ ok: true, json: vi.fn().mockResolvedValue(null) })
    vi.stubGlobal('fetch', fetchMock)

    await addFavorite(1001)
    await removeFavorite(1001)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      `${API_BASE_URL}/products/1001/favorites`,
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      `${API_BASE_URL}/products/1001/favorites`,
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
