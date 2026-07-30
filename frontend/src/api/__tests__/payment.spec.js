import { afterEach, describe, expect, it, vi } from 'vitest'
import { confirmPayment, createPayment, getPayment } from '../payment'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data = null, meta = null) {
  return { ok: true, json: vi.fn().mockResolvedValue({ data, meta }) }
}

describe('payment api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('결제 생성·승인·조회 경로와 메서드를 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ paymentId: 500 }))
    vi.stubGlobal('fetch', fetchMock)

    await createPayment({ listingId: 1001, method: 'CARD', idempotencyKey: 'idem-1' })
    await confirmPayment(500, { paymentKey: 'pk-1', orderId: 'PAY-500-1', amount: 650000 })
    await getPayment(500)

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      [`${API_BASE_URL}/payments`, 'POST'],
      [`${API_BASE_URL}/payments/500/confirm`, 'POST'],
      [`${API_BASE_URL}/payments/500`, 'GET'],
    ])
  })
})
