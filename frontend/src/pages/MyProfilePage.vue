<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseAddressInput from '../components/BaseAddressInput.vue'
import ProfileAvatar from '../components/ProfileAvatar.vue'
import {
  changeMyPassword,
  completeProfileImage,
  createProfileImageUploadUrl,
  deleteProfileImage,
  getMyProfile,
  updateMyProfile,
} from '../api/member'
import { uploadToPresignedUrl } from '../api/products'
import { compressImage } from '../utils/mediaOptimize'
import { getSocialAccounts, unlinkSocialAccount } from '../api/auth'
import { startOAuthLink } from '../auth/oauth'
import { clearAuthSession, getAccessToken, setSessionProfileImage } from '../auth/session'
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

/*
  프로필 사진 올리기.
  ---------------------------------------------------------------------------
  상품 사진과 같은 세 단계입니다 — 자리 요청 → 그 주소로 직접 PUT → 완료 통보.
  올리기 전에 Canvas로 줄입니다. 요즘 휴대폰 사진은 한 장이 5MB를 쉽게 넘는데,
  프로필은 작게 보여 주는 그림이라 원본을 그대로 올릴 이유가 없습니다.
*/
const MAX_PROFILE_IMAGE_BYTES = 5 * 1024 * 1024
const isSavingProfileImage = ref(false)
const profileImageError = ref('')

async function onProfileImageInput(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || isSavingProfileImage.value) return

  isSavingProfileImage.value = true
  profileImageError.value = ''
  try {
    const optimized = await compressImage(file)
    if (optimized.size > MAX_PROFILE_IMAGE_BYTES) {
      profileImageError.value = '5MB 이하 사진만 올릴 수 있습니다. 더 작은 사진을 골라 주세요.'
      return
    }
    const upload = await createProfileImageUploadUrl({
      contentType: optimized.type || 'image/jpeg',
      fileSize: optimized.size,
    })
    await uploadToPresignedUrl(upload.presignedUrl, optimized, upload.requiredHeaders || {})
    const result = await completeProfileImage(upload.objectKey)
    profile.value = { ...profile.value, profileImageUrl: result.profileImageUrl }
    setSessionProfileImage(result.profileImageUrl)
    profileMessage.value = '프로필 사진을 변경했습니다.'
  } catch (error) {
    profileImageError.value = error.message || '프로필 사진을 올리지 못했습니다.'
  } finally {
    isSavingProfileImage.value = false
  }
}

async function removeProfileImage() {
  if (isSavingProfileImage.value) return
  if (!window.confirm('프로필 사진을 삭제할까요?')) return

  isSavingProfileImage.value = true
  profileImageError.value = ''
  try {
    await deleteProfileImage()
    profile.value = { ...profile.value, profileImageUrl: null }
    setSessionProfileImage(null)
    profileMessage.value = '프로필 사진을 삭제했습니다.'
  } catch (error) {
    profileImageError.value = error.message || '프로필 사진을 삭제하지 못했습니다.'
  } finally {
    isSavingProfileImage.value = false
  }
}
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
  if (!window.confirm('이 배송지를 삭제할까요?')) return
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
    <PageHeader
      eyebrow="MY PAGE"
      title="내 정보"
      description="로그인한 회원에게만 표시되는 페이지입니다."
    />

    <p
      v-if="isLoading"
      class="card-soft rounded-lg bg-surface p-10 text-center text-sm text-text-sub"
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
      <!--
        내용을 상자 여러 개로 늘어놓지 않고 한 판 안에 담습니다. 구분은 가로선으로만 합니다.
        카드 안에 또 카드를 넣으면 테두리가 겹쳐 답답해집니다.
      -->
      <BaseCard
        :padded="false"
        class="mb-6 divide-y divide-border"
      >
        <section class="p-6">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="flex items-center gap-4">
              <!--
                사진을 눌러도 바꿀 수 있게 파일 입력을 사진 위에 덮어 둡니다.
                아래 '사진 변경' 글자만 두면 사진 자체는 눌러도 반응이 없어, 눌러 본
                사람은 고장난 줄 압니다.
              -->
              <label
                class="relative shrink-0"
                :class="isSavingProfileImage ? 'cursor-wait opacity-60' : 'cursor-pointer'"
              >
                <ProfileAvatar
                  :src="profile.profileImageUrl"
                  :name="profile.nickname"
                  size-class="h-14 w-14"
                  text-class="text-lg"
                />
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  class="sr-only"
                  :disabled="isSavingProfileImage"
                  aria-label="프로필 사진 변경"
                  @change="onProfileImageInput"
                >
              </label>
              <div>
                <h2 class="text-lg font-bold text-text-main">
                  {{ profile.nickname }}
                </h2>
                <p class="text-sm text-text-sub">
                  {{ profile.email }}
                </p>
                <div class="mt-1.5 flex items-center gap-2 text-[13px]">
                  <label
                    class="font-semibold text-primary hover:underline"
                    :class="isSavingProfileImage ? 'cursor-wait opacity-60' : 'cursor-pointer'"
                  >
                    {{ isSavingProfileImage ? '사진 올리는 중…' : '사진 변경' }}
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      class="sr-only"
                      :disabled="isSavingProfileImage"
                      @change="onProfileImageInput"
                    >
                  </label>
                  <template v-if="profile.profileImageUrl">
                    <span
                      class="text-border"
                      aria-hidden="true"
                    >|</span>
                    <button
                      type="button"
                      class="text-text-sub hover:text-red-600"
                      :disabled="isSavingProfileImage"
                      @click="removeProfileImage"
                    >
                      사진 삭제
                    </button>
                  </template>
                </div>
                <p
                  v-if="profileImageError"
                  role="alert"
                  class="mt-1 text-xs text-red-600"
                >
                  {{ profileImageError }}
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
          <!-- 회색 판을 깔지 않습니다. 카드 안에 또 상자가 생겨 겹쳐 보입니다. -->
          <dl class="mt-4 grid gap-x-8 gap-y-5 text-sm sm:grid-cols-2">
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
        </section>
        <!-- 숫자 셋은 테두리 없이 나란히만 둡니다. -->
        <section class="grid gap-4 p-6 sm:grid-cols-3">
          <div
            v-for="stat in stats"
            :key="stat.label"
          >
            <p class="text-[13px] font-semibold text-text-sub">
              {{ stat.label }}
            </p>
            <p class="mt-2 text-2xl font-bold text-text-main">
              {{ stat.value }}
            </p>
          </div>
        </section>


        <section
          id="address-section"
          class="p-6"
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

          <!--
            배송지 하나하나에 테두리를 두르면 카드 안에 또 상자가 늘어섭니다.
            여러 건이 나열되는 자리라 구분은 필요하니, 선 대신 옅은 바탕으로만 나눕니다.
          -->
          <ul
            v-else
            class="mt-4 space-y-3"
          >
            <li
              v-for="address in addressBook"
              :key="address.id"
              class="rounded-lg bg-bg p-4"
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
        </section>

        <section class="p-6">
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
            class="mt-4 space-y-5"
          >
            <div
              v-for="provider in socialProviders"
              :key="provider.id"
              class="flex items-center justify-between gap-4"
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
        </section>

        <section class="p-6">
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
        </section>
      </BaseCard>

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
