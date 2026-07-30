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

export function getProductDraftProgress(productId) {
  return apiClient.get(`/products/${productId}/draft-progress`)
}

export function updateProductDraftProgress(productId, payload) {
  return apiClient.patch(`/products/${productId}/draft-progress`, payload)
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

export function getEvidenceHistory(productId, checklistItemId) {
  return apiClient.get(`/products/${productId}/checklist-items/${checklistItemId}/evidence`)
}

export function deleteEvidence(productId, checklistItemId, evidenceId) {
  return apiClient.delete(`/products/${productId}/checklist-items/${checklistItemId}/evidence/${evidenceId}`)
}

export function uploadToPresignedUrl(presignedUrl, file, requiredHeaders = {}, onProgress = () => {}) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('PUT', presignedUrl)
    Object.entries(requiredHeaders).forEach(([name, value]) => request.setRequestHeader(name, value))
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    })
    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) resolve()
      else reject(new Error(`S3 upload failed with status ${request.status}`))
    })
    request.addEventListener('error', () => reject(new Error('S3 upload network error')))
    request.addEventListener('abort', () => reject(new Error('S3 upload aborted')))
    request.send(file)
  })
}

export function createProductImageUploadUrl(productId, payload) {
  return apiClient.post(`/products/${productId}/images/upload-urls`, payload)
}

export function completeProductImage(productId, payload) {
  return apiClient.post(`/products/${productId}/images`, payload)
}

export function getProductImages(productId) {
  return apiClient.get(`/products/${productId}/images`)
}

export function deleteProductImage(productId, imageId) {
  return apiClient.delete(`/products/${productId}/images/${imageId}`)
}

export function updateProductImageOrder(productId, payload) {
  return apiClient.put(`/products/${productId}/images/order`, payload)
}

export function getHandoverGuide(modelId) {
  return apiClient.get(`/device-models/${modelId}/handover-guide`)
}

export function createReinspectionRequest(productId, payload) {
  return apiClient.post(`/listings/${productId}/reinspection-requests`, payload)
}

export function getReinspectionRequest(requestKey) {
  return apiClient.get(`/reinspection-requests/${requestKey}`)
}

export function completeReinspectionRequest(requestKey) {
  return apiClient.post(`/reinspection-requests/${requestKey}/complete`, {})
}

export function getMyReinspectionRequests() {
  return apiClient.get('/members/me/reinspection-requests')
}
