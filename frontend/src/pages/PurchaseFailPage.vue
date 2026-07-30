<script setup>
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'

const route = useRoute()
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto flex min-h-[70vh] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 sm:p-10">
        <h1 class="text-center text-xl font-bold text-text-main">
          결제가 완료되지 않았습니다
        </h1>
        <p class="mx-auto mt-3 max-w-md text-center text-sm leading-6 text-text-sub">
          {{ route.query.message || '결제가 취소되었거나 처리 중 오류가 발생했습니다.' }}
        </p>

        <div class="mt-6 flex flex-col gap-3">
          <BaseButton
            :to="`/purchase/${route.params.productId}`"
            block
          >
            다시 시도하기
          </BaseButton>
          <BaseButton
            to="/products"
            variant="outline"
            block
          >
            쇼핑 계속하기
          </BaseButton>
        </div>
      </BaseCard>
    </div>
  </DefaultLayout>
</template>
