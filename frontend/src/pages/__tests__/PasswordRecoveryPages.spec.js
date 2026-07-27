import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ForgotPasswordPage from '../ForgotPasswordPage.vue'
import ResetPasswordPage from '../ResetPasswordPage.vue'

const mocks = vi.hoisted(() => ({
  requestPasswordReset: vi.fn(),
  resetPassword: vi.fn(),
  routerReplace: vi.fn(),
  routeQuery: {},
}))

vi.mock('../../api/auth', () => ({
  requestPasswordReset: mocks.requestPasswordReset,
  resetPassword: mocks.resetPassword,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({ replace: mocks.routerReplace }),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}))

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    AuthShell: { template: '<section><slot /></section>' },
    RouterLink: {
      props: ['to'],
      template: '<a><slot /></a>',
    },
  },
}

describe('Password recovery pages', () => {
  afterEach(() => {
    mocks.routeQuery = {}
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('계정 존재 여부를 노출하지 않고 메일 요청 완료 상태를 표시한다', async () => {
    mocks.requestPasswordReset.mockResolvedValue()
    const wrapper = mount(ForgotPasswordPage, { global })

    await wrapper.get('input[type="email"]').setValue('member@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.requestPasswordReset).toHaveBeenCalledWith('member@example.com')
    expect(wrapper.text()).toContain('메일 요청을 접수했습니다')
    expect(wrapper.text()).toContain('가입한 계정이 있다면')
  })

  it('일회용 토큰과 검증된 새 비밀번호를 전송하고 로그인으로 이동한다', async () => {
    mocks.routeQuery = { token: 'one-time-token' }
    mocks.resetPassword.mockResolvedValue()
    const replaceState = vi.spyOn(window.history, 'replaceState')
    const wrapper = mount(ResetPasswordPage, { global })
    await flushPromises()

    expect(replaceState).toHaveBeenCalledWith({}, document.title, '/reset-password')

    const inputs = wrapper.findAll('input[type="password"]')
    await inputs[0].setValue('NewPassword456!')
    await inputs[1].setValue('NewPassword456!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.resetPassword).toHaveBeenCalledWith('one-time-token', 'NewPassword456!')
    expect(mocks.routerReplace).toHaveBeenCalledWith({
      name: 'login',
      query: { passwordChanged: '1' },
    })
  })

  it('토큰이 없는 링크에는 재요청 안내를 표시한다', async () => {
    const wrapper = mount(ResetPasswordPage, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('재설정 링크를 확인해 주세요')
    expect(wrapper.find('form').exists()).toBe(false)
  })
})
