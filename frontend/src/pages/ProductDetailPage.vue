<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import { addFavorite, getFavoriteStatus, removeFavorite } from '../api/favorites'
import { getProduct } from '../api/products'
import { getAccessToken } from '../auth/session'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const isLoading = ref(true)
const isFavorite = ref(false)
const isUpdatingFavorite = ref(false)
const errorMessage = ref('')

async function toggleFavorite() {
  if (!getAccessToken()) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  isUpdatingFavorite.value = true
  errorMessage.value = ''
  try {
    if (isFavorite.value) await removeFavorite(product.value.productId)
    else await addFavorite(product.value.productId)
    isFavorite.value = !isFavorite.value
  } catch (error) {
    errorMessage.value = error.message || '관심 상품을 변경하지 못했습니다.'
  } finally {
    isUpdatingFavorite.value = false
  }
}

onMounted(async () => {
  try {
    product.value = await getProduct(route.params.productId)
    if (getAccessToken()) {
      const favoriteStatus = await getFavoriteStatus(route.params.productId)
      isFavorite.value = Boolean(favoriteStatus?.favorite)
    }
  } catch (error) {
    errorMessage.value = error.message || '상품을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1000px] px-6 py-10 lg:px-10">
      <p
        v-if="isLoading"
        class="py-16 text-center text-sm text-text-sub"
      >
        상품을 불러오는 중입니다.
      </p>
      <p
        v-else-if="!product"
        role="alert"
        class="rounded-md bg-red-50 px-4 py-3 text-red-700"
      >
        {{ errorMessage }}
      </p>
      <div
        v-else
        class="grid gap-8 md:grid-cols-2"
      >
        <div class="flex aspect-square items-center justify-center overflow-hidden rounded-lg bg-bg text-text-sub">
          <img
            v-if="product.thumbnailUrl"
            :src="product.thumbnailUrl"
            :alt="product.name"
            class="h-full w-full object-cover"
          >
          <span v-else>상품 이미지</span>
        </div>
        <section>
          <BaseBadge>{{ product.status }}</BaseBadge>
          <p class="mt-4 text-sm text-text-sub">
            {{ product.device?.manufacturer }} {{ product.device?.model }}
          </p>
          <h1 class="mt-1 text-2xl font-bold text-text-main">
            {{ product.name }}
          </h1>
          <p class="mt-3 text-2xl font-bold text-text-main">
            ₩{{ Number(product.price).toLocaleString() }}
          </p>
          <dl class="mt-6 grid grid-cols-2 gap-3 rounded-lg border border-border p-4 text-sm">
            <dt class="text-text-sub">
              색상
            </dt><dd>{{ product.device?.color || '-' }}</dd>
            <dt class="text-text-sub">
              저장 용량
            </dt><dd>{{ product.device?.storageGb ? `${product.device.storageGb}GB` : '-' }}</dd>
            <dt class="text-text-sub">
              거래 지역
            </dt><dd>{{ product.tradeRegion || '협의' }}</dd>
            <dt class="text-text-sub">
              필수 확인
            </dt><dd>{{ product.checklistSummary?.completed || 0 }} / {{ product.checklistSummary?.required || 0 }}</dd>
          </dl>
          <p class="mt-6 whitespace-pre-wrap text-sm leading-6 text-text-sub">
            {{ product.description || '등록된 설명이 없습니다.' }}
          </p>
          <BaseButton
            class="mt-6"
            block
            :disabled="isUpdatingFavorite"
            @click="toggleFavorite"
          >
            {{ isUpdatingFavorite ? '처리 중' : isFavorite ? '관심 상품 해제' : '관심 상품 등록' }}
          </BaseButton>
          <p
            v-if="errorMessage"
            role="alert"
            class="mt-3 text-sm text-red-700"
          >
            {{ errorMessage }}
          </p>
        </section>
      </div>
    </main>
  </DefaultLayout>
</template>
