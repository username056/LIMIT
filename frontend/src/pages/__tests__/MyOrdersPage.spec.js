import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyOrdersPage from '../MyOrdersPage.vue'
import { createOrGetChatRoom } from '../../api/chat'

const { routerPushMock } = vi.hoisted(() => ({ routerPushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushMock }),
}))

vi.mock('../../api/chat', () => ({
  createOrGetChatRoom: vi.fn(),
}))

const buttonStub = {
  props: ['variant', 'disabled'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
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

// 주문 상세는 목록 항목 자체를 눌러서 엽니다(버튼 자리는 판매자 문의가 차지했습니다).
async function openOrderDetail(wrapper, orderName) {
  const row = wrapper.findAll('li').find((item) => item.text().includes(orderName))
  await row.find('button').trigger('click')
}

describe('MyOrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createOrGetChatRoom.mockResolvedValue({ roomId: 501 })
  })

  it('결제 완료 주문은 거래 취소를 요청하면 상태 뱃지가 바뀐다', async () => {
    const wrapper = mountPage()
    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')

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

  // 배송을 지원하지 않으므로 배송 기반 상태와 탭은 두지 않습니다.
  it('배송 관련 상태와 탭을 보여주지 않는다', async () => {
    const wrapper = mountPage()

    expect(wrapper.text()).not.toContain('배송중')
    expect(wrapper.text()).not.toContain('배송완료')
    expect(wrapper.text()).not.toContain('배송 정보')

    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')
    expect(wrapper.text()).toContain('진행 상태')
  })

  it('결제 완료 주문에서는 취소와 반품을 모두 신청할 수 있다', async () => {
    const wrapper = mountPage()
    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')

    expect(buttonByText(wrapper, '반품 신청')).toBeTruthy()
    expect(buttonByText(wrapper, '거래 취소 요청')).toBeTruthy()

    await buttonByText(wrapper, '반품 신청').trigger('click')
    await wrapper.findAll('input[type="radio"]')[0].setValue(true)
    await buttonByText(wrapper, '신청하기').trigger('click')

    expect(wrapper.text()).toContain('반품 신청을 접수했습니다.')
    expect(wrapper.text()).toContain('반품 신청 접수 · 판매자 확인 대기')
  })

  // 대표 이미지는 그 상품의 썸네일이라 주문 내역에서도 필요합니다.
  it('주문 항목에 대표 이미지 자리가 있고 값이 있으면 표시한다', async () => {
    const wrapper = mountPage()

    // 예시 데이터에는 URL이 없어 자리표시자가 보입니다.
    expect(wrapper.find('li img').exists()).toBe(false)
    expect(wrapper.findAll('li')[0].text()).toContain('▣')
  })

  it('주문 조회 대신 판매자에게 문의로 채팅방을 연다', async () => {
    const wrapper = mountPage()

    expect(buttonByText(wrapper, '주문 조회')).toBeUndefined()

    await buttonByText(wrapper, '판매자에게 문의').trigger('click')
    await flushPromises()

    expect(createOrGetChatRoom).toHaveBeenCalledWith(7)
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'chat', params: { roomId: 501 } })
  })
})
