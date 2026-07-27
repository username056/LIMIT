import { apiClient } from './client'

/**
 * @typedef {Object} ChatRoomSummaryResponse
 * @property {number} roomId
 * @property {number} listingId
 * @property {number} counterpartId
 * @property {string} status
 * @property {number|null} lastMessageId
 * @property {number} lastMessageSequence
 * @property {string|null} lastMessageAt
 * @property {number} unreadCount
 * @property {string} createdAt
 */

/**
 * @typedef {Object} ChatMessageResponse
 * @property {number} messageId
 * @property {number} roomSequence
 * @property {number} senderId
 * @property {string} clientMessageId
 * @property {string} type - 'TEXT' | 'IMAGE' | 'VIDEO' | 'SYSTEM'
 * @property {string} content
 * @property {string} status - 'SENT' | 'DELETED'
 * @property {string} sentAt
 */

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function getChatRooms(params = {}) {
  return apiClient.get(`/chat-rooms${query(params)}`)
}

export function getChatMessages(roomId, params = {}) {
  return apiClient.get(`/chat-rooms/${roomId}/messages${query(params)}`)
}

export function createOrGetChatRoom(listingId) {
  return apiClient.post(`/listings/${listingId}/chat-rooms`, {})
}
