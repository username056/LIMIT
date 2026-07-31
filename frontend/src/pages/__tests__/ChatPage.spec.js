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
})
