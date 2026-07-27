import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MyProfilePage from '../MyProfilePage.vue'
import { clearAuthSession, setAuthSession } from '../../auth/session'

const mocks = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  updateMyProfile: vi.fn(),
  changeMyPassword: vi.fn(),
  routerReplace: vi.fn(),
}))

vi.mock('../../api/member', () => ({
  getMyProfile: mocks.getMyProfile,
  updateMyProfile: mocks.updateMyProfile,
  changeMyPassword: mocks.changeMyPassword,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mocks.routerReplace }),
  useRoute: () => ({ path: '/mypage/profile' }),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}))

const profile = {
  memberId: 1,
  email: 'member@example.com',
  nickname: 'limit-user',
  phone: '010****5678',
  status: 'ACTIVE',
  authType: 'LOCAL',
  roles: ['MEMBER'],
  lastLoginAt: '2026-07-20T10:00:00+09:00',
  createdAt: '2026-07-01T10:00:00+09:00',
}

describe('MyProfilePage', () => {
  beforeEach(() => {
    setAuthSession({ accessToken: 'member-token', member: { nickname: 'limit-user' } })
    mocks.getMyProfile.mockResolvedValue(profile)
    mocks.updateMyProfile.mockResolvedValue({
      memberId: 1,
      nickname: 'new-nickname',
      phone: '010****5678',
      updatedAt: '2026-07-23T10:00:00+09:00',
    })
  })

  afterEach(() => {
    clearAuthSession()
    vi.clearAllMocks()
  })

  it('회원 정보를 불러오고 변경된 닉네임만 수정 요청한다', async () => {
    const wrapper = mount(MyProfilePage, {
      global: {
        stubs: {
          MyPageLayout: { template: '<main><slot /></main>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('member@example.com')
    expect(wrapper.text()).toContain('010****5678')

    const nicknameInput = wrapper.find('input[type="text"]')
    await nicknameInput.setValue('new-nickname')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.updateMyProfile).toHaveBeenCalledWith({ nickname: 'new-nickname' })
    expect(wrapper.text()).toContain('회원 정보를 수정했습니다.')
  })
})
