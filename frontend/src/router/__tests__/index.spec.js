import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAuthSession, setAuthSession } from '../../auth/session'
import router from '../index'

describe('router seller authorization', () => {
  beforeEach(async () => {
    vi.stubGlobal('scrollTo', vi.fn())
    clearAuthSession()
    await router.replace('/')
  })

  afterEach(() => {
    clearAuthSession()
    vi.unstubAllGlobals()
  })

  it('세션이 없으면 보호된 판매자 화면에서 로그인으로 이동한다', async () => {
    await router.push('/seller/products')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/seller/products')
  })

  it('일반 회원이 판매자 화면에 접근하면 판매자 등록으로 이동한다', async () => {
    setAuthSession({
      accessToken: 'stub',
      member: { roles: ['MEMBER'], sellerStatus: null },
    })

    await router.push('/seller/dashboard')

    expect(router.currentRoute.value.name).toBe('seller-apply')
  })

  it('판매자는 등록 화면 대신 상품 관리로 이동한다', async () => {
    setAuthSession({
      accessToken: 'stub',
      member: { roles: ['MEMBER', 'SELLER'], sellerStatus: 'ACTIVE' },
    })

    await router.push('/seller/apply')

    expect(router.currentRoute.value.name).toBe('seller-products')
  })
})
