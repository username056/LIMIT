import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MyOrdersPage from '../MyOrdersPage.vue'

const buttonStub = {
  props: ['variant'],
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
}

function mountPage() {
  return mount(MyOrdersPage, {
    global: {
      stubs: {
        MyPageLayout: { template: '<main><slot /></main>' },
        BaseCard: { template: '<section><slot /></section>' },
        BaseTabs: true,
        BaseBadge: { props: ['variant'], template: '<span :data-variant="variant"><slot /></span>' },
        BaseButton: buttonStub,
      },
    },
  })
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

async function openOrderDetail(wrapper, orderName) {
  const row = wrapper.findAll('li').find((item) => item.text().includes(orderName))
  await row.find('button').trigger('click')
}

describe('MyOrdersPage', () => {
  it('결제완료 주문은 거래 취소를 요청하면 상태 뱃지가 바뀐다', async () => {
    const wrapper = mountPage()
    await openOrderDetail(wrapper, 'New Balance 990v6')

    await buttonByText(wrapper, '거래 취소 요청').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    // 사유를 고르지 않으면 접수되지 않습니다.
    await buttonByText(wrapper, '신청하기').trigger('click')
    expect(wrapper.text()).toContain('사유를 선택해 주세요.')

    await wrapper.findAll('input[type="radio"]')[0].setValue(true)
    await buttonByText(wrapper, '신청하기').trigger('click')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('거래 취소 요청을 접수했습니다.')
    expect(wrapper.text()).toContain('취소 요청 접수 · 판매자 확인 대기')

    const badge = wrapper.findAll('[data-variant]').find((node) => node.text() === '취소 요청')
    expect(badge.attributes('data-variant')).toBe('danger')
  })

  it('배송완료 주문에는 반품 신청만 노출된다', async () => {
    const wrapper = mountPage()
    await openOrderDetail(wrapper, 'Air Jordan 1')

    expect(buttonByText(wrapper, '반품 신청')).toBeTruthy()
    expect(buttonByText(wrapper, '거래 취소 요청')).toBeUndefined()

    await buttonByText(wrapper, '반품 신청').trigger('click')
    await wrapper.findAll('input[type="radio"]')[0].setValue(true)
    await buttonByText(wrapper, '신청하기').trigger('click')

    expect(wrapper.text()).toContain('반품 신청을 접수했습니다.')
    expect(wrapper.text()).toContain('반품 신청 접수 · 회수 대기')
  })
})
