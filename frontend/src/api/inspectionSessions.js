import { apiClient } from './client'

export function createInspectionSession(listingId) {
  return apiClient.post('/inspection-sessions', { listingId })
}

export function getInspectionSession(sessionKey) {
  return apiClient.get(`/inspection-sessions/${sessionKey}`)
}
