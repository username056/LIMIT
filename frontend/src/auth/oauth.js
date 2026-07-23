import { beginSocialLink, beginSocialLogin } from '../api/auth'

const PROVIDERS = new Set(['google', 'kakao', 'naver'])

export function oauthRedirectUri(provider) {
  const base = (import.meta.env.VITE_OAUTH_REDIRECT_BASE_URL || window.location.origin).replace(/\/$/, '')
  return `${base}/auth/callback/${provider}`
}

export async function startOAuthLogin(provider) {
  if (!isSupportedProvider(provider)) {
    throw new Error('지원하지 않는 소셜 로그인 공급자입니다.')
  }
  sessionStorage.removeItem('limit.oauth.mode')
  const result = await beginSocialLogin(provider, oauthRedirectUri(provider))
  if (!result?.authorizationUrl) {
    throw new Error(`${provider.toUpperCase()} 소셜 로그인이 아직 준비되지 않았습니다.`)
  }
  window.location.assign(result.authorizationUrl)
}

export async function startOAuthLink(provider) {
  if (!isSupportedProvider(provider)) {
    throw new Error('지원하지 않는 소셜 로그인 공급자입니다.')
  }
  sessionStorage.setItem('limit.oauth.mode', 'link')
  try {
    const result = await beginSocialLink(provider, oauthRedirectUri(provider))
    if (!result?.authorizationUrl) {
      throw new Error(`${provider.toUpperCase()} 계정 연동이 아직 준비되지 않았습니다.`)
    }
    window.location.assign(result.authorizationUrl)
  } catch (error) {
    sessionStorage.removeItem('limit.oauth.mode')
    throw error
  }
}

export function consumeOAuthMode() {
  const mode = sessionStorage.getItem('limit.oauth.mode')
  sessionStorage.removeItem('limit.oauth.mode')
  return mode === 'link' ? 'link' : 'login'
}

export function isSupportedProvider(provider) {
  return PROVIDERS.has(provider)
}
