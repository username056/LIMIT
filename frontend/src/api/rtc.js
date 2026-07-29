import { apiClient } from './client'

export function createChatRoom(listingId) {
  return apiClient.post(`/listings/${listingId}/chat-rooms`, {})
}

export function requestRtcCall(roomId, payload = {}) {
  return apiClient.post(`/chat-rooms/${roomId}/calls`, payload)
}

export function getMyRtcCalls() {
  return apiClient.get('/calls')
}

export function getRtcCall(callId) {
  return apiClient.get(`/calls/${callId}`)
}

export function respondRtcCall(callId, accepted, reason = null) {
  return apiClient.post(`/calls/${callId}/response`, { accepted, reason })
}

export function updateRtcCall(callId, payload) {
  return apiClient.patch(`/calls/${callId}`, payload)
}

export function cancelRtcCall(callId, reason = '') {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return apiClient.delete(`/calls/${callId}${query}`)
}

export function getRtcSession(sessionId) {
  return apiClient.get(`/rtc-sessions/${sessionId}`)
}

export function issueRtcJoinToken(sessionId) {
  return apiClient.post(`/rtc-sessions/${sessionId}/join`, {})
}

export function markRtcConnected(sessionId, connectionType = 'P2P') {
  return apiClient.post(`/rtc-sessions/${sessionId}/connected`, { connectionType })
}

export function endRtcSession(sessionId, payload) {
  return apiClient.post(`/rtc-sessions/${sessionId}/end`, payload)
}

export function signalingSocketUrl(join) {
  const configured = import.meta.env.VITE_WS_BASE_URL
  const apiBase = import.meta.env.VITE_API_BASE_URL
  const origin = configured || (apiBase?.startsWith('http') ? new URL(apiBase).origin : window.location.origin)
  const url = new URL(join.signalingUrl, origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.searchParams.set('token', join.joinToken)
  return url.toString()
}
