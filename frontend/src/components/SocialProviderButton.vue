<script setup>
import googleLogin from '../assets/social/google_login.png'
import kakaoLogin from '../assets/social/kakao_login.png'
import naverLogin from '../assets/social/naver_login.png'

/*
  소셜 로그인 버튼.
  ---------------------------------------------------------------------------
  세 회사가 배포하는 공식 버튼 이미지를 그대로 씁니다. 색·글자·로고가 규정으로 정해져
  있어 직접 그리면 심사에서 지적을 받을 수 있습니다. 예전에는 로고 자리에 'G'·'K'·'N'
  글자를 흰 동그라미에 넣어 두었습니다.

  파일 원본 크기가 다릅니다(google 180x40, kakao 366x90, naver 840x192). 폭을 억지로
  맞추면 로고가 늘어나 규정을 벗어나므로, 높이만 44px로 맞추고 폭은 비율대로 둡니다.
  세 파일의 가로세로 비가 4.07~4.50으로 비슷해서 폭도 195~216px로 모여, 세로로 쌓아도
  들쭉날쭉해 보이지 않습니다.

  글자가 이미지 안에 있으므로 alt로 읽어 줍니다. 넣지 않으면 화면 읽어 주는 프로그램에는
  버튼이 비어 있는 것으로 들립니다.
*/
defineProps({
  provider: { type: String, required: true },
  loading: { type: Boolean, default: false },
})
defineEmits(['click'])

const PROVIDERS = {
  google: { label: 'Google 계정으로 로그인', image: googleLogin },
  naver: { label: '네이버 로그인', image: naverLogin },
  kakao: { label: '카카오 로그인', image: kakaoLogin },
}
</script>

<template>
  <button
    type="button"
    class="flex w-full items-center justify-center rounded-md transition hover:-translate-y-0.5 hover:brightness-105 disabled:cursor-wait disabled:opacity-60"
    :disabled="loading"
    @click="$emit('click')"
  >
    <img
      :src="PROVIDERS[provider].image"
      :alt="loading ? `${PROVIDERS[provider].label} 연결 중` : PROVIDERS[provider].label"
      class="h-11 w-auto"
    >
  </button>
</template>
