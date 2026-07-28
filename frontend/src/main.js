import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreAuthSession } from './auth/session'
import { initializeSentry } from './monitoring/sentry'
import './index.css'

const app = createApp(App)

initializeSentry(app)

// 라우터를 설치하면 Vue Router가 즉시 초기 내비게이션(및 requiresAuth 가드)을 실행하므로,
// 세션 복원이 끝나기 전에 라우터부터 설치하면 아직 null인 세션 기준으로 가드가 실행되어
// 유효한 세션이 있어도 로그인 페이지로 튕기는 경쟁 상태가 생깁니다. 세션 복원을 먼저 끝낸 뒤
// 라우터를 설치·마운트합니다.
restoreAuthSession().finally(() => {
  app.use(router)
  app.mount('#app')
})
