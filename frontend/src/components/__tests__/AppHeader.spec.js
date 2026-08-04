import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AppHeader from '../AppHeader.vue'
import { useAuthSession } from '../../auth/session'
import { getChatRooms } from '../../api/chat'
import { getMyReinspectionRequests } from '../../api/products'
import { getMyRtcCalls } from '../../api/rtc'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', fullPath: '/' }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../../api/auth', () => ({ logout: vi.fn() }))
vi.mock('../../api/chat', () => ({ getChatRooms: vi.fn() }))
vi.mock('../../api/rtc', () => ({ getMyRtcCalls: vi.fn() }))
vi.mock('../../api/products', () => ({ getMyReinspectionRequests: vi.fn() }))

vi.mock('../../auth/session', () => ({
  useAuthSession: vi.fn(),
  clearAuthSession: vi.fn(),
}))

vi.mock('../../auth/sellerGate', () => ({
  SELL_ENTRY_PATH: '/seller/products/new',
  useSellerGate: () => ({
    isSellerNoticeOpen: { value: false },
    goToSell: vi.fn(),
    goToSellerApply: vi.fn(),
    closeSellerNotice: vi.fn(),
  }),
}))

const globalOptions = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a><slot /></a>' },
    SellerNoticeModal: true,
  },
}

// 마운트한 래퍼를 모아 둡니다. 벨 점 감시가 모듈 수준이라 반드시 풀어야 합니다.
const mounted = []

function mountHeader() {
  const wrapper = mount(AppHeader, { global: globalOptions })
  mounted.push(wrapper)
  return wrapper
}

function signedIn(member) {
  useAuthSession.mockReturnValue({ value: member ? { member } : null })
}

const futureScheduledAt = new Date(Date.now() + 60 * 60 * 1000).toISOString()

/*
  '오늘의 약속'은 날짜가 오늘이면서 아직 만료(+30분)되지 않아야 합니다.
  고정 시각을 쓰면 테스트를 몇 시에 돌리느냐에 따라 결과가 달라지므로 지금을
  기준으로 잡고, 자정 근처에서 날짜가 넘어가면 앞쪽으로 당깁니다.
  표시 문구도 같은 시각에서 만들어 하드코딩하지 않습니다.
*/
function upcomingToday() {
  const today = new Date().getDate()
  const date = new Date()
  date.setMinutes(date.getMinutes() + 10)
  if (date.getDate() !== today) date.setMinutes(date.getMinutes() - 20)
  return {
    iso: date.toISOString(),
    label: new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' }).format(date),
  }
}

describe('AppHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getChatRooms.mockResolvedValue({ content: [] })
    getMyRtcCalls.mockResolvedValue([])
    getMyReinspectionRequests.mockResolvedValue([])
  })

  afterEach(() => {
    // 벨 점 감시는 모듈 수준 상태라, 마운트를 풀지 않으면 타이머가 남습니다.
    mounted.forEach((wrapper) => wrapper.unmount())
    mounted.length = 0
  })

  // 눌러 봐야 로그인 화면으로 튕기는 항목은 처음부터 보여주지 않습니다.
  it('로그인하지 않으면 채팅과 실시간 확인을 감춘다', () => {
    signedIn(null)
    const wrapper = mountHeader()

    expect(wrapper.text()).not.toContain('채팅')
    expect(wrapper.text()).not.toContain('실시간 확인')
    expect(wrapper.text()).toContain('전체 상품')
  })

  it('로그인하면 채팅과 실시간 확인을 보여준다', () => {
    signedIn({ nickname: '회원' })
    const wrapper = mountHeader()

    expect(wrapper.text()).toContain('채팅')
    expect(wrapper.text()).toContain('실시간 확인')
  })

  // scoped 스타일에 display를 두면 Tailwind의 md:hidden을 이겨 햄버거가 항상 보였습니다.
  it('햄버거 버튼은 md 이상에서 숨도록 유틸리티로 제어한다', () => {
    signedIn(null)
    const wrapper = mountHeader()

    const menuButton = wrapper.get('button[aria-label="메뉴 열기"]')
    expect(menuButton.classes()).toContain('md:hidden')
    expect(menuButton.classes()).toContain('flex')
  })

  /*
    벨 점.
    -------------------------------------------------------------------------
    알림 API가 없어 안 읽은 채팅과 받은 검증 약속으로 셉니다.
    아래 셋은 점이 켜지는 조건, 꺼지는 조건, 그리고 못 세었을 때를 확인합니다.
  */
  it('손댈 일이 없으면 벨에 점을 켜지 않는다', async () => {
    signedIn({ nickname: '회원' })
    const wrapper = mountHeader()
    await flushPromises()

    expect(wrapper.get('[aria-label="알림"]').classes()).not.toContain('notification-btn--on')
  })

  it('안 읽은 채팅이 있으면 벨에 점을 켠다', async () => {
    signedIn({ nickname: '회원' })
    getChatRooms.mockResolvedValue({ content: [{ roomId: 1, unreadCount: 2 }] })

    const wrapper = mountHeader()
    await flushPromises()

    expect(wrapper.get('[aria-label="알림 (새 소식 있음)"]').classes())
      .toContain('notification-btn--on')
  })

  it('내가 받은 검증 약속이 있으면 벨에 점을 켠다', async () => {
    signedIn({ nickname: '회원' })
    getMyRtcCalls.mockResolvedValue([
      { callId: 1, status: 'PROPOSED', incoming: true, scheduledAt: futureScheduledAt },
    ])

    const wrapper = mountHeader()
    await flushPromises()

    expect(wrapper.get('[aria-label="알림 (새 소식 있음)"]').classes())
      .toContain('notification-btn--on')
  })

  it('시간이 지난 약속으로는 알리지 않는다', async () => {
    signedIn({ nickname: '회원' })
    // 시각 +30분이 지나면 서버가 수락을 거부해서(RTC_SESSION_EXPIRED) 손쓸 것이 없습니다.
    getMyRtcCalls.mockResolvedValue([
      { callId: 1, status: 'PROPOSED', incoming: true, scheduledAt: '2020-01-01T00:00:00' },
    ])

    const wrapper = mountHeader()
    await flushPromises()

    expect(wrapper.get('[aria-label="알림"]').classes()).not.toContain('notification-btn--on')
  })

  it('내가 보낸 오늘 약속은 응답을 기다리는 중이라고 덧붙인다', async () => {
    signedIn({ nickname: '회원' })
    const appointment = upcomingToday()
    getMyRtcCalls.mockResolvedValue([
      { callId: 1, status: 'PROPOSED', incoming: false, scheduledAt: appointment.iso },
    ])

    const wrapper = mountHeader()
    await flushPromises()
    await wrapper.get('[aria-label="알림 (새 소식 있음)"]').trigger('click')

    expect(wrapper.text()).toContain(appointment.label)
    expect(wrapper.text()).toContain('상대방의 응답을 기다리고 있습니다.')
  })

  it('벨을 누르면 말풍선에 두 줄을 보여 주고 점을 끈다', async () => {
    signedIn({ nickname: '회원' })
    const appointment = upcomingToday()
    getChatRooms.mockResolvedValue({ content: [{ roomId: 1, unreadCount: 3 }] })
    getMyRtcCalls.mockResolvedValue([
      { callId: 1, status: 'ACCEPTED', incoming: true, scheduledAt: appointment.iso },
    ])

    const wrapper = mountHeader()
    await flushPromises()

    const bell = wrapper.get('[aria-label="알림 (새 소식 있음)"]')
    await bell.trigger('click')

    expect(wrapper.text()).toContain('안 읽은 채팅이')
    expect(wrapper.text()).toContain('3건')
    expect(wrapper.text()).toContain('오늘의 검증 약속')
    expect(wrapper.text()).toContain(appointment.label)

    // 한 번 본 소식으로는 점이 다시 켜지지 않습니다.
    expect(wrapper.find('[aria-label="알림 (새 소식 있음)"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="알림"]').classes()).not.toContain('notification-btn--on')
  })

  it('소식이 없으면 말풍선에 없다고 알린다', async () => {
    signedIn({ nickname: '회원' })
    const wrapper = mountHeader()
    await flushPromises()

    await wrapper.get('[aria-label="알림"]').trigger('click')

    expect(wrapper.text()).toContain('새로운 소식이 없습니다.')
  })

  it('어제 잡힌 약속은 오늘의 약속으로 세지 않는다', async () => {
    signedIn({ nickname: '회원' })
    getMyRtcCalls.mockResolvedValue([
      { callId: 1, status: 'ACCEPTED', incoming: true, scheduledAt: '2020-01-01T14:30:00' },
    ])

    const wrapper = mountHeader()
    await flushPromises()
    await wrapper.get('[aria-label="알림"]').trigger('click')

    expect(wrapper.text()).toContain('새로운 소식이 없습니다.')
  })

  it('판매자에게 들어온 재촬영 요청 건수를 알린다', async () => {
    signedIn({ nickname: '회원' })
    getMyReinspectionRequests.mockResolvedValue([
      { requestKey: 'a', status: 'REQUESTED' },
      { requestKey: 'b', status: 'REQUESTED' },
      // 이미 올렸거나 물린 요청은 손댈 일이 없습니다.
      { requestKey: 'c', status: 'COMPLETED' },
      { requestKey: 'd', status: 'CANCELED' },
    ])

    const wrapper = mountHeader()
    await flushPromises()
    await wrapper.get('[aria-label="알림 (새 소식 있음)"]').trigger('click')

    expect(wrapper.text()).toContain('재촬영 요청이')
    expect(wrapper.text()).toContain('2건')
  })

  it('구매자에게 재촬영이 올라온 방을 알린다', async () => {
    signedIn({ nickname: '회원' })
    // 판매자가 완료하면 구매자 채팅방에 이 문구로 SYSTEM 메시지가 남습니다.
    getChatRooms.mockResolvedValue({
      content: [
        { roomId: 1, unreadCount: 1, lastMessagePreview: '재검수가 완료되었습니다.' },
        { roomId: 2, unreadCount: 2, lastMessagePreview: '안녕하세요' },
      ],
    })

    const wrapper = mountHeader()
    await flushPromises()
    await wrapper.get('[aria-label="알림 (새 소식 있음)"]').trigger('click')

    expect(wrapper.text()).toContain('요청한 재촬영이')
    expect(wrapper.text()).toContain('1건')
    // 완료 안내도 결국 안 읽은 채팅이라 채팅 줄에도 함께 셉니다.
    expect(wrapper.text()).toContain('안 읽은 채팅이')
  })

  it('이미 읽은 재촬영 완료 안내는 세지 않는다', async () => {
    signedIn({ nickname: '회원' })
    getChatRooms.mockResolvedValue({
      content: [{ roomId: 1, unreadCount: 0, lastMessagePreview: '재검수가 완료되었습니다.' }],
    })

    const wrapper = mountHeader()
    await flushPromises()
    await wrapper.get('[aria-label="알림"]').trigger('click')

    expect(wrapper.text()).toContain('새로운 소식이 없습니다.')
  })

  it('세지 못하면 점을 켜지 않는다', async () => {
    signedIn({ nickname: '회원' })
    getChatRooms.mockRejectedValue(new Error('네트워크 오류'))
    getMyRtcCalls.mockRejectedValue(new Error('네트워크 오류'))
    getMyReinspectionRequests.mockRejectedValue(new Error('네트워크 오류'))

    const wrapper = mountHeader()
    await flushPromises()

    // 없는 알림을 있다고 하는 편이 더 나쁩니다.
    expect(wrapper.get('[aria-label="알림"]').classes()).not.toContain('notification-btn--on')
  })

  it('로그인하지 않으면 신호를 세지 않는다', async () => {
    signedIn(null)
    mountHeader()
    await flushPromises()

    expect(getChatRooms).not.toHaveBeenCalled()
    expect(getMyRtcCalls).not.toHaveBeenCalled()
    expect(getMyReinspectionRequests).not.toHaveBeenCalled()
  })
})
