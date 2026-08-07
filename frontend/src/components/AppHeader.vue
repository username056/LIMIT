<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearAuthSession, useAuthSession } from '../auth/session'
import { SELL_ENTRY_PATH, useSellerGate } from '../auth/sellerGate'
import SellerNoticeModal from './SellerNoticeModal.vue'
import {
  doneRecaptureCount,
  hasNotice,
  pendingRecaptureCount,
  refreshNotificationDot,
  startNotificationDotWatch,
  stopNotificationDotWatch,
  todayAppointment,
  unreadChatCount,
} from '../stores/notificationDot'
import limitLogo from '../assets/real_limt_logo.png'

const props = defineProps({
  navItems: {
    type: Array,
    default: () => [
      { label: '전체 상품', href: '/products' },
      // 판매하기는 판매자 여부에 따라 목적지가 달라서 링크가 아니라 동작으로 처리합니다.
      { label: '판매하기', href: SELL_ENTRY_PATH, action: 'sell' },
      // 채팅은 로그인해야 쓸 수 있어 requiresLogin으로 표시합니다.
      { label: '채팅', href: '/chat', requiresLogin: true },
    ],
  },
})

// 템플릿에서 판매하기 활성 여부를 판단할 때 씁니다.
const sellEntryPath = SELL_ENTRY_PATH

const route = useRoute()
const router = useRouter()
const session = useAuthSession()
const searchQuery = ref('')
const member = computed(() => session.value?.member || null)
// 로그인해야 쓸 수 있는 항목은 비로그인 메뉴에서 감춥니다. 눌러 봐야 로그인 화면으로 튕깁니다.
const visibleNavItems = computed(
  () => props.navItems.filter((item) => !item.requiresLogin || member.value),
)
const isProfileMenuOpen = ref(false)
const isMobileMenuOpen = ref(false)
const {
  isSellerNoticeOpen,
  goToSell: openSellFlow,
  goToSellerApply,
  closeSellerNotice,
} = useSellerGate(router)

// 판매하기: 판매자면 등록 화면으로 바로, 아니면 판매자 등록을 먼저 안내합니다.
async function goToSell() {
  isMobileMenuOpen.value = false
  await openSellFlow()
}

// 홈에서는 히어로 한가운데에 큰 검색창이 이미 있습니다. 같은 화면에 검색창이 둘이면
// 어느 쪽에 쳐야 하는지 헷갈리고, 로고 옆 여백도 답답해집니다.
const showSearch = computed(() => route.name !== 'home')

/*
  맨 위에서는 헤더를 투명하게 둡니다. 히어로 배경이 헤더 뒤까지 이어져 화면이
  한 덩어리로 보입니다. 다만 그대로 두면 스크롤할 때 본문 글자가 헤더 뒤로 지나가며
  메뉴와 겹쳐 읽히므로, 조금이라도 내리면 배경을 깔아 줍니다.
  구분선 대신 아주 옅은 그림자로 경계를 냅니다. 선을 그으면 다시 두 조각으로 보입니다.
*/
const isScrolled = ref(false)

function syncScrolled() {
  isScrolled.value = window.scrollY > 8
}

onMounted(() => {
  syncScrolled()
  window.addEventListener('scroll', syncScrolled, { passive: true })
  if (member.value) startNotificationDotWatch()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', syncScrolled)
  if (member.value) stopNotificationDotWatch()
})

/*
  로그인 상태가 바뀌면 세는 것도 같이 켜고 끕니다. 로그아웃한 채로 계속 부르면 401만
  쌓이고, 로그인한 직후에 안 부르면 말풍선이 빈 채로 열립니다.
*/
watch(member, (current, previous) => {
  if (current && !previous) startNotificationDotWatch()
  else if (!current && previous) stopNotificationDotWatch()
})

/*
  채팅과 실시간 확인을 보고 나오면 대개 읽음 처리가 끝나 있습니다.
  그 화면을 떠날 때 다시 세어, 이미 처리한 일로 점이 남아 있지 않게 합니다.
*/
const SIGNAL_PATHS = ['/chat', '/calls']

watch(() => route.path, (current, previous) => {
  isNoticeOpen.value = false
  if (!member.value) return
  if (SIGNAL_PATHS.some((path) => previous?.startsWith(path))) refreshNotificationDot()
})

/*
  벨을 누르면 말풍선으로 두 줄만 보여 줍니다.
  ---------------------------------------------------------------------------
  알림 페이지는 만들지 않습니다. 여기서 알려 줄 것은 "안 읽은 채팅"과 "오늘 약속"
  둘뿐이고, 둘 다 누르면 갈 곳이 이미 있는 화면(채팅·실시간 확인)입니다.
  한 줄 보려고 화면을 하나 더 두면 오히려 손이 늘어납니다.

  벨 위의 점은 두지 않습니다. 손댈 일이 없을 때도 점이 남아 있는 것처럼 느껴져 오히려
  신경이 쓰였습니다. 소식은 눌러서 확인합니다.
*/
const isNoticeOpen = ref(false)

function toggleNotice() {
  isNoticeOpen.value = !isNoticeOpen.value
}

function closeNotice() {
  isNoticeOpen.value = false
}

async function goFromNotice(path) {
  closeNotice()
  await router.push(path)
}

function isActiveNavItem(href) {
  return route.path === href
}

watch(() => route.fullPath, () => {
  isMobileMenuOpen.value = false
})

async function submitSearch() {
  const query = searchQuery.value.trim()
  if (!query) return
  await router.push({
    name: 'products',
    query: { q: query },
  })
}

async function logoutMember() {
  if (!window.confirm('로그아웃하시겠습니까?')) return
  isProfileMenuOpen.value = false
  try {
    await logout()
  } finally {
    clearAuthSession()
    await router.push('/')
  }
}
</script>

<template>
  <header
    class="header-glass sticky top-0 z-50 w-full"
    :class="{ 'header-glass--solid': isScrolled }"
  >
    <div class="mx-auto flex h-[72px] max-w-[1200px] items-center gap-6 px-6 lg:px-10">
      <RouterLink
        to="/"
        class="flex shrink-0 items-center"
      >
        <!--
          새 로고는 위아래에 여백이 들어 있어, 예전 로고에 쓰던 -6.4px 보정은 뺍니다.
          그만큼 글자가 작아 보이므로 높이를 32px에서 40px로 올렸습니다.
        -->
        <img
          :src="limitLogo"
          alt="LIMIT"
          class="h-10 w-auto"
        >
      </RouterLink>

      <!--
        로고 옆: 물건을 사고파는 두 가지 주 동선. 로고와 붙지 않게 한 칸 띄웁니다.
        로고 이미지는 글자가 상자 가운데보다 아래쪽에 놓여 있어, 메뉴를 세로 가운데에
        그대로 두면 로고보다 살짝 위로 떠 보입니다. 2px만 내려 눈높이를 맞춥니다.
      -->
      <nav class="mt-0.5 hidden shrink-0 items-center gap-6 md:ml-2 md:flex lg:ml-4">
        <RouterLink
          to="/products"
          class="nav-link"
          :class="isActiveNavItem('/products') ? 'nav-link--active' : ''"
        >
          전체 상품
        </RouterLink>
        <button
          type="button"
          class="nav-link"
          :class="isActiveNavItem(sellEntryPath) ? 'nav-link--active' : ''"
          @click="goToSell"
        >
          판매하기
        </button>
      </nav>

      <!-- 검색이 이 서비스에서 가장 자주 쓰는 입구라 가운데에 크게 둡니다. -->
      <form
        v-if="showSearch"
        class="header-search mx-auto hidden w-full max-w-xs items-center gap-2 rounded-pill px-4 py-2.5 sm:flex md:ml-4 lg:ml-8 lg:max-w-md"
        role="search"
        @submit.prevent="submitSearch"
      >
        <button
          type="submit"
          aria-label="상품 검색"
          class="shrink-0 text-text-sub transition-colors hover:text-primary"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z"
            />
          </svg>
        </button>
        <label
          for="global-product-search"
          class="sr-only"
        >상품 검색</label>
        <input
          id="global-product-search"
          v-model="searchQuery"
          type="search"
          placeholder="상품, 기기명 검색..."
          class="w-full bg-transparent text-sm text-text-main placeholder:text-text-sub focus:outline-none"
        >
      </form>

      <!-- 알림·프로필 아이콘도 옆 글자와 같이 2px 내려 로고에 눈높이를 맞춥니다. -->
      <div class="ml-auto mt-0.5 flex shrink-0 items-center gap-3">
        <!-- 검색 오른쪽: 거래가 시작된 뒤에 쓰는 동선 -->
        <!-- 왼쪽 내비와 같은 간격·여백으로 두어 헤더 전체가 한 줄로 읽히게 합니다. -->
        <!-- 내림은 바깥 묶음(mt-0.5)이 이미 하고 있어 여기서 또 주면 4px이 됩니다. -->
        <nav class="hidden items-center gap-6 md:mr-2 md:flex lg:mr-4">
          <!-- 채팅과 실시간 확인은 로그인해야 쓸 수 있어, 비로그인에는 보여 주지 않습니다. -->
          <RouterLink
            v-if="member"
            to="/chat"
            class="nav-link"
            :class="isActiveNavItem('/chat') ? 'nav-link--active' : ''"
          >
            채팅
          </RouterLink>
          <RouterLink
            v-if="member"
            :to="{ name: 'calls' }"
            class="nav-link"
            :class="isActiveNavItem('/calls') ? 'nav-link--active' : ''"
          >
            실시간 확인
          </RouterLink>
        </nav>

        <template v-if="member">
          <div class="relative">
            <button
              type="button"
              aria-label="알림"
              :aria-expanded="isNoticeOpen"
              class="header-icon-btn notification-btn flex items-center justify-center"
              @click="toggleNotice"
            >
              <svg
                class="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="1.5"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0"
                />
              </svg>
            </button>

            <template v-if="isNoticeOpen">
              <div
                class="fixed inset-0 z-10"
                @click="closeNotice"
              />
              <div
                role="status"
                class="notice-bubble absolute right-0 top-full z-20 mt-2 w-64 rounded-md border border-border bg-surface p-2 shadow-elevated"
              >
                <button
                  v-if="unreadChatCount"
                  type="button"
                  class="block w-full rounded px-2 py-2 text-left text-sm text-text-main hover:bg-bg"
                  @click="goFromNotice('/chat')"
                >
                  안 읽은 채팅이 <strong class="font-bold text-primary">{{ unreadChatCount }}건</strong> 있습니다.
                </button>
                <button
                  v-if="todayAppointment"
                  type="button"
                  class="block w-full rounded px-2 py-2 text-left text-sm text-text-main hover:bg-bg"
                  @click="goFromNotice('/calls')"
                >
                  오늘의 검증 약속
                  <strong class="font-bold text-primary">{{ todayAppointment.timeLabel }}</strong>
                  <span
                    v-if="todayAppointment.isWaitingForCounterpart"
                    class="mt-0.5 block text-xs text-text-sub"
                  >상대방의 응답을 기다리고 있습니다.</span>
                </button>
                <!-- 판매자 입장: 내 상품을 다시 찍어 달라는 요청이 들어왔습니다. -->
                <button
                  v-if="pendingRecaptureCount"
                  type="button"
                  class="block w-full rounded px-2 py-2 text-left text-sm text-text-main hover:bg-bg"
                  @click="goFromNotice('/calls')"
                >
                  재촬영 요청이
                  <strong class="font-bold text-primary">{{ pendingRecaptureCount }}건</strong>
                  들어왔습니다.
                </button>
                <!-- 구매자 입장: 내가 요청한 재촬영을 판매자가 끝냈습니다. -->
                <button
                  v-if="doneRecaptureCount"
                  type="button"
                  class="block w-full rounded px-2 py-2 text-left text-sm text-text-main hover:bg-bg"
                  @click="goFromNotice('/chat')"
                >
                  요청한 재촬영이
                  <strong class="font-bold text-primary">{{ doneRecaptureCount }}건</strong>
                  올라왔습니다.
                </button>
                <p
                  v-if="!hasNotice"
                  class="px-2 py-2 text-sm text-text-sub"
                >
                  새로운 소식이 없습니다.
                </p>
              </div>
            </template>
          </div>

          <div class="relative">
            <button
              type="button"
              aria-haspopup="true"
              :aria-expanded="isProfileMenuOpen"
              aria-label="내 계정"
              class="header-icon-btn flex items-center justify-center"
              @click="isProfileMenuOpen = !isProfileMenuOpen"
            >
              <!-- 사진을 올린 회원은 사진을, 올리지 않았으면 지금까지의 사람 아이콘을
                   그대로 씁니다. 여기만 첫 글자 대신 아이콘을 쓰는 건 버튼이 작아서입니다. -->
              <img
                v-if="member?.profileImageUrl"
                :src="member.profileImageUrl"
                alt=""
                class="h-7 w-7 rounded-full object-cover"
              >
              <svg
                v-else
                class="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0"
                />
              </svg>
            </button>

            <template v-if="isProfileMenuOpen">
              <div
                class="fixed inset-0 z-10"
                @click="isProfileMenuOpen = false"
              />
              <div class="absolute right-0 top-full z-20 mt-2 w-44 rounded-md border border-border bg-surface p-2 shadow-elevated">
                <p class="truncate px-2 py-1.5 text-xs text-text-sub">
                  {{ member.nickname || '회원' }}
                </p>
                <RouterLink
                  to="/mypage/profile"
                  class="block rounded px-2 py-1.5 text-sm text-text-main hover:bg-bg"
                  @click="isProfileMenuOpen = false"
                >
                  <!--
                    사이드바가 딸린 마이페이지 전체로 들어가는 입구입니다.
                    '내 정보'라고 하면 그 안의 한 메뉴 이름과 겹쳐서, 어디로 가는지 헷갈립니다.
                  -->
                  마이 페이지
                </RouterLink>
                <button
                  type="button"
                  class="block w-full rounded px-2 py-1.5 text-left text-sm text-text-main hover:bg-bg"
                  @click="logoutMember"
                >
                  로그아웃
                </button>
              </div>
            </template>
          </div>
        </template>
        <!-- 글자 크기는 옆의 내비 링크(nav-link, 16px)와 맞춥니다. -->
        <template v-else>
          <RouterLink
            to="/login"
            class="nav-link hidden sm:inline"
          >
            로그인
          </RouterLink>
          <RouterLink
            to="/signup"
            class="rounded-md bg-primary-gradient px-3 py-2 text-[15px] font-semibold text-white shadow-card transition-all hover:brightness-110"
          >
            회원가입
          </RouterLink>
        </template>

        <!-- 좁은 화면에서는 위 내비를 감추고 이 메뉴로 모읍니다. -->
        <button
          type="button"
          class="header-icon-btn flex items-center justify-center md:hidden"
          aria-label="메뉴 열기"
          :aria-expanded="isMobileMenuOpen"
          @click="isMobileMenuOpen = !isMobileMenuOpen"
        >
          <svg
            v-if="!isMobileMenuOpen"
            class="h-6 w-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M4 6h16M4 12h16M4 18h16"
            />
          </svg>
          <svg
            v-else
            class="h-6 w-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      </div>
    </div>

    <div
      v-if="isMobileMenuOpen"
      class="border-t border-border bg-surface px-6 py-4 md:hidden"
    >
      <form
        class="header-search mb-4 flex items-center gap-2 rounded-pill px-4 py-2.5 sm:hidden"
        role="search"
        @submit.prevent="submitSearch"
      >
        <button
          type="submit"
          aria-label="상품 검색"
          class="text-text-sub hover:text-primary"
        >
          <svg
            class="h-4 w-4 text-text-sub"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z"
            />
          </svg>
        </button>
        <label
          for="mobile-product-search"
          class="sr-only"
        >상품 검색</label>
        <input
          id="mobile-product-search"
          v-model="searchQuery"
          type="search"
          placeholder="상품 검색..."
          class="w-full bg-transparent text-base text-text-main placeholder:text-text-sub focus:outline-none"
        >
      </form>

      <nav class="flex flex-col">
        <template
          v-for="item in visibleNavItems"
          :key="item.label"
        >
          <button
            v-if="item.action === 'sell'"
            type="button"
            class="rounded-md px-2 py-2.5 text-left text-base font-semibold text-text-sub transition-colors hover:bg-bg"
            @click="goToSell"
          >
            {{ item.label }}
          </button>
          <RouterLink
            v-else
            :to="item.href"
            class="rounded-md px-2 py-2.5 text-base transition-colors"
            :class="isActiveNavItem(item.href)
              ? 'font-bold text-text-main'
              : 'font-semibold text-text-sub hover:bg-bg'"
          >
            {{ item.label }}
          </RouterLink>
        </template>
        <RouterLink
          v-if="member"
          :to="{ name: 'calls' }"
          class="rounded-md px-2 py-2.5 text-base font-semibold text-text-sub hover:bg-bg"
        >
          실시간 확인
        </RouterLink>
        <RouterLink
          v-else
          to="/login"
          class="rounded-md px-2 py-2.5 text-base font-semibold text-text-sub hover:bg-bg"
        >
          로그인
        </RouterLink>
      </nav>
    </div>

    <!-- 판매자가 아닌 회원이 판매하기를 눌렀을 때 -->
    <SellerNoticeModal
      :open="isSellerNoticeOpen"
      @close="closeSellerNotice"
      @apply="goToSellerApply"
    />
  </header>
</template>

<style scoped>
/*
  헤더는 늘 붙어 있어야 검색과 메뉴에 언제든 닿습니다. 배경을 반투명 유리로 두어
  아래 내용이 비쳐 보이게 하고, 경계는 브랜드 색으로 옅게만 긋습니다.
*/
.header-glass {
  background-color: transparent;
  transition:
    background-color 0.3s ease,
    box-shadow 0.3s ease,
    backdrop-filter 0.3s ease;
}

/* 조금이라도 내리면 배경이 깔립니다. 선 대신 옅은 그림자로만 경계를 냅니다. */
.header-glass--solid {
  background-color: rgb(255 255 255 / 85%);
  backdrop-filter: blur(12px);
  box-shadow: 0 1px 12px -4px rgb(76 100 200 / 20%);
}

/* backdrop-filter를 지원하지 않는 브라우저에서는 불투명 배경으로 둡니다. */
@supports not (backdrop-filter: blur(12px)) {
  .header-glass--solid {
    background-color: #fff;
  }
}

@media (prefers-reduced-motion: reduce) {
  .header-glass {
    transition: none;
  }
}

.header-search {
  background-color: #f1f5f9;
  border: 1px solid transparent;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.header-search:focus-within {
  border-color: rgb(99 102 241 / 45%);
  box-shadow: 0 0 0 3px rgb(99 102 241 / 10%);
}

/*
  헤더 내비 링크. 배포되어 있던 모양 그대로입니다. 굵기·색과 함께 아래 밑줄로
  현재 위치를 알립니다. 색만으로 구분하면 어디에 있는지 한눈에 안 들어옵니다.
*/
.nav-link {
  position: relative;
  font-size: 16px;
  font-weight: 600;
  color: #64748b;
  white-space: nowrap;
  transition: color 0.2s ease;
}

/*
  밑줄은 글자 밖 8px 아래에 띄웁니다. padding으로 만들면 링크 높이가 늘어나
  같은 줄에 선 검색창·버튼과 세로 가운데가 어긋납니다.
*/
.nav-link::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 0;
  right: 0;
  height: 2px;
  border-radius: 9999px;
  background-color: transparent;
  transition: background-color 0.2s ease;
}

.nav-link:hover {
  font-weight: 700;
  color: #1f2937;
}

.nav-link:hover::after {
  background-color: #6c8dff;
}

.nav-link--active {
  font-weight: 700;
  color: #1f2937;
}

.nav-link--active::after {
  background-color: #6c8dff;
}

/*
  display는 여기서 정하지 않습니다. scoped 스타일이 Tailwind보다 뒤에 실려서
  display를 넣으면 md:hidden 같은 반응형 유틸리티를 이겨 버립니다(햄버거가 안 숨겨졌던 원인).
  배치는 마크업의 flex 유틸리티에 맡기고, 여기서는 크기와 색만 다룹니다.
*/
.header-icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 9999px;
  color: #334155;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.header-icon-btn:hover {
  background-color: #f1f5f9;
  color: #6366f1;
}

/*
  손댈 일이 있다는 표시.
  ---------------------------------------------------------------------------
  예전에는 늘 켜져 있었습니다. 항상 켜진 점은 아무것도 알리지 못하고, 눌러도
  새로운 게 없으니 나중에는 아예 안 보게 됩니다.
  지금은 stores/notificationDot.js가 안 읽은 채팅과 받은 검증 약속을 세어
  둘 중 하나라도 있을 때만 켭니다.
*/
.notification-btn {
  position: relative;
}

/* 벨에서 톡 튀어나오는 느낌만 줍니다. 길면 누른 뒤 읽기까지 기다리게 됩니다. */
.notice-bubble {
  transform-origin: top right;
  animation: notice-pop 160ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes notice-pop {
  from {
    opacity: 0;
    transform: scale(0.92) translateY(-4px);
  }

  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .notice-bubble {
    animation: none;
  }
}
</style>
