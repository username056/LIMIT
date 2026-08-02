import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppHeader from '../AppHeader.vue'
import { useAuthSession } from '../../auth/session'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', fullPath: '/' }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../../api/auth', () => ({ logout: vi.fn() }))

vi.mock('../../auth/session', () => ({
  useAuthSession: vi.fn(),
  clearAuthSession: vi.fn(),
}))

vi.mock('../../auth/sellerGate', () => ({
  SELL_ENTRY_PATH: '/seller/products/new',
  useSellerGate: () => ({
    isSellerNoticeOpen: { value: false },
    goToSell: vi.fn(),
    goToSellerApply: vi.fn(),
    closeSellerNotice: vi.fn(),
  }),
}))

const globalOptions = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a><slot /></a>' },
    SellerNoticeModal: true,
  },
}

function signedIn(member) {
  useAuthSession.mockReturnValue({ value: member ? { member } : null })
}

describe('AppHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 눌러 봐야 로그인 화면으로 튕기는 항목은 처음부터 보여주지 않습니다.
  it('로그인하지 않으면 채팅과 실시간 확인을 감춘다', () => {
    signedIn(null)
    const wrapper = mount(AppHeader, { global: globalOptions })

    expect(wrapper.text()).not.toContain('채팅')
    expect(wrapper.text()).not.toContain('실시간 확인')
    expect(wrapper.text()).toContain('전체 상품')
  })

  it('로그인하면 채팅과 실시간 확인을 보여준다', () => {
    signedIn({ nickname: '회원' })
    const wrapper = mount(AppHeader, { global: globalOptions })

    expect(wrapper.text()).toContain('채팅')
    expect(wrapper.text()).toContain('실시간 확인')
  })

  // scoped 스타일에 display를 두면 Tailwind의 md:hidden을 이겨 햄버거가 항상 보였습니다.
  it('햄버거 버튼은 md 이상에서 숨도록 유틸리티로 제어한다', () => {
    signedIn(null)
    const wrapper = mount(AppHeader, { global: globalOptions })

    const menuButton = wrapper.get('button[aria-label="메뉴 열기"]')
    expect(menuButton.classes()).toContain('md:hidden')
    expect(menuButton.classes()).toContain('flex')
  })
})
