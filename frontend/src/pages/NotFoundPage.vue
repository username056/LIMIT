<script setup>
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const router = useRouter()
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto flex min-h-[560px] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 text-center sm:p-12">
        <span class="inline-flex rounded-full bg-accent px-3 py-1 text-xs font-bold text-primary">
          404
        </span>
        <h1 class="mt-5 text-2xl font-bold tracking-[-0.03em] text-text-main sm:text-3xl">
          페이지를 찾을 수 없습니다
        </h1>
        <p class="mx-auto mt-4 max-w-lg text-sm leading-6 text-text-sub">
          요청하신 주소가 잘못되었거나 삭제된 페이지입니다.
        </p>
        <div class="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <BaseButton
            variant="outline"
            @click="router.back()"
          >
            이전 화면
          </BaseButton>
          <BaseButton to="/">
            홈으로 이동
          </BaseButton>
        </div>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
