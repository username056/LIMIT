import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import LoginPage from '../pages/LoginPage.vue'
import SellerDashboardPage from '../pages/SellerDashboardPage.vue'
import MyOrdersPage from '../pages/MyOrdersPage.vue'
import SellerApplyPage from '../pages/SellerApplyPage.vue'
import ProductManagePage from '../pages/ProductManagePage.vue'
import AdminPage from '../pages/AdminPage.vue'

const routes = [
  { path: '/', name: 'home', component: HomePage },
  { path: '/login', name: 'login', component: LoginPage },
  { path: '/seller/dashboard', name: 'seller-dashboard', component: SellerDashboardPage },
  { path: '/mypage/orders', name: 'my-orders', component: MyOrdersPage },
  { path: '/seller/apply', name: 'seller-apply', component: SellerApplyPage },
  { path: '/seller/products', name: 'seller-products', component: ProductManagePage },
  { path: '/admin', name: 'admin', component: AdminPage },

  // 각자 담당 페이지는 여기에 이렇게 추가하면 됩니다:
  // { path: '/checkout', name: 'checkout', component: () => import('../pages/CheckoutPage.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
