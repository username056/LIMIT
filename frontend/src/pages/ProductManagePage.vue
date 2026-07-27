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
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })
const form = reactive({
  categoryId: '', deviceModelId: '', name: '', description: '', price: '',
  color: '', storageGb: '', tradeRegion: '',
})

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    categoryId: '', deviceModelId: '', name: '', description: '', price: '',
    color: '', storageGb: '', tradeRegion: '',
  })
  models.value = []
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
    <section class="mb-8 rounded-lg border border-border bg-surface p-6">
      <div class="mb-5 flex items-center justify-between">
        <div>
          <h1 class="text-xl font-bold text-text-main">
            {{ editingId ? '상품 수정' : '상품 등록' }}
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            기기 모델과 거래 정보를 입력하면 초안으로 저장됩니다.
          </p>
        </div>
        <button
          v-if="editingId"
          type="button"
          class="text-sm text-primary"
          @click="resetForm"
        >
          등록으로 돌아가기
        </button>
      </div>
      <form
        class="grid gap-4 sm:grid-cols-2"
        @submit.prevent="submit"
      >
        <label class="text-sm text-text-main">카테고리
          <select
            v-model="form.categoryId"
            :disabled="Boolean(editingId)"
            required
            class="mt-1 w-full rounded-md border border-border bg-white px-3 py-2"
            @change="loadModels"
          >
            <option value="">선택하세요</option><option
              v-for="item in categories"
              :key="item.categoryId"
              :value="item.categoryId"
            >{{ item.name }}</option>
          </select>
        </label>
        <label class="text-sm text-text-main">기기 모델
          <select
            v-model="form.deviceModelId"
            :disabled="Boolean(editingId)"
            required
            class="mt-1 w-full rounded-md border border-border bg-white px-3 py-2"
          >
            <option value="">선택하세요</option><option
              v-for="item in models"
              :key="item.deviceModelId"
              :value="item.deviceModelId"
            >{{ item.manufacturerName }} {{ item.modelName }}</option>
          </select>
        </label>
        <label class="text-sm text-text-main">상품명<input
          v-model.trim="form.name"
          required
          maxlength="100"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        ></label>
        <label class="text-sm text-text-main">가격<input
          v-model="form.price"
          required
          min="1"
          type="number"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        ></label>
        <label class="text-sm text-text-main">색상<input
          v-model.trim="form.color"
          maxlength="50"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        ></label>
        <label class="text-sm text-text-main">저장 용량(GB)<input
          v-model="form.storageGb"
          min="1"
          type="number"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        ></label>
        <label class="text-sm text-text-main sm:col-span-2">거래 지역<input
          v-model.trim="form.tradeRegion"
          required
          maxlength="100"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        ></label>
        <label class="text-sm text-text-main sm:col-span-2">설명<textarea
          v-model.trim="form.description"
          maxlength="2000"
          rows="4"
          class="mt-1 w-full rounded-md border border-border px-3 py-2"
        /></label>
        <BaseButton
          type="submit"
          :disabled="isSaving"
          class="sm:col-span-2"
        >
          {{ isSaving ? '저장 중…' : editingId ? '수정 저장' : '초안 등록' }}
        </BaseButton>
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
