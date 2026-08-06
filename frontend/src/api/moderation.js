import { apiClient } from './client'

export function reportProduct(productId, payload) {
  return apiClient.post(`/products/${productId}/reports`, payload)
}

export function acknowledgeProductWarning(productId) {
  return apiClient.post(`/products/${productId}/moderation-warning-acknowledgements`, {})
}

export function requestProductRestoration(productId, requestNote) {
  return apiClient.post(`/products/${productId}/restoration-requests`, { requestNote })
}
