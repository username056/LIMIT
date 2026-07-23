<script setup>
import DefaultLayout from '../layouts/DefaultLayout.vue'
import LegalDocumentLayout from '../components/LegalDocumentLayout.vue'
import { legalVersion, serviceTermsSections } from '../legal/documents'
</script>

<template>
  <DefaultLayout>
    <LegalDocumentLayout
      title="서비스 이용약관"
      description="L1MIT 회원과 운영자가 안전하고 투명하게 중고거래 서비스를 이용하기 위한 기본 규칙입니다. 중요한 권리와 제한을 이해하기 쉬운 표현으로 정리했습니다."
      :version="legalVersion"
      :sections="serviceTermsSections"
    />
  </DefaultLayout>
</template>
