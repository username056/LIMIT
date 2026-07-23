import { apiClient } from './client'

export function loginWithEmail(email, password) {
  return apiClient.post('/auth/sessions', { email, password })
}

export function completeSocialLogin(provider, authorizationCode, redirectUri, state) {
  return apiClient.post(`/auth/social-sessions/${provider}`, {
    authorizationCode,
    redirectUri,
    state,
  })
}

export function beginSocialLogin(provider, redirectUri) {
  return apiClient.post(`/auth/social-authorizations/${provider}`, { redirectUri })
}

export function signupWithEmail(payload) {
  return apiClient.post('/members', payload)
}

export function completeSocialSignup(payload) {
  return apiClient.post('/auth/social-signups', payload)
}

export function requestEmailVerification(email) {
  return apiClient.post('/auth/email-verification-requests', { email })
}

export function verifyEmail(token) {
  return apiClient.post('/auth/email-verifications', { token })
}

export function checkEmailAvailability(email) {
  return apiClient.get(`/auth/email-availability?email=${encodeURIComponent(email)}`)
}

export function checkNicknameAvailability(nickname) {
  return apiClient.get(`/auth/nickname-availability?nickname=${encodeURIComponent(nickname)}`)
}

export function refreshSession() {
  return apiClient.post('/auth/token-refreshes')
}

export function logout() {
  return apiClient.post('/auth/session-revocations')
}

export function getSocialAccounts() {
  return apiClient.get('/members/me/social-accounts')
}

export function unlinkSocialAccount(socialAccountId) {
  return apiClient.delete(`/members/me/social-accounts/${socialAccountId}`)
}

export function beginSocialLink(provider, redirectUri) {
  return apiClient.post(`/members/me/social-authorizations/${provider}`, { redirectUri })
}

export function completeSocialLink(provider, authorizationCode, redirectUri, state) {
  return apiClient.post(`/members/me/social-accounts/${provider}`, {
    authorizationCode,
    redirectUri,
    state,
  })
}
