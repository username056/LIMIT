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
 * @property {string|null} sellerStatus
 * @property {string|null} emailVerifiedAt
 * @property {string|null} lastLoginAt
 * @property {string} createdAt
 * @property {string|null} profileImageUrl 올리지 않았으면 null. 화면은 닉네임 첫 글자로 대신한다.
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

/*
  프로필 사진은 상품 사진과 같은 세 단계로 올립니다.
  ---------------------------------------------------------------------------
  1) 서버에 자리를 물어 presigned URL을 받고  2) 그 주소로 파일을 직접 PUT 하고
  3) 다 올렸다고 알립니다. 파일이 서버를 거치지 않아 업로드가 빠르고, 서버가 큰
  파일을 들고 있지 않습니다.
*/
export function createProfileImageUploadUrl(payload) {
  return apiClient.post('/members/me/profile-image/upload-url', payload)
}

export function completeProfileImage(objectKey) {
  return apiClient.put('/members/me/profile-image', { objectKey })
}

export function deleteProfileImage() {
  return apiClient.delete('/members/me/profile-image')
}
