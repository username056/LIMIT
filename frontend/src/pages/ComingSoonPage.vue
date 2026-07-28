<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const route = useRoute()
const router = useRouter()

const content = {
  eyebrow: 'LIMIT',
  title: '요청한 화면을 준비하고 있습니다',
  description: '연결할 API와 화면이 준비되는 대로 이 경로에서 바로 이용할 수 있습니다.',
}

const context = computed(() => route.query.name || route.query.q || '')
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto flex min-h-[560px] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 text-center sm:p-12">
        <span class="inline-flex rounded-full bg-accent px-3 py-1 text-xs font-bold text-primary">
          {{ content.eyebrow }} · 준비 중
        </span>
        <h1 class="mt-5 text-2xl font-bold tracking-[-0.03em] text-text-main sm:text-3xl">
          {{ content.title }}
        </h1>
        <p class="mx-auto mt-4 max-w-lg text-sm leading-6 text-text-sub">
          {{ content.description }}
        </p>
        <p
          v-if="context"
          class="mx-auto mt-5 max-w-lg rounded-md bg-bg px-4 py-3 text-sm font-semibold text-text-main"
        >
          요청 항목: {{ context }}
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
