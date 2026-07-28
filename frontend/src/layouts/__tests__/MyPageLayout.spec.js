import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'
import { clearAuthSession, setAuthSession } from '../../auth/session'
import MyPageLayout from '../MyPageLayout.vue'

const sidebarStub = {
  props: ['sidebarItems'],
  template: `
    <aside>
      <a v-for="item in sidebarItems" :key="item.href" :data-active="item.active">
        {{ item.label }}
      </a>
      <slot />
    </aside>
  `,
}

async function mountLayout(path, roles) {
  setAuthSession({ accessToken: 'token', member: { roles } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/mypage/profile', component: { template: '<div />' } },
      { path: '/seller/products', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(MyPageLayout, {
    global: {
      plugins: [router],
      stubs: { SidebarLayout: sidebarStub },
    },
  })
  await flushPromises()
  return wrapper
}

describe('MyPageLayout', () => {
  afterEach(() => clearAuthSession())

  it('일반 회원 화면에서는 공통 회원 메뉴와 판매자 등록을 표시한다', async () => {
    const wrapper = await mountLayout('/mypage/profile', ['MEMBER'])

    expect(wrapper.findAll('a').map((item) => item.text())).toEqual([
      '내 정보',
      '관심 상품',
      '주문 내역',
      '판매자 등록',
    ])
    expect(wrapper.find('a[data-active="true"]').text()).toBe('내 정보')
  })

  it('판매자 화면에서도 같은 회원 메뉴에 판매자 메뉴만 확장한다', async () => {
    const wrapper = await mountLayout('/seller/products', ['MEMBER', 'SELLER'])

    expect(wrapper.findAll('a').map((item) => item.text())).toEqual([
      '내 정보',
      '관심 상품',
      '주문 내역',
      '판매자 대시보드',
      '상품 관리',
    ])
    expect(wrapper.find('a[data-active="true"]').text()).toBe('상품 관리')
  })
})
