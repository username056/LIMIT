<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthSession } from '../auth/session'
import SidebarLayout from './SidebarLayout.vue'

const route = useRoute()
const session = useAuthSession()

const isSeller = computed(() => session.value?.member?.roles?.includes('SELLER') || false)
const sidebarItems = computed(() => {
  const items = [
    { label: '내 정보', href: '/mypage/profile' },
    { label: '좋아요한 상품', href: '/mypage/favorites' },
    { label: '주문 내역', href: '/mypage/orders' },
  ]

  if (isSeller.value) {
    items.push(
      { label: '판매자 대시보드', href: '/seller/dashboard' },
      { label: '상품 관리', href: '/seller/products' },
    )
  } else {
    items.push({ label: '판매자 등록', href: '/seller/apply' })
  }

  return items.map((item) => ({ ...item, active: route.path === item.href }))
})
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <slot />
  </SidebarLayout>
</template>
