import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import LoginPage from '../pages/LoginPage.vue'
import OAuthCallbackPage from '../pages/OAuthCallbackPage.vue'
import SellerDashboardPage from '../pages/SellerDashboardPage.vue'
import MyOrdersPage from '../pages/MyOrdersPage.vue'
import SellerApplyPage from '../pages/SellerApplyPage.vue'
import ProductManagePage from '../pages/ProductManagePage.vue'
import AdminPage from '../pages/AdminPage.vue'
import DevToolsPage from '../pages/DevToolsPage.vue'
import SignupPage from '../pages/SignupPage.vue'
import SocialSignupPage from '../pages/SocialSignupPage.vue'
import VerifyEmailPage from '../pages/VerifyEmailPage.vue'
import EmailVerificationRequestedPage from '../pages/EmailVerificationRequestedPage.vue'
import ForgotPasswordPage from '../pages/ForgotPasswordPage.vue'
import ResetPasswordPage from '../pages/ResetPasswordPage.vue'
import TermsPage from '../pages/TermsPage.vue'
import PrivacyPage from '../pages/PrivacyPage.vue'
import ComingSoonPage from '../pages/ComingSoonPage.vue'
import MyFavoritesPage from '../pages/MyFavoritesPage.vue'
import MyProfilePage from '../pages/MyProfilePage.vue'
import PurchasePage from '../pages/PurchasePage.vue'
import PurchaseSuccessPage from '../pages/PurchaseSuccessPage.vue'
import ChatPage from '../pages/ChatPage.vue'
import ProductListPage from '../pages/ProductListPage.vue'
import ProductDetailPage from '../pages/ProductDetailPage.vue'
import CallsPage from '../pages/CallsPage.vue'
import RtcCallPage from '../pages/RtcCallPage.vue'
import { getAccessToken } from '../auth/session'

const routes = [
  { path: '/', name: 'home', component: HomePage },
  { path: '/products', name: 'products', component: ProductListPage },
  { path: '/products/:productId', name: 'product-detail', component: ProductDetailPage },
  { path: '/calls', name: 'calls', component: CallsPage, meta: { requiresAuth: true } },
  { path: '/calls/:callId/session', name: 'rtc-call', component: RtcCallPage, meta: { requiresAuth: true } },
  { path: '/purchase/:productId', name: 'purchase', component: PurchasePage },
  { path: '/purchase/:productId/success', name: 'purchase-success', component: PurchaseSuccessPage },
  { path: '/chat/:roomId?', name: 'chat', component: ChatPage, meta: { requiresAuth: true } },
  { path: '/login', name: 'login', component: LoginPage },
  { path: '/signup', name: 'signup', component: SignupPage },
  { path: '/signup/social', name: 'social-signup', component: SocialSignupPage },
  { path: '/verify-email', name: 'verify-email', component: VerifyEmailPage },
  { path: '/verify-email/requested', name: 'verify-email-requested', component: EmailVerificationRequestedPage },
  { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordPage },
  { path: '/reset-password', name: 'reset-password', component: ResetPasswordPage },
  {
    path: '/mypage/profile',
    name: 'my-profile',
    component: MyProfilePage,
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/favorites',
    name: 'my-favorites',
    component: MyFavoritesPage,
    meta: { requiresAuth: true },
  },
  { path: '/auth/callback/:provider', name: 'oauth-callback', component: OAuthCallbackPage },
  { path: '/terms/service', name: 'terms-service', component: TermsPage },
  { path: '/terms/privacy', name: 'terms-privacy', component: PrivacyPage },
  { path: '/seller/dashboard', name: 'seller-dashboard', component: SellerDashboardPage },
  {
    path: '/mypage/orders',
    name: 'my-orders',
    component: MyOrdersPage,
    meta: { requiresAuth: true },
  },
  { path: '/seller/apply', name: 'seller-apply', component: SellerApplyPage },
  { path: '/seller/products', name: 'seller-products', component: ProductManagePage, meta: { requiresAuth: true } },
  { path: '/admin', name: 'admin', component: AdminPage },
  { path: '/dev-tools', name: 'dev-tools', component: DevToolsPage },
  { path: '/coming-soon/:feature', name: 'coming-soon', component: ComingSoonPage },
  { path: '/:pathMatch(.*)*', redirect: '/coming-soon/not-found' },

  // 각자 담당 페이지는 여기에 이렇게 추가하면 됩니다:
  // { path: '/wishlist', name: 'wishlist', component: () => import('../pages/WishlistPage.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth || getAccessToken()) return true
  return {
    name: 'login',
    query: { redirect: to.fullPath },
  }
})

export default router
