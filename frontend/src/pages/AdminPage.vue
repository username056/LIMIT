<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AuthShell from '../components/AuthShell.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import AdminShell from '../components/AdminShell.vue'
import AdminDeviceModelManagement from '../components/admin/AdminDeviceModelManagement.vue'
import {
  createAdminAccount,
  createMemberRestriction,
  approveChecklistResearch,
  approveDeviceModelRequest,
  getAdminAccounts,
  getAdminActionLog,
  getAdminActionLogs,
  getAdminMember,
  getAdminMembers,
  getMemberRestrictions,
  getChecklistResearches,
  getDeviceModelRequests,
  loginAdmin,
  rejectChecklistResearch,
  rejectDeviceModelRequest,
  releaseMemberRestriction,
  retryChecklistResearch,
  updateAdminActionLog,
  updateAdminAccount,
  updateDeviceModelRequest,
} from '../api/admin'
import { getDeviceCategories } from '../api/products'
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
const checklistResearches = ref([])
const checklistResearchStatus = ref('PENDING_REVIEW')
const retryingResearchId = ref(null)
const deviceModelRequests = ref([])
const deviceCategories = ref([])
const editingModelRequestId = ref(null)
const isSavingModelRequest = ref(false)
const modelRequestForm = reactive({
  categoryId: '',
  manufacturer: '',
  modelName: '',
  modelCode: '',
  osFamily: 'ANDROID',
})
const selectedActionLog = ref(null)
const isLoadingActionLog = ref(false)
const isEditingActionLog = ref(false)
const isSavingActionLog = ref(false)
const actionLogReason = ref('')
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
  { id: 'device-models', label: '모델 관리' },
  { id: 'checklist-researches', label: '체크리스트 AI 검토' },
  { id: 'device-model-requests', label: '신규 기기 모델 검토' },
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

function researchBadgeVariant(status) {
  if (status === 'FAILED') return 'danger'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'gray'
  return 'primary'
}

function researchStatusLabel(status) {
  if (status === 'PENDING_REVIEW') return '관리자 검토 대기'
  if (status === 'FAILED') return 'AI 조사 실패'
  if (status === 'APPROVED') return '승인 완료'
  if (status === 'REJECTED') return '반려'
  return status
}

function deviceCategoryLabel(categoryId) {
  return deviceCategories.value.find(
    (category) => Number(category.categoryId) === Number(categoryId),
  )?.name || `카테고리 #${categoryId}`
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
  if (!window.confirm('로그아웃하시겠습니까?')) return
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
    } else if (section === 'checklist-researches') {
      checklistResearches.value = await getChecklistResearches(checklistResearchStatus.value)
    } else if (section === 'device-model-requests') {
      const [requests, categories] = await Promise.all([
        getDeviceModelRequests(),
        getDeviceCategories({ activeOnly: true }),
      ])
      deviceModelRequests.value = requests
      deviceCategories.value = categories
    } else if (section === 'logs') {
      logsPage.value = await getAdminActionLogs(0, 20)
      selectedActionLog.value = null
      isEditingActionLog.value = false
    } else if (section === 'accounts' && isSuperAdmin.value) {
      accountsPage.value = await getAdminAccounts(0, 20)
    }
  } catch (error) {
    showError(error, '관리자 데이터를 불러오지 못했습니다.')
  } finally {
    isLoading.value = false
  }
}

async function selectChecklistResearchStatus(status) {
  if (isLoading.value || retryingResearchId.value) return
  checklistResearchStatus.value = status
  await loadSection('checklist-researches')
}

async function approveResearch(research) {
  const note = window.prompt('승인 메모를 입력해 주세요. (선택)', '') ?? ''
  try {
    await approveChecklistResearch(research.researchId, {
      approvedFeatureCodes: research.suggestions.map((item) => item.featureCode),
      note,
    })
    checklistResearches.value = checklistResearches.value
      .filter((item) => item.researchId !== research.researchId)
    successMessage.value = 'AI 조사 결과를 사후 검토 완료로 기록했습니다.'
  } catch (error) {
    showError(error, '체크리스트 조사 결과를 승인하지 못했습니다.')
  }
}

async function rejectResearch(research) {
  const note = window.prompt('반려 사유를 입력해 주세요.')
  if (!note) return
  try {
    await rejectChecklistResearch(research.researchId, { approvedFeatureCodes: [], note })
    checklistResearches.value = checklistResearches.value
      .filter((item) => item.researchId !== research.researchId)
    successMessage.value = '체크리스트 조사 결과를 반려했습니다.'
  } catch (error) {
    showError(error, '체크리스트 조사 결과를 반려하지 못했습니다.')
  }
}

async function retryResearch(research) {
  if (retryingResearchId.value) return
  retryingResearchId.value = research.researchId
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const retried = await retryChecklistResearch(research.researchId)
    if (retried.status === 'PENDING_REVIEW') {
      checklistResearchStatus.value = 'PENDING_REVIEW'
      checklistResearches.value = await getChecklistResearches('PENDING_REVIEW')
      successMessage.value = 'AI 재조사가 완료되어 관리자 검토 대기 목록으로 이동했습니다.'
    } else {
      checklistResearches.value = checklistResearches.value.map((item) => (
        item.researchId === retried.researchId ? retried : item
      ))
      errorMessage.value = retried.failureMessage || 'AI 재조사에 다시 실패했습니다.'
    }
  } catch (error) {
    showError(error, '체크리스트 AI 재조사를 시작하지 못했습니다.')
  } finally {
    retryingResearchId.value = null
  }
}

async function approveModelRequest(request) {
  const note = window.prompt('승인 메모를 입력해 주세요. (선택)', '') ?? ''
  try {
    await approveDeviceModelRequest(request.requestId, { note })
    deviceModelRequests.value = deviceModelRequests.value
      .filter((item) => item.requestId !== request.requestId)
    successMessage.value = '즉시 등록된 모델의 사후 검토를 완료했습니다.'
  } catch (error) {
    showError(error, '기기 모델 요청을 승인하지 못했습니다.')
  }
}

function startEditingModelRequest(request) {
  editingModelRequestId.value = request.requestId
  Object.assign(modelRequestForm, {
    categoryId: request.categoryId || '',
    manufacturer: request.manufacturer || '',
    modelName: request.modelName || '',
    modelCode: request.modelCode || '',
    osFamily: request.osFamily || 'ANDROID',
  })
  errorMessage.value = ''
  successMessage.value = ''
}

function cancelEditingModelRequest() {
  editingModelRequestId.value = null
}

async function saveModelRequest(request) {
  if (
    !modelRequestForm.categoryId
    || !modelRequestForm.manufacturer.trim()
    || !modelRequestForm.modelName.trim()
  ) {
    errorMessage.value = '카테고리, 제조사와 모델명을 입력해 주세요.'
    return
  }
  isSavingModelRequest.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await updateDeviceModelRequest(request.requestId, {
      categoryId: Number(modelRequestForm.categoryId),
      manufacturer: modelRequestForm.manufacturer.trim(),
      modelName: modelRequestForm.modelName.trim(),
      modelCode: modelRequestForm.modelCode.trim() || null,
      osFamily: modelRequestForm.osFamily,
    })
    deviceModelRequests.value = deviceModelRequests.value.map((item) => (
      item.requestId === updated.requestId ? updated : item
    ))
    editingModelRequestId.value = null
    successMessage.value = '즉시 등록된 모델 정보를 수정했습니다. 확인 후 사후 검토를 완료해 주세요.'
  } catch (error) {
    showError(error, '기기 모델 요청을 수정하지 못했습니다.')
  } finally {
    isSavingModelRequest.value = false
  }
}

async function rejectModelRequest(request) {
  const note = window.prompt('반려 사유를 입력해 주세요.')
  if (!note) return
  try {
    await rejectDeviceModelRequest(request.requestId, { note })
    deviceModelRequests.value = deviceModelRequests.value
      .filter((item) => item.requestId !== request.requestId)
    successMessage.value = '기기 모델 요청을 반려했습니다.'
  } catch (error) {
    showError(error, '기기 모델 요청을 반려하지 못했습니다.')
  }
}

function formatActionLogData(value) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

async function selectActionLog(log) {
  isLoadingActionLog.value = true
  isEditingActionLog.value = false
  errorMessage.value = ''
  try {
    selectedActionLog.value = await getAdminActionLog(log.adminActionLogId)
    actionLogReason.value = selectedActionLog.value.reason || ''
  } catch (error) {
    showError(error, '관리자 작업 로그 상세를 불러오지 못했습니다.')
  } finally {
    isLoadingActionLog.value = false
  }
}

function startEditingActionLog() {
  if (!selectedActionLog.value) return
  actionLogReason.value = selectedActionLog.value.reason || ''
  isEditingActionLog.value = true
}

function cancelEditingActionLog() {
  actionLogReason.value = selectedActionLog.value?.reason || ''
  isEditingActionLog.value = false
}

async function saveActionLog() {
  if (!selectedActionLog.value) return
  isSavingActionLog.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await updateAdminActionLog(
      selectedActionLog.value.adminActionLogId,
      { reason: actionLogReason.value.trim() || null },
    )
    selectedActionLog.value = updated
    logsPage.value.content = logsPage.value.content.map((item) => (
      item.adminActionLogId === updated.adminActionLogId ? updated : item
    ))
    isEditingActionLog.value = false
    successMessage.value = '작업 로그 사유를 수정하고 변경 이력을 남겼습니다.'
  } catch (error) {
    showError(error, '관리자 작업 로그를 수정하지 못했습니다.')
  } finally {
    isSavingActionLog.value = false
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

    <section
      v-else-if="activeSection === 'device-models'"
    >
      <AdminDeviceModelManagement />
    </section>
    <section
      v-else-if="activeSection === 'device-model-requests'"
      class="space-y-5"
    >
      <BaseCard>
        <h2 class="font-bold">
          신규 기기 모델 요청
        </h2>
        <p class="mt-2 text-xs leading-5 text-text-sub">
          사용자가 요청한 모델과 기본 템플릿은 이미 즉시 등록되어 판매에 사용할 수 있습니다.
          이 화면에서는 오타와 분류를 확인하고 사후 검토를 완료합니다.
        </p>
      </BaseCard>
      <BaseCard
        v-for="request in deviceModelRequests"
        :key="request.requestId"
      >
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-primary">
              {{ deviceCategoryLabel(request.categoryId) }} · 요청자 #{{ request.requestedByMemberId }}
            </p>
            <template v-if="editingModelRequestId !== request.requestId">
              <h3 class="mt-1 text-lg font-bold">
                {{ request.manufacturer }} {{ request.modelName }}
              </h3>
              <p class="mt-1 text-xs text-text-sub">
                {{ request.modelCode || '모델 코드 미입력' }} · {{ request.osFamily }}
                · {{ formatDate(request.createdAt) }}
              </p>
            </template>
          </div>
          <BaseBadge variant="primary">
            {{ request.status }}
          </BaseBadge>
        </div>
        <form
          v-if="editingModelRequestId === request.requestId"
          class="mt-5 grid gap-3 rounded-md border border-border bg-bg p-4 sm:grid-cols-2"
          @submit.prevent="saveModelRequest(request)"
        >
          <label class="text-xs font-semibold text-text-main sm:col-span-2">
            카테고리
            <select
              v-model="modelRequestForm.categoryId"
              aria-label="카테고리 수정"
              required
              class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal"
            >
              <option value="">
                카테고리 선택
              </option>
              <option
                v-for="category in deviceCategories"
                :key="category.categoryId"
                :value="category.categoryId"
              >
                {{ category.name }}
              </option>
            </select>
          </label>
          <label class="text-xs font-semibold text-text-main">
            제조사
            <input
              v-model="modelRequestForm.manufacturer"
              aria-label="제조사 수정"
              maxlength="50"
              required
              class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal"
            >
          </label>
          <label class="text-xs font-semibold text-text-main">
            모델명
            <input
              v-model="modelRequestForm.modelName"
              aria-label="모델명 수정"
              maxlength="100"
              required
              class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal"
            >
          </label>
          <label class="text-xs font-semibold text-text-main">
            모델 코드
            <input
              v-model="modelRequestForm.modelCode"
              aria-label="모델 코드 수정"
              maxlength="50"
              class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal"
            >
          </label>
          <label class="text-xs font-semibold text-text-main">
            운영체제
            <select
              v-model="modelRequestForm.osFamily"
              aria-label="운영체제 수정"
              class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal"
            >
              <option value="ANDROID">Android</option>
              <option value="IOS">iOS</option>
              <option value="WINDOWS">Windows</option>
              <option value="MACOS">macOS</option>
              <option value="LINUX">Linux</option>
            </select>
          </label>
          <div class="flex gap-2 sm:col-span-2">
            <BaseButton
              type="submit"
              :disabled="isSavingModelRequest"
            >
              {{ isSavingModelRequest ? '저장 중…' : '수정 저장' }}
            </BaseButton>
            <BaseButton
              type="button"
              variant="secondary"
              :disabled="isSavingModelRequest"
              @click="cancelEditingModelRequest"
            >
              취소
            </BaseButton>
          </div>
        </form>
        <div
          v-else
          class="mt-5 flex gap-2"
        >
          <BaseButton
            type="button"
            variant="secondary"
            @click="startEditingModelRequest(request)"
          >
            수정
          </BaseButton>
          <BaseButton
            type="button"
            @click="approveModelRequest(request)"
          >
            사후 검토 완료
          </BaseButton>
          <BaseButton
            type="button"
            variant="secondary"
            @click="rejectModelRequest(request)"
          >
            반려
          </BaseButton>
        </div>
      </BaseCard>
      <BaseCard v-if="!deviceModelRequests.length">
        <p class="py-8 text-center text-sm text-text-sub">
          검토 대기 중인 모델 요청이 없습니다.
        </p>
      </BaseCard>
    </section>

    <section
      v-else-if="activeSection === 'checklist-researches'"
      class="space-y-5"
    >
      <BaseCard>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="font-bold">
            체크리스트 AI 조사 관리
          </h2>
          <div
            class="flex gap-2"
            aria-label="AI 조사 상태 필터"
          >
            <BaseButton
              type="button"
              :variant="checklistResearchStatus === 'PENDING_REVIEW' ? 'primary' : 'outline'"
              :disabled="isLoading || Boolean(retryingResearchId)"
              @click="selectChecklistResearchStatus('PENDING_REVIEW')"
            >
              검토 대기
            </BaseButton>
            <BaseButton
              type="button"
              :variant="checklistResearchStatus === 'FAILED' ? 'primary' : 'outline'"
              :disabled="isLoading || Boolean(retryingResearchId)"
              @click="selectChecklistResearchStatus('FAILED')"
            >
              조사 실패
            </BaseButton>
          </div>
        </div>
        <p class="mt-2 text-xs leading-5 text-text-sub">
          <template v-if="checklistResearchStatus === 'PENDING_REVIEW'">
            조사 후보는 판매자가 선택해서 매물별로 적용합니다. 이 화면의 승인은
            전역 기본 템플릿을 바꾸지 않고 사후 검토 이력만 남깁니다.
          </template>
          <template v-else>
            AI 요청 실패 사유를 확인하고 모델별로 재조사할 수 있습니다.
            재조사가 성공하면 검토 대기 목록으로 이동합니다.
          </template>
        </p>
      </BaseCard>

      <BaseCard
        v-for="research in checklistResearches"
        :key="research.researchId"
      >
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-primary">
              {{ research.deviceType }} · #{{ research.deviceModelId }}
            </p>
            <h3 class="mt-1 text-lg font-bold">
              {{ research.manufacturer }} {{ research.modelName }}
            </h3>
            <p class="mt-1 text-xs text-text-sub">
              조사 버전 {{ research.researchVersion }} · {{ formatDate(research.createdAt) }}
            </p>
          </div>
          <BaseBadge :variant="researchBadgeVariant(research.status)">
            {{ researchStatusLabel(research.status) }}
          </BaseBadge>
        </div>

        <div
          v-if="research.status === 'FAILED'"
          class="mt-5 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
        >
          <p class="font-semibold">
            {{ research.failureMessage || 'AI 조사 결과를 사용할 수 없습니다.' }}
          </p>
          <p
            v-if="research.failureCode"
            class="mt-1 text-xs"
          >
            오류 코드: {{ research.failureCode }}
          </p>
        </div>

        <ul
          v-if="research.suggestions.length"
          class="mt-5 grid gap-3 md:grid-cols-2"
        >
          <li
            v-for="suggestion in research.suggestions"
            :key="suggestion.featureCode"
            class="rounded-md border border-border bg-bg p-4"
          >
            <strong class="text-sm">{{ suggestion.featureName || suggestion.featureCode }}</strong>
            <p class="mt-2 text-xs leading-5 text-text-sub">
              {{ suggestion.reason }}
            </p>
            <a
              :href="suggestion.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="mt-2 inline-block text-xs font-semibold text-primary underline"
            >
              {{ suggestion.sourceTitle || '공식 자료 확인' }}
            </a>
          </li>
        </ul>
        <p
          v-if="research.reviewCandidates.length"
          class="mt-4 rounded-md bg-amber-50 px-4 py-3 text-xs text-amber-800"
        >
          미지원 기능 후보: {{ research.reviewCandidates.join(', ') }}
        </p>
        <div
          v-if="research.status === 'PENDING_REVIEW'"
          class="mt-5 flex gap-2"
        >
          <BaseButton
            type="button"
            @click="approveResearch(research)"
          >
            조사 결과 검토 완료
          </BaseButton>
          <BaseButton
            type="button"
            variant="secondary"
            @click="rejectResearch(research)"
          >
            반려
          </BaseButton>
        </div>
        <div
          v-else-if="research.status === 'FAILED'"
          class="mt-5"
        >
          <BaseButton
            type="button"
            :disabled="Boolean(retryingResearchId)"
            @click="retryResearch(research)"
          >
            {{ retryingResearchId === research.researchId ? 'AI 재조사 중…' : 'AI 재조사' }}
          </BaseButton>
        </div>
      </BaseCard>

      <BaseCard v-if="!checklistResearches.length">
        <p class="py-8 text-center text-sm text-text-sub">
          {{
            checklistResearchStatus === 'PENDING_REVIEW'
              ? '검토 대기 중인 모델 조사가 없습니다.'
              : '실패한 모델 조사가 없습니다.'
          }}
        </p>
      </BaseCard>
    </section>

    <section
      v-else-if="activeSection === 'logs'"
      class="space-y-5"
    >
      <BaseCard
        :padded="false"
        class="overflow-hidden"
      >
        <div class="border-b border-border px-6 py-5">
          <h2 class="font-bold">
            감사 가능한 관리자 작업 기록
          </h2><p class="mt-1 text-xs text-text-sub">
            총 {{ logsPage.totalElements }}건 · 작업명을 누르면 상세 내용을 확인할 수 있습니다.
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
                :class="selectedActionLog?.adminActionLogId === log.adminActionLogId ? 'bg-accent/50' : ''"
              >
                <td class="px-5 py-4 text-text-sub">
                  {{ formatDate(log.createdAt) }}
                </td><td class="px-5 py-4">
                  #{{ log.adminId }}
                </td><td class="px-5 py-4 font-semibold">
                  <button
                    type="button"
                    class="text-left text-primary underline-offset-2 hover:underline"
                    @click="selectActionLog(log)"
                  >
                    {{ log.actionType }}
                  </button>
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

      <BaseCard v-if="isLoadingActionLog">
        <p
          role="status"
          class="py-6 text-center text-sm text-text-sub"
        >
          작업 로그 상세 정보를 불러오는 중…
        </p>
      </BaseCard>

      <BaseCard
        v-else-if="selectedActionLog"
        aria-label="관리자 작업 로그 상세"
      >
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-primary">
              작업 로그 #{{ selectedActionLog.adminActionLogId }}
            </p>
            <h3 class="mt-1 text-lg font-bold">
              {{ selectedActionLog.actionType }}
            </h3>
          </div>
          <BaseButton
            v-if="!isEditingActionLog"
            type="button"
            variant="secondary"
            @click="startEditingActionLog"
          >
            수정
          </BaseButton>
        </div>

        <dl class="mt-5 grid gap-4 rounded-md border border-border bg-bg p-4 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-xs font-semibold text-text-sub">
              작업 일시
            </dt>
            <dd class="mt-1">
              {{ formatDate(selectedActionLog.createdAt) }}
            </dd>
          </div>
          <div>
            <dt class="text-xs font-semibold text-text-sub">
              관리자
            </dt>
            <dd class="mt-1">
              #{{ selectedActionLog.adminId }}
            </dd>
          </div>
          <div>
            <dt class="text-xs font-semibold text-text-sub">
              대상
            </dt>
            <dd class="mt-1">
              {{ selectedActionLog.targetType }} #{{ selectedActionLog.targetId }}
            </dd>
          </div>
          <div>
            <dt class="text-xs font-semibold text-text-sub">
              접속 IP
            </dt>
            <dd class="mt-1">
              {{ selectedActionLog.ipAddress || '-' }}
            </dd>
          </div>
        </dl>

        <form
          v-if="isEditingActionLog"
          class="mt-5"
          @submit.prevent="saveActionLog"
        >
          <label class="text-sm font-semibold text-text-main">
            작업 사유
            <textarea
              v-model="actionLogReason"
              aria-label="작업 로그 사유 수정"
              maxlength="500"
              class="mt-2 min-h-28 w-full rounded-md border border-border bg-white px-3 py-2 font-normal"
              placeholder="작업 사유를 입력해 주세요."
            />
          </label>
          <p class="mt-2 text-xs leading-5 text-text-sub">
            작업 종류·대상·발생 시각은 감사 무결성을 위해 변경할 수 없습니다.
            사유 수정 전후 값은 별도의 작업 로그로 남습니다.
          </p>
          <div class="mt-4 flex gap-2">
            <BaseButton
              type="submit"
              :disabled="isSavingActionLog"
            >
              {{ isSavingActionLog ? '저장 중…' : '수정 저장' }}
            </BaseButton>
            <BaseButton
              type="button"
              variant="secondary"
              :disabled="isSavingActionLog"
              @click="cancelEditingActionLog"
            >
              취소
            </BaseButton>
          </div>
        </form>
        <div
          v-else
          class="mt-5"
        >
          <h4 class="text-sm font-semibold">
            작업 사유
          </h4>
          <p class="mt-2 whitespace-pre-wrap rounded-md bg-bg px-4 py-3 text-sm text-text-sub">
            {{ selectedActionLog.reason || '-' }}
          </p>
        </div>

        <div class="mt-5 grid gap-4 lg:grid-cols-2">
          <div>
            <h4 class="text-sm font-semibold">
              변경 전 데이터
            </h4>
            <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-md bg-slate-950 p-4 text-xs text-slate-100">{{ formatActionLogData(selectedActionLog.beforeData) }}</pre>
          </div>
          <div>
            <h4 class="text-sm font-semibold">
              변경 후 데이터
            </h4>
            <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-md bg-slate-950 p-4 text-xs text-slate-100">{{ formatActionLogData(selectedActionLog.afterData) }}</pre>
          </div>
        </div>
      </BaseCard>
    </section>

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
