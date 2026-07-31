import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPage from '../AdminPage.vue'
import { clearAuthSession, setAuthSession } from '../../auth/session'
import {
  getAdminAccounts,
  getAdminActionLog,
  getAdminActionLogs,
  getAdminMembers,
  getChecklistResearches,
  getDeviceModelRequests,
  retryChecklistResearch,
  updateAdminActionLog,
  updateDeviceModelRequest,
} from '../../api/admin'
import { getDeviceCategories } from '../../api/products'

vi.mock('../../api/admin', () => ({
  approveChecklistResearch: vi.fn(),
  approveDeviceModelRequest: vi.fn(),
  createAdminAccount: vi.fn(),
  createMemberRestriction: vi.fn(),
  getAdminAccounts: vi.fn(),
  getAdminActionLog: vi.fn(),
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
  updateAdminActionLog: vi.fn(),
  updateDeviceModelRequest: vi.fn(),
}))

vi.mock('../../api/products', () => ({
  getDeviceCategories: vi.fn(),
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
    getDeviceModelRequests.mockResolvedValue([])
    getDeviceCategories.mockResolvedValue([
      { categoryId: 10, name: '스마트폰' },
      { categoryId: 20, name: '노트북' },
    ])
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

  it('신규 모델 요청의 오타를 수정한 뒤 승인할 수 있게 표시한다', async () => {
    const request = {
      requestId: 91,
      categoryId: 10,
      requestedByMemberId: 7,
      manufacturer: 'Samsnug',
      modelName: 'Galxy S25',
      modelCode: 'SM-S931',
      osFamily: 'ANDROID',
      status: 'PENDING',
      createdAt: '2026-07-30T16:03:00',
    }
    const updated = {
      ...request,
      categoryId: 20,
      manufacturer: 'Samsung',
      modelName: 'Galaxy S25',
      modelCode: 'SM-S931N',
    }
    getDeviceModelRequests.mockResolvedValue([request])
    updateDeviceModelRequest.mockResolvedValue(updated)
    setAuthSession({
      accessToken: 'admin-token',
      admin: { name: '관리자', roles: ['SUPER_ADMIN'] },
    })

    const wrapper = mount(AdminPage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === '신규 기기 모델 검토')
      .trigger('click')
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === '수정')
      .trigger('click')

    await wrapper.find('select[aria-label="카테고리 수정"]').setValue('20')
    await wrapper.find('input[aria-label="제조사 수정"]').setValue('Samsung')
    await wrapper.find('input[aria-label="모델명 수정"]').setValue('Galaxy S25')
    await wrapper.find('input[aria-label="모델 코드 수정"]').setValue('SM-S931N')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateDeviceModelRequest).toHaveBeenCalledWith(91, {
      categoryId: 20,
      manufacturer: 'Samsung',
      modelName: 'Galaxy S25',
      modelCode: 'SM-S931N',
      osFamily: 'ANDROID',
    })
    expect(wrapper.text()).toContain('Samsung Galaxy S25')
    expect(wrapper.text()).toContain('노트북')
    expect(wrapper.text()).toContain('모델 등록 승인')
  })

  it('작업명을 눌러 로그 상세를 조회하고 사유를 수정한다', async () => {
    const summary = {
      adminActionLogId: 301,
      adminId: 1,
      actionType: 'DEVICE_MODEL_REQUEST_APPROVE',
      targetType: 'DEVICE_MODEL_REQUEST',
      targetId: 91,
      reason: '초기 사유',
      createdAt: '2026-07-30T16:03:00',
    }
    const detail = {
      ...summary,
      beforeData: '{"status":"PENDING"}',
      afterData: '{"status":"APPROVED"}',
      ipAddress: '127.0.0.1',
    }
    const updated = { ...detail, reason: '정정된 승인 사유' }
    getAdminActionLogs.mockResolvedValue({
      ...emptyPage,
      content: [summary],
      totalElements: 1,
    })
    getAdminActionLog.mockResolvedValue(detail)
    updateAdminActionLog.mockResolvedValue(updated)
    setAuthSession({
      accessToken: 'admin-token',
      admin: { name: '관리자', roles: ['SUPER_ADMIN'] },
    })

    const wrapper = mount(AdminPage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === '관리자 작업 로그')
      .trigger('click')
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === 'DEVICE_MODEL_REQUEST_APPROVE')
      .trigger('click')
    await flushPromises()

    expect(getAdminActionLog).toHaveBeenCalledWith(301)
    expect(wrapper.text()).toContain('작업 로그 #301')
    expect(wrapper.text()).toContain('"status": "APPROVED"')

    await wrapper.findAll('button')
      .find((button) => button.text() === '수정')
      .trigger('click')
    await wrapper.find('textarea[aria-label="작업 로그 사유 수정"]')
      .setValue('정정된 승인 사유')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateAdminActionLog).toHaveBeenCalledWith(301, { reason: '정정된 승인 사유' })
    expect(wrapper.text()).toContain('정정된 승인 사유')
  })
})
