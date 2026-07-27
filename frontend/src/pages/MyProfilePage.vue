<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import { changeMyPassword, getMyProfile, updateMyProfile } from '../api/member'
import { clearAuthSession, getAccessToken } from '../auth/session'

const router = useRouter()
const profile = ref(null)
const isLoading = ref(true)
const isSaving = ref(false)
const isChangingPassword = ref(false)
const errorMessage = ref('')
const profileMessage = ref('')
const passwordError = ref('')

const profileForm = reactive({ nickname: '', phone: '' })
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  newPasswordConfirm: '',
})

const isLocalMember = computed(() => profile.value?.authType === 'LOCAL')

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
    profileForm.nickname = updated.nickname
    profileForm.phone = ''
    profileMessage.value = '회원 정보를 수정했습니다.'
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

onMounted(loadProfile)
</script>

<template>
  <MyPageLayout>
    <div class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p class="text-xs font-semibold text-primary">
          MY LIMIT
        </p>
        <h1 class="mt-2 text-2xl font-bold text-text-main">
          내 정보
        </h1>
        <p class="mt-2 text-sm text-text-sub">
          계정 정보와 로그인 보안을 한곳에서 관리하세요.
        </p>
      </div>
      <BaseBadge
        v-if="profile"
        :variant="profile.status === 'ACTIVE' ? 'success' : 'gray'"
      >
        {{ profile.status }}
      </BaseBadge>
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
        <BaseCard>
          <p class="text-xs font-semibold text-text-sub">
            주문 내역
          </p>
          <p class="mt-3 text-2xl font-bold text-text-main">
            3건
          </p>
          <BaseButton
            to="/mypage/orders"
            variant="ghost"
            class="mt-3 px-0 py-1"
          >
            확인하기 →
          </BaseButton>
        </BaseCard>
        <BaseCard>
          <p class="text-xs font-semibold text-text-sub">
            관심 상품
          </p>
          <p class="mt-3 text-2xl font-bold text-text-main">
            3개
          </p>
          <BaseButton
            to="/mypage/favorites"
            variant="ghost"
            class="mt-3 px-0 py-1"
          >
            확인하기 →
          </BaseButton>
        </BaseCard>
        <BaseCard>
          <p class="text-xs font-semibold text-text-sub">
            계정 유형
          </p>
          <p class="mt-3 text-2xl font-bold text-text-main">
            {{ profile.authType }}
          </p>
          <span class="mt-3 block text-xs text-text-sub">와이어프레임 목업 요약</span>
        </BaseCard>
      </section>

      <div class="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <div class="space-y-6">
          <BaseCard>
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold text-text-main">
                  기본 정보
                </h2>
                <p class="mt-1 text-xs text-text-sub">
                  이메일과 가입 정보는 안전을 위해 직접 변경할 수 없습니다.
                </p>
              </div>
              <BaseBadge variant="primary">
                {{ profile.roles.join(', ') }}
              </BaseBadge>
            </div>

            <dl class="mt-6 grid gap-4 rounded-lg bg-bg p-5 text-sm sm:grid-cols-2">
              <div>
                <dt class="text-xs text-text-sub">
                  이메일
                </dt>
                <dd class="mt-1 break-all font-semibold text-text-main">
                  {{ profile.email }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  현재 연락처
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  {{ profile.phone || '등록되지 않음' }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  가입일
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  {{ formatDate(profile.createdAt) }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  최근 로그인
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  {{ formatDate(profile.lastLoginAt) }}
                </dd>
              </div>
            </dl>

            <form
              class="mt-6 space-y-4"
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
                v-if="profileMessage"
                class="rounded-md bg-accent px-4 py-3 text-sm text-primary"
                role="status"
              >
                {{ profileMessage }}
              </p>
              <p
                v-if="errorMessage"
                class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
                role="alert"
              >
                {{ errorMessage }}
              </p>
              <BaseButton
                type="submit"
                :disabled="isSaving"
              >
                {{ isSaving ? '저장 중...' : '변경 사항 저장' }}
              </BaseButton>
            </form>
          </BaseCard>

          <BaseCard>
            <h2 class="text-lg font-bold text-text-main">
              소셜 계정
            </h2>
            <p class="mt-2 text-sm leading-6 text-text-sub">
              로그인 수단을 추가하거나 더 이상 사용하지 않는 소셜 계정 연결을 해제할 수 있습니다.
            </p>
            <BaseButton
              to="/mypage/social-accounts"
              variant="outline"
              class="mt-5"
            >
              소셜 계정 관리
            </BaseButton>
          </BaseCard>
        </div>

        <BaseCard class="h-fit">
          <h2 class="text-lg font-bold text-text-main">
            비밀번호 변경
          </h2>
          <p class="mt-2 text-xs leading-5 text-text-sub">
            변경 후 모든 Refresh Token이 폐기되어 다시 로그인해야 합니다.
          </p>
          <form
            v-if="isLocalMember"
            class="mt-6 space-y-4"
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
              block
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
      </div>
    </template>
  </MyPageLayout>
</template>
