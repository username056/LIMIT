import { ref } from 'vue'

// WIREFRAME MOCK: 채팅/실시간 화상 검증 API가 아직 없어, 방 목록과 대화 내용을
// 모듈 스코프의 반응형 mock 데이터로 관리합니다. 실제 연동 시 이 파일 대신
// src/api/chat.js(신규) + 소켓 연결로 교체하세요.

export const chatRooms = ref([
  {
    id: 1,
    productId: 3,
    name: '우주최강노트북전문가',
    online: true,
    productIcon: '💻',
    productName: 'MacBook Pro 14 M3 Pro',
    productPrice: 1850000,
    productSpec: 'Space Black · RAM 18GB · SSD 512GB',
    lastMessage: '안녕하세요! 내일 오후 3시에 제안드린 WebRTC 라이브 검증 어떠세요?',
    time: '오후 2:41',
    unreadCount: 2,
    statusBadge: '실시간 검증 예약됨',
    dateLabel: '2024년 3월 14일 목요일',
    messages: [
      {
        id: 1,
        from: 'seller',
        type: 'text',
        text: '안녕하세요! 올리신 1:1 화상 검증 요청 확인했습니다. 맥북 제품 외관하고 작동 성능 전반적으로 보여드리려고 하는데 제안 드린 일정 확인해 주세요.',
        time: '오후 2:38',
      },
      {
        id: 2,
        from: 'seller',
        type: 'proposal',
        proposedAt: '2024.03.15 (금) 오후 3:00',
        status: 'accepted',
        time: '오후 2:39',
      },
      {
        id: 3,
        from: 'me',
        type: 'text',
        text: '네 수락하겠습니다! 해당 일정에 라이브 참여할게요. 포트 부위 기스 재확인 부탁드립니다.',
        time: '오후 2:40',
      },
      {
        id: 4,
        from: 'seller',
        type: 'confirmed',
        confirmedAt: '2024.03.15 (금) 오후 3:00',
        time: '오후 2:41',
      },
      {
        id: 5,
        type: 'banner',
        confirmedAt: '2024년 3월 15일 (금) 오후 3:00',
        time: '오후 2:41',
      },
    ],
  },
  {
    id: 2,
    productId: 5,
    name: '착한아이폰판매',
    online: false,
    productIcon: '📱',
    productName: 'iPhone 15 Pro Max',
    productPrice: 1280000,
    productSpec: 'Natural Titanium · 256GB',
    lastMessage: '네, 배터리 잔상 테스트 결과는 상세 체크리스트 리포트에 올려뒀어요.',
    time: '어제',
    unreadCount: 0,
    statusBadge: '',
    dateLabel: '어제',
    messages: [
      {
        id: 1,
        from: 'me',
        type: 'text',
        text: '배터리 성능 테스트 결과 좀 더 자세히 볼 수 있을까요?',
        time: '오후 6:10',
      },
      {
        id: 2,
        from: 'seller',
        type: 'text',
        text: '네, 배터리 잔상 테스트 결과는 상세 체크리스트 리포트에 올려뒀어요.',
        time: '오후 6:24',
      },
    ],
  },
  {
    id: 3,
    productId: 2,
    name: '아이패드대장',
    online: true,
    productIcon: '📱',
    productName: 'iPad Pro 11 M2',
    productPrice: 1450000,
    productSpec: 'Space Gray · 256GB',
    lastMessage: '조금 절충 가능한가요? 가능하다면 지금 바로 즉석 라이브 매칭해요.',
    time: '3일 전',
    unreadCount: 0,
    statusBadge: '',
    dateLabel: '3일 전',
    messages: [
      {
        id: 1,
        from: 'me',
        type: 'text',
        text: '가격 조금 절충 가능할까요?',
        time: '오전 11:02',
      },
      {
        id: 2,
        from: 'seller',
        type: 'text',
        text: '조금 절충 가능한가요? 가능하다면 지금 바로 즉석 라이브 매칭해요.',
        time: '오전 11:15',
      },
    ],
  },
])

export function getChatRoom(roomId) {
  return chatRooms.value.find((room) => String(room.id) === String(roomId)) || null
}

export function markRoomRead(roomId) {
  const room = getChatRoom(roomId)
  if (room) room.unreadCount = 0
}

let nextMessageId = 1000

export function respondToProposal(roomId, messageId, status) {
  const message = getChatRoom(roomId)?.messages.find((item) => item.id === messageId)
  if (message) message.status = status
}

export function addProposalMessage(roomId, proposedAt) {
  const room = getChatRoom(roomId)
  if (!room) return
  room.messages.push({ id: nextMessageId++, from: 'me', type: 'proposal', proposedAt, status: 'pending', time: '방금' })
}

export function addTextMessage(roomId, from, text) {
  const room = getChatRoom(roomId)
  if (!room) return
  room.messages.push({ id: nextMessageId++, from, type: 'text', text, time: '방금' })
}
