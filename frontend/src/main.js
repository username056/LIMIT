import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreAuthSession } from './auth/session'
import { initializeSentry } from './monitoring/sentry'
import './index.css'

const app = createApp(App)

app.use(router)
initializeSentry(app)

restoreAuthSession().finally(() => app.mount('#app'))
