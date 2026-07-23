<script setup>
import { ref } from 'vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseTabs from '../components/BaseTabs.vue'
import BaseStepper from '../components/BaseStepper.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseSelect from '../components/BaseSelect.vue'
import FileUploadBox from '../components/FileUploadBox.vue'
import BaseButton from '../components/BaseButton.vue'

const sellerType = ref('개인 판매자')
const name = ref('')
const phone = ref('')
const country = ref('')
const bank = ref('')
const accountNumber = ref('')
const identityFile = ref(null)
const supportingFile = ref(null)
const message = ref('')
const isError = ref(false)

const steps = [
  { label: '정보 입력', description: '기본 판매자 정보를 입력해주세요.' },
  { label: '서류 업로드', description: '판매 자격을 증명할 서류를 첨부해주세요.' },
  { label: '심사 및 승인', description: '영업일 기준 3일 내 심사가 완료됩니다.' },
]

function submitApplication() {
  message.value = ''
  isError.value = false
  if (!name.value.trim() || !phone.value.trim() || !country.value || !bank.value || !accountNumber.value.trim()) {
    isError.value = true
    message.value = '필수 판매자 정보와 정산 계좌를 모두 입력해 주세요.'
    return
  }
  if (!identityFile.value) {
    isError.value = true
    message.value = '신분증 또는 여권 파일을 선택해 주세요.'
    return
  }
  message.value = '셀러 신청 목업이 접수되었습니다. 실제 API 연결 전에는 서버에 저장되지 않습니다.'
}
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-2xl px-6 py-10">
      <h1 class="mb-2 text-2xl font-bold text-text-main">
        셀러 신청하기
      </h1>
      <p class="mb-6 text-sm text-text-sub">
        Limit의 공식 셀러가 되어 전 세계 전자기기 컬렉터들과 만나보세요.
      </p>

      <BaseStepper
        :steps="steps"
        :current-step="1"
        class="mb-8"
      />

      <BaseTabs
        v-model="sellerType"
        :tabs="['개인 판매자', '기업 판매자']"
        class="mb-6"
      />

      <form
        class="space-y-6 rounded-lg border border-border bg-surface p-6"
        @submit.prevent="submitApplication"
      >
        <div>
          <h2 class="mb-3 text-sm font-bold text-text-main">
            기본 정보
          </h2>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="name"
              label="이름 / 상호명"
              placeholder="실명을 입력하세요"
              required
            />
            <BaseInput
              v-model="phone"
              label="연락처"
              placeholder="010-0000-0000"
              required
            />
          </div>
          <BaseSelect
            v-model="country"
            label="국가"
            class="mt-4"
            :options="['대한민국 (South Korea)', 'United States', 'Japan']"
          />
        </div>

        <div>
          <h2 class="mb-3 text-sm font-bold text-text-main">
            정산 계좌 정보
          </h2>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <BaseSelect
              v-model="bank"
              label="은행"
              :options="['국민은행', '신한은행', '카카오뱅크']"
            />
            <BaseInput
              v-model="accountNumber"
              label="계좌번호"
              placeholder="하이픈 없이 입력"
              required
            />
          </div>
        </div>

        <div>
          <h2 class="mb-3 text-sm font-bold text-text-main">
            증빙 서류 제출
          </h2>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FileUploadBox
              title="신분증 (여권/주민등록증)"
              description="파일을 드래그하거나 클릭하여 선택"
              @change="identityFile = $event"
            />
            <FileUploadBox
              title="기타 증빙 (선택)"
              description="사업자등록증 등 추가 서류"
              @change="supportingFile = $event"
            />
          </div>
          <p class="mt-2 text-xs text-text-sub">
            JPG, PNG, PDF 형식만 지원되며, 파일당 10MB까지 업로드 가능합니다.
          </p>
          <p
            v-if="identityFile || supportingFile"
            class="mt-3 text-xs font-semibold text-primary"
          >
            선택 파일:
            {{ [identityFile?.name, supportingFile?.name].filter(Boolean).join(', ') }}
          </p>
        </div>

        <p
          v-if="message"
          class="rounded-md px-4 py-3 text-sm"
          :class="isError ? 'bg-red-50 text-red-600' : 'bg-green-50 text-green-700'"
          :role="isError ? 'alert' : 'status'"
        >
          {{ message }}
        </p>

        <BaseButton
          block
          type="submit"
        >
          신청 완료하기
        </BaseButton>
      </form>
    </section>
  </DefaultLayout>
</template>
