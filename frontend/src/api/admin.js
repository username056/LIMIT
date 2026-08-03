import { apiClient } from './client'

function pageQuery(page = 0, size = 20) {
  return `?page=${page}&size=${size}`
}

function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const suffix = search.toString()
  return suffix ? `?${suffix}` : ''
}

export function loginAdmin(email, password) {
  return apiClient.post('/admin/sessions', { email, password })
}

export function getAdminMembers(page = 0, size = 20) {
  return apiClient.get(`/admin/members${pageQuery(page, size)}`)
}

export function getAdminMember(memberId) {
  return apiClient.get(`/admin/members/${memberId}`)
}

export function getMemberRestrictions(memberId, page = 0, size = 20) {
  return apiClient.get(`/admin/members/${memberId}/restrictions${pageQuery(page, size)}`)
}

export function createMemberRestriction(memberId, payload) {
  return apiClient.post(`/admin/members/${memberId}/restrictions`, payload)
}

export function releaseMemberRestriction(restrictionId, releaseReason) {
  return apiClient.patch(`/admin/member-restrictions/${restrictionId}`, { releaseReason })
}

export function getAdminActionLogs(page = 0, size = 20) {
  return apiClient.get(`/admin/action-logs${pageQuery(page, size)}`)
}

export function getAdminActionLog(actionLogId) {
  return apiClient.get(`/admin/action-logs/${actionLogId}`)
}

export function updateAdminActionLog(actionLogId, payload) {
  return apiClient.patch(`/admin/action-logs/${actionLogId}`, payload)
}

export function getAdminAccounts(page = 0, size = 20) {
  return apiClient.get(`/admin/accounts${pageQuery(page, size)}`)
}

export function createAdminAccount(payload) {
  return apiClient.post('/admin/accounts', payload)
}

export function updateAdminAccount(adminId, payload) {
  return apiClient.patch(`/admin/accounts/${adminId}`, payload)
}

export function getChecklistResearches(status = 'PENDING_REVIEW') {
  return apiClient.get(`/admin/checklist-researches?status=${encodeURIComponent(status)}`)
}

export function approveChecklistResearch(researchId, payload) {
  return apiClient.post(`/admin/checklist-researches/${researchId}/approval`, payload)
}

export function rejectChecklistResearch(researchId, payload) {
  return apiClient.post(`/admin/checklist-researches/${researchId}/rejection`, payload)
}

export function retryChecklistResearch(researchId) {
  return apiClient.post(`/admin/checklist-researches/${researchId}/retry`)
}

export function getAdminDeviceModels(params = {}) {
  return apiClient.get(`/admin/device-models${query(params)}`)
}

export function getAdminDeviceModel(modelId) {
  return apiClient.get(`/admin/device-models/${modelId}`)
}

export function updateAdminDeviceModel(modelId, payload) {
  return apiClient.patch(`/admin/device-models/${modelId}`, payload)
}

export function updateAdminDeviceModelStatus(modelId, payload) {
  return apiClient.patch(`/admin/device-models/${modelId}/status`, payload)
}

export function getAdminDeviceModelProducts(modelId, params = {}) {
  return apiClient.get(`/admin/device-models/${modelId}/products${query(params)}`)
}

export function getAdminDeviceModelResearches(modelId, params = {}) {
  return apiClient.get(`/admin/device-models/${modelId}/researches${query(params)}`)
}

export function getAdminDeviceModelProductMaterials(modelId, productId) {
  return apiClient.get(`/admin/device-models/${modelId}/products/${productId}/materials`)
}

export function researchAdminDeviceModel(modelId) {
  return apiClient.post(`/admin/device-models/${modelId}/researches`)
}

export function getDeviceModelRequests(status = 'PENDING') {
  return apiClient.get(`/admin/device-model-requests?status=${encodeURIComponent(status)}`)
}

export function updateDeviceModelRequest(requestId, payload) {
  return apiClient.patch(`/admin/device-model-requests/${requestId}`, payload)
}

export function approveDeviceModelRequest(requestId, payload) {
  return apiClient.post(`/admin/device-model-requests/${requestId}/approval`, payload)
}

export function rejectDeviceModelRequest(requestId, payload) {
  return apiClient.post(`/admin/device-model-requests/${requestId}/rejection`, payload)
}
