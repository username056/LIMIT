<script setup>
import DefaultLayout from '../layouts/DefaultLayout.vue'
import LegalDocumentLayout from '../components/LegalDocumentLayout.vue'
import { legalVersion, privacySections } from '../legal/documents'
</script>

<template>
  <DefaultLayout>
    <LegalDocumentLayout
      title="개인정보 처리방침"
      description="LIMIT가 어떤 개인정보를 왜 처리하고, 얼마나 보관하며, 이용자가 어떤 권리를 행사할 수 있는지 안내합니다."
      :version="legalVersion"
      :sections="privacySections"
    />
  </DefaultLayout>
</template>
