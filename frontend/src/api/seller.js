import { apiClient } from './client'

export function registerSeller(payload) {
  return apiClient.post('/sellers', payload)
}

export function getMySellerProfile() {
  return apiClient.get('/sellers/me')
}

// 구매자가 상품 상세에서 판매자를 확인할 때 씁니다. 정산 계좌나 사업자 상호는 담기지 않습니다.
export function getSellerProfile(sellerId) {
  return apiClient.get(`/sellers/${sellerId}`)
}
