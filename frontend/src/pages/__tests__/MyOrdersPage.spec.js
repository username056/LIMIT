import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyOrdersPage from '../MyOrdersPage.vue'
import { createOrGetChatRoom } from '../../api/chat'
import { listOrders } from '../../api/orders'

const { routerPushMock } = vi.hoisted(() => ({ routerPushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushMock }),
}))

vi.mock('../../api/chat', () => ({
  createOrGetChatRoom: vi.fn(),
}))

vi.mock('../../api/orders', () => ({
  listOrders: vi.fn(),
}))

const ORDERS_FIXTURE = [
  {
    paymentId: 500,
    listingId: 7,
    productName: '갤럭시 S24 Ultra 256GB 자급제',
    thumbnailUrl: null,
    price: 1_050_000,
    paymentStatus: 'APPROVED',
    listingStatus: 'PAID',
    requestedAt: '2026-07-28T10:00:00+09:00',
    approvedAt: '2026-07-28T10:05:00+09:00',
  },
  {
    paymentId: 501,
    listingId: 8,
    productName: '갤럭시 북4 프로 512GB',
    thumbnailUrl: null,
    price: 1_890_000,
    paymentStatus: 'CANCELLED',
    listingStatus: null,
    requestedAt: '2026-07-20T09:00:00+09:00',
    approvedAt: null,
  },
]

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

async function mountLoadedPage() {
  const wrapper = mountPage()
  await flushPromises()
  return wrapper
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
    listOrders.mockResolvedValue(ORDERS_FIXTURE)
  })

  it('주문 내역을 불러와 결제 상태를 한글 라벨로 보여준다', async () => {
    const wrapper = await mountLoadedPage()

    expect(wrapper.text()).toContain('갤럭시 S24 Ultra 256GB 자급제')
    expect(wrapper.text()).toContain('결제 완료')
    expect(wrapper.text()).toContain('취소/환불')
  })

  it('목록을 불러오지 못하면 오류 메시지를 보여준다', async () => {
    listOrders.mockRejectedValue(new Error('주문 내역을 불러오지 못했습니다.'))

    const wrapper = await mountLoadedPage()

    expect(wrapper.text()).toContain('주문 내역을 불러오지 못했습니다.')
  })

  // 취소/반품 API가 아직 없으므로(환불 도메인 미연동) 버튼 대신 준비 중 안내만 보여준다 —
  // 예전에는 로컬에서만 "접수됨"으로 위장해 새로고침하면 사라지는 문제가 있었다.
  it('결제 완료 주문에서는 거래 취소·반품 버튼 대신 준비 중 안내를 보여준다', async () => {
    const wrapper = await mountLoadedPage()
    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')

    expect(buttonByText(wrapper, '거래 취소 요청')).toBeUndefined()
    expect(buttonByText(wrapper, '반품 신청')).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('거래 취소·반품 신청은 아직 준비 중입니다.')
  })

  it('환불 요청 상태의 주문은 실제 백엔드 상태로 접수 중임을 보여준다', async () => {
    listOrders.mockResolvedValue([{ ...ORDERS_FIXTURE[0], paymentStatus: 'REFUND_REQUESTED' }])

    const wrapper = await mountLoadedPage()
    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')

    expect(wrapper.text()).toContain('취소 요청')
    expect(wrapper.text()).toContain('환불 요청이 접수되어 판매자 확인을 기다리는 중입니다.')
    const badge = wrapper.findAll('[data-variant]').find((node) => node.text() === '취소 요청')
    expect(badge.attributes('data-variant')).toBe('danger')
  })

  // 배송을 지원하지 않으므로 배송 기반 상태와 탭은 두지 않습니다.
  it('배송 관련 상태와 탭을 보여주지 않는다', async () => {
    const wrapper = await mountLoadedPage()

    expect(wrapper.text()).not.toContain('배송중')
    expect(wrapper.text()).not.toContain('배송완료')
    expect(wrapper.text()).not.toContain('배송 정보')

    await openOrderDetail(wrapper, '갤럭시 S24 Ultra')
    expect(wrapper.text()).toContain('진행 상태')
  })

  // 대표 이미지는 그 상품의 썸네일이라 주문 내역에서도 필요합니다.
  it('대표 이미지가 없으면 자리표시자를 보여준다', async () => {
    const wrapper = await mountLoadedPage()

    expect(wrapper.find('li img').exists()).toBe(false)
    expect(wrapper.findAll('li')[0].text()).toContain('▣')
  })

  it('대표 이미지 값이 있으면 이미지를 보여준다', async () => {
    listOrders.mockResolvedValue([
      { ...ORDERS_FIXTURE[0], thumbnailUrl: 'https://cdn.example.com/thumb.jpg' },
    ])

    const wrapper = await mountLoadedPage()

    const img = wrapper.find('li img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://cdn.example.com/thumb.jpg')
  })

  it('주문 조회 대신 판매자에게 문의로 채팅방을 연다', async () => {
    const wrapper = await mountLoadedPage()

    expect(buttonByText(wrapper, '주문 조회')).toBeUndefined()

    await buttonByText(wrapper, '판매자에게 문의').trigger('click')
    await flushPromises()

    expect(createOrGetChatRoom).toHaveBeenCalledWith(7)
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'chat', params: { roomId: 501 } })
  })
})
