import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getAdminDeviceModelProductMaterials,
  getAdminDeviceModelProducts,
  getAdminDeviceModelResearches,
  getAdminDeviceModels,
  updateAdminDeviceModelStatus,
} from '../admin'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function ok(data = null) {
  return { ok: true, json: vi.fn().mockResolvedValue({ data, meta: null }) }
}

describe('admin model api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('모델 검색·필터·정렬·페이징 조건을 서버로 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ content: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await getAdminDeviceModels({
      keyword: 'Galaxy S25',
      categoryId: 10,
      isActive: false,
      researchStatus: 'FAILED',
      page: 2,
      size: 20,
      sort: 'updatedAt,desc',
    })

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${API_BASE_URL}/admin/device-models?keyword=Galaxy+S25&categoryId=10&isActive=false&researchStatus=FAILED&page=2&size=20&sort=updatedAt%2Cdesc`,
    )
  })

  it('상태 변경과 연관 상품·조사·자료 조회 경로를 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}))
    vi.stubGlobal('fetch', fetchMock)
    const statusPayload = {
      isActive: false,
      reason: '중복 모델',
      replacementModelId: 203,
    }

    await updateAdminDeviceModelStatus(202, statusPayload)
    await getAdminDeviceModelProducts(202, { page: 0, size: 10, sort: 'createdAt,desc' })
    await getAdminDeviceModelResearches(202, { page: 1, size: 10 })
    await getAdminDeviceModelProductMaterials(202, 901)

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      [`${API_BASE_URL}/admin/device-models/202/status`, 'PATCH'],
      [`${API_BASE_URL}/admin/device-models/202/products?page=0&size=10&sort=createdAt%2Cdesc`, 'GET'],
      [`${API_BASE_URL}/admin/device-models/202/researches?page=1&size=10`, 'GET'],
      [`${API_BASE_URL}/admin/device-models/202/products/901/materials`, 'GET'],
    ])
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(statusPayload)
  })
})
