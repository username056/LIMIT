import { readonly, ref } from 'vue'
import { getMyProfile } from '../api/member'

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
    const tokens = body?.data || null
    if (!tokens?.accessToken) return false

    // 세션 복원(refresh) 응답에는 로그인 응답과 달리 회원 정보가 없어, 헤더 등에서
    // 로그인 상태가 반영되지 않는 문제가 있었습니다. accessToken을 먼저 세팅해
    // 인증된 상태로 프로필을 조회한 뒤 병합합니다.
    session.value = tokens
    try {
      const profile = await getMyProfile()
      session.value = {
        ...tokens,
        member: { memberId: profile.memberId, nickname: profile.nickname, roles: profile.roles },
      }
    } catch {
      // 프로필 조회 실패는 세션 자체를 무효화하지 않습니다(헤더 표시에만 영향).
    }
    return true
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
