<script setup>
import { ref } from 'vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const email = ref('')
const password = ref('')
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-md px-6 py-16 text-center">
      <p class="mb-2 text-sm font-semibold text-primary">
        Limit
      </p>
      <h1 class="mb-2 text-2xl font-bold text-text-main">
        다시 만나서 반가워요
      </h1>
      <p class="mb-8 text-sm text-text-sub">
        익스클루시브 서클에 다시 오신 것을 환영합니다.
      </p>

      <BaseCard class="text-left">
        <div class="space-y-4">
          <BaseInput
            v-model="email"
            label="이메일 주소"
            type="email"
            placeholder="name@example.com"
          />
          <BaseInput
            v-model="password"
            label="비밀번호"
            type="password"
            placeholder="••••••••"
          />
        </div>

        <BaseButton
          block
          class="mt-6"
        >
          로그인
        </BaseButton>
      </BaseCard>

      <p class="mt-6 text-sm text-text-sub">
        계정이 없으신가요? <a
          href="#"
          class="font-medium text-primary"
        >회원가입</a>
      </p>
    </section>
  </DefaultLayout>
</template>
