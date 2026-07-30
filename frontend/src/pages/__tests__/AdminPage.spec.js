import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPage from '../AdminPage.vue'
import { clearAuthSession, setAuthSession } from '../../auth/session'
import {
  getAdminAccounts,
  getAdminActionLogs,
  getAdminMembers,
  getChecklistResearches,
  retryChecklistResearch,
} from '../../api/admin'

vi.mock('../../api/admin', () => ({
  approveChecklistResearch: vi.fn(),
  approveDeviceModelRequest: vi.fn(),
  createAdminAccount: vi.fn(),
  createMemberRestriction: vi.fn(),
  getAdminAccounts: vi.fn(),
  getAdminActionLogs: vi.fn(),
  getAdminMember: vi.fn(),
  getAdminMembers: vi.fn(),
  getChecklistResearches: vi.fn(),
  getDeviceModelRequests: vi.fn(),
  getMemberRestrictions: vi.fn(),
  loginAdmin: vi.fn(),
  rejectChecklistResearch: vi.fn(),
  rejectDeviceModelRequest: vi.fn(),
  releaseMemberRestriction: vi.fn(),
  retryChecklistResearch: vi.fn(),
  updateAdminAccount: vi.fn(),
}))

const emptyPage = {
  content: [],
  page: 0,
  size: 5,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
}

describe('AdminPage', () => {
  beforeEach(() => {
    clearAuthSession()
    vi.clearAllMocks()
    getAdminMembers.mockResolvedValue(emptyPage)
    getAdminActionLogs.mockResolvedValue(emptyPage)
    getAdminAccounts.mockResolvedValue(emptyPage)
  })

  afterEach(() => clearAuthSession())

  it('일반 회원 로그인과 분리된 관리자 로그인 화면을 표시한다', () => {
    const wrapper = mount(AdminPage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.get('h1').text()).toBe('관리자 로그인')
    expect(wrapper.findAll('input')).toHaveLength(2)
    expect(wrapper.text()).toContain('accessToken')
    expect(wrapper.text()).toContain('accessToken 값만')
    expect(wrapper.text()).toContain('Authorization: Bearer')
  })

  it('실패한 AI 조사의 사유를 표시하고 재조사한다', async () => {
    const failedResearch = {
      researchId: 501,
      deviceModelId: 13,
      deviceType: 'LAPTOP',
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book4',
      researchVersion: 1,
      status: 'FAILED',
      suggestions: [],
      reviewCandidates: [],
      failureCode: 'AI_REQUEST_FAILED',
      failureMessage: 'AI 서비스 요청이 시간 초과되었습니다.',
      createdAt: '2026-07-30T16:03:00',
    }
    const pendingResearch = {
      ...failedResearch,
      status: 'PENDING_REVIEW',
      failureCode: null,
      failureMessage: null,
    }
    getChecklistResearches.mockImplementation((status) => (
      Promise.resolve(status === 'FAILED' ? [failedResearch] : [pendingResearch])
    ))
    retryChecklistResearch.mockResolvedValue(pendingResearch)
    setAuthSession({
      accessToken: 'admin-token',
      admin: { name: '관리자', roles: ['SUPER_ADMIN'] },
    })

    const wrapper = mount(AdminPage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    await wrapper.findAll('button')
      .find((button) => button.text() === '체크리스트 AI 검토')
      .trigger('click')
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === '조사 실패')
      .trigger('click')
    await flushPromises()

    expect(getChecklistResearches).toHaveBeenCalledWith('FAILED')
    expect(wrapper.text()).toContain('Samsung Galaxy Book4')
    expect(wrapper.text()).toContain('AI 서비스 요청이 시간 초과되었습니다.')
    expect(wrapper.text()).toContain('AI_REQUEST_FAILED')

    await wrapper.findAll('button')
      .find((button) => button.text() === 'AI 재조사')
      .trigger('click')
    await flushPromises()

    expect(retryChecklistResearch).toHaveBeenCalledWith(501)
    expect(getChecklistResearches).toHaveBeenLastCalledWith('PENDING_REVIEW')
    expect(wrapper.text()).toContain('관리자 검토 대기')
  })
})
