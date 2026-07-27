<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import SidebarLayout from '../layouts/SidebarLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTable from '../components/BaseTable.vue'
import {
  createProduct,
  deleteProduct,
  getDeviceCategories,
  getDeviceModels,
  getMyProduct,
  getMyProducts,
  transitionProductStatus,
  updateProduct,
} from '../api/products'

const sidebarItems = [
  { label: '대시보드', href: '/seller/dashboard', active: false },
  { label: '상품 관리', href: '/seller/products', active: true },
  { label: '판매 내역', href: '/coming-soon/seller-orders', active: false },
  { label: '정산', href: '/seller/dashboard', active: false },
]

const categories = ref([])
const models = ref([])
const products = ref([])
const statusFilter = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref(null)
const activeStep = ref(1)
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })
const form = reactive({
  categoryId: '', deviceModelId: '', name: '', description: '', price: '',
  color: '', storageGb: '', tradeRegion: '',
})

function resetForm() {
  editingId.value = null
  activeStep.value = 1
  Object.assign(form, {
    categoryId: '', deviceModelId: '', name: '', description: '', price: '',
    color: '', storageGb: '', tradeRegion: '',
  })
  models.value = []
}

function goToNextStep() {
  errorMessage.value = ''
  if (activeStep.value === 1 && (!form.categoryId || !form.deviceModelId)) {
    errorMessage.value = '카테고리와 기기 모델을 선택해 주세요.'
    return
  }
  if (activeStep.value === 2 && (!form.name || form.price === '' || !form.tradeRegion)) {
    errorMessage.value = '상품명, 가격, 거래 지역을 입력해 주세요.'
    return
  }
  if (activeStep.value === 2 && (!Number.isFinite(Number(form.price)) || Number(form.price) < 1)) {
    errorMessage.value = '가격은 1원 이상 입력해 주세요.'
    return
  }
  if (activeStep.value === 2 && form.storageGb !== ''
    && (!Number.isFinite(Number(form.storageGb)) || Number(form.storageGb) < 1)) {
    errorMessage.value = '저장 용량은 1GB 이상 입력해 주세요.'
    return
  }
  activeStep.value = Math.min(3, activeStep.value + 1)
}

async function loadProducts(page = 0) {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getMyProducts({
      status: statusFilter.value,
      page,
      size: 20,
      sort: 'updatedAt,desc',
    })
    products.value = response?.data || []
    pageMeta.value = response?.meta || { page, totalPages: 0, hasNext: false }
  } catch (error) {
    errorMessage.value = error.message || '상품 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function loadModels() {
  form.deviceModelId = ''
  models.value = form.categoryId
    ? await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    : []
}

async function submit() {
  isSaving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const payload = {
      name: form.name,
      description: form.description || null,
      price: Number(form.price),
      color: form.color || null,
      storageGb: form.storageGb ? Number(form.storageGb) : null,
      tradeRegion: form.tradeRegion,
    }
    if (editingId.value) {
      await updateProduct(editingId.value, payload)
      notice.value = '상품 정보를 수정했습니다.'
    } else {
      await createProduct({
        ...payload,
        categoryId: Number(form.categoryId),
        deviceModelId: Number(form.deviceModelId),
      })
      notice.value = '상품 초안을 등록했습니다.'
    }
    resetForm()
    await loadProducts(0)
  } catch (error) {
    errorMessage.value = error.message || '상품을 저장하지 못했습니다.'
  } finally {
    isSaving.value = false
  }
}

async function startEdit(productId) {
  errorMessage.value = ''
  try {
    const product = await getMyProduct(productId)
    editingId.value = productId
    activeStep.value = 2
    Object.assign(form, {
      categoryId: product.category?.categoryId || '',
      deviceModelId: product.device?.deviceModelId || '',
      name: product.name || '', description: product.description || '', price: product.price || '',
      color: product.device?.color || '', storageGb: product.device?.storageGb || '',
      tradeRegion: product.tradeRegion || '',
    })
    if (form.categoryId) models.value = await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    errorMessage.value = error.message || '상품 상세를 불러오지 못했습니다.'
  }
}

async function remove(product) {
  if (!window.confirm(`‘${product.name}’ 상품을 삭제할까요?`)) return
  try {
    await deleteProduct(product.productId)
    notice.value = '상품을 삭제했습니다.'
    const targetPage = products.value.length === 1 && pageMeta.value.page > 0
      ? pageMeta.value.page - 1
      : pageMeta.value.page
    await loadProducts(targetPage)
  } catch (error) {
    errorMessage.value = error.message || '상품을 삭제하지 못했습니다.'
  }
}

async function publish(product) {
  try {
    await transitionProductStatus(product.productId, 'ON_SALE', '판매 등록')
    notice.value = '상품을 판매중으로 전환했습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '필수 체크리스트를 먼저 완료해 주세요.'
  }
}

watch(statusFilter, () => loadProducts(0))
onMounted(async () => {
  try {
    categories.value = await getDeviceCategories({ activeOnly: true })
  } catch (error) {
    errorMessage.value = error.message || '기기 카테고리를 불러오지 못했습니다.'
  }
  await loadProducts(0)
})
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <section class="mb-10 overflow-hidden rounded-lg border border-border bg-surface shadow-card">
      <div class="flex flex-col gap-5 border-b border-border bg-bg px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
            Sell your device
          </p>
          <h1 class="mt-1 text-2xl font-bold text-text-main">
            {{ editingId ? '상품 수정' : '상품 등록' }}
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            기기 정보와 거래 조건을 확인한 뒤 안전하게 초안으로 저장합니다.
          </p>
        </div>
        <button
          v-if="editingId"
          type="button"
          class="text-sm font-semibold text-primary"
          @click="resetForm"
        >
          등록으로 돌아가기
        </button>
      </div>

      <ol
        class="grid grid-cols-3 border-b border-border"
        aria-label="상품 등록 단계"
      >
        <li
          v-for="step in [
            { number: 1, label: '기기 선택' },
            { number: 2, label: '판매 정보' },
            { number: 3, label: '최종 확인' },
          ]"
          :key="step.number"
          class="flex items-center justify-center gap-2 border-r border-border px-2 py-4 text-xs font-semibold last:border-r-0 sm:text-sm"
          :class="activeStep >= step.number ? 'bg-accent text-primary' : 'text-text-sub'"
        >
          <span
            class="flex h-6 w-6 items-center justify-center rounded-full text-xs"
            :class="activeStep >= step.number ? 'bg-primary text-white' : 'bg-slate-100 text-text-sub'"
          >{{ step.number }}</span>
          {{ step.label }}
        </li>
      </ol>

      <form
        class="p-6 lg:p-8"
        @submit.prevent="submit"
      >
        <section
          v-if="activeStep === 1"
          class="mx-auto max-w-2xl"
        >
          <h2 class="text-lg font-bold text-text-main">
            판매할 기기를 선택해 주세요.
          </h2>
          <p class="mt-1 text-sm text-text-sub">
            선택한 모델에 맞는 검증 체크리스트가 자동으로 연결됩니다.
          </p>
          <div class="mt-6 grid gap-5 sm:grid-cols-2">
            <label class="text-sm font-semibold text-text-main">카테고리
              <select
                v-model="form.categoryId"
                :disabled="Boolean(editingId)"
                required
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary"
                @change="loadModels"
              >
                <option value="">카테고리 선택</option><option
                  v-for="item in categories"
                  :key="item.categoryId"
                  :value="item.categoryId"
                >{{ item.name }}</option>
              </select>
            </label>
            <label class="text-sm font-semibold text-text-main">기기 모델
              <select
                v-model="form.deviceModelId"
                :disabled="Boolean(editingId) || !form.categoryId"
                required
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary disabled:opacity-60"
              >
                <option value="">기기 모델 선택</option><option
                  v-for="item in models"
                  :key="item.deviceModelId"
                  :value="item.deviceModelId"
                >{{ item.manufacturerName }} {{ item.modelName }}</option>
              </select>
            </label>
          </div>
        </section>

        <section v-else-if="activeStep === 2">
          <h2 class="text-lg font-bold text-text-main">
            판매 정보를 입력해 주세요.
          </h2>
          <p class="mt-1 text-sm text-text-sub">
            구매자가 상품 상태와 거래 조건을 이해할 수 있도록 작성해 주세요.
          </p>
          <div class="mt-6 grid gap-5 sm:grid-cols-2">
            <label class="text-sm font-semibold text-text-main">상품명<input
              v-model.trim="form.name"
              required
              maxlength="100"
              placeholder="예: 갤럭시 S24 256GB 자급제"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">가격<input
              v-model="form.price"
              required
              min="1"
              type="number"
              placeholder="판매 가격"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">색상<input
              v-model.trim="form.color"
              maxlength="50"
              placeholder="예: 오닉스 블랙"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">저장 용량(GB)<input
              v-model="form.storageGb"
              min="1"
              type="number"
              placeholder="예: 256"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main sm:col-span-2">
              거래 지역
              <input
                v-model.trim="form.tradeRegion"
                required
                maxlength="100"
                placeholder="예: 광주광역시 광산구"
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
              >
            </label>
            <label class="text-sm font-semibold text-text-main sm:col-span-2">
              상품 설명
              <textarea
                v-model.trim="form.description"
                maxlength="2000"
                rows="5"
                placeholder="외관 상태, 사용 기간, 구성품 등을 알려 주세요."
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
              />
            </label>
          </div>
        </section>

        <section
          v-else
          class="mx-auto max-w-2xl"
        >
          <h2 class="text-lg font-bold text-text-main">
            입력한 내용을 확인해 주세요.
          </h2>
          <p class="mt-1 text-sm text-text-sub">
            등록 후 체크리스트 자료를 완료하면 판매 중으로 전환할 수 있습니다.
          </p>
          <dl class="mt-6 grid grid-cols-2 gap-x-8 gap-y-5 rounded-lg border border-border bg-bg p-6 text-sm">
            <div>
              <dt class="text-xs text-text-sub">
                상품명
              </dt><dd class="mt-1 font-semibold text-text-main">
                {{ form.name }}
              </dd>
            </div>
            <div>
              <dt class="text-xs text-text-sub">
                가격
              </dt><dd class="mt-1 font-semibold text-text-main">
                {{ Number(form.price).toLocaleString() }}원
              </dd>
            </div>
            <div>
              <dt class="text-xs text-text-sub">
                색상
              </dt><dd class="mt-1 font-semibold text-text-main">
                {{ form.color || '미입력' }}
              </dd>
            </div>
            <div>
              <dt class="text-xs text-text-sub">
                저장 용량
              </dt><dd class="mt-1 font-semibold text-text-main">
                {{ form.storageGb ? `${form.storageGb}GB` : '미입력' }}
              </dd>
            </div>
            <div class="col-span-2">
              <dt class="text-xs text-text-sub">
                거래 지역
              </dt><dd class="mt-1 font-semibold text-text-main">
                {{ form.tradeRegion }}
              </dd>
            </div>
            <div class="col-span-2">
              <dt class="text-xs text-text-sub">
                설명
              </dt><dd class="mt-1 whitespace-pre-wrap leading-6 text-text-main">
                {{ form.description || '설명 없음' }}
              </dd>
            </div>
          </dl>
        </section>

        <div class="mt-8 flex items-center justify-between border-t border-border pt-5">
          <BaseButton
            v-if="activeStep > 1"
            type="button"
            variant="outline"
            @click="activeStep -= 1"
          >
            이전
          </BaseButton>
          <span v-else />
          <BaseButton
            v-if="activeStep < 3"
            type="button"
            @click="goToNextStep"
          >
            다음 단계
          </BaseButton>
          <BaseButton
            v-else
            type="submit"
            :disabled="isSaving"
          >
            {{ isSaving ? '저장 중…' : editingId ? '수정 저장' : '초안 등록' }}
          </BaseButton>
        </div>
      </form>
    </section>

    <p
      v-if="errorMessage"
      role="alert"
      class="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </p>
    <p
      v-if="notice"
      role="status"
      class="mb-4 rounded-md bg-accent px-4 py-3 text-sm text-primary"
    >
      {{ notice }}
    </p>

    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-lg font-bold text-text-main">
        내 상품
      </h2>
      <select
        v-model="statusFilter"
        aria-label="상품 상태 필터"
        class="rounded-md border border-border bg-white px-3 py-2 text-sm"
      >
        <option value="">
          전체 상태
        </option><option value="DRAFT">
          초안
        </option><option value="ON_SALE">
          판매중
        </option><option value="HIDDEN">
          숨김
        </option>
      </select>
    </div>
    <p
      v-if="isLoading"
      class="py-8 text-center text-sm text-text-sub"
    >
      상품을 불러오는 중입니다.
    </p>
    <BaseTable
      v-else
      :columns="['상품', '상태', '체크리스트', '관리']"
    >
      <tr
        v-for="product in products"
        :key="product.productId"
      >
        <td class="px-4 py-3">
          <p class="font-semibold text-text-main">
            {{ product.name }}
          </p><p class="text-xs text-text-sub">
            #{{ product.productId }}
          </p>
        </td>
        <td class="px-4 py-3">
          <BaseBadge :variant="product.status === 'ON_SALE' ? 'primary' : 'gray'">
            {{ product.status }}
          </BaseBadge>
        </td>
        <td class="px-4 py-3 text-sm text-text-sub">
          {{ product.completedItemCount }} / {{ product.requiredItemCount }}
        </td>
        <td class="space-x-3 px-4 py-3 text-sm">
          <button
            class="text-primary"
            @click="startEdit(product.productId)"
          >
            수정
          </button><button
            v-if="product.status === 'DRAFT'"
            class="text-primary"
            @click="publish(product)"
          >
            판매 시작
          </button><button
            class="text-red-600"
            @click="remove(product)"
          >
            삭제
          </button>
        </td>
      </tr>
      <tr v-if="!products.length">
        <td
          colspan="4"
          class="px-4 py-12 text-center text-sm text-text-sub"
        >
          등록한 상품이 없습니다.
        </td>
      </tr>
    </BaseTable>
    <nav
      v-if="!isLoading && pageMeta.totalPages > 1"
      class="mt-6 flex items-center justify-center gap-4"
      aria-label="내 상품 페이지"
    >
      <BaseButton
        variant="outline"
        :disabled="pageMeta.page === 0"
        @click="loadProducts(pageMeta.page - 1)"
      >
        이전
      </BaseButton>
      <span class="text-sm text-text-sub">
        {{ pageMeta.page + 1 }} / {{ pageMeta.totalPages }}
      </span>
      <BaseButton
        variant="outline"
        :disabled="!pageMeta.hasNext"
        @click="loadProducts(pageMeta.page + 1)"
      >
        다음
      </BaseButton>
    </nav>
  </SidebarLayout>
</template>
