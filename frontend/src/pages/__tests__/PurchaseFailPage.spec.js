import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PurchaseFailPage from '../PurchaseFailPage.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { productId: '1001' },
    query: { message: 'Toss 결제창에서 취소되었습니다.' },
  }),
}))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = { props: ['to'], template: '<a><slot /></a>' }

describe('PurchaseFailPage', () => {
  it('결제 실패 사유와 다시 시도 링크를 보여준다', () => {
    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })

    expect(wrapper.text()).toContain('결제가 완료되지 않았습니다')
    expect(wrapper.text()).toContain('Toss 결제창에서 취소되었습니다.')
  })
})
