import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMySellerProfile, registerSeller } from '../seller'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data = null) {
  return { ok: true, json: vi.fn().mockResolvedValue({ data, meta: null }) }
}

describe('seller api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('즉시 판매자 등록과 내 판매자 프로필 경로를 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ status: 'ACTIVE' }))
    vi.stubGlobal('fetch', fetchMock)
    const payload = {
      sellerType: 'INDIVIDUAL',
      countryCode: 'KR',
      settlementBankName: '국민은행',
      settlementAccountHolder: '판매자',
      settlementAccountLast4: '1234',
      sellerTermsAccepted: true,
    }

    await registerSeller(payload)
    await getMySellerProfile()

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      [`${API_BASE_URL}/sellers`, 'POST'],
      [`${API_BASE_URL}/sellers/me`, 'GET'],
    ])
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(payload)
  })
})
