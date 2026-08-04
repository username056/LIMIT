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

/*
  프로필 사진을 바꾸면 헤더 아이콘도 바로 따라가야 합니다.
  헤더는 세션의 member만 보고 그리므로, 마이페이지에서 사진을 바꾼 뒤 이 함수로
  세션 쪽 주소도 같이 갈아 줍니다. 이게 없으면 새로고침할 때까지 예전 사진이 남습니다.
*/
export function setSessionProfileImage(profileImageUrl) {
  if (!session.value?.member) return
  session.value = {
    ...session.value,
    member: { ...session.value.member, profileImageUrl: profileImageUrl || null },
  }
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

    session.value = tokens
    if (!tokens.member) {
      // 일부 세션 복원(refresh) 응답에는 회원 정보가 없어 헤더 등에서 로그인 상태가
      // 반영되지 않는 문제가 있었습니다. accessToken을 먼저 세팅해 인증된 상태로
      // 프로필을 조회한 뒤 병합합니다.
      try {
        const profile = await getMyProfile()
        session.value = {
          ...tokens,
          member: {
            memberId: profile.memberId,
            nickname: profile.nickname,
            roles: profile.roles,
            profileImageUrl: profile.profileImageUrl || null,
          },
        }
      } catch {
        // 프로필 조회 실패는 세션 자체를 무효화하지 않습니다(헤더 표시에만 영향).
      }
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
