<script setup>
import { onMounted, ref, watch } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import ProductCard from '../components/ProductCard.vue'
import { deleteProduct, getMyProducts, transitionProductStatus } from '../api/products'
import {
  canSellerMarkSold,
  canSellerReopen,
  isProductModerationNormal,
  isProductEditable,
  productModerationStatusLabel,
  productStatusLabel,
} from '../utils/productStatus'

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

/**
 * 판매 시작에 꼭 필요한 것만 확인합니다.
 *
 * 촬영 검증은 전부 채우지 않아도 올릴 수 있습니다(나중에 이어서 채우면 됩니다). 반면 기기 정보와
 * 개인정보 정리 확인은 빠지면 안 됩니다 — 기기 정보가 없으면 구매자가 무엇을 사는지 알 수 없고,
 * 개인정보는 기기를 넘긴 뒤에 되돌릴 수 없습니다.
 */
function missingEssentials(product) {
  const missing = []
  if (!product.manufacturerName || !product.modelName) missing.push('카테고리·기기 모델')
  if (!product.name) missing.push('글제목')
  if (!(Number(product.price) > 0)) missing.push('가격')
  if (product.pendingPrivacyConfirmation) missing.push('개인정보 정리 확인')
  return missing
}

async function publish(product) {
  errorMessage.value = ''
  notice.value = ''
  const missing = missingEssentials(product)
  if (missing.length) {
    window.alert(
      `필수 사항이 전부 입력되지 않았어요.\n\n${missing.map((item) => `· ${item}`).join('\n')}`,
    )
    return
  }
  if (!window.confirm(`‘${product.name}’의 판매가 시작됩니다!\n\n구매자에게 상품이 공개됩니다.`)) return

  try {
    await transitionProductStatus(product.productId, 'ON_SALE', '판매 등록')
    notice.value = '상품을 판매 중으로 전환했습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '필수 체크리스트를 먼저 완료해 주세요.'
  }
}

// 서비스 결제를 거치지 않은 직거래를 판매자가 직접 닫는 경로입니다.
async function markSold(product) {
  const confirmed = window.confirm(
    `‘${product.name}’을 판매 완료로 바꿀까요?\n\n`
    + '구매자에게 더 이상 노출되지 않습니다. 거래가 깨지면 다시 판매 중으로 되돌릴 수 있습니다.',
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

// 직거래 약속이 깨졌을 때 상품을 새로 등록하지 않고 원래 글로 돌아가는 경로입니다.
async function reopen(product) {
  errorMessage.value = ''
  try {
    await transitionProductStatus(product.productId, 'ON_SALE', '거래 파기로 판매 재개')
    notice.value = '다시 판매 중으로 바꿨습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '판매 중으로 되돌리지 못했습니다.'
  }
}

watch(statusFilter, () => loadProducts(0))
onMounted(() => loadProducts(0))
</script>

<template>
  <MyPageLayout>
    <PageHeader
      eyebrow="MY ITEMS"
      title="상품 관리"
      description="내가 등록한 상품의 상태와 검증 진행률을 확인하고 관리하세요."
    />

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
    <!--
      상품 목록과 같은 카드를 씁니다. 판매자도 자기 대표 이미지가 구매자에게 어떻게 보이는지
      같은 모양으로 확인할 수 있어야 합니다. 관리 버튼은 카드 하단 슬롯에 둡니다.
    -->
    <div
      v-else-if="products.length"
      class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3"
    >
      <ProductCard
        v-for="product in products"
        :key="product.productId"
        :product="product"
        :title-to="{ name: 'product-detail', params: { productId: product.productId } }"
      >
        <template #image-overlay>
          <BaseBadge
            :variant="product.status === 'ON_SALE' ? 'primary' : 'gray'"
            class="absolute left-3 top-3"
          >
            {{ productStatusLabel(product.status) }}
          </BaseBadge>
          <BaseBadge
            v-if="!isProductModerationNormal(product.moderationStatus)"
            variant="danger"
            class="absolute right-3 top-3"
          >
            {{ productModerationStatusLabel(product.moderationStatus) }}
          </BaseBadge>
        </template>
        <template #footer>
          <p class="truncate text-xs text-text-sub">
            #{{ product.productId }} · 검증 {{ product.completedItemCount }}/{{ product.requiredItemCount }}
          </p>
          <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
            <RouterLink
              v-if="isProductEditable(product.status)"
              class="font-semibold text-primary"
              :to="{ name: 'seller-product-edit', params: { productId: product.productId } }"
            >
              수정
            </RouterLink>
            <span
              v-else
              class="text-text-sub"
              title="거래가 시작된 상품은 수정할 수 없습니다."
            >
              수정 불가
            </span>
            <button
              v-if="product.status === 'DRAFT'"
              type="button"
              class="font-semibold text-primary"
              @click="publish(product)"
            >
              판매 시작
            </button>
            <button
              v-if="canSellerMarkSold(product.status) && isProductModerationNormal(product.moderationStatus)"
              type="button"
              class="font-semibold text-primary"
              @click="markSold(product)"
            >
              판매 완료 처리
            </button>
            <button
              v-if="canSellerReopen(product.status)"
              type="button"
              class="font-semibold text-primary"
              @click="reopen(product)"
            >
              다시 판매하기
            </button>
            <button
              type="button"
              class="ml-auto font-semibold text-red-600"
              :disabled="!isProductModerationNormal(product.moderationStatus)"
              :class="!isProductModerationNormal(product.moderationStatus) ? 'cursor-not-allowed opacity-40' : ''"
              :title="!isProductModerationNormal(product.moderationStatus) ? '신고 조치가 끝난 뒤 삭제할 수 있습니다.' : ''"
              @click="remove(product)"
            >
              삭제
            </button>
          </div>
        </template>
      </ProductCard>
    </div>
    <p
      v-else
      class="rounded-lg border border-dashed border-border bg-surface px-6 py-16 text-center text-sm text-text-sub"
    >
      등록한 상품이 없습니다.
    </p>
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
