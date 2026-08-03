import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ChatPage from '../ChatPage.vue'
import { getChatRooms } from '../../api/chat'
import { createChatListSocket } from '../../api/chatSocket'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../../api/chat', () => ({
  getChatRooms: vi.fn(),
  leaveChatRoom: vi.fn(),
}))
vi.mock('../../api/chatSocket', () => ({
  createChatListSocket: vi.fn(),
}))

let listSocketHandlers

function room(unreadCount) {
  return {
    roomId: 10,
    listingId: 1,
    counterpartId: 2,
    counterpartNickname: '상대방',
    listingTitle: '갤럭시',
    lastMessageId: 100,
    lastMessageAt: '2026-07-31T10:00:00',
    lastMessagePreview: '오늘 오후에 가능하실까요?',
    unreadCount,
  }
}

function mountPage() {
  return mount(ChatPage, {
    global: {
      stubs: {
        DefaultLayout: { template: '<main><slot /></main>' },
        BaseCard: { template: '<section><slot /></section>' },
        ChatThread: true,
        RouterLink: {
          props: ['to'],
          template: '<a><slot /></a>',
        },
      },
    },
  })
}

describe('ChatPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    listSocketHandlers = null
    getChatRooms.mockResolvedValue({ content: [room(1)] })
    createChatListSocket.mockImplementation((handlers) => {
      listSocketHandlers = handlers
      return { close: vi.fn() }
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('updates unread counts when a realtime room event arrives', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[aria-label="읽지 않은 메시지 1개"]').text()).toBe('1')
    expect(createChatListSocket).toHaveBeenCalledWith(
      expect.objectContaining({ roomIds: [10] }),
    )

    getChatRooms.mockResolvedValue({ content: [room(2)] })
    listSocketHandlers.onEvent({ type: 'MESSAGE', roomId: 10 })
    await vi.runAllTimersAsync()
    await flushPromises()

    expect(wrapper.find('[aria-label="읽지 않은 메시지 1개"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="읽지 않은 메시지 2개"]').text()).toBe('2')
    expect(getChatRooms).toHaveBeenCalledTimes(2)
  })

  it('마지막 메시지를 본문 자리에 두고 상품명은 작게 위에 붙인다', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const item = wrapper.get('a')
    expect(item.text()).toContain('상대방')
    expect(item.text()).toContain('오늘 오후에 가능하실까요?')
    expect(item.text()).toContain('갤럭시')
  })

  it('주고받은 말이 없으면 대화를 시작하라고 알린다', async () => {
    getChatRooms.mockResolvedValue({
      content: [{ ...room(0), lastMessagePreview: null }],
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('대화를 시작해 보세요.')
  })

  it('고른 대화에만 옅은 파란 바탕과 왼쪽 띠를 준다', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // 지금은 아무 방도 고르지 않은 상태(useRoute의 params가 비어 있음)입니다.
    expect(wrapper.get('a').classes()).toContain('border-transparent')
    expect(wrapper.get('a').classes()).not.toContain('bg-[#F5F7FF]')
  })
})
