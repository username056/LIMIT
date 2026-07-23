import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import AdminPage from '../AdminPage.vue'
import { clearAuthSession } from '../../auth/session'

describe('AdminPage', () => {
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
})
