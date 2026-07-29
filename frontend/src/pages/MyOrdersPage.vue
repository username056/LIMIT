<script setup>
import { computed, ref } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseTabs from '../components/BaseTabs.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const activeTab = ref('전체')
const selectedOrder = ref(null)

// 주문 상태 문자열은 화면 표시와 탭 필터에 함께 쓰입니다.
// TODO(주문 API 연동): 취소·반품 요청은 아직 화면 상태만 바꿉니다. 주문 상태 전이 API가
// 준비되면 requestModal 제출 부분에서 실제 요청을 보내고 응답으로 상태를 갱신하세요.
const ORDER_TABS = ['전체', '결제완료', '배송중', '배송완료', '취소/반품']
const TAB_STATUS_GROUPS = {
  결제완료: ['결제완료'],
  배송중: ['배송중'],
  배송완료: ['배송완료'],
  '취소/반품': ['취소 요청', '반품 접수', '취소/환불'],
}
const IN_PROGRESS_STATUSES = ['결제완료', '배송중']
const REQUESTED_STATUSES = ['취소 요청', '반품 접수']

const CANCEL_REASONS = [
  '상품이 필요 없어졌습니다.',
  '다른 상품으로 다시 구매하려고 합니다.',
  '판매자와 연락이 되지 않습니다.',
  '배송이 너무 지연되고 있습니다.',
]
const RETURN_REASONS = [
  '상품 상태가 설명과 다릅니다.',
  '설명에 없던 하자가 있습니다.',
  '구성품이 누락되었습니다.',
  '배송 중 파손되었습니다.',
]

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const orders = ref([
  {
    name: "Air Jordan 1 Retro High OG 'White Cement'",
    date: '2024.05.28',
    id: '#OR20240528-001',
    price: '249,000',
    status: '배송완료',
    delivery: '2024.05.30 배송 완료',
    requestNote: '',
  },
  {
    name: "Yeezy Boost 350 V2 'Slate'",
    date: '2024.05.20',
    id: '#OR20240520-004',
    price: '229,000',
    status: '배송중',
    delivery: '2024.05.23 집화 완료',
  },
  {
    name: "New Balance 990v6 Made in USA 'Grey'",
    date: '2024.05.12',
    id: '#OR20240512-002',
    price: '299,000',
    status: '결제완료',
    delivery: '판매자 발송 대기',
    requestNote: '',
  },
  {
    name: "Sony WH-1000XM5 'Black'",
    date: '2024.05.02',
    id: '#OR20240502-003',
    price: '289,000',
    status: '취소/환불',
    delivery: '2024.05.03 결제 취소',
    requestNote: '',
  },
])

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
  return order.status === '결제완료'
}

function canRequestReturn(order) {
  return order.status === '배송완료'
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
    target.delivery = isReturn ? '반품 신청 접수 · 회수 대기' : '취소 요청 접수 · 판매자 확인 대기'
    target.requestNote = [requestReason.value, requestDetail.value.trim()].filter(Boolean).join(' / ')
    selectedOrder.value = target
  }

  noticeMessage.value = isReturn
    ? '반품 신청을 접수했습니다. 판매자 확인 후 회수 절차를 안내드립니다.'
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
        최근 주문한 상품의 결제와 배송 상태를 확인하세요.
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

    <ul class="space-y-3">
      <li
        v-for="order in visibleOrders"
        :key="order.id"
        class="flex items-center gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <div class="h-14 w-14 shrink-0 rounded-md bg-bg" />
        <div class="min-w-0 flex-1">
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
        </div>
        <div class="text-right">
          <p class="mb-2 text-sm font-bold text-text-main">
            {{ order.price }}원
          </p>
          <BaseButton
            variant="outline"
            @click="selectedOrder = order"
          >
            주문 조회
          </BaseButton>
        </div>
      </li>
    </ul>

    <BaseCard
      v-if="!visibleOrders.length"
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
            배송 정보
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.delivery }}
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
