import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '../LoginPage.vue'

const mocks = vi.hoisted(() => ({
  loginWithEmail: vi.fn(),
  setAuthSession: vi.fn(),
  startOAuthLogin: vi.fn(),
  routerPush: vi.fn(),
  routeQuery: {},
}))

vi.mock('../../api/auth', () => ({
  loginWithEmail: mocks.loginWithEmail,
}))

vi.mock('../../auth/session', () => ({
  setAuthSession: mocks.setAuthSession,
}))

vi.mock('../../auth/oauth', () => ({
  startOAuthLogin: mocks.startOAuthLogin,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({ push: mocks.routerPush }),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}))

const global = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    AuthShell: { template: '<section><slot /></section>' },
    BaseCard: { template: '<div><slot /></div>' },
    SocialProviderButton: true,
    RouterLink: {
      props: ['to'],
      template: '<a><slot /></a>',
    },
  },
}

function loginError(code, message, status) {
  return Object.assign(new Error(message), { code, status })
}

async function submitLogin(wrapper) {
  await wrapper.get('input[type="email"]').setValue(' member@example.com ')
  await wrapper.get('input[type="password"]').setValue('wrong-password')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginPage', () => {
  afterEach(() => {
    mocks.routeQuery = {}
    vi.resetAllMocks()
  })

  it('빈 입력은 API를 호출하지 않고 로그인 실패 이유를 표시한다', async () => {
    const wrapper = mount(LoginPage, { global })

    await wrapper.get('form').trigger('submit')

    expect(mocks.loginWithEmail).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('이메일과 비밀번호를 모두 입력해 주세요.')
  })

  it('잘못된 이메일 또는 비밀번호 안내를 로그인 폼 바로 아래에 표시한다', async () => {
    mocks.loginWithEmail.mockRejectedValue(
      loginError('AUTH001', '서버 오류 메시지', 401),
    )
    const wrapper = mount(LoginPage, { global })

    await submitLogin(wrapper)

    expect(mocks.loginWithEmail).toHaveBeenCalledWith('member@example.com', 'wrong-password')
    const alert = wrapper.get('form + [role="alert"]')
    expect(alert.text()).toContain('로그인할 수 없습니다.')
    expect(alert.text()).toContain('이메일 또는 비밀번호가 올바르지 않습니다.')
  })

  it('이메일 미인증 계정에는 인증이 필요하다는 이유를 표시한다', async () => {
    mocks.loginWithEmail.mockRejectedValue(
      loginError('AUTH009', '이메일 인증이 필요합니다.', 403),
    )
    const wrapper = mount(LoginPage, { global })

    await submitLogin(wrapper)

    expect(wrapper.get('[role="alert"]').text()).toContain(
      '가입할 때 받은 인증 메일을 확인해 주세요.',
    )
  })

  it('서버에 연결하지 못하면 네트워크 확인 안내를 표시한다', async () => {
    mocks.loginWithEmail.mockRejectedValue(new TypeError('Failed to fetch'))
    const wrapper = mount(LoginPage, { global })

    await submitLogin(wrapper)

    expect(wrapper.get('[role="alert"]').text()).toContain(
      '네트워크 상태를 확인한 뒤 다시 시도해 주세요.',
    )
  })
})
