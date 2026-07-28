<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseToggle from '../components/BaseToggle.vue'
import BaseAddressInput from '../components/BaseAddressInput.vue'
import { changeMyPassword, getMyProfile, updateMyProfile } from '../api/member'
import { getSocialAccounts, unlinkSocialAccount } from '../api/auth'
import { startOAuthLink } from '../auth/oauth'
import { clearAuthSession, getAccessToken } from '../auth/session'
import { formatAddress } from '../utils/daumPostcode'
import { addressBook } from '../stores/addressBook'

const route = useRoute()
const router = useRouter()
const profile = ref(null)
const isLoading = ref(true)
const isSaving = ref(false)
const isChangingPassword = ref(false)
const errorMessage = ref('')
const profileMessage = ref('')
const passwordError = ref('')
const isEditingProfile = ref(false)
const isPasswordSectionOpen = ref(false)
const withdrawalStep = ref(null) // null | 'confirm' | 'done'

const profileForm = reactive({ nickname: '', phone: '' })
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  newPasswordConfirm: '',
})

const isLocalMember = computed(() => profile.value?.authType === 'LOCAL')
const sellerStatusLabel = computed(() => ({
  ACTIVE: '활성 판매자',
  RESTRICTED: '판매자 이용 제한',
  SUSPENDED: '판매자 이용 정지',
  BANNED: '판매자 이용 차단',
  WITHDRAWN: '판매자 탈퇴',
}[profile.value?.sellerStatus] || '판매자 미등록'))

const socialAccounts = ref([])
const isSocialLoading = ref(true)
const activeSocialProvider = ref('')
const socialMessage = ref(route.query.linked ? '소셜 계정이 연결되었습니다.' : '')
const socialErrorMessage = ref('')
const socialProviders = [
  { id: 'google', label: 'Google' },
  { id: 'kakao', label: '카카오' },
  { id: 'naver', label: '네이버' },
]

function socialAccountFor(provider) {
  return socialAccounts.value.find((account) => account.provider.toLowerCase() === provider)
}

async function loadSocialAccounts() {
  isSocialLoading.value = true
  socialErrorMessage.value = ''
  try {
    socialAccounts.value = await getSocialAccounts()
  } catch (error) {
    socialErrorMessage.value = error.message || '연결된 계정을 불러오지 못했습니다.'
  } finally {
    isSocialLoading.value = false
  }
}

async function linkSocialAccount(provider) {
  activeSocialProvider.value = provider
  socialErrorMessage.value = ''
  try {
    await startOAuthLink(provider)
  } catch (error) {
    socialErrorMessage.value = error.message
    activeSocialProvider.value = ''
  }
}

async function unlinkSocialAccountFor(account) {
  if (!window.confirm(`${account.provider} 계정 연결을 해제할까요?`)) return
  activeSocialProvider.value = account.provider.toLowerCase()
  socialErrorMessage.value = ''
  try {
    await unlinkSocialAccount(account.socialAccountId)
    socialAccounts.value = socialAccounts.value.filter(
      (item) => item.socialAccountId !== account.socialAccountId,
    )
    socialMessage.value = '소셜 계정 연결을 해제했습니다.'
  } catch (error) {
    socialErrorMessage.value = error.message || '연결을 해제하지 못했습니다.'
  } finally {
    activeSocialProvider.value = ''
  }
}

// 거래·정산 API가 아직 없어 대시보드 요약은 예시 데이터로 표시합니다.
const stats = [
  { label: '구매 완료 건수', value: '12건' },
  { label: '판매 완료 건수', value: '3건' },
  { label: '진행 중인 안심거래', value: '2건' },
]

// 배송지 API가 아직 없어 등록/수정/삭제는 세션 메모리(addressBook 스토어)에서만 동작합니다.
const isAddressFormOpen = ref(false)
const addressForm = reactive({
  id: null,
  label: '',
  address: { zonecode: '', address: '', addressDetail: '' },
  receiverName: '',
  receiverPhone: '',
  isDefault: false,
})

// 알림 발송 API가 아직 없어 토글 상태는 화면 안에서만 유지되는 예시 값입니다.
const notificationSettings = reactive({
  priceAlert: true,
  inspectionAlert: true,
  rtcAlert: false,
})
const notificationItems = [
  {
    key: 'priceAlert',
    label: '관심 상품 가격 변동 알림',
    description: '위시리스트에 담은 상품의 가격이 낮아지면 즉시 알림을 발송합니다.',
  },
  {
    key: 'inspectionAlert',
    label: '자가 기기 검수 완료 소식',
    description: '새로운 전문 체크리스트가 등록되거나 업데이트 되었을 때 알려드립니다.',
  },
  {
    key: 'rtcAlert',
    label: '실시간 WebRTC 라이브 화상 거래 검증 안내',
    description: '판매자와 합의된 실시간 화상 검증 세션 예정 일정을 전송합니다.',
  },
]

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

async function loadProfile() {
  if (!getAccessToken()) {
    await router.replace({ name: 'login', query: { redirect: '/mypage/profile' } })
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  try {
    profile.value = await getMyProfile()
    profileForm.nickname = profile.value.nickname
    profileForm.phone = ''
  } catch (error) {
    errorMessage.value = error.message || '회원 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function openProfileEdit() {
  profileForm.nickname = profile.value.nickname
  profileForm.phone = ''
  profileMessage.value = ''
  isEditingProfile.value = true
}

async function saveProfile() {
  if (!profile.value || isSaving.value) return
  profileMessage.value = ''
  errorMessage.value = ''

  const payload = {}
  const nickname = profileForm.nickname.trim()
  const phone = profileForm.phone.replaceAll('-', '').trim()
  if (nickname && nickname !== profile.value.nickname) payload.nickname = nickname
  if (phone) payload.phone = phone

  if (!Object.keys(payload).length) {
    profileMessage.value = '변경할 정보를 입력해 주세요.'
    return
  }

  isSaving.value = true
  try {
    const updated = await updateMyProfile(payload)
    profile.value = { ...profile.value, ...updated }
    profileForm.phone = ''
    profileMessage.value = '회원 정보를 수정했습니다.'
    isEditingProfile.value = false
  } catch (error) {
    errorMessage.value = error.message || '회원 정보를 수정하지 못했습니다.'
  } finally {
    isSaving.value = false
  }
}

async function submitPasswordChange() {
  if (isChangingPassword.value) return
  passwordError.value = ''

  if (passwordForm.newPassword !== passwordForm.newPasswordConfirm) {
    passwordError.value = '새 비밀번호 확인이 일치하지 않습니다.'
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(passwordForm.newPassword)) {
    passwordError.value = '새 비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다.'
    return
  }

  isChangingPassword.value = true
  try {
    await changeMyPassword(passwordForm.currentPassword, passwordForm.newPassword)
    clearAuthSession()
    await router.replace({ name: 'login', query: { passwordChanged: '1' } })
  } catch (error) {
    passwordError.value = error.message || '비밀번호를 변경하지 못했습니다.'
  } finally {
    isChangingPassword.value = false
  }
}

function openWithdrawalConfirm() {
  withdrawalStep.value = 'confirm'
}

function cancelWithdrawal() {
  withdrawalStep.value = null
}

// 탈퇴 처리 API가 아직 없어 완료 화면만 보여준 뒤 로그아웃으로 대체합니다.
async function confirmWithdrawal() {
  withdrawalStep.value = 'done'
  await new Promise((resolve) => setTimeout(resolve, 1200))
  clearAuthSession()
  await router.replace({ name: 'home' })
}

function resetAddressForm() {
  addressForm.id = null
  addressForm.label = ''
  addressForm.address = { zonecode: '', address: '', addressDetail: '' }
  addressForm.receiverName = ''
  addressForm.receiverPhone = ''
  addressForm.isDefault = addressBook.value.length === 0
}

function openAddressForm(address = null) {
  if (address) {
    addressForm.id = address.id
    addressForm.label = address.label
    addressForm.address = {
      zonecode: address.zonecode || '',
      address: address.addressLine || address.address,
      addressDetail: address.addressDetail || '',
    }
    addressForm.receiverName = address.receiverName
    addressForm.receiverPhone = address.receiverPhone
    addressForm.isDefault = address.isDefault
  } else {
    resetAddressForm()
  }
  isAddressFormOpen.value = true
}

function saveAddress() {
  const combinedAddress = formatAddress(addressForm.address)
  if (!addressForm.label.trim() || !combinedAddress) return

  if (addressForm.isDefault) {
    addressBook.value.forEach((item) => { item.isDefault = false })
  }

  const entry = {
    id: addressForm.id || Date.now(),
    label: addressForm.label,
    address: combinedAddress,
    zonecode: addressForm.address.zonecode,
    addressLine: addressForm.address.address,
    addressDetail: addressForm.address.addressDetail,
    receiverName: addressForm.receiverName,
    receiverPhone: addressForm.receiverPhone,
    isDefault: addressForm.isDefault,
  }

  if (addressForm.id) {
    const target = addressBook.value.find((item) => item.id === addressForm.id)
    Object.assign(target, entry)
  } else {
    addressBook.value.push(entry)
  }

  isAddressFormOpen.value = false
}

function deleteAddress(id) {
  const wasDefault = addressBook.value.find((item) => item.id === id)?.isDefault
  addressBook.value = addressBook.value.filter((item) => item.id !== id)
  if (wasDefault && addressBook.value.length) addressBook.value[0].isDefault = true
}

onMounted(() => {
  loadProfile()
  loadSocialAccounts()
})
</script>

<template>
  <MyPageLayout>
    <div class="mb-6">
      <p class="text-[13px] font-semibold text-primary">
        MY LIMIT
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        마이페이지
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        로그인한 회원에게만 표시되는 페이지입니다.
      </p>
    </div>

    <p
      v-if="isLoading"
      class="rounded-lg border border-border bg-surface p-10 text-center text-sm text-text-sub"
    >
      회원 정보를 불러오고 있습니다...
    </p>

    <BaseCard
      v-else-if="errorMessage && !profile"
      class="py-12 text-center"
    >
      <p
        class="text-sm text-red-600"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <BaseButton
        variant="outline"
        class="mt-5"
        @click="loadProfile"
      >
        다시 시도
      </BaseButton>
    </BaseCard>

    <template v-else-if="profile">
      <section class="mb-6 grid gap-4 sm:grid-cols-3">
        <BaseCard
          v-for="stat in stats"
          :key="stat.label"
        >
          <p class="text-[13px] font-semibold text-text-sub">
            {{ stat.label }}
          </p>
          <p class="mt-3 text-2xl font-bold text-text-main">
            {{ stat.value }}
          </p>
        </BaseCard>
      </section>

      <BaseCard class="mb-6">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="flex items-center gap-4">
            <div class="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-primary-gradient text-lg font-bold text-white">
              {{ profile.nickname?.charAt(0) || '?' }}
            </div>
            <div>
              <h2 class="text-lg font-bold text-text-main">
                {{ profile.nickname }}
              </h2>
              <p class="text-sm text-text-sub">
                {{ profile.email }}
              </p>
            </div>
          </div>
          <BaseButton
            variant="outline"
            class="px-4 py-1.5 text-[13px]"
            @click="openProfileEdit"
          >
            수정
          </BaseButton>
        </div>

        <h3 class="mt-6 text-sm font-bold text-text-main">
          프로필 상세 정보
        </h3>
        <dl class="mt-4 grid gap-4 rounded-lg bg-bg p-5 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-[13px] text-text-sub">
              닉네임
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.nickname }}
            </dd>
          </div>
          <div>
            <dt class="text-[13px] text-text-sub">
              연락처
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.phone || '등록되지 않음' }}
            </dd>
          </div>
          <div>
            <dt class="text-[13px] text-text-sub">
              이메일 주소
            </dt>
            <dd class="mt-1 break-all font-semibold text-text-main">
              {{ profile.email }}
            </dd>
          </div>
          <div>
            <dt class="text-[13px] text-text-sub">
              가입 날짜
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ formatDate(profile.createdAt) }}
            </dd>
          </div>
          <div>
            <dt class="text-[13px] text-text-sub">
              계정 역할
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.roles?.join(', ') || 'MEMBER' }}
            </dd>
          </div>
          <div>
            <dt class="text-[13px] text-text-sub">
              판매자 상태
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ sellerStatusLabel }}
            </dd>
          </div>
        </dl>

        <p
          v-if="profileMessage"
          class="mt-4 rounded-md bg-accent px-4 py-3 text-sm text-primary"
          role="status"
        >
          {{ profileMessage }}
        </p>

        <form
          v-if="isEditingProfile"
          class="mt-6 space-y-4 border-t border-border pt-6"
          @submit.prevent="saveProfile"
        >
          <BaseInput
            v-model="profileForm.nickname"
            label="닉네임"
            required
            placeholder="2~20자"
          />
          <BaseInput
            v-model="profileForm.phone"
            label="새 휴대전화 번호 (선택)"
            type="tel"
            autocomplete="tel"
            :placeholder="profile.phone || '01012345678'"
          />
          <p
            v-if="errorMessage"
            class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
            role="alert"
          >
            {{ errorMessage }}
          </p>
          <div class="flex gap-3">
            <BaseButton
              type="submit"
              :disabled="isSaving"
            >
              {{ isSaving ? '저장 중...' : '변경 사항 저장' }}
            </BaseButton>
            <BaseButton
              type="button"
              variant="outline"
              @click="isEditingProfile = false"
            >
              취소
            </BaseButton>
          </div>
        </form>
      </BaseCard>

      <BaseCard
        id="address-section"
        class="mb-6"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-bold text-text-main">
            배송지 주소 관리
          </h2>
          <BaseButton
            variant="outline"
            class="px-3 py-1.5 text-[13px]"
            @click="openAddressForm()"
          >
            + 새 배송지 등록
          </BaseButton>
        </div>

        <p
          v-if="!addressBook.length"
          class="mt-4 rounded-md bg-bg px-4 py-4 text-sm text-text-sub"
        >
          등록된 배송지가 없습니다.
        </p>

        <ul
          v-else
          class="mt-4 space-y-3"
        >
          <li
            v-for="address in addressBook"
            :key="address.id"
            class="rounded-lg border border-border p-4"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="flex items-center gap-2 text-sm font-bold text-text-main">
                  {{ address.label }}
                  <BaseBadge v-if="address.isDefault">
                    기본
                  </BaseBadge>
                </p>
                <p class="mt-1 text-sm text-text-sub">
                  {{ address.address }}
                </p>
                <p class="mt-1 text-[13px] text-text-sub">
                  수령인: {{ address.receiverName }} · {{ address.receiverPhone }}
                </p>
              </div>
              <div class="flex shrink-0 gap-3 text-[13px] font-semibold">
                <button
                  type="button"
                  class="text-text-sub hover:text-primary"
                  @click="openAddressForm(address)"
                >
                  수정
                </button>
                <button
                  type="button"
                  class="text-red-600 hover:text-red-700"
                  @click="deleteAddress(address.id)"
                >
                  삭제
                </button>
              </div>
            </div>
          </li>
        </ul>

        <form
          v-if="isAddressFormOpen"
          class="mt-5 space-y-4 border-t border-border pt-5"
          @submit.prevent="saveAddress"
        >
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="addressForm.label"
              label="배송지 이름"
              required
              placeholder="우리집, 회사 등"
            />
            <BaseInput
              v-model="addressForm.receiverName"
              label="수령인"
              required
            />
            <BaseInput
              v-model="addressForm.receiverPhone"
              label="수령인 연락처"
              type="tel"
              required
              placeholder="01012345678"
            />
          </div>
          <BaseAddressInput
            v-model="addressForm.address"
            label="주소"
            required
          />
          <label class="flex items-center gap-2 text-sm text-text-sub">
            <input
              v-model="addressForm.isDefault"
              type="checkbox"
              class="h-4 w-4 rounded border-border"
            >
            기본 배송지로 설정
          </label>
          <div class="flex gap-3">
            <BaseButton type="submit">
              저장
            </BaseButton>
            <BaseButton
              type="button"
              variant="outline"
              @click="isAddressFormOpen = false"
            >
              취소
            </BaseButton>
          </div>
        </form>
      </BaseCard>

      <BaseCard
        id="notification-section"
        class="mb-6"
      >
        <h2 class="text-lg font-bold text-text-main">
          실시간 알림 및 마케팅 설정
        </h2>
        <ul class="mt-5 divide-y divide-border">
          <li
            v-for="item in notificationItems"
            :key="item.key"
            class="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0"
          >
            <div>
              <p class="text-sm font-bold text-text-main">
                {{ item.label }}
              </p>
              <p class="mt-1 text-[13px] text-text-sub">
                {{ item.description }}
              </p>
            </div>
            <BaseToggle v-model="notificationSettings[item.key]" />
          </li>
        </ul>
      </BaseCard>

      <BaseCard class="mb-6">
        <h2 class="text-lg font-bold text-text-main">
          소셜 계정 관리
        </h2>
        <p class="mt-2 text-[13px] leading-5 text-text-sub">
          로그인 수단을 연결하거나 더 이상 사용하지 않는 연결을 안전하게 해제할 수 있습니다.
        </p>
        <p
          v-if="socialMessage"
          class="mt-4 rounded-md bg-green-50 px-4 py-3 text-sm text-green-700"
          role="status"
        >
          {{ socialMessage }}
        </p>
        <p
          v-if="socialErrorMessage"
          class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
          role="alert"
        >
          {{ socialErrorMessage }}
        </p>
        <p
          v-if="isSocialLoading"
          class="mt-4 text-sm text-text-sub"
        >
          연결 정보를 불러오고 있습니다...
        </p>
        <div
          v-else
          class="mt-4 divide-y divide-border"
        >
          <div
            v-for="provider in socialProviders"
            :key="provider.id"
            class="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0"
          >
            <div>
              <p class="text-sm font-bold text-text-main">
                {{ provider.label }}
              </p>
              <p class="mt-1 text-[13px] text-text-sub">
                {{ socialAccountFor(provider.id)?.providerEmail || '연결되지 않음' }}
              </p>
            </div>
            <BaseButton
              v-if="socialAccountFor(provider.id)"
              variant="outline"
              class="px-4 py-1.5 text-[13px]"
              :disabled="Boolean(activeSocialProvider)"
              @click="unlinkSocialAccountFor(socialAccountFor(provider.id))"
            >
              연결 해제
            </BaseButton>
            <BaseButton
              v-else
              class="px-4 py-1.5 text-[13px]"
              :disabled="Boolean(activeSocialProvider)"
              @click="linkSocialAccount(provider.id)"
            >
              {{ activeSocialProvider === provider.id ? '연결 중...' : '연결' }}
            </BaseButton>
          </div>
        </div>
      </BaseCard>

      <div class="flex flex-wrap gap-4 text-[13px] font-semibold text-text-sub">
        <button
          type="button"
          class="hover:text-primary"
          @click="isPasswordSectionOpen = !isPasswordSectionOpen"
        >
          비밀번호 변경
        </button>
        <button
          type="button"
          class="text-red-600 hover:text-red-700"
          @click="openWithdrawalConfirm"
        >
          서비스 회원 탈퇴
        </button>
      </div>

      <div
        v-if="withdrawalStep"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      >
        <BaseCard class="w-full max-w-sm text-center">
          <template v-if="withdrawalStep === 'confirm'">
            <p class="text-base font-bold text-text-main">
              정말 탈퇴하시겠습니까?
            </p>
            <p class="mt-2 text-sm text-text-sub">
              탈퇴하면 계정 정보와 거래 내역에 다시 접근할 수 없습니다.
            </p>
            <div class="mt-6 flex justify-center gap-3">
              <BaseButton
                variant="outline"
                @click="cancelWithdrawal"
              >
                아니오
              </BaseButton>
              <BaseButton @click="confirmWithdrawal">
                네
              </BaseButton>
            </div>
          </template>
          <template v-else>
            <p class="text-base font-bold text-text-main">
              탈퇴가 완료되었습니다.
            </p>
            <p class="mt-2 text-sm text-text-sub">
              그동안 이용해 주셔서 감사합니다.
            </p>
          </template>
        </BaseCard>
      </div>

      <BaseCard
        v-if="isPasswordSectionOpen"
        id="password-section"
        class="mt-6"
      >
        <h2 class="text-lg font-bold text-text-main">
          비밀번호 변경
        </h2>
        <p class="mt-2 text-[13px] leading-5 text-text-sub">
          변경 후 모든 Refresh Token이 폐기되어 다시 로그인해야 합니다.
        </p>
        <form
          v-if="isLocalMember"
          class="mt-6 max-w-md space-y-4"
          @submit.prevent="submitPasswordChange"
        >
          <BaseInput
            v-model="passwordForm.currentPassword"
            label="현재 비밀번호"
            type="password"
            autocomplete="current-password"
            required
          />
          <BaseInput
            v-model="passwordForm.newPassword"
            label="새 비밀번호"
            type="password"
            autocomplete="new-password"
            required
            placeholder="영문·숫자 포함 8자 이상"
          />
          <BaseInput
            v-model="passwordForm.newPasswordConfirm"
            label="새 비밀번호 확인"
            type="password"
            autocomplete="new-password"
            required
          />
          <p
            v-if="passwordError"
            class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
            role="alert"
          >
            {{ passwordError }}
          </p>
          <BaseButton
            type="submit"
            :disabled="isChangingPassword"
          >
            {{ isChangingPassword ? '변경 중...' : '비밀번호 변경' }}
          </BaseButton>
        </form>
        <p
          v-else
          class="mt-6 rounded-md bg-bg px-4 py-4 text-sm leading-6 text-text-sub"
        >
          소셜 회원은 연결된 소셜 계정에서 비밀번호를 관리합니다.
        </p>
      </BaseCard>
    </template>
  </MyPageLayout>
</template>
