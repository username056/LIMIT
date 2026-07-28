import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreAuthSession } from './auth/session'
import { initializeSentry } from './monitoring/sentry'
import './index.css'

const app = createApp(App)

async function bootstrap() {
  await restoreAuthSession()
  app.use(router)
  initializeSentry(app)
  await router.isReady()
  app.mount('#app')
}

bootstrap()
