<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AuthShell from '../components/AuthShell.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import AdminShell from '../components/AdminShell.vue'
import {
  createAdminAccount,
  createMemberRestriction,
  getAdminAccounts,
  getAdminActionLogs,
  getAdminMember,
  getAdminMembers,
  getMemberRestrictions,
  loginAdmin,
  releaseMemberRestriction,
  updateAdminAccount,
} from '../api/admin'
import { clearAuthSession, setAuthSession, useAuthSession } from '../auth/session'

const session = useAuthSession()
const loginForm = reactive({ email: '', password: '' })
const loginLoading = ref(false)
const loginError = ref('')
const activeSection = ref('dashboard')
const isLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const membersPage = ref(emptyPage())
const logsPage = ref(emptyPage())
const accountsPage = ref(emptyPage())
const selectedMember = ref(null)
const restrictions = ref([])
const restrictionForm = reactive({
  restrictionType: 'PURCHASE',
  reasonCode: 'POLICY_VIOLATION',
  reasonDetail: '',
  startsAt: '',
  endsAt: '',
})
const accountForm = reactive({ email: '', password: '', name: '', role: 'OPERATOR' })

const admin = computed(() => session.value?.admin || null)
const isSuperAdmin = computed(() => admin.value?.roles?.includes('SUPER_ADMIN'))
const sections = computed(() => [
  { id: 'dashboard', label: '대시보드' },
  { id: 'members', label: '회원 관리' },
  { id: 'logs', label: '관리자 작업 로그' },
  ...(isSuperAdmin.value ? [{ id: 'accounts', label: '관리자 계정' }] : []),
])

function emptyPage() {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false }
}

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function badgeVariant(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'SUSPENDED' || status === 'RELEASED') return 'gray'
  return 'primary'
}

function showError(error, fallback) {
  errorMessage.value = error.message || fallback
  successMessage.value = ''
}

async function submitLogin() {
  if (loginLoading.value) return
  loginLoading.value = true
  loginError.value = ''
  try {
    const result = await loginAdmin(loginForm.email, loginForm.password)
    setAuthSession(result)
    await loadSection('dashboard')
  } catch (error) {
    loginError.value = error.message || '관리자 로그인에 실패했습니다.'
  } finally {
    loginLoading.value = false
  }
}

function logoutAdmin() {
  clearAuthSession()
  activeSection.value = 'dashboard'
  selectedMember.value = null
}

async function loadSection(section) {
  activeSection.value = section
  isLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (section === 'dashboard') {
      const requests = [getAdminMembers(0, 5), getAdminActionLogs(0, 5)]
      if (isSuperAdmin.value) requests.push(getAdminAccounts(0, 5))
      const [memberResult, logResult, accountResult] = await Promise.all(requests)
      membersPage.value = memberResult
      logsPage.value = logResult
      if (accountResult) accountsPage.value = accountResult
    } else if (section === 'members') {
      membersPage.value = await getAdminMembers(0, 20)
    } else if (section === 'logs') {
      logsPage.value = await getAdminActionLogs(0, 20)
    } else if (section === 'accounts' && isSuperAdmin.value) {
      accountsPage.value = await getAdminAccounts(0, 20)
    }
  } catch (error) {
    showError(error, '관리자 데이터를 불러오지 못했습니다.')
  } finally {
    isLoading.value = false
  }
}

async function selectMember(memberId) {
  errorMessage.value = ''
  try {
    const [memberResult, restrictionResult] = await Promise.all([
      getAdminMember(memberId),
      getMemberRestrictions(memberId),
    ])
    selectedMember.value = memberResult
    restrictions.value = restrictionResult.content
  } catch (error) {
    showError(error, '회원 상세 정보를 불러오지 못했습니다.')
  }
}

async function submitRestriction() {
  if (!selectedMember.value) return
  errorMessage.value = ''
  try {
    const created = await createMemberRestriction(selectedMember.value.memberId, {
      ...restrictionForm,
      startsAt: new Date(restrictionForm.startsAt).toISOString(),
      endsAt: new Date(restrictionForm.endsAt).toISOString(),
    })
    restrictions.value = [created, ...restrictions.value]
    restrictionForm.reasonDetail = ''
    successMessage.value = '회원 이용 제한을 등록했습니다.'
  } catch (error) {
    showError(error, '회원 이용 제한을 등록하지 못했습니다.')
  }
}

async function releaseRestriction(restriction) {
  const reason = window.prompt('해제 사유를 입력해 주세요.')
  if (!reason) return
  try {
    const released = await releaseMemberRestriction(restriction.restrictionId, reason)
    restrictions.value = restrictions.value.map((item) => item.restrictionId === released.restrictionId ? released : item)
    successMessage.value = '회원 이용 제한을 해제했습니다.'
  } catch (error) {
    showError(error, '회원 이용 제한을 해제하지 못했습니다.')
  }
}

async function submitAccount() {
  errorMessage.value = ''
  try {
    const created = await createAdminAccount({ ...accountForm })
    accountsPage.value = {
      ...accountsPage.value,
      content: [created, ...accountsPage.value.content],
      totalElements: accountsPage.value.totalElements + 1,
    }
    Object.assign(accountForm, { email: '', password: '', name: '', role: 'OPERATOR' })
    successMessage.value = '관리자 계정을 생성했습니다.'
  } catch (error) {
    showError(error, '관리자 계정을 생성하지 못했습니다.')
  }
}

async function saveAccount(account) {
  try {
    const updated = await updateAdminAccount(account.adminId, { role: account.role, status: account.status })
    accountsPage.value.content = accountsPage.value.content.map((item) => item.adminId === updated.adminId ? updated : item)
    successMessage.value = '관리자 권한과 상태를 변경했습니다.'
  } catch (error) {
    showError(error, '관리자 계정을 변경하지 못했습니다.')
  }
}

onMounted(() => {
  if (admin.value) loadSection('dashboard')
})
</script>

<template>
  <div
    v-if="!admin"
    class="min-h-screen bg-bg font-main"
  >
    <div class="border-b border-border bg-white px-6 py-5">
      <RouterLink
        to="/"
        class="bg-primary-gradient bg-clip-text text-xl font-extrabold text-transparent"
      >
        LIMIT
      </RouterLink>
      <span class="ml-2 rounded-full bg-accent px-2 py-1 text-[10px] font-bold text-primary">ADMIN</span>
    </div>
    <AuthShell
      eyebrow="관리자 전용"
      title="관리자 로그인"
      description="운영자 계정은 일반 회원 계정과 분리되어 있습니다."
    >
      <BaseCard class="p-8">
        <form
          class="space-y-5"
          @submit.prevent="submitLogin"
        >
          <BaseInput
            v-model="loginForm.email"
            label="관리자 이메일"
            type="email"
            autocomplete="username"
            required
            placeholder="admin@example.com"
          />
          <BaseInput
            v-model="loginForm.password"
            label="비밀번호"
            type="password"
            autocomplete="current-password"
            required
            placeholder="관리자 비밀번호"
          />
          <p
            v-if="loginError"
            class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
            role="alert"
          >
            {{ loginError }}
          </p>
          <BaseButton
            block
            type="submit"
            :disabled="loginLoading"
          >
            {{ loginLoading ? '확인 중...' : '관리자 로그인' }}
          </BaseButton>
        </form>
        <p class="mt-5 text-xs leading-5 text-text-sub">
          Swagger에서 테스트할 때는 로그인 응답의 <strong>accessToken 값만</strong>
          Authorize에 입력하세요. Swagger UI가 Authorization: Bearer 헤더를 자동으로
          추가합니다.
        </p>
      </BaseCard>
    </AuthShell>
  </div>

  <AdminShell
    v-else
    :admin-name="admin.name"
    :admin-role="admin.roles.join(', ')"
    :active-section="activeSection"
    :sections="sections"
    @select="loadSection"
    @logout="logoutAdmin"
  >
    <header class="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p class="text-sm font-semibold text-primary">
          ADMIN CONSOLE
        </p>
        <h1 class="mt-2 text-3xl font-bold tracking-[-0.03em]">
          {{ sections.find((item) => item.id === activeSection)?.label }}
        </h1>
      </div>
      <p class="text-sm text-text-sub">
        민감 정보는 마스킹되어 표시됩니다.
      </p>
    </header>

    <p
      v-if="errorMessage"
      class="mb-6 rounded-md border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p
      v-if="successMessage"
      class="mb-6 rounded-md border border-green-100 bg-green-50 px-4 py-3 text-sm text-green-700"
      role="status"
    >
      {{ successMessage }}
    </p>
    <p
      v-if="isLoading"
      class="rounded-lg border border-border bg-white p-8 text-center text-sm text-text-sub"
    >
      데이터를 불러오고 있습니다...
    </p>

    <template v-else-if="activeSection === 'dashboard'">
      <section class="grid gap-5 md:grid-cols-3">
        <BaseCard>
          <p class="text-sm text-text-sub">
            전체 회원
          </p><p class="mt-3 text-3xl font-bold">
            {{ membersPage.totalElements }}
          </p>
        </BaseCard>
        <BaseCard>
          <p class="text-sm text-text-sub">
            관리자 작업 기록
          </p><p class="mt-3 text-3xl font-bold">
            {{ logsPage.totalElements }}
          </p>
        </BaseCard>
        <BaseCard>
          <p class="text-sm text-text-sub">
            관리자 계정
          </p><p class="mt-3 text-3xl font-bold">
            {{ isSuperAdmin ? accountsPage.totalElements : '-' }}
          </p>
        </BaseCard>
      </section>
      <section class="mt-6 grid gap-6 xl:grid-cols-2">
        <BaseCard>
          <h2 class="font-bold">
            최근 가입 회원
          </h2>
          <div class="mt-5 divide-y divide-border">
            <button
              v-for="member in membersPage.content"
              :key="member.memberId"
              class="flex w-full items-center justify-between py-3 text-left"
              @click="selectMember(member.memberId); loadSection('members')"
            >
              <span><strong class="block text-sm">{{ member.nickname }}</strong><small class="text-text-sub">{{ member.email }}</small></span>
              <BaseBadge :variant="badgeVariant(member.status)">
                {{ member.status }}
              </BaseBadge>
            </button>
          </div>
        </BaseCard>
        <BaseCard>
          <h2 class="font-bold">
            최근 관리자 작업
          </h2>
          <div class="mt-5 divide-y divide-border">
            <div
              v-for="log in logsPage.content"
              :key="log.adminActionLogId"
              class="py-3"
            >
              <p class="text-sm font-semibold">
                {{ log.actionType }}
              </p>
              <p class="mt-1 text-xs text-text-sub">
                {{ log.targetType }} #{{ log.targetId }} · {{ formatDate(log.createdAt) }}
              </p>
            </div>
          </div>
        </BaseCard>
      </section>
    </template>

    <section
      v-else-if="activeSection === 'members'"
      class="grid gap-6 xl:grid-cols-[minmax(0,1.4fr)_minmax(340px,0.8fr)]"
    >
      <BaseCard
        :padded="false"
        class="overflow-hidden"
      >
        <div class="border-b border-border px-6 py-5">
          <h2 class="font-bold">
            회원 목록
          </h2><p class="mt-1 text-xs text-text-sub">
            총 {{ membersPage.totalElements }}명
          </p>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[720px] text-left text-sm">
            <thead class="bg-bg text-text-sub">
              <tr>
                <th class="px-5 py-3">
                  회원
                </th><th class="px-5 py-3">
                  인증
                </th><th class="px-5 py-3">
                  상태
                </th><th class="px-5 py-3">
                  제한
                </th><th class="px-5 py-3">
                  가입일
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border">
              <tr
                v-for="member in membersPage.content"
                :key="member.memberId"
                class="cursor-pointer hover:bg-bg"
                @click="selectMember(member.memberId)"
              >
                <td class="px-5 py-4">
                  <strong class="block">{{ member.nickname }}</strong><small class="text-text-sub">{{ member.email }}</small>
                </td>
                <td class="px-5 py-4">
                  {{ member.authType }}
                </td>
                <td class="px-5 py-4">
                  <BaseBadge :variant="badgeVariant(member.status)">
                    {{ member.status }}
                  </BaseBadge>
                </td>
                <td class="px-5 py-4">
                  {{ member.activeRestrictionCount }}
                </td>
                <td class="px-5 py-4 text-text-sub">
                  {{ formatDate(member.createdAt) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </BaseCard>

      <BaseCard class="h-fit">
        <template v-if="selectedMember">
          <div class="flex items-start justify-between">
            <div>
              <h2 class="text-lg font-bold">
                {{ selectedMember.nickname }}
              </h2><p class="mt-1 text-sm text-text-sub">
                {{ selectedMember.email }}
              </p>
            </div><BaseBadge :variant="badgeVariant(selectedMember.status)">
              {{ selectedMember.status }}
            </BaseBadge>
          </div>
          <div class="my-5 h-px bg-border" />
          <h3 class="text-sm font-bold">
            이용 제한 등록
          </h3>
          <form
            class="mt-4 space-y-3"
            @submit.prevent="submitRestriction"
          >
            <div class="grid grid-cols-2 gap-3">
              <select
                v-model="restrictionForm.restrictionType"
                class="rounded-md border border-border bg-white px-3 py-2 text-sm"
              >
                <option value="PURCHASE">
                  구매 제한
                </option><option value="SELLING">
                  판매 제한
                </option><option value="ACCOUNT">
                  계정 제한
                </option>
              </select>
              <input
                v-model="restrictionForm.reasonCode"
                class="rounded-md border border-border px-3 py-2 text-sm"
                placeholder="사유 코드"
                required
              >
            </div>
            <textarea
              v-model="restrictionForm.reasonDetail"
              class="min-h-20 w-full rounded-md border border-border px-3 py-2 text-sm"
              placeholder="구체적인 제한 사유"
              required
            />
            <div class="grid grid-cols-2 gap-3">
              <input
                v-model="restrictionForm.startsAt"
                type="datetime-local"
                class="rounded-md border border-border px-3 py-2 text-xs"
                required
              ><input
                v-model="restrictionForm.endsAt"
                type="datetime-local"
                class="rounded-md border border-border px-3 py-2 text-xs"
                required
              >
            </div>
            <BaseButton
              block
              type="submit"
            >
              제한 등록
            </BaseButton>
          </form>
          <div class="mt-6 divide-y divide-border border-t border-border">
            <div
              v-for="restriction in restrictions"
              :key="restriction.restrictionId"
              class="py-4"
            >
              <div class="flex items-center justify-between">
                <strong class="text-sm">{{ restriction.restrictionType }}</strong><BaseBadge :variant="badgeVariant(restriction.status)">
                  {{ restriction.status }}
                </BaseBadge>
              </div>
              <p class="mt-2 text-xs leading-5 text-text-sub">
                {{ restriction.reasonDetail }}
              </p>
              <button
                v-if="restriction.status === 'ACTIVE'"
                class="mt-2 text-xs font-semibold text-red-500"
                @click="releaseRestriction(restriction)"
              >
                제한 해제
              </button>
            </div>
          </div>
        </template>
        <p
          v-else
          class="py-10 text-center text-sm text-text-sub"
        >
          목록에서 회원을 선택해 주세요.
        </p>
      </BaseCard>
    </section>

    <BaseCard
      v-else-if="activeSection === 'logs'"
      :padded="false"
      class="overflow-hidden"
    >
      <div class="border-b border-border px-6 py-5">
        <h2 class="font-bold">
          감사 가능한 관리자 작업 기록
        </h2><p class="mt-1 text-xs text-text-sub">
          총 {{ logsPage.totalElements }}건
        </p>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[760px] text-left text-sm">
          <thead class="bg-bg text-text-sub">
            <tr>
              <th class="px-5 py-3">
                일시
              </th><th class="px-5 py-3">
                관리자
              </th><th class="px-5 py-3">
                작업
              </th><th class="px-5 py-3">
                대상
              </th><th class="px-5 py-3">
                사유
              </th>
            </tr>
          </thead><tbody class="divide-y divide-border">
            <tr
              v-for="log in logsPage.content"
              :key="log.adminActionLogId"
            >
              <td class="px-5 py-4 text-text-sub">
                {{ formatDate(log.createdAt) }}
              </td><td class="px-5 py-4">
                #{{ log.adminId }}
              </td><td class="px-5 py-4 font-semibold">
                {{ log.actionType }}
              </td><td class="px-5 py-4">
                {{ log.targetType }} #{{ log.targetId }}
              </td><td class="max-w-xs px-5 py-4 text-text-sub">
                {{ log.reason || '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>

    <section
      v-else-if="activeSection === 'accounts' && isSuperAdmin"
      class="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]"
    >
      <BaseCard class="h-fit">
        <h2 class="font-bold">
          관리자 계정 생성
        </h2>
        <p class="mt-2 text-xs leading-5 text-text-sub">
          SUPER_ADMIN만 새 운영자 계정을 만들 수 있습니다.
        </p>
        <form
          class="mt-5 space-y-4"
          @submit.prevent="submitAccount"
        >
          <BaseInput
            v-model="accountForm.email"
            label="이메일"
            type="email"
            required
            placeholder="operator@l1mit.shop"
          />
          <BaseInput
            v-model="accountForm.name"
            label="이름"
            required
            placeholder="운영자"
          />
          <BaseInput
            v-model="accountForm.password"
            label="초기 비밀번호"
            type="password"
            required
            placeholder="12자 이상"
          />
          <label class="block text-sm font-medium">권한<select
            v-model="accountForm.role"
            class="mt-2 w-full rounded-md border border-border bg-white px-4 py-3"
          ><option value="OPERATOR">OPERATOR</option><option value="SUPER_ADMIN">SUPER_ADMIN</option></select></label>
          <BaseButton
            block
            type="submit"
          >
            계정 생성
          </BaseButton>
        </form>
      </BaseCard>
      <BaseCard
        :padded="false"
        class="overflow-hidden"
      >
        <div class="border-b border-border px-6 py-5">
          <h2 class="font-bold">
            관리자 계정 목록
          </h2><p class="mt-1 text-xs text-text-sub">
            마지막 활성 SUPER_ADMIN은 강등하거나 정지할 수 없습니다.
          </p>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[780px] text-left text-sm">
            <thead class="bg-bg text-text-sub">
              <tr>
                <th class="px-5 py-3">
                  관리자
                </th><th class="px-5 py-3">
                  권한
                </th><th class="px-5 py-3">
                  상태
                </th><th class="px-5 py-3">
                  최근 로그인
                </th><th class="px-5 py-3">
                  변경
                </th>
              </tr>
            </thead><tbody class="divide-y divide-border">
              <tr
                v-for="account in accountsPage.content"
                :key="account.adminId"
              >
                <td class="px-5 py-4">
                  <strong class="block">{{ account.name }}</strong><small class="text-text-sub">{{ account.email }}</small>
                </td><td class="px-5 py-4">
                  <select
                    v-model="account.role"
                    class="rounded-md border border-border bg-white px-2 py-2"
                  >
                    <option value="OPERATOR">
                      OPERATOR
                    </option><option value="SUPER_ADMIN">
                      SUPER_ADMIN
                    </option>
                  </select>
                </td><td class="px-5 py-4">
                  <select
                    v-model="account.status"
                    class="rounded-md border border-border bg-white px-2 py-2"
                  >
                    <option value="ACTIVE">
                      ACTIVE
                    </option><option value="SUSPENDED">
                      SUSPENDED
                    </option>
                  </select>
                </td><td class="px-5 py-4 text-text-sub">
                  {{ formatDate(account.lastLoginAt) }}
                </td><td class="px-5 py-4">
                  <button
                    class="font-semibold text-primary"
                    @click="saveAccount(account)"
                  >
                    저장
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </BaseCard>
    </section>
  </AdminShell>
</template>
