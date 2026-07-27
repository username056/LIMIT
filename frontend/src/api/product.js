import { apiClient } from './client'

/**
 * @typedef {Object} ProductDetailResponse
 * @property {number} productId
 * @property {number} sellerId
 * @property {string} name
 * @property {string} status
 * @property {string} thumbnailUrl
 */

// TODO(상품 API 연동): 백엔드가 실제 상품 데이터를 반환하게 되면
// ProductDetailPage.vue의 mock 표시 데이터를 이 함수의 응답으로 교체하세요.
// 지금은 백엔드가 스텁이라 어떤 productId를 넣어도 고정된 값만 돌아옵니다.
export function getProduct(productId) {
  return apiClient.get(`/products/${productId}`)
}
