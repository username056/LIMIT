import { apiClient } from './client'

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function getProducts(params = {}) {
  return apiClient.getEnvelope(`/products${query(params)}`)
}

export function getProduct(productId) {
  return apiClient.get(`/products/${productId}`)
}

export function getMyProducts(params = {}) {
  return apiClient.getEnvelope(`/members/me/products${query(params)}`)
}

export function getMyProduct(productId) {
  return apiClient.get(`/members/me/products/${productId}`)
}

export function createProduct(payload) {
  return apiClient.post('/products', payload)
}

export function generateChecklist(payload) {
  return apiClient.post('/checklist-generations', payload)
}

export function updateProduct(productId, payload) {
  return apiClient.patch(`/products/${productId}`, payload)
}

export function deleteProduct(productId) {
  return apiClient.delete(`/products/${productId}`)
}

export function transitionProductStatus(productId, targetStatus, reason) {
  return apiClient.post(`/products/${productId}/status-transitions`, { targetStatus, reason })
}

export function getDeviceCategories(params = {}) {
  return apiClient.get(`/device-categories${query(params)}`)
}

export function getDeviceModels(params = {}) {
  return apiClient.get(`/device-models${query(params)}`)
}

export function getDeviceModel(modelId) {
  return apiClient.get(`/device-models/${modelId}`)
}

export function getChecklistTemplate(modelId) {
  return apiClient.get(`/device-models/${modelId}/checklist-template`)
}

export function getProductChecklist(productId) {
  return apiClient.get(`/products/${productId}/checklist-items`)
}

export function createEvidenceUploadUrl(productId, checklistItemId, payload) {
  return apiClient.post(`/products/${productId}/checklist-items/${checklistItemId}/upload-urls`, payload)
}

export function completeEvidence(productId, checklistItemId, payload) {
  return apiClient.post(`/products/${productId}/checklist-items/${checklistItemId}/evidence`, payload)
}

export function getHandoverGuide(modelId) {
  return apiClient.get(`/device-models/${modelId}/handover-guide`)
}

export function requestRecapture(productId, checklistItemId, payload) {
  return apiClient.post(`/products/${productId}/checklist-items/${checklistItemId}/recapture-requests`, payload)
}
