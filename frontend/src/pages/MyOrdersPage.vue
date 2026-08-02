<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseTabs from '../components/BaseTabs.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import { createOrGetChatRoom } from '../api/chat'
import { listOrders } from '../api/orders'

const router = useRouter()
const activeTab = ref('전체')
const selectedOrder = ref(null)

// 이 서비스는 배송을 지원하지 않습니다(판매자·구매자가 직접 만나거나 직거래로 주고받는 구조).
// 그래서 '배송중'·'배송완료' 같은 상태를 둘 수 없고, 결제와 취소·환불만 실제로 추적 가능합니다.
const ORDER_TABS = ['전체', '결제 완료', '취소/반품']
const TAB_STATUS_GROUPS = {
  '결제 완료': ['결제 완료'],
  '취소/반품': ['취소 요청', '반품 접수', '취소/환불'],
}
const IN_PROGRESS_STATUSES = ['결제 완료']
const REQUESTED_STATUSES = ['취소 요청', '반품 접수']

// 배송이 없으므로 배송 지연·파손 같은 사유는 뺐습니다.
const CANCEL_REASONS = [
  '상품이 필요 없어졌습니다.',
  '다른 상품으로 다시 구매하려고 합니다.',
  '판매자와 연락이 되지 않습니다.',
  '거래 일정을 맞추기 어렵습니다.',
]
const RETURN_REASONS = [
  '상품 상태가 설명과 다릅니다.',
  '설명에 없던 하자가 있습니다.',
  '구성품이 누락되었습니다.',
  '검증 자료와 실물이 다릅니다.',
]

// 결제 상태(paymentStatus)만으로는 화면에 보여줄 한글 라벨을 정할 수 없어 매핑한다.
// 취소 요청/반품 접수는 아직 서버에 별도 상태가 없어(환불 도메인 미연동), 실제로는
// submitRequest()가 로컬에서만 상태를 바꾼다 — 새로고침하면 서버 값(REFUND_REQUESTED 등)
// 대신 이 매핑으로 되돌아간다. 환불 API가 연동되면 이 부분을 실제 상태로 교체해야 한다.
function resolveStatusLabel(paymentStatus) {
  if (paymentStatus === 'APPROVED') return '결제 완료'
  if (paymentStatus === 'REFUND_REQUESTED') return '취소 요청'
  return '취소/환불'
}

function resolveProgressText(order, dateLabel) {
  if (order.paymentStatus === 'APPROVED') {
    if (order.listingStatus === 'INSPECTING') return '검수가 진행 중입니다.'
    if (order.listingStatus === 'CONFIRMED' || order.listingStatus === 'SETTLED') {
      return '구매확정이 완료되었습니다.'
    }
    return '판매자가 상품을 준비하고 있습니다.'
  }
  if (order.paymentStatus === 'REFUND_REQUESTED') return '환불 요청이 접수되어 처리 중입니다.'
  if (order.paymentStatus === 'REFUNDED') return `${dateLabel} 환불 완료`
  return `${dateLabel} 결제 취소`
}

function formatDate(isoString) {
  if (!isoString) return ''
  return isoString.slice(0, 10).replaceAll('-', '.')
}

function mapOrder(order) {
  const dateLabel = formatDate(order.approvedAt || order.requestedAt)
  const status = resolveStatusLabel(order.paymentStatus)
  return {
    name: order.productName || '삭제된 상품',
    date: dateLabel,
    id: `#ORDER-${order.paymentId}`,
    productId: order.listingId,
    thumbnailUrl: order.thumbnailUrl || '',
    price: Number(order.price).toLocaleString('ko-KR'),
    status,
    progress: resolveProgressText(order, dateLabel),
    requestNote: '',
    paymentStatus: order.paymentStatus,
  }
}

const orders = ref([])
const isLoading = ref(true)
const loadError = ref('')

async function loadOrders() {
  isLoading.value = true
  loadError.value = ''
  try {
    const response = await listOrders()
    orders.value = response.map(mapOrder)
  } catch (error) {
    loadError.value = error.message || '주문 내역을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

onMounted(loadOrders)

const visibleOrders = computed(() => {
  const group = TAB_STATUS_GROUPS[activeTab.value]
  if (!group) return orders.value
  return orders.value.filter((order) => group.includes(order.status))
})

const requestModal = ref(null)
const requestReason = ref('')
const requestDetail = ref('')
const requestError = ref('')
const noticeMessage = ref('')

const requestReasonOptions = computed(
  () => (requestModal.value?.type === 'RETURN' ? RETURN_REASONS : CANCEL_REASONS),
)
const requestModalTitle = computed(
  () => (requestModal.value?.type === 'RETURN' ? '반품 신청' : '거래 취소 요청'),
)

function badgeVariant(status) {
  if (IN_PROGRESS_STATUSES.includes(status)) return 'primary'
  if (REQUESTED_STATUSES.includes(status)) return 'danger'
  return 'gray'
}

function canRequestCancel(order) {
  return order.status === '결제 완료'
}

// 배송 완료 개념이 없으므로, 물건을 받아 본 뒤의 반품도 결제 완료 상태에서 신청합니다.
function canRequestReturn(order) {
  return order.status === '결제 완료'
}

// '주문 조회'는 결제 정보만 다시 보여줄 뿐 구매자가 실제로 필요한 행동이 아니었습니다.
// 배송 추적이 없는 서비스에서 막히면 결국 판매자에게 물어봐야 하므로 문의 동선으로 바꿨습니다.
const openingChatOrderId = ref('')
const chatError = ref('')

// 채팅방은 ON_SALE 매물에만 만들 수 있습니다(ChatRoomService.CHAT_CREATABLE_LISTING_STATUS).
// 결제까지 한 구매자가 판매 완료된 상품의 판매자에게 문의할 수 없는 것은 별도로 다뤄야 하는
// 채팅 도메인 정책 문제입니다. 지금은 서버 메시지를 그대로 보여 줍니다.
async function contactSeller(order) {
  if (!order.productId) {
    chatError.value = '이 주문에 연결된 상품 정보를 찾을 수 없습니다.'
    return
  }
  openingChatOrderId.value = order.id
  chatError.value = ''
  try {
    const room = await createOrGetChatRoom(order.productId)
    await router.push({ name: 'chat', params: { roomId: room.roomId } })
  } catch (error) {
    chatError.value = error.message || '채팅방을 열지 못했습니다.'
  } finally {
    openingChatOrderId.value = ''
  }
}

function openRequestModal(type) {
  requestModal.value = { type, orderId: selectedOrder.value.id }
  requestReason.value = ''
  requestDetail.value = ''
  requestError.value = ''
}

function closeRequestModal() {
  requestModal.value = null
}

function submitRequest() {
  requestError.value = ''
  if (!requestReason.value) {
    requestError.value = '사유를 선택해 주세요.'
    return
  }

  const isReturn = requestModal.value.type === 'RETURN'
  const target = orders.value.find((order) => order.id === requestModal.value.orderId)
  if (target) {
    target.status = isReturn ? '반품 접수' : '취소 요청'
    target.progress = isReturn ? '반품 신청 접수 · 판매자 확인 대기' : '취소 요청 접수 · 판매자 확인 대기'
    target.requestNote = [requestReason.value, requestDetail.value.trim()].filter(Boolean).join(' / ')
    selectedOrder.value = target
  }

  noticeMessage.value = isReturn
    ? '반품 신청을 접수했습니다. 판매자 확인 후 반환 방법을 협의하게 됩니다.'
    : '거래 취소 요청을 접수했습니다. 판매자 확인 후 환불이 진행됩니다.'
  closeRequestModal()
}
</script>

<template>
  <MyPageLayout>
    <div class="mb-5">
      <p class="text-xs font-semibold text-primary">
        MY PAGE
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        주문 내역
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        최근 주문한 상품의 결제 상태를 확인하고, 필요하면 판매자에게 바로 문의하세요.
      </p>
    </div>

    <BaseTabs
      v-model="activeTab"
      :tabs="ORDER_TABS"
      class="mb-5"
    />

    <p
      v-if="noticeMessage"
      role="status"
      class="mb-4 rounded-md bg-accent px-4 py-3 text-sm text-primary-dark"
    >
      {{ noticeMessage }}
    </p>
    <p
      v-if="chatError"
      role="alert"
      class="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ chatError }}
    </p>

    <BaseCard
      v-if="isLoading"
      class="py-14 text-center"
    >
      <p
        class="text-sm text-text-sub"
        aria-live="polite"
      >
        주문 내역을 불러오는 중...
      </p>
    </BaseCard>

    <BaseCard
      v-else-if="loadError"
      class="py-14 text-center"
    >
      <p
        role="alert"
        class="text-sm text-red-700"
      >
        {{ loadError }}
      </p>
    </BaseCard>

    <ul
      v-else
      class="space-y-3"
    >
      <li
        v-for="order in visibleOrders"
        :key="order.id"
        class="flex items-center gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <!-- 대표 이미지는 그 상품의 얼굴이라 주문 내역에서도 보여줍니다. -->
        <div class="h-14 w-14 shrink-0 overflow-hidden rounded-md bg-bg">
          <img
            v-if="order.thumbnailUrl"
            :src="order.thumbnailUrl"
            :alt="order.name"
            class="h-full w-full object-cover"
          >
          <span
            v-else
            class="flex h-full w-full items-center justify-center text-lg text-slate-300"
          >▣</span>
        </div>
        <!-- 주문 상세는 버튼 대신 항목 자체를 눌러서 엽니다. 버튼 자리는 문의에 씁니다. -->
        <button
          type="button"
          class="min-w-0 flex-1 text-left"
          @click="selectedOrder = order"
        >
          <BaseBadge
            :variant="badgeVariant(order.status)"
            class="mb-1"
          >
            {{ order.status }}
          </BaseBadge>
          <p class="truncate text-sm font-semibold text-text-main">
            {{ order.name }}
          </p>
          <p class="text-xs text-text-sub">
            {{ order.date }} · {{ order.id }}
          </p>
        </button>
        <div class="text-right">
          <p class="mb-2 text-sm font-bold text-text-main">
            {{ order.price }}원
          </p>
          <BaseButton
            variant="outline"
            :disabled="openingChatOrderId === order.id"
            @click="contactSeller(order)"
          >
            {{ openingChatOrderId === order.id ? '채팅방 여는 중…' : '판매자에게 문의' }}
          </BaseButton>
        </div>
      </li>
    </ul>

    <BaseCard
      v-if="!isLoading && !loadError && !visibleOrders.length"
      class="py-14 text-center"
    >
      <p class="font-semibold text-text-main">
        해당 상태의 주문이 없습니다.
      </p>
    </BaseCard>

    <BaseCard
      v-if="selectedOrder"
      class="mt-6"
    >
      <div class="flex items-start justify-between gap-4">
        <div>
          <p class="text-xs font-semibold text-primary">
            주문 상세
          </p>
          <h2 class="mt-2 text-lg font-bold text-text-main">
            {{ selectedOrder.name }}
          </h2>
          <p class="mt-1 text-sm text-text-sub">
            {{ selectedOrder.id }} · {{ selectedOrder.date }}
          </p>
        </div>
        <button
          type="button"
          class="text-sm font-semibold text-text-sub hover:text-text-main"
          aria-label="주문 상세 닫기"
          @click="selectedOrder = null"
        >
          닫기
        </button>
      </div>
      <div class="mt-5 grid gap-4 rounded-lg bg-bg p-5 text-sm sm:grid-cols-3">
        <div>
          <p class="text-xs text-text-sub">
            주문 상태
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.status }}
          </p>
        </div>
        <div>
          <p class="text-xs text-text-sub">
            결제 금액
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.price }}원
          </p>
        </div>
        <div>
          <p class="text-xs text-text-sub">
            진행 상태
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.progress }}
          </p>
        </div>
      </div>

      <p
        v-if="selectedOrder.requestNote"
        class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        접수된 사유: {{ selectedOrder.requestNote }}
      </p>

      <div
        v-if="canRequestCancel(selectedOrder) || canRequestReturn(selectedOrder)"
        class="mt-5 flex flex-wrap gap-3 border-t border-border pt-5"
      >
        <BaseButton
          v-if="canRequestCancel(selectedOrder)"
          variant="outline"
          @click="openRequestModal('CANCEL')"
        >
          거래 취소 요청
        </BaseButton>
        <BaseButton
          v-if="canRequestReturn(selectedOrder)"
          variant="outline"
          @click="openRequestModal('RETURN')"
        >
          반품 신청
        </BaseButton>
      </div>
      <p
        v-else-if="REQUESTED_STATUSES.includes(selectedOrder.status)"
        class="mt-5 border-t border-border pt-5 text-sm text-text-sub"
      >
        요청이 접수되어 판매자 확인을 기다리는 중입니다.
      </p>
    </BaseCard>

    <!-- 거래 취소 / 반품 신청 모달 -->
    <div
      v-if="requestModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      role="dialog"
      aria-modal="true"
      :aria-label="requestModalTitle"
      @click.self="closeRequestModal"
    >
      <div class="w-full max-w-md rounded-lg bg-surface p-6 shadow-elevated">
        <h2 class="text-base font-bold text-text-main">
          {{ requestModalTitle }}
        </h2>
        <p class="mt-1 text-xs text-text-sub">
          {{ requestModal.orderId }}
        </p>

        <fieldset class="mt-5">
          <legend class="text-sm font-semibold text-text-main">
            사유를 선택해 주세요.
          </legend>
          <label
            v-for="reason in requestReasonOptions"
            :key="reason"
            class="mt-2 flex cursor-pointer items-start gap-3 rounded-md border border-border p-3 text-sm text-text-main"
            :class="requestReason === reason ? 'border-primary bg-accent' : 'hover:border-primary'"
          >
            <input
              v-model="requestReason"
              type="radio"
              :value="reason"
              class="mt-1 h-4 w-4 accent-primary"
            >
            <span>{{ reason }}</span>
          </label>
        </fieldset>

        <label class="mt-4 block text-sm font-semibold text-text-main">
          추가 설명 (선택)
          <textarea
            v-model="requestDetail"
            rows="3"
            maxlength="500"
            placeholder="판매자에게 전달할 내용을 적어 주세요."
            class="mt-2 w-full rounded-md border border-border px-3 py-2 text-sm font-normal outline-none focus:border-primary"
          />
        </label>

        <p
          v-if="requestError"
          role="alert"
          class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {{ requestError }}
        </p>

        <div class="mt-5 flex gap-3">
          <BaseButton
            variant="outline"
            class="flex-1"
            @click="closeRequestModal"
          >
            닫기
          </BaseButton>
          <BaseButton
            class="flex-1"
            @click="submitRequest"
          >
            신청하기
          </BaseButton>
        </div>
      </div>
    </div>
  </MyPageLayout>
</template>
