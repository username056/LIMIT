<script setup>
import { computed, onMounted, ref } from 'vue'
import BaseBadge from '../BaseBadge.vue'
import BaseButton from '../BaseButton.vue'
import BaseCard from '../BaseCard.vue'
import {
  decideAdminReport,
  decideAdminRestoration,
  getAdminModeratedProducts,
  getAdminModeratedProduct,
  getAdminReports,
  getAdminRestorationRequests,
  getAdminRiskSignals,
  getModerationDashboard,
  resolveAdminRiskSignal,
} from '../../api/admin'

const activeTab = ref('overview')
const isLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const dashboard = ref(null)
const reports = ref([])
const restorations = ref([])
const signals = ref([])
const products = ref([])
const selectedProduct = ref(null)
const reportStatus = ref('PENDING')
const restorationStatus = ref('PENDING')
const riskStatus = ref('OPEN')
const riskType = ref('')
const productKeyword = ref('')
const productModerationStatus = ref('')

const tabs = [
  { id: 'overview', label: '현황' },
  { id: 'reports', label: '신고 심사' },
  { id: 'restorations', label: '복구 승인' },
  { id: 'risks', label: '이상 활동' },
  { id: 'products', label: '전체 상품' },
]

const overviewCards = computed(() => {
  const value = dashboard.value || {}
  return [
    { label: '처리 대기 신고', value: value.pendingReportCount || 0, tone: 'text-red-600' },
    { label: '경고 확인 대기', value: value.warningRequiredProductCount || 0, tone: 'text-amber-600' },
    { label: '판매 중지', value: value.suspendedProductCount || 0, tone: 'text-red-600' },
    { label: '복구 승인 대기', value: value.pendingRestorationCount || 0, tone: 'text-sky-600' },
    { label: '미검토 위험 신호', value: value.openRiskSignalCount || 0, tone: 'text-violet-600' },
  ]
})

const categoryLabels = {
  INACCURATE_INFORMATION: '상품 정보가 사실과 다름',
  DUPLICATE_LISTING: '중복 등록',
  FRAUD_SUSPECTED: '사기 의심',
  PROHIBITED_ITEM: '판매 금지 상품',
  INAPPROPRIATE_CONTENT: '부적절한 내용',
  OTHER: '기타',
}

const riskLabels = {
  PUBLISHING_VELOCITY: '7일 내 과다 판매 시작',
  SIMILAR_TITLE: '유사 상품명 반복',
  SIMILAR_IMAGE: '유사 이미지 반복',
}

const moderationLabels = {
  NORMAL: '정상',
  WARNING_ACK_REQUIRED: '경고 확인 대기',
  SUSPENDED: '판매 중지',
  RESTORE_REQUESTED: '복구 심사 중',
}

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function pageContent(result) {
  return result?.content || []
}

function showError(error, fallback) {
  errorMessage.value = error.message || fallback
  successMessage.value = ''
}

async function loadCurrentTab() {
  isLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (activeTab.value === 'overview') {
      dashboard.value = await getModerationDashboard()
    } else if (activeTab.value === 'reports') {
      reports.value = pageContent(await getAdminReports({ status: reportStatus.value, size: 50 }))
    } else if (activeTab.value === 'restorations') {
      restorations.value = pageContent(await getAdminRestorationRequests({
        status: restorationStatus.value,
        size: 50,
      }))
    } else if (activeTab.value === 'risks') {
      signals.value = pageContent(await getAdminRiskSignals({
        status: riskStatus.value,
        type: riskType.value,
        size: 50,
      }))
    } else {
      products.value = pageContent(await getAdminModeratedProducts({
        keyword: productKeyword.value.trim(),
        moderationStatus: productModerationStatus.value,
        size: 50,
      }))
    }
  } catch (error) {
    showError(error, '상품 운영 데이터를 불러오지 못했습니다.')
  } finally {
    isLoading.value = false
  }
}

async function selectTab(tab) {
  activeTab.value = tab
  await loadCurrentTab()
}

async function decideReport(report, decision) {
  const promptText = decision === 'DISMISS'
    ? '신고를 기각하는 사유를 입력해 주세요.'
    : decision === 'WARN'
      ? '판매자에게 보여 줄 경고 내용을 입력해 주세요.'
      : '판매 중지 사유와 수정이 필요한 부분을 입력해 주세요.'
  const note = window.prompt(promptText)
  if (!note?.trim()) return
  try {
    await decideAdminReport(report.reportId, { decision, note: note.trim() })
    await loadCurrentTab()
    successMessage.value = decision === 'SUSPEND'
      ? '상품을 판매 중지하고 판매자에게 사유를 안내했습니다.'
      : '신고 처리를 완료했습니다.'
  } catch (error) {
    showError(error, '신고를 처리하지 못했습니다.')
  }
}

async function decideRestoration(request, decision) {
  const note = window.prompt(decision === 'APPROVE'
    ? '복구 승인 메모를 입력해 주세요.'
    : '판매자에게 보여 줄 반려 사유를 입력해 주세요.')
  if (!note?.trim()) return
  try {
    await decideAdminRestoration(request.restorationRequestId, {
      decision,
      note: note.trim(),
    })
    await loadCurrentTab()
    successMessage.value = decision === 'APPROVE'
      ? '복구를 승인해 상품을 다시 공개했습니다.'
      : '복구 신청을 반려했습니다.'
  } catch (error) {
    showError(error, '복구 신청을 처리하지 못했습니다.')
  }
}

async function resolveRisk(signal) {
  const note = window.prompt('검토 결과를 입력해 주세요. 이 작업은 제재를 자동 적용하지 않습니다.')
  if (!note?.trim()) return
  try {
    await resolveAdminRiskSignal(signal.riskSignalId, note.trim())
    await loadCurrentTab()
    successMessage.value = '위험 신호를 검토 완료로 기록했습니다.'
  } catch (error) {
    showError(error, '위험 신호를 처리하지 못했습니다.')
  }
}

async function inspectProduct(productId) {
  errorMessage.value = ''
  try {
    selectedProduct.value = await getAdminModeratedProduct(productId)
  } catch (error) {
    showError(error, '상품 상세를 불러오지 못했습니다.')
  }
}

onMounted(loadCurrentTab)
</script>

<template>
  <section aria-labelledby="marketplace-moderation-heading">
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2
          id="marketplace-moderation-heading"
          class="text-xl font-bold text-text-main"
        >
          상품 신고·이상 활동 관리
        </h2>
        <p class="mt-1 text-sm text-text-sub">
          위험 신호는 검토 우선순위를 제안하며, 제재는 관리자가 신고 내용을 확인한 뒤 결정합니다.
        </p>
      </div>
      <BaseButton
        variant="outline"
        :disabled="isLoading"
        @click="loadCurrentTab"
      >
        새로고침
      </BaseButton>
    </div>

    <div
      class="mb-5 flex gap-2 overflow-x-auto border-b border-border"
      role="tablist"
    >
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.id"
        class="whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold"
        :class="activeTab === tab.id
          ? 'border-primary text-primary'
          : 'border-transparent text-text-sub hover:text-text-main'"
        @click="selectTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>

    <p
      v-if="errorMessage"
      role="alert"
      class="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </p>
    <p
      v-if="successMessage"
      role="status"
      class="mb-4 rounded-md bg-accent px-4 py-3 text-sm text-primary"
    >
      {{ successMessage }}
    </p>
    <p
      v-if="isLoading"
      class="py-16 text-center text-sm text-text-sub"
    >
      운영 데이터를 불러오는 중입니다.
    </p>

    <template v-else-if="activeTab === 'overview'">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <BaseCard
          v-for="card in overviewCards"
          :key="card.label"
          class="p-5"
        >
          <p class="text-sm text-text-sub">
            {{ card.label }}
          </p>
          <p
            class="mt-2 text-3xl font-black"
            :class="card.tone"
          >
            {{ card.value }}
          </p>
        </BaseCard>
      </div>
      <div class="mt-6 grid gap-6 xl:grid-cols-2">
        <BaseCard class="p-5">
          <h3 class="font-bold text-text-main">
            우선 확인할 판매자
          </h3>
          <div
            v-if="dashboard?.suspiciousSellers?.length"
            class="mt-4 space-y-3"
          >
            <div
              v-for="seller in dashboard.suspiciousSellers"
              :key="seller.sellerId"
              class="rounded-lg border border-border p-4"
            >
              <div class="flex items-center justify-between gap-3">
                <strong>판매자 #{{ seller.sellerId }}</strong>
                <BaseBadge variant="danger">
                  {{ seller.riskLevel }}
                </BaseBadge>
              </div>
              <p class="mt-2 text-sm text-text-sub">
                7일 판매 시작 {{ seller.publishedLastSevenDays }}회 · 열린 신호 {{ seller.openRiskSignalCount }}건
              </p>
            </div>
          </div>
          <p
            v-else
            class="mt-4 text-sm text-text-sub"
          >
            현재 우선 확인할 판매자가 없습니다.
          </p>
        </BaseCard>
        <BaseCard class="p-5">
          <h3 class="font-bold text-text-main">
            최근 위험 신호
          </h3>
          <div
            v-if="dashboard?.recentRiskSignals?.length"
            class="mt-4 space-y-3"
          >
            <div
              v-for="signal in dashboard.recentRiskSignals"
              :key="signal.riskSignalId"
              class="border-b border-border pb-3 last:border-0"
            >
              <div class="flex justify-between gap-3 text-sm">
                <strong>{{ riskLabels[signal.signalType] || signal.signalType }}</strong>
                <span>{{ signal.score }}점</span>
              </div>
              <p class="mt-1 text-sm text-text-sub">
                {{ signal.detail }}
              </p>
            </div>
          </div>
          <p
            v-else
            class="mt-4 text-sm text-text-sub"
          >
            최근 위험 신호가 없습니다.
          </p>
        </BaseCard>
      </div>
    </template>

    <template v-else-if="activeTab === 'reports'">
      <div class="mb-4 flex items-center gap-3">
        <label
          class="text-sm font-semibold"
          for="report-status"
        >처리 상태</label>
        <select
          id="report-status"
          v-model="reportStatus"
          class="rounded-md border border-border bg-white px-3 py-2 text-sm"
          @change="loadCurrentTab"
        >
          <option value="PENDING">
            처리 대기
          </option><option value="">
            전체
          </option>
          <option value="DISMISSED">
            기각
          </option><option value="WARNING_ISSUED">
            경고
          </option>
          <option value="SUSPENDED">
            판매 중지
          </option><option value="RESOLVED">
            확인 완료
          </option>
        </select>
      </div>
      <div
        v-if="reports.length"
        class="space-y-4"
      >
        <BaseCard
          v-for="report in reports"
          :key="report.reportId"
          class="p-5"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="text-xs text-text-sub">
                신고 #{{ report.reportId }} · {{ formatDate(report.createdAt) }}
              </p>
              <h3 class="mt-1 font-bold text-text-main">
                {{ report.productName }} <span class="font-normal text-text-sub">#{{ report.productId }}</span>
              </h3>
              <p class="mt-2 text-sm">
                <strong>{{ categoryLabels[report.category] || report.category }}</strong> — {{ report.detail }}
              </p>
              <p class="mt-2 text-xs text-text-sub">
                판매자 #{{ report.sellerId }} · 신고자 #{{ report.reporterId }} · {{ moderationLabels[report.moderationStatus] || report.moderationStatus }}
              </p>
            </div>
            <BaseBadge :variant="report.status === 'PENDING' ? 'danger' : 'gray'">
              {{ report.status }}
            </BaseBadge>
          </div>
          <div
            v-if="report.status === 'PENDING'"
            class="mt-4 flex flex-wrap gap-2"
          >
            <BaseButton
              variant="outline"
              @click="decideReport(report, 'DISMISS')"
            >
              기각
            </BaseButton>
            <BaseButton
              variant="outline"
              @click="decideReport(report, 'WARN')"
            >
              경고
            </BaseButton>
            <BaseButton
              variant="primary"
              @click="decideReport(report, 'SUSPEND')"
            >
              판매 중지
            </BaseButton>
          </div>
        </BaseCard>
      </div>
      <p
        v-else
        class="rounded-lg border border-dashed border-border py-14 text-center text-sm text-text-sub"
      >
        조건에 맞는 신고가 없습니다.
      </p>
    </template>

    <template v-else-if="activeTab === 'restorations'">
      <div class="mb-4 flex items-center gap-3">
        <label
          class="text-sm font-semibold"
          for="restoration-status"
        >심사 상태</label>
        <select
          id="restoration-status"
          v-model="restorationStatus"
          class="rounded-md border border-border bg-white px-3 py-2 text-sm"
          @change="loadCurrentTab"
        >
          <option value="PENDING">
            승인 대기
          </option><option value="">
            전체
          </option>
          <option value="APPROVED">
            승인
          </option><option value="REJECTED">
            반려
          </option>
        </select>
      </div>
      <div
        v-if="restorations.length"
        class="space-y-4"
      >
        <BaseCard
          v-for="request in restorations"
          :key="request.restorationRequestId"
          class="p-5"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="text-xs text-text-sub">
                복구 신청 #{{ request.restorationRequestId }} · {{ formatDate(request.createdAt) }}
              </p>
              <h3 class="mt-1 font-bold">
                {{ request.productName }} <span class="font-normal text-text-sub">#{{ request.productId }}</span>
              </h3>
              <p class="mt-2 text-sm">
                {{ request.requestNote || '판매자가 별도 메모를 남기지 않았습니다.' }}
              </p>
              <p class="mt-2 text-xs text-text-sub">
                판매자 #{{ request.sellerId }} · 마지막 상품 수정 {{ formatDate(request.productUpdatedAt) }}
              </p>
            </div>
            <BaseBadge :variant="request.status === 'PENDING' ? 'primary' : 'gray'">
              {{ request.status }}
            </BaseBadge>
          </div>
          <div
            v-if="request.status === 'PENDING'"
            class="mt-4 flex gap-2"
          >
            <BaseButton
              variant="primary"
              @click="decideRestoration(request, 'APPROVE')"
            >
              복구 승인
            </BaseButton>
            <BaseButton
              variant="outline"
              @click="decideRestoration(request, 'REJECT')"
            >
              반려
            </BaseButton>
          </div>
        </BaseCard>
      </div>
      <p
        v-else
        class="rounded-lg border border-dashed border-border py-14 text-center text-sm text-text-sub"
      >
        조건에 맞는 복구 신청이 없습니다.
      </p>
    </template>

    <template v-else-if="activeTab === 'risks'">
      <div class="mb-4 flex flex-wrap gap-3">
        <select
          v-model="riskStatus"
          aria-label="위험 신호 상태"
          class="rounded-md border border-border bg-white px-3 py-2 text-sm"
          @change="loadCurrentTab"
        >
          <option value="OPEN">
            미검토
          </option><option value="">
            전체
          </option><option value="RESOLVED">
            검토 완료
          </option>
        </select>
        <select
          v-model="riskType"
          aria-label="위험 신호 종류"
          class="rounded-md border border-border bg-white px-3 py-2 text-sm"
          @change="loadCurrentTab"
        >
          <option value="">
            전체 유형
          </option><option value="PUBLISHING_VELOCITY">
            7일 과다 등록
          </option>
          <option value="SIMILAR_TITLE">
            유사 상품명
          </option><option value="SIMILAR_IMAGE">
            유사 이미지
          </option>
        </select>
      </div>
      <div
        v-if="signals.length"
        class="overflow-x-auto rounded-lg border border-border bg-white"
      >
        <table class="w-full min-w-[780px] text-left text-sm">
          <thead class="bg-surface text-text-sub">
            <tr>
              <th class="p-3">
                유형
              </th><th class="p-3">
                판매자/상품
              </th><th class="p-3">
                점수
              </th><th class="p-3">
                탐지 근거
              </th><th class="p-3">
                상태
              </th><th class="p-3">
                처리
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="signal in signals"
              :key="signal.riskSignalId"
              class="border-t border-border"
            >
              <td class="p-3 font-semibold">
                {{ riskLabels[signal.signalType] || signal.signalType }}
              </td>
              <td class="p-3">
                #{{ signal.sellerId }} / #{{ signal.productId }}<span v-if="signal.relatedProductId"> ↔ #{{ signal.relatedProductId }}</span>
              </td>
              <td class="p-3">
                {{ signal.score }}
              </td><td class="max-w-sm p-3 text-text-sub">
                {{ signal.detail }}
              </td>
              <td class="p-3">
                {{ signal.status }}
              </td>
              <td class="p-3">
                <button
                  v-if="signal.status === 'OPEN'"
                  type="button"
                  class="font-semibold text-primary"
                  @click="resolveRisk(signal)"
                >
                  검토 완료
                </button><span v-else>-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        v-else
        class="rounded-lg border border-dashed border-border py-14 text-center text-sm text-text-sub"
      >
        조건에 맞는 위험 신호가 없습니다.
      </p>
    </template>

    <template v-else>
      <form
        class="mb-4 flex flex-wrap gap-3"
        @submit.prevent="loadCurrentTab"
      >
        <input
          v-model="productKeyword"
          type="search"
          placeholder="상품명 검색"
          class="min-w-64 rounded-md border border-border px-3 py-2 text-sm"
        >
        <select
          v-model="productModerationStatus"
          aria-label="상품 운영 상태"
          class="rounded-md border border-border bg-white px-3 py-2 text-sm"
        >
          <option value="">
            전체 운영 상태
          </option><option value="NORMAL">
            정상
          </option>
          <option value="WARNING_ACK_REQUIRED">
            경고 확인 대기
          </option><option value="SUSPENDED">
            판매 중지
          </option>
          <option value="RESTORE_REQUESTED">
            복구 심사 중
          </option>
        </select>
        <BaseButton type="submit">
          검색
        </BaseButton>
      </form>
      <div
        v-if="products.length"
        class="overflow-x-auto rounded-lg border border-border bg-white"
      >
        <table class="w-full min-w-[820px] text-left text-sm">
          <thead class="bg-surface text-text-sub">
            <tr>
              <th class="p-3">
                상품
              </th><th class="p-3">
                판매자
              </th><th class="p-3">
                판매 상태
              </th><th class="p-3">
                운영 상태
              </th><th class="p-3">
                대기 신고
              </th><th class="p-3">
                위험 신호
              </th><th class="p-3">
                최근 수정
              </th><th class="p-3">
                조회
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in products"
              :key="item.productId"
              class="border-t border-border"
            >
              <td class="p-3 font-semibold">
                {{ item.productName }} <span class="font-normal text-text-sub">#{{ item.productId }}</span>
              </td>
              <td class="p-3">
                {{ item.sellerNickname || '-' }} (#{{ item.sellerId }})
              </td><td class="p-3">
                {{ item.lifecycleStatus }}
              </td>
              <td class="p-3">
                <BaseBadge :variant="item.moderationStatus === 'NORMAL' ? 'success' : 'danger'">
                  {{ moderationLabels[item.moderationStatus] || item.moderationStatus }}
                </BaseBadge>
              </td>
              <td class="p-3">
                {{ item.pendingReportCount }}
              </td><td class="p-3">
                {{ item.openRiskSignalCount }}
              </td><td class="p-3">
                {{ formatDate(item.updatedAt) }}
              </td>
              <td class="p-3">
                <button
                  type="button"
                  class="font-semibold text-primary"
                  @click="inspectProduct(item.productId)"
                >
                  상세
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        v-else
        class="rounded-lg border border-dashed border-border py-14 text-center text-sm text-text-sub"
      >
        조건에 맞는 상품이 없습니다.
      </p>
      <BaseCard
        v-if="selectedProduct"
        class="mt-5 p-5"
      >
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-primary">
              관리자 전용 상품 상세 #{{ selectedProduct.productId }}
            </p>
            <h3 class="mt-1 text-lg font-bold text-text-main">
              {{ selectedProduct.name }}
            </h3>
          </div>
          <button
            type="button"
            class="text-sm text-text-sub"
            @click="selectedProduct = null"
          >
            닫기
          </button>
        </div>
        <p class="mt-4 whitespace-pre-wrap text-sm leading-6 text-text-main">
          {{ selectedProduct.description || '등록된 설명이 없습니다.' }}
        </p>
        <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-3">
          <div>
            <dt class="text-text-sub">
              가격
            </dt><dd class="font-semibold">
              {{ Number(selectedProduct.price || 0).toLocaleString() }}원
            </dd>
          </div>
          <div>
            <dt class="text-text-sub">
              판매 상태
            </dt><dd class="font-semibold">
              {{ selectedProduct.status }}
            </dd>
          </div>
          <div>
            <dt class="text-text-sub">
              운영 상태
            </dt><dd class="font-semibold">
              {{ moderationLabels[selectedProduct.moderationStatus] || selectedProduct.moderationStatus }}
            </dd>
          </div>
        </dl>
        <div
          v-if="selectedProduct.moderationNotices?.length"
          class="mt-4 rounded-md bg-red-50 p-4 text-sm"
        >
          <p class="font-semibold text-red-700">
            신고·조치 내역
          </p>
          <ul class="mt-2 space-y-2 text-text-main">
            <li
              v-for="notice in selectedProduct.moderationNotices"
              :key="notice.reportId"
            >
              {{ notice.detail }}<span v-if="notice.adminNote"> — {{ notice.adminNote }}</span>
            </li>
          </ul>
        </div>
      </BaseCard>
    </template>
  </section>
</template>
