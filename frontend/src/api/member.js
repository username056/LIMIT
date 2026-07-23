import { apiClient } from './client'

/**
 * @typedef {Object} MemberProfileResponse
 * @property {number} memberId
 * @property {string} email
 * @property {string} nickname
 * @property {string|null} phone
 * @property {string} status
 * @property {string} authType
 * @property {string[]} roles
 * @property {string|null} emailVerifiedAt
 * @property {string|null} lastLoginAt
 * @property {string} createdAt
 */

export function getMyProfile() {
  return apiClient.get('/members/me')
}

export function updateMyProfile(payload) {
  return apiClient.patch('/members/me', payload)
}

export function changeMyPassword(currentPassword, newPassword) {
  return apiClient.patch('/members/me/password', { currentPassword, newPassword })
}
