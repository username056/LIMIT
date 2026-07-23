<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SidebarLayout from './SidebarLayout.vue'

const route = useRoute()

const sidebarItems = computed(() => [
  { label: '내 정보', href: '/mypage/profile', active: route.path === '/mypage/profile' },
  { label: '주문 내역', href: '/mypage/orders', active: route.path === '/mypage/orders' },
  { label: '관심 상품', href: '/mypage/favorites', active: route.path === '/mypage/favorites' },
  {
    label: '소셜 계정',
    href: '/mypage/social-accounts',
    active: route.path === '/mypage/social-accounts',
  },
])
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <slot />
  </SidebarLayout>
</template>
