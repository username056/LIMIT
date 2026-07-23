import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreAuthSession } from './auth/session'
import './index.css'

restoreAuthSession().finally(() => createApp(App).use(router).mount('#app'))
