import { apiClient } from './client'

function pageQuery(page = 0, size = 20) {
  return `?page=${page}&size=${size}`
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

export function getAdminAccounts(page = 0, size = 20) {
  return apiClient.get(`/admin/accounts${pageQuery(page, size)}`)
}

export function createAdminAccount(payload) {
  return apiClient.post('/admin/accounts', payload)
}

export function updateAdminAccount(adminId, payload) {
  return apiClient.patch(`/admin/accounts/${adminId}`, payload)
}
