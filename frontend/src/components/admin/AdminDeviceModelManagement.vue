<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  getAdminDeviceModel,
  getAdminDeviceModelProductMaterials,
  getAdminDeviceModelProducts,
  getAdminDeviceModelResearches,
  getAdminDeviceModels,
  researchAdminDeviceModel,
  updateAdminDeviceModel,
  updateAdminDeviceModelStatus,
} from '../../api/admin'
import { getDeviceCategories } from '../../api/products'
import BaseBadge from '../BaseBadge.vue'
import BaseButton from '../BaseButton.vue'
import BaseCard from '../BaseCard.vue'
import BaseInput from '../BaseInput.vue'

const emptyPage = (size = 20) => ({
  content: [], page: 0, size, totalElements: 0, totalPages: 0, hasNext: false,
})

const filters = reactive({
  keyword: '',
  categoryId: '',
  isActive: '',
  reviewStatus: '',
  researchStatus: '',
  sort: 'updatedAt,desc',
})
const modelPage = ref(emptyPage())
const categories = ref([])
const selectedModel = ref(null)
const activeTab = ref('overview')
const isLoadingModels = ref(false)
const isLoadingDetail = ref(false)
const isSaving = ref(false)
const isResearching = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({
  categoryId: '', manufacturer: '', modelName: '', modelCode: '', osFamily: 'ANDROID',
})

const researchPage = ref(emptyPage(10))
const isLoadingResearches = ref(false)
const productPage = ref(emptyPage(10))
const productSort = ref('updatedAt,desc')
const isLoadingProducts = ref(false)
const selectedProductId = ref(null)
const productMaterials = ref(null)
const isLoadingMaterials = ref(false)

const showStatusPanel = ref(false)
const statusReason = ref('')
const replacementKeyword = ref('')
const replacementModels = ref([])
const replacementModelId = ref('')
const isUpdatingStatus = ref(false)
let keywordTimer
let replacementTimer

function queryParams(page = 0) {
  return {
    keyword: filters.keyword.trim() || undefined,
    categoryId: filters.categoryId || undefined,
    isActive: filters.isActive === '' ? undefined : filters.isActive,
    reviewStatus: filters.reviewStatus || undefined,
    researchStatus: filters.researchStatus || undefined,
    page,
    size: 20,
    sort: filters.sort,
  }
}

function showError(error, fallback) {
  errorMessage.value = error?.message || fallback
  successMessage.value = ''
}

async function loadModels(page = 0) {
  isLoadingModels.value = true
  errorMessage.value = ''
  try {
    modelPage.value = await getAdminDeviceModels(queryParams(page))
  } catch (error) {
    showError(error, '모델 목록을 불러오지 못했습니다.')
  } finally {
    isLoadingModels.value = false
  }
}

function applyDetail(detail) {
  selectedModel.value = detail
  Object.assign(form, {
    categoryId: detail.categoryId || '',
    manufacturer: detail.manufacturer || '',
    modelName: detail.modelName || '',
    modelCode: detail.modelCode || '',
    osFamily: detail.osFamily || 'ANDROID',
  })
}

async function selectModel(modelId) {
  isLoadingDetail.value = true
  errorMessage.value = ''
  successMessage.value = ''
  activeTab.value = 'overview'
  researchPage.value = emptyPage(10)
  productPage.value = emptyPage(10)
  productMaterials.value = null
  selectedProductId.value = null
  showStatusPanel.value = false
  try {
    applyDetail(await getAdminDeviceModel(modelId))
  } catch (error) {
    showError(error, '모델 상세 정보를 불러오지 못했습니다.')
  } finally {
    isLoadingDetail.value = false
  }
}

async function saveModel(showSuccess = true) {
  if (!selectedModel.value || isSaving.value) return false
  if (!form.categoryId || !form.manufacturer.trim() || !form.modelName.trim()) {
    errorMessage.value = '카테고리, 제조사, 모델명을 입력해 주세요.'
    return false
  }
  isSaving.value = true
  errorMessage.value = ''
  try {
    applyDetail(await updateAdminDeviceModel(selectedModel.value.modelId, {
      categoryId: Number(form.categoryId),
      manufacturer: form.manufacturer.trim(),
      modelName: form.modelName.trim(),
      modelCode: form.modelCode.trim() || null,
      osFamily: form.osFamily,
    }))
    await loadModels(modelPage.value.page)
    if (showSuccess) successMessage.value = '모델 정보를 수정했습니다.'
    return true
  } catch (error) {
    showError(error, '모델 정보를 수정하지 못했습니다.')
    return false
  } finally {
    isSaving.value = false
  }
}

async function rerunResearch() {
  if (!selectedModel.value || isResearching.value) return
  if (!(await saveModel(false))) return
  isResearching.value = true
  errorMessage.value = ''
  try {
    await researchAdminDeviceModel(selectedModel.value.modelId)
    await selectModel(selectedModel.value.modelId)
    successMessage.value = '수정된 모델 정보로 AI 재조사를 완료했습니다.'
  } catch (error) {
    showError(error, '모델 AI 재조사를 완료하지 못했습니다.')
  } finally {
    isResearching.value = false
  }
}

async function selectTab(tab) {
  activeTab.value = tab
  if (tab === 'researches' && researchPage.value.totalElements === 0) await loadResearches(0)
  if (tab === 'products' && productPage.value.totalElements === 0) await loadProducts(0)
}

async function loadResearches(page = 0) {
  if (!selectedModel.value) return
  isLoadingResearches.value = true
  try {
    researchPage.value = await getAdminDeviceModelResearches(selectedModel.value.modelId, {
      page, size: 10,
    })
  } catch (error) {
    showError(error, 'AI 조사 이력을 불러오지 못했습니다.')
  } finally {
    isLoadingResearches.value = false
  }
}

async function loadProducts(page = 0) {
  if (!selectedModel.value) return
  isLoadingProducts.value = true
  productMaterials.value = null
  selectedProductId.value = null
  try {
    productPage.value = await getAdminDeviceModelProducts(selectedModel.value.modelId, {
      page, size: 10, sort: productSort.value,
    })
  } catch (error) {
    showError(error, '연관 상품을 불러오지 못했습니다.')
  } finally {
    isLoadingProducts.value = false
  }
}

async function loadMaterials(productId) {
  if (!selectedModel.value) return
  isLoadingMaterials.value = true
  selectedProductId.value = productId
  productMaterials.value = null
  try {
    productMaterials.value = await getAdminDeviceModelProductMaterials(
      selectedModel.value.modelId,
      productId,
    )
  } catch (error) {
    showError(error, '상품 자료를 불러오지 못했습니다.')
  } finally {
    isLoadingMaterials.value = false
  }
}

async function searchReplacementModels() {
  if (!replacementKeyword.value.trim()) {
    replacementModels.value = []
    replacementModelId.value = ''
    return
  }
  try {
    const page = await getAdminDeviceModels({
      keyword: replacementKeyword.value.trim(),
      isActive: true,
      reviewStatus: 'VERIFIED',
      page: 0,
      size: 10,
      sort: 'modelName,asc',
    })
    replacementModels.value = page.content.filter(
      (model) => model.modelId !== selectedModel.value?.modelId,
    )
  } catch (error) {
    showError(error, '대체 모델을 검색하지 못했습니다.')
  }
}

async function updateStatus(isActive) {
  if (!selectedModel.value || isUpdatingStatus.value) return
  const reason = statusReason.value.trim()
  if (!isActive && !reason) {
    errorMessage.value = '비활성화 사유를 입력해 주세요.'
    return
  }
  const action = isActive ? '재활성화' : '비활성화'
  if (!window.confirm(`${selectedModel.value.modelName} 모델을 ${action}하시겠습니까?`)) return
  isUpdatingStatus.value = true
  errorMessage.value = ''
  try {
    applyDetail(await updateAdminDeviceModelStatus(selectedModel.value.modelId, {
      isActive,
      reason: reason || null,
      replacementModelId: !isActive && replacementModelId.value
        ? Number(replacementModelId.value)
        : null,
    }))
    await loadModels(modelPage.value.page)
    showStatusPanel.value = false
    statusReason.value = ''
    replacementKeyword.value = ''
    replacementModels.value = []
    replacementModelId.value = ''
    successMessage.value = `모델을 ${action}했습니다. 기존 상품은 그대로 유지됩니다.`
  } catch (error) {
    showError(error, `모델을 ${action}하지 못했습니다.`)
  } finally {
    isUpdatingStatus.value = false
  }
}

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value))
}

function formatPrice(value) {
  return `${Number(value || 0).toLocaleString('ko-KR')}원`
}

function reviewLabel(status) {
  if (status === 'PENDING_REVIEW') return '사후 검토 대기'
  if (status === 'VERIFIED') return '검토 완료'
  if (status === 'DISABLED') return '비활성'
  return status || '미지정'
}

function researchLabel(status) {
  if (status === 'PENDING_REVIEW') return '검토 대기'
  if (status === 'PROCESSING') return '조사 중'
  if (status === 'APPROVED') return '검토 완료'
  if (status === 'FAILED') return '조사 실패'
  if (status === 'REJECTED') return '반려'
  return status || '미조사'
}

function badgeVariant(status) {
  if (status === 'VERIFIED' || status === 'APPROVED' || status === 'ON_SALE') return 'success'
  if (status === 'FAILED' || status === 'SUSPENDED') return 'danger'
  if (status === 'DISABLED' || status === 'REJECTED' || status === 'HIDDEN') return 'gray'
  return 'primary'
}

watch(() => filters.keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => loadModels(0), 300)
})
watch(replacementKeyword, () => {
  clearTimeout(replacementTimer)
  replacementTimer = setTimeout(searchReplacementModels, 300)
})

onMounted(async () => {
  const [, loadedCategories] = await Promise.all([
    loadModels(0),
    getDeviceCategories({ activeOnly: true }),
  ])
  categories.value = loadedCategories
})
onBeforeUnmount(() => {
  clearTimeout(keywordTimer)
  clearTimeout(replacementTimer)
})
</script>

<template>
  <div class="space-y-5">
    <BaseCard>
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 class="font-bold">
            모델 관리
          </h2>
          <p class="mt-2 text-xs leading-5 text-text-sub">
            모델을 검색·수정하고 연관 상품과 조사 자료를 확인합니다. 비활성화해도 기존 상품은 유지됩니다.
          </p>
        </div>
        <span class="text-xs text-text-sub">총 {{ modelPage.totalElements }}개</span>
      </div>
      <div class="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <BaseInput
          v-model="filters.keyword"
          class="xl:col-span-2"
          label="모델 검색"
          placeholder="제조사·모델명·모델 코드"
        />
        <label class="text-sm font-medium text-text-main">
          카테고리
          <select
            v-model="filters.categoryId"
            aria-label="관리자 모델 카테고리 필터"
            class="mt-2 w-full rounded-md border border-border bg-white px-3 py-3 text-sm"
            @change="loadModels(0)"
          >
            <option value="">전체</option>
            <option
              v-for="category in categories"
              :key="category.categoryId"
              :value="category.categoryId"
            >
              {{ category.name }}
            </option>
          </select>
        </label>
        <label class="text-sm font-medium text-text-main">
          활성 상태
          <select
            v-model="filters.isActive"
            aria-label="관리자 모델 활성 상태 필터"
            class="mt-2 w-full rounded-md border border-border bg-white px-3 py-3 text-sm"
            @change="loadModels(0)"
          >
            <option value="">전체</option>
            <option value="true">활성</option>
            <option value="false">비활성</option>
          </select>
        </label>
        <label class="text-sm font-medium text-text-main">
          AI 조사
          <select
            v-model="filters.researchStatus"
            aria-label="관리자 모델 AI 조사 상태 필터"
            class="mt-2 w-full rounded-md border border-border bg-white px-3 py-3 text-sm"
            @change="loadModels(0)"
          >
            <option value="">전체</option>
            <option value="PENDING_REVIEW">검토 대기</option>
            <option value="APPROVED">검토 완료</option>
            <option value="FAILED">실패</option>
            <option value="PROCESSING">조사 중</option>
          </select>
        </label>
        <label class="text-sm font-medium text-text-main">
          정렬
          <select
            v-model="filters.sort"
            aria-label="관리자 모델 정렬"
            class="mt-2 w-full rounded-md border border-border bg-white px-3 py-3 text-sm"
            @change="loadModels(0)"
          >
            <option value="updatedAt,desc">최근 수정순</option>
            <option value="createdAt,desc">최근 등록순</option>
            <option value="modelName,asc">모델명순</option>
          </select>
        </label>
      </div>
    </BaseCard>

    <p
      v-if="errorMessage"
      class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p
      v-if="successMessage"
      class="rounded-md bg-green-50 px-4 py-3 text-sm text-green-700"
    >
      {{ successMessage }}
    </p>

    <div class="grid gap-5 xl:grid-cols-[minmax(300px,0.8fr)_minmax(0,1.6fr)]">
      <BaseCard>
        <p
          v-if="isLoadingModels"
          class="py-10 text-center text-sm text-text-sub"
        >
          모델을 불러오는 중입니다.
        </p>
        <div
          v-else
          class="space-y-2"
        >
          <button
            v-for="model in modelPage.content"
            :key="model.modelId"
            type="button"
            class="w-full rounded-md border px-4 py-3 text-left transition-colors"
            :class="selectedModel?.modelId === model.modelId
              ? 'border-primary bg-accent/60'
              : 'border-border bg-white hover:border-primary/40'"
            @click="selectModel(model.modelId)"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <strong class="text-sm">{{ model.manufacturer }} {{ model.modelName }}</strong>
                <p class="mt-1 text-xs text-text-sub">
                  {{ model.categoryName }} · {{ model.modelCode || '코드 없음' }}
                </p>
                <p class="mt-1 text-xs text-text-sub">
                  연관 상품 {{ model.relatedProductCount }}개 · {{ formatDate(model.updatedAt) }}
                </p>
              </div>
              <BaseBadge :variant="badgeVariant(model.reviewStatus)">
                {{ reviewLabel(model.reviewStatus) }}
              </BaseBadge>
            </div>
          </button>
          <p
            v-if="!modelPage.content.length"
            class="py-10 text-center text-sm text-text-sub"
          >
            검색 조건에 맞는 모델이 없습니다.
          </p>
        </div>
        <div
          v-if="modelPage.totalPages > 1"
          class="mt-4 flex items-center justify-between"
        >
          <BaseButton
            variant="outline"
            :disabled="modelPage.page === 0"
            @click="loadModels(modelPage.page - 1)"
          >
            이전
          </BaseButton>
          <span class="text-xs text-text-sub">{{ modelPage.page + 1 }} / {{ modelPage.totalPages }}</span>
          <BaseButton
            variant="outline"
            :disabled="!modelPage.hasNext"
            @click="loadModels(modelPage.page + 1)"
          >
            다음
          </BaseButton>
        </div>
      </BaseCard>

      <BaseCard v-if="selectedModel">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-primary">
              모델 #{{ selectedModel.modelId }} · {{ selectedModel.sourceType }}
            </p>
            <h3 class="mt-1 text-xl font-bold">
              {{ selectedModel.manufacturer }} {{ selectedModel.modelName }}
            </h3>
          </div>
          <BaseBadge :variant="badgeVariant(selectedModel.reviewStatus)">
            {{ reviewLabel(selectedModel.reviewStatus) }}
          </BaseBadge>
        </div>

        <div class="mt-5 grid gap-3 sm:grid-cols-4">
          <div class="rounded-md bg-bg p-3">
            <p class="text-xs text-text-sub">
              연관 상품
            </p><strong>{{ selectedModel.impact.productCount }}개</strong>
          </div>
          <div class="rounded-md bg-bg p-3">
            <p class="text-xs text-text-sub">
              판매 중
            </p><strong>{{ selectedModel.impact.productStatusCounts.ON_SALE || 0 }}개</strong>
          </div>
          <div class="rounded-md bg-bg p-3">
            <p class="text-xs text-text-sub">
              조사 이력
            </p><strong>{{ selectedModel.impact.researchCount }}건</strong>
          </div>
          <div class="rounded-md bg-bg p-3">
            <p class="text-xs text-text-sub">
              판매 옵션
            </p><strong>{{ selectedModel.impact.variantCount }}개</strong>
          </div>
        </div>

        <div class="mt-5 flex flex-wrap gap-2 border-b border-border pb-3">
          <button
            v-for="tab in [{ id: 'overview', label: '기본 정보' }, { id: 'researches', label: 'AI 조사 자료' }, { id: 'products', label: '연관 상품' }]"
            :key="tab.id"
            type="button"
            class="rounded-md px-3 py-2 text-sm font-semibold"
            :class="activeTab === tab.id ? 'bg-accent text-primary' : 'text-text-sub'"
            @click="selectTab(tab.id)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div
          v-if="activeTab === 'overview'"
          class="mt-5"
        >
          <form
            class="grid gap-3 sm:grid-cols-2"
            @submit.prevent="saveModel()"
          >
            <label class="text-xs font-semibold">
              카테고리
              <select
                v-model="form.categoryId"
                aria-label="관리자 모델 카테고리"
                class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm"
                required
              >
                <option value="">선택</option>
                <option
                  v-for="category in categories"
                  :key="category.categoryId"
                  :value="category.categoryId"
                >{{ category.name }}</option>
              </select>
            </label>
            <BaseInput
              v-model="form.manufacturer"
              label="제조사"
              required
            />
            <BaseInput
              v-model="form.modelName"
              label="모델명"
              required
            />
            <BaseInput
              v-model="form.modelCode"
              label="모델 코드"
            />
            <label class="text-xs font-semibold sm:col-span-2">
              운영체제
              <select
                v-model="form.osFamily"
                aria-label="관리자 모델 운영체제"
                class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm"
              >
                <option value="ANDROID">Android</option><option value="IOS">iOS</option><option value="WINDOWS">Windows</option><option value="LINUX">Linux</option><option value="MACOS">macOS</option>
              </select>
            </label>
            <div class="flex flex-wrap gap-2 sm:col-span-2">
              <BaseButton
                type="submit"
                :disabled="isSaving || isResearching"
              >
                {{ isSaving ? '저장 중…' : '수정 완료' }}
              </BaseButton>
              <BaseButton
                type="button"
                variant="outline"
                :disabled="isSaving || isResearching"
                @click="rerunResearch"
              >
                {{ isResearching ? '재조사 중…' : '수정값으로 모델 재조사' }}
              </BaseButton>
              <BaseButton
                type="button"
                variant="ghost"
                @click="showStatusPanel = !showStatusPanel"
              >
                {{ selectedModel.isActive ? '모델 삭제(비활성화)' : '모델 재활성화' }}
              </BaseButton>
            </div>
          </form>

          <div
            v-if="!selectedModel.isActive"
            class="mt-5 grid gap-3 rounded-md border border-border bg-bg p-4 text-xs sm:grid-cols-2"
          >
            <p>
              <span class="text-text-sub">삭제 사유</span><br>
              <strong>{{ selectedModel.disableReason || '-' }}</strong>
            </p>
            <p>
              <span class="text-text-sub">처리 일시</span><br>
              <strong>{{ formatDate(selectedModel.disabledAt) }}</strong>
            </p>
            <p>
              <span class="text-text-sub">처리 관리자</span><br>
              <strong>{{ selectedModel.disabledByAdminId ? `#${selectedModel.disabledByAdminId}` : '-' }}</strong>
            </p>
            <p>
              <span class="text-text-sub">대체 모델</span><br>
              <strong>{{ selectedModel.replacementModelId ? `#${selectedModel.replacementModelId}` : '지정 없음' }}</strong>
            </p>
          </div>

          <div
            v-if="showStatusPanel"
            class="mt-5 rounded-md border border-red-200 bg-red-50/60 p-4"
          >
            <h4 class="text-sm font-bold">
              {{ selectedModel.isActive ? '모델 삭제(비활성화)' : '모델 재활성화' }}
            </h4>
            <p class="mt-1 text-xs leading-5 text-text-sub">
              기존 상품 {{ selectedModel.impact.productCount }}개는 유지되며 신규 등록 목록에서만 제외됩니다.
            </p>
            <BaseInput
              v-model="statusReason"
              class="mt-3"
              label="처리 사유"
              :required="selectedModel.isActive"
            />
            <template v-if="selectedModel.isActive">
              <BaseInput
                v-model="replacementKeyword"
                class="mt-3"
                label="대체 모델 검색 (선택)"
                placeholder="제조사·모델명·모델 코드"
              />
              <select
                v-if="replacementModels.length"
                v-model="replacementModelId"
                aria-label="대체 모델 선택"
                class="mt-3 w-full rounded-md border border-border bg-white px-3 py-2 text-sm"
              >
                <option value="">
                  대체 모델 없음
                </option>
                <option
                  v-for="model in replacementModels"
                  :key="model.modelId"
                  :value="model.modelId"
                >
                  {{ model.manufacturer }} {{ model.modelName }} ({{ model.modelCode }})
                </option>
              </select>
            </template>
            <div class="mt-3 flex gap-2">
              <BaseButton
                :disabled="isUpdatingStatus"
                @click="updateStatus(!selectedModel.isActive)"
              >
                {{ isUpdatingStatus ? '처리 중…' : selectedModel.isActive ? '삭제 확인' : '재활성화 확인' }}
              </BaseButton>
              <BaseButton
                variant="ghost"
                @click="showStatusPanel = false"
              >
                취소
              </BaseButton>
            </div>
          </div>

          <div class="mt-6 grid gap-5 lg:grid-cols-2">
            <section>
              <h4 class="text-sm font-bold">
                필수 기본 체크리스트
              </h4>
              <ul class="mt-3 space-y-2">
                <li
                  v-for="item in selectedModel.baseChecklistItems"
                  :key="item.itemCode"
                  class="rounded-md border border-border bg-bg px-3 py-2 text-xs"
                >
                  <strong>{{ item.name }}</strong><p class="mt-1 text-text-sub">
                    {{ item.guide }}
                  </p>
                </li>
              </ul>
            </section>
            <section>
              <h4 class="text-sm font-bold">
                최신 AI 조사 항목
              </h4>
              <p
                v-if="selectedModel.latestResearch?.status === 'FAILED'"
                class="mt-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700"
              >
                {{ selectedModel.latestResearch.failureMessage || 'AI 조사에 실패했습니다.' }}
              </p>
              <ul class="mt-3 space-y-2">
                <li
                  v-for="suggestion in selectedModel.latestResearch?.suggestions || []"
                  :key="suggestion.featureCode"
                  class="rounded-md border border-primary/20 bg-accent/50 px-3 py-2 text-xs"
                >
                  <strong>{{ suggestion.featureName || suggestion.featureCode }}</strong><p class="mt-1 text-text-sub">
                    {{ suggestion.reason }}
                  </p>
                </li>
              </ul>
              <p
                v-if="!selectedModel.latestResearch"
                class="mt-3 text-xs text-text-sub"
              >
                아직 AI 조사 이력이 없습니다.
              </p>
            </section>
          </div>
        </div>

        <div
          v-else-if="activeTab === 'researches'"
          class="mt-5 space-y-3"
        >
          <p
            v-if="isLoadingResearches"
            class="py-8 text-center text-sm text-text-sub"
          >
            조사 이력을 불러오는 중입니다.
          </p>
          <article
            v-for="research in researchPage.content"
            :key="research.researchId"
            class="rounded-md border border-border p-4"
          >
            <div class="flex items-center justify-between gap-3">
              <strong>조사 버전 {{ research.researchVersion }}</strong><BaseBadge :variant="badgeVariant(research.status)">
                {{ researchLabel(research.status) }}
              </BaseBadge>
            </div>
            <p class="mt-1 text-xs text-text-sub">
              {{ formatDate(research.updatedAt || research.createdAt) }}
            </p>
            <p
              v-if="research.failureMessage"
              class="mt-3 text-xs text-red-700"
            >
              {{ research.failureCode }} · {{ research.failureMessage }}
            </p>
            <ul class="mt-3 space-y-2">
              <li
                v-for="suggestion in research.suggestions || []"
                :key="suggestion.featureCode"
                class="rounded-md bg-bg p-3 text-xs"
              >
                <strong>{{ suggestion.featureName || suggestion.featureCode }}</strong>
                <p class="mt-1 text-text-sub">
                  {{ suggestion.reason }}
                </p>
                <a
                  v-if="suggestion.sourceUrl"
                  :href="suggestion.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="mt-2 inline-block font-semibold text-primary"
                >{{ suggestion.sourceTitle || '공식 자료 열기' }}</a>
              </li>
            </ul>
          </article>
          <p
            v-if="!isLoadingResearches && !researchPage.content.length"
            class="py-8 text-center text-sm text-text-sub"
          >
            AI 조사 이력이 없습니다.
          </p>
          <div
            v-if="researchPage.totalPages > 1"
            class="flex items-center justify-between"
          >
            <BaseButton
              variant="outline"
              :disabled="researchPage.page === 0"
              @click="loadResearches(researchPage.page - 1)"
            >
              이전
            </BaseButton><span class="text-xs text-text-sub">{{ researchPage.page + 1 }} / {{ researchPage.totalPages }}</span><BaseButton
              variant="outline"
              :disabled="!researchPage.hasNext"
              @click="loadResearches(researchPage.page + 1)"
            >
              다음
            </BaseButton>
          </div>
        </div>

        <div
          v-else
          class="mt-5 space-y-4"
        >
          <div class="flex justify-end">
            <select
              v-model="productSort"
              aria-label="연관 상품 정렬"
              class="rounded-md border border-border bg-white px-3 py-2 text-sm"
              @change="loadProducts(0)"
            >
              <option value="updatedAt,desc">
                최근 수정순
              </option><option value="createdAt,desc">
                최근 등록순
              </option>
            </select>
          </div>
          <p
            v-if="isLoadingProducts"
            class="py-8 text-center text-sm text-text-sub"
          >
            연관 상품을 불러오는 중입니다.
          </p>
          <div
            v-for="product in productPage.content"
            :key="product.productId"
            class="rounded-md border border-border p-4"
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <strong>{{ product.title }}</strong><p class="mt-1 text-xs text-text-sub">
                  상품 #{{ product.productId }} · 판매자 #{{ product.sellerId }} · {{ formatPrice(product.price) }}
                </p><p class="mt-1 text-xs text-text-sub">
                  등록 {{ formatDate(product.createdAt) }} · 수정 {{ formatDate(product.updatedAt) }}
                </p>
              </div><BaseBadge :variant="badgeVariant(product.status)">
                {{ product.status }}
              </BaseBadge>
            </div>
            <div class="mt-3 flex gap-2">
              <a
                :href="`/products/${product.productId}`"
                target="_blank"
                rel="noopener noreferrer"
                class="rounded-md border border-border px-3 py-2 text-xs font-semibold"
              >상품 보기</a><button
                type="button"
                class="rounded-md bg-accent px-3 py-2 text-xs font-semibold text-primary"
                @click="loadMaterials(product.productId)"
              >
                사진·영상·검수 증빙 보기
              </button>
            </div>
          </div>
          <p
            v-if="!isLoadingProducts && !productPage.content.length"
            class="py-8 text-center text-sm text-text-sub"
          >
            연관 상품이 없습니다.
          </p>
          <div
            v-if="productPage.totalPages > 1"
            class="flex items-center justify-between"
          >
            <BaseButton
              variant="outline"
              :disabled="productPage.page === 0"
              @click="loadProducts(productPage.page - 1)"
            >
              이전
            </BaseButton><span class="text-xs text-text-sub">{{ productPage.page + 1 }} / {{ productPage.totalPages }}</span><BaseButton
              variant="outline"
              :disabled="!productPage.hasNext"
              @click="loadProducts(productPage.page + 1)"
            >
              다음
            </BaseButton>
          </div>

          <section
            v-if="selectedProductId"
            class="rounded-md border border-primary/20 bg-accent/20 p-4"
          >
            <h4 class="font-bold">
              상품 #{{ selectedProductId }} 자료
            </h4>
            <p
              v-if="isLoadingMaterials"
              class="py-8 text-center text-sm text-text-sub"
            >
              자료를 불러오는 중입니다.
            </p>
            <template v-else-if="productMaterials">
              <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <img
                  v-for="image in productMaterials.images"
                  :key="image.imageId"
                  :src="image.imageUrl"
                  :alt="`상품 이미지 ${image.imageId}`"
                  class="h-36 w-full rounded-md border border-border object-cover"
                >
              </div>
              <p
                v-if="!productMaterials.images.length"
                class="mt-3 text-xs text-text-sub"
              >
                등록된 상품 이미지가 없습니다.
              </p>
              <div class="mt-5 space-y-3">
                <article
                  v-for="item in productMaterials.checklistItems"
                  :key="item.checklistItemId"
                  class="rounded-md bg-white p-3"
                >
                  <div class="flex items-center justify-between">
                    <strong class="text-sm">{{ item.name }}</strong><BaseBadge :variant="item.completionStatus === 'COMPLETED' ? 'success' : 'gray'">
                      {{ item.completionStatus }}
                    </BaseBadge>
                  </div>
                  <div class="mt-3 grid gap-3 sm:grid-cols-2">
                    <template
                      v-for="evidence in item.evidence"
                      :key="evidence.evidenceId"
                    >
                      <img
                        v-if="evidence.evidenceType === 'PHOTO'"
                        :src="evidence.mediaUrl"
                        :alt="`${item.name} 증빙`"
                        class="h-36 w-full rounded-md border border-border object-cover"
                      >
                      <video
                        v-else-if="evidence.evidenceType === 'VIDEO'"
                        :src="evidence.mediaUrl"
                        controls
                        preload="metadata"
                        class="h-40 w-full rounded-md border border-border bg-black"
                      />
                      <a
                        v-else
                        :href="evidence.mediaUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="rounded-md border border-border px-3 py-3 text-xs font-semibold text-primary"
                      >진단 자료 열기 · 시도 {{ evidence.attemptNo }}</a>
                    </template>
                  </div>
                  <p
                    v-if="!item.evidence.length"
                    class="mt-2 text-xs text-text-sub"
                  >
                    업로드된 증빙이 없습니다.
                  </p>
                </article>
              </div>
            </template>
          </section>
        </div>
      </BaseCard>

      <BaseCard v-else>
        <p class="py-12 text-center text-sm text-text-sub">
          왼쪽 목록에서 상세 조회할 모델을 선택해 주세요.
        </p>
      </BaseCard>
    </div>
    <p
      v-if="isLoadingDetail"
      class="text-center text-xs text-text-sub"
    >
      모델 상세 정보를 불러오는 중입니다.
    </p>
  </div>
</template>
