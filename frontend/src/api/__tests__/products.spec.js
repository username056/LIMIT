import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createDeviceModel,
  createProduct,
  deleteProduct,
  generateChecklist,
  getDeviceModels,
  getMyProduct,
  getMyProducts,
  getProduct,
  transitionProductStatus,
  updateProduct,
} from '../products'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data = null, meta = null) {
  return { ok: true, json: vi.fn().mockResolvedValue({ data, meta }) }
}

describe('products api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('상품 목록과 카탈로그 조건을 쿼리 문자열로 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([]))
    vi.stubGlobal('fetch', fetchMock)

    await getMyProducts({ status: 'DRAFT', page: 0, size: 20 })
    await getDeviceModels({ categoryId: 1, keyword: 'Galaxy' })

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/members/me/products?status=DRAFT&page=0&size=20`)
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE_URL}/device-models?categoryId=1&keyword=Galaxy`)
  })

  it('상품 생성·체크리스트 생성·조회·수정·상태 전환·삭제 경로를 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ productId: 1001 }))
    vi.stubGlobal('fetch', fetchMock)
    const payload = { categoryId: 1, deviceModelId: 101, name: 'Galaxy S24' }

    await createProduct(payload)
    await generateChecklist({ deviceModelId: 101, confirmedFeatures: [] })
    await getProduct(1001)
    await getMyProduct(1001)
    await updateProduct(1001, { name: '수정 상품' })
    await transitionProductStatus(1001, 'ON_SALE', '검수 완료')
    await deleteProduct(1001)

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      [`${API_BASE_URL}/products`, 'POST'],
      [`${API_BASE_URL}/checklist-generations`, 'POST'],
      [`${API_BASE_URL}/products/1001`, 'GET'],
      [`${API_BASE_URL}/members/me/products/1001`, 'GET'],
      [`${API_BASE_URL}/products/1001`, 'PATCH'],
      [`${API_BASE_URL}/products/1001/status-transitions`, 'POST'],
      [`${API_BASE_URL}/products/1001`, 'DELETE'],
    ])
  })

  it('직접 입력한 기기 모델을 등록한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ deviceModelId: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const payload = {
      categoryId: 10,
      manufacturer: 'LG',
      modelName: 'gram 16',
      modelCode: '16Z90S',
      osFamily: 'WINDOWS',
    }

    await createDeviceModel(payload)

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/device-models`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    )
  })
})
