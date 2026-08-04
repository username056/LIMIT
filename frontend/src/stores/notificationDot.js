import { computed, ref } from 'vue'
import { getChatRooms } from '../api/chat'
import { getMyReinspectionRequests } from '../api/products'
import { getMyRtcCalls } from '../api/rtc'

/*
  네비게이션 벨에 띄울 두 줄.
  ---------------------------------------------------------------------------
  알림 API와 notification 테이블은 아직 없습니다. 그런데 "손댈 일이 있다"는 신호는
  이미 지금 응답에 들어 있습니다.

    안 읽은 채팅       chat-rooms 응답의 unreadCount 합계
    오늘의 검증 약속    calls 응답에서 오늘 잡힌 약속 중 가장 이른 것
    받은 재촬영 요청    내 재검수 요청 목록에서 REQUESTED (판매자 입장)
    끝난 재촬영        채팅방 마지막 메시지가 완료 알림인 것 (구매자 입장)

  그래서 백엔드를 건드리지 않고 이것만 세어 벨에 보여 줍니다. 알림 목록이
  필요해지면 이 자리를 서버 조회로 바꾸면 되고, 화면(AppHeader)은 그대로 둡니다.

  헤더는 모든 화면에 있으므로 요청을 아껴야 합니다.
  - 로그인한 사람만 부릅니다.
  - 헤더가 여러 번 붙어도 타이머는 하나만 돕니다.
  - 60초에 한 번, 그리고 탭으로 돌아왔을 때만 다시 셉니다.
*/

const REFRESH_INTERVAL_MS = 60_000

// 약속 시각 +30분이 지나면 서버가 수락을 거부합니다(RtcCallService.respond).
// 이미 손쓸 수 없는 약속을 알려 봐야 할 일이 없습니다.
const APPOINTMENT_WINDOW_MS = 30 * 60 * 1000

export const unreadChatCount = ref(0)
/** { callId, timeLabel, isWaitingForCounterpart } | null */
export const todayAppointment = ref(null)
/** 판매자 입장: 내 상품에 들어온, 아직 안 올린 재촬영 요청 수 */
export const pendingRecaptureCount = ref(0)
/** 구매자 입장: 내가 요청한 재촬영이 끝났다는 안내가 안 읽힌 채로 있는 방 수 */
export const doneRecaptureCount = ref(0)

/*
  구매자 쪽은 조회할 API가 없습니다.
  ---------------------------------------------------------------------------
  /members/me/reinspection-requests는 findForSeller(sellerId)라 판매자가 받은 것만
  돌려줍니다. 내가 구매자로서 보낸 요청이 끝났는지 묻는 길이 없습니다.

  대신 판매자가 완료하면 구매자 채팅방에 이 문구로 SYSTEM 메시지가 남습니다
  (ReinspectionChatNotificationService.content). 채팅방 목록의 마지막 메시지
  미리보기로 그 방을 찾아냅니다.

  서버 문구에 기대는 방식이라 문구가 바뀌면 못 알아봅니다. 다만 그때는 '안 읽은
  채팅'으로만 세어질 뿐 점은 그대로 켜지므로, 알림을 놓치지는 않습니다.
  구매자용 조회 API가 생기면 이 줄을 지우고 그걸 부르면 됩니다.
*/
const RECAPTURE_DONE_PREVIEW = '재검수가 완료되었습니다'

/*
  벨의 점은 "아직 안 본 소식이 있다"는 뜻입니다.
  한 번 열어 본 뒤에는 같은 소식으로 다시 켜지지 않아야 하므로, 지금 소식을 짧은
  문자열로 만들어 두고 열었을 때의 것과 비교합니다. 새 메시지가 오거나 약속이
  바뀌면 문자열이 달라져 점이 다시 켜집니다.

  저장은 메모리에만 합니다. localStorage에 두면 새로고침해도 남지만, 회원의 활동
  내역을 브라우저에 남기지 않는다는 규칙을 지키는 편이 낫습니다.
*/
const seenSignature = ref('')

const signature = computed(() => {
  const parts = [
    unreadChatCount.value,
    todayAppointment.value?.timeLabel || '',
    pendingRecaptureCount.value,
    doneRecaptureCount.value,
  ]
  if (parts.every((part) => !part)) return ''
  return parts.join('|')
})

export const hasNotice = computed(() => Boolean(signature.value))
export const hasUnreadNotification = computed(
  () => Boolean(signature.value) && signature.value !== seenSignature.value,
)

export function markNotificationsSeen() {
  seenSignature.value = signature.value
}

let refreshTimer = null
let subscriberCount = 0
let inFlight = null

function isToday(value, now) {
  if (!value) return false
  const date = new Date(value)
  const today = new Date(now)
  return date.getFullYear() === today.getFullYear()
    && date.getMonth() === today.getMonth()
    && date.getDate() === today.getDate()
}

function formatTimeLabel(value) {
  return new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })
    .format(new Date(value))
}

// 오늘 잡혀 있고 아직 시간이 지나지 않은 약속 중 가장 이른 것.
function pickTodayAppointment(calls, now) {
  const candidates = calls
    .filter((call) => ['PROPOSED', 'ACCEPTED'].includes(call?.status))
    .filter((call) => isToday(call.scheduledAt, now))
    .filter((call) => new Date(call.scheduledAt).getTime() + APPOINTMENT_WINDOW_MS > now)
    .sort((first, second) => new Date(first.scheduledAt) - new Date(second.scheduledAt))

  const next = candidates[0]
  if (!next) return null
  return {
    callId: next.callId,
    timeLabel: formatTimeLabel(next.scheduledAt),
    // 내가 보낸 약속은 상대의 답을 기다리는 중이라 문구가 달라집니다.
    isWaitingForCounterpart: next.status === 'PROPOSED' && !next.incoming,
  }
}

export async function refreshNotificationDot() {
  // 같은 순간에 여러 번 불려도 요청은 한 번만 나갑니다.
  if (inFlight) return inFlight
  inFlight = Promise.all([
    // 하나가 실패해도 나머지 신호는 살립니다. 벨 하나 때문에 화면을 막지 않습니다.
    getChatRooms({ size: 100 }).then((result) => result?.content || []).catch(() => null),
    getMyRtcCalls().catch(() => null),
    // 판매자가 아니면 403이 옵니다. 그때는 받은 재촬영 요청이 없는 것과 같습니다.
    getMyReinspectionRequests().catch(() => null),
  ])
    .then(([rooms, calls, recaptures]) => {
      const now = Date.now()
      // 못 세었으면 이전 값을 지웁니다. 없는 소식을 있다고 하는 편이 더 나쁩니다.
      unreadChatCount.value = (rooms || [])
        .reduce((total, room) => total + Number(room?.unreadCount || 0), 0)
      todayAppointment.value = pickTodayAppointment(calls || [], now)
      pendingRecaptureCount.value = (recaptures || [])
        .filter((request) => request?.status === 'REQUESTED').length
      doneRecaptureCount.value = (rooms || []).filter(
        (room) => Number(room?.unreadCount || 0) > 0
          && String(room?.lastMessagePreview || '').startsWith(RECAPTURE_DONE_PREVIEW),
      ).length
    })
    .finally(() => {
      inFlight = null
    })
  return inFlight
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') refreshNotificationDot()
}

/**
 * 헤더가 붙을 때 켜고, 마지막 헤더가 사라질 때 끕니다.
 *
 * 세는 것은 붙을 때마다 합니다. 타이머만 하나로 묶습니다. 새로 붙은 헤더가
 * 60초짜리 다음 차례를 기다리면, 로그인 직후나 화면을 옮긴 뒤 소식이 늦게 뜹니다.
 */
export function startNotificationDotWatch() {
  subscriberCount += 1
  refreshNotificationDot()
  if (subscriberCount > 1) return

  refreshTimer = setInterval(refreshNotificationDot, REFRESH_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
}

export function stopNotificationDotWatch() {
  subscriberCount = Math.max(0, subscriberCount - 1)
  if (subscriberCount > 0) return

  clearInterval(refreshTimer)
  refreshTimer = null
  document.removeEventListener('visibilitychange', onVisibilityChange)
  unreadChatCount.value = 0
  todayAppointment.value = null
  pendingRecaptureCount.value = 0
  doneRecaptureCount.value = 0
  seenSignature.value = ''
}
