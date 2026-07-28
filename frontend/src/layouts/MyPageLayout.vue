<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SidebarLayout from './SidebarLayout.vue'

const route = useRoute()

const sidebarItems = computed(() => [
  { label: '내 정보', href: '/mypage/profile', active: route.path === '/mypage/profile' },
  { label: '내 상품', href: '/seller/products', active: route.path === '/seller/products' },
  { label: '좋아요한 상품', href: '/mypage/favorites', active: route.path === '/mypage/favorites' },
  { label: '주문 내역', href: '/mypage/orders', active: route.path === '/mypage/orders' },
])
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <slot />
  </SidebarLayout>
</template>
