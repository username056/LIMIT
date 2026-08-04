import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MyProfilePage from '../MyProfilePage.vue'
import { clearAuthSession, getSessionMember, setAuthSession } from '../../auth/session'

const mocks = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  updateMyProfile: vi.fn(),
  changeMyPassword: vi.fn(),
  createProfileImageUploadUrl: vi.fn(),
  completeProfileImage: vi.fn(),
  deleteProfileImage: vi.fn(),
  uploadToPresignedUrl: vi.fn(),
  compressImage: vi.fn(),
  routerReplace: vi.fn(),
}))

vi.mock('../../api/member', () => ({
  getMyProfile: mocks.getMyProfile,
  updateMyProfile: mocks.updateMyProfile,
  changeMyPassword: mocks.changeMyPassword,
  createProfileImageUploadUrl: mocks.createProfileImageUploadUrl,
  completeProfileImage: mocks.completeProfileImage,
  deleteProfileImage: mocks.deleteProfileImage,
}))

vi.mock('../../api/products', () => ({
  uploadToPresignedUrl: mocks.uploadToPresignedUrl,
}))

vi.mock('../../utils/mediaOptimize', () => ({
  compressImage: mocks.compressImage,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mocks.routerReplace }),
  useRoute: () => ({ path: '/mypage/profile', query: {} }),
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

    await wrapper.find('button[type="button"]').trigger('click')

    const nicknameInput = wrapper.find('input[type="text"]')
    await nicknameInput.setValue('new-nickname')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.updateMyProfile).toHaveBeenCalledWith({ nickname: 'new-nickname' })
    expect(wrapper.text()).toContain('회원 정보를 수정했습니다.')
  })

  /*
    프로필 사진 올리기는 상품 사진과 같은 세 단계(자리 요청 → 직접 PUT → 완료 통보)를 거칩니다.
    세 단계 중 하나만 빠져도 화면에는 사진이 뜨지만 다음 접속 때 사라지므로, 순서를 함께 봅니다.
  */
  async function mountPage(overrides = {}) {
    mocks.getMyProfile.mockResolvedValue({ ...profile, ...overrides })
    const wrapper = mount(MyProfilePage, {
      global: { stubs: { MyPageLayout: { template: '<main><slot /></main>' } } },
    })
    await flushPromises()
    return wrapper
  }

  async function chooseFile(wrapper, file) {
    const input = wrapper.find('input[aria-label="프로필 사진 변경"]')
    Object.defineProperty(input.element, 'files', { value: [file], writable: true })
    await input.trigger('change')
    await flushPromises()
  }

  it('사진을 고르면 세 단계를 거쳐 올리고 헤더에 쓰는 세션까지 갱신한다', async () => {
    const optimized = new File(['x'], 'small.jpg', { type: 'image/jpeg' })
    mocks.compressImage.mockResolvedValue(optimized)
    mocks.createProfileImageUploadUrl.mockResolvedValue({
      objectKey: 'members/1/profile/abc.jpg',
      presignedUrl: 'https://s3/put',
      requiredHeaders: { 'Content-Type': 'image/jpeg' },
    })
    mocks.uploadToPresignedUrl.mockResolvedValue()
    mocks.completeProfileImage.mockResolvedValue({ profileImageUrl: 'https://cdn/new.jpg' })

    const wrapper = await mountPage()
    await chooseFile(wrapper, new File(['원본'], 'photo.jpg', { type: 'image/jpeg' }))

    expect(mocks.createProfileImageUploadUrl).toHaveBeenCalledWith({
      contentType: 'image/jpeg',
      fileSize: optimized.size,
    })
    expect(mocks.uploadToPresignedUrl).toHaveBeenCalledWith(
      'https://s3/put', optimized, { 'Content-Type': 'image/jpeg' },
    )
    expect(mocks.completeProfileImage).toHaveBeenCalledWith('members/1/profile/abc.jpg')
    expect(wrapper.find('img').attributes('src')).toBe('https://cdn/new.jpg')
    // 이걸 빼면 새로고침할 때까지 헤더에는 예전 사진이 남습니다.
    expect(getSessionMember().profileImageUrl).toBe('https://cdn/new.jpg')
  })

  it('줄여도 5MB를 넘으면 올리지 않고 이유를 알린다', async () => {
    const tooBig = new File(['x'], 'big.jpg', { type: 'image/jpeg' })
    Object.defineProperty(tooBig, 'size', { value: 6 * 1024 * 1024 })
    mocks.compressImage.mockResolvedValue(tooBig)

    const wrapper = await mountPage()
    await chooseFile(wrapper, tooBig)

    expect(mocks.createProfileImageUploadUrl).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('5MB 이하 사진만 올릴 수 있습니다.')
  })

  it('사진 삭제는 확인을 받은 뒤 첫 글자로 되돌린다', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    mocks.deleteProfileImage.mockResolvedValue({ profileImageUrl: null })

    const wrapper = await mountPage({ profileImageUrl: 'https://cdn/old.jpg' })
    expect(wrapper.find('img').exists()).toBe(true)

    await wrapper.findAll('button').find((button) => button.text() === '사진 삭제').trigger('click')
    await flushPromises()

    expect(mocks.deleteProfileImage).toHaveBeenCalled()
    expect(wrapper.find('img').exists()).toBe(false)
    expect(getSessionMember().profileImageUrl).toBeNull()
    confirmSpy.mockRestore()
  })
})

