<script setup>
import { onMounted, ref, watch } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTable from '../components/BaseTable.vue'
import { deleteProduct, getMyProducts, transitionProductStatus } from '../api/products'
import { canSellerMarkSold, isProductEditable, productStatusLabel } from '../utils/productStatus'

// 이 화면은 내가 등록한 상품을 확인하고 관리하는 곳입니다.
// 등록·수정 위자드는 ProductRegisterPage로 분리되어 있습니다.
const products = ref([])
const statusFilter = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const notice = ref('')
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })

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
    notice.value = '상품을 판매 중으로 전환했습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '필수 체크리스트를 먼저 완료해 주세요.'
  }
}

// 서비스 결제를 거치지 않은 직거래를 판매자가 직접 닫는 경로입니다. 되돌릴 수 없어 한 번 확인합니다.
async function markSold(product) {
  const confirmed = window.confirm(
    `‘${product.name}’을 판매 완료로 바꿀까요?\n\n`
    + '구매자에게 더 이상 노출되지 않고, 되돌리거나 수정할 수 없습니다.',
  )
  if (!confirmed) return
  errorMessage.value = ''
  try {
    await transitionProductStatus(product.productId, 'SOLD', '판매자 직거래 판매 완료')
    notice.value = '판매 완료로 처리했습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '판매 완료로 처리하지 못했습니다.'
  }
}

watch(statusFilter, () => loadProducts(0))
onMounted(() => loadProducts(0))
</script>

<template>
  <MyPageLayout>
    <div class="mb-5">
      <p class="text-xs font-semibold text-primary">
        MY PAGE
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        상품 관리
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        내가 등록한 상품의 상태와 검증 진행률을 확인하고 관리하세요.
      </p>
    </div>

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
          임시 저장 중
        </option><option value="ON_SALE">
          판매 중
        </option><option value="HIDDEN">
          숨김
        </option><option value="SOLD">
          판매 완료
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
          <RouterLink
            :to="{ name: 'product-detail', params: { productId: product.productId } }"
            class="font-semibold text-text-main hover:text-primary hover:underline"
          >
            {{ product.name }}
          </RouterLink><p class="text-xs text-text-sub">
            #{{ product.productId }}
          </p>
        </td>
        <td class="px-4 py-3">
          <BaseBadge :variant="product.status === 'ON_SALE' ? 'primary' : 'gray'">
            {{ productStatusLabel(product.status) }}
          </BaseBadge>
        </td>
        <td class="px-4 py-3 text-sm text-text-sub">
          {{ product.completedItemCount }} / {{ product.requiredItemCount }}
        </td>
        <td class="space-x-3 px-4 py-3 text-sm">
          <RouterLink
            v-if="isProductEditable(product.status)"
            class="text-primary"
            :to="{ name: 'seller-product-edit', params: { productId: product.productId } }"
          >
            수정
          </RouterLink><span
            v-else
            class="text-text-sub"
            title="거래가 시작된 상품은 수정할 수 없습니다."
          >
            수정 불가
          </span><button
            v-if="product.status === 'DRAFT'"
            class="text-primary"
            @click="publish(product)"
          >
            판매 시작
          </button><button
            v-if="canSellerMarkSold(product.status)"
            class="text-primary"
            @click="markSold(product)"
          >
            판매 완료 처리
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
  </MyPageLayout>
</template>
