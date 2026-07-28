import { readonly, ref } from 'vue'

const session = ref(null)
const pendingSocialSignup = ref(null)
const pendingVerificationEmail = ref('')

export function useAuthSession() {
  return readonly(session)
}

export function setAuthSession(authSession) {
  session.value = authSession
}

export function clearAuthSession() {
  session.value = null
}

export function getAccessToken() {
  return session.value?.accessToken || null
}

export function getSessionMember() {
  return session.value?.member || null
}

export function hasRole(role) {
  return getSessionMember()?.roles?.includes(role) || false
}

export async function restoreAuthSession() {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  try {
    const response = await fetch(`${baseUrl}/auth/token-refreshes`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    })
    if (!response.ok) {
      clearAuthSession()
      return false
    }
    const body = await response.json()
    session.value = body?.data || null
    return Boolean(session.value?.accessToken)
  } catch {
    clearAuthSession()
    return false
  }
}

export function setPendingSocialSignup(signup) {
  pendingSocialSignup.value = signup
}

export function usePendingSocialSignup() {
  return readonly(pendingSocialSignup)
}

export function clearPendingSocialSignup() {
  pendingSocialSignup.value = null
}

export function setPendingVerificationEmail(email) {
  pendingVerificationEmail.value = email
}

export function usePendingVerificationEmail() {
  return readonly(pendingVerificationEmail)
}
