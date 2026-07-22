import { afterEach, describe, expect, it, vi } from 'vitest'
import { isSupportedProvider, oauthRedirectUri } from '../oauth'

describe('OAuth flow helpers', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('builds a callback URI from the configured frontend origin', () => {
    vi.stubEnv('VITE_OAUTH_REDIRECT_BASE_URL', 'https://frontend.example.com/')

    expect(oauthRedirectUri('google')).toBe('https://frontend.example.com/auth/callback/google')
  })

  it('falls back to the browser origin when no frontend origin is configured', () => {
    vi.stubEnv('VITE_OAUTH_REDIRECT_BASE_URL', '')

    expect(oauthRedirectUri('naver')).toBe(`${window.location.origin}/auth/callback/naver`)
  })

  it('accepts only supported providers', () => {
    expect(isSupportedProvider('google')).toBe(true)
    expect(isSupportedProvider('kakao')).toBe(true)
    expect(isSupportedProvider('naver')).toBe(true)
    expect(isSupportedProvider('unknown')).toBe(false)
  })
})
