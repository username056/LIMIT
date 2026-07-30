import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import { getAccessToken, hasRole } from '../auth/session'

const LoginPage = () => import('../pages/LoginPage.vue')
const OAuthCallbackPage = () => import('../pages/OAuthCallbackPage.vue')
const SellerDashboardPage = () => import('../pages/SellerDashboardPage.vue')
const MyOrdersPage = () => import('../pages/MyOrdersPage.vue')
const SellerApplyPage = () => import('../pages/SellerApplyPage.vue')
const ProductManagePage = () => import('../pages/ProductManagePage.vue')
const ProductRegisterPage = () => import('../pages/ProductRegisterPage.vue')
const DeviceCheckPage = () => import('../pages/DeviceCheckPage.vue')
const AdminPage = () => import('../pages/AdminPage.vue')
const DevToolsPage = () => import('../pages/DevToolsPage.vue')
const SignupPage = () => import('../pages/SignupPage.vue')
const SocialSignupPage = () => import('../pages/SocialSignupPage.vue')
const VerifyEmailPage = () => import('../pages/VerifyEmailPage.vue')
const EmailVerificationRequestedPage = () => import('../pages/EmailVerificationRequestedPage.vue')
const ForgotPasswordPage = () => import('../pages/ForgotPasswordPage.vue')
const ResetPasswordPage = () => import('../pages/ResetPasswordPage.vue')
const TermsPage = () => import('../pages/TermsPage.vue')
const PrivacyPage = () => import('../pages/PrivacyPage.vue')
const ComingSoonPage = () => import('../pages/ComingSoonPage.vue')
const NotFoundPage = () => import('../pages/NotFoundPage.vue')
const MyFavoritesPage = () => import('../pages/MyFavoritesPage.vue')
const MyProfilePage = () => import('../pages/MyProfilePage.vue')
const PurchasePage = () => import('../pages/PurchasePage.vue')
const PurchaseSuccessPage = () => import('../pages/PurchaseSuccessPage.vue')
const PurchaseFailPage = () => import('../pages/PurchaseFailPage.vue')
const ChatPage = () => import('../pages/ChatPage.vue')
const ProductListPage = () => import('../pages/ProductListPage.vue')
const ProductDetailPage = () => import('../pages/ProductDetailPage.vue')
const SellerProfilePage = () => import('../pages/SellerProfilePage.vue')
const CallsPage = () => import('../pages/CallsPage.vue')
const RtcCallPage = () => import('../pages/RtcCallPage.vue')

const routes = [
  { path: '/', name: 'home', component: HomePage },
  { path: '/products', name: 'products', component: ProductListPage },
  { path: '/products/:productId', name: 'product-detail', component: ProductDetailPage },
  { path: '/sellers/:sellerId', name: 'seller-profile', component: SellerProfilePage },
  { path: '/calls', name: 'calls', component: CallsPage, meta: { requiresAuth: true } },
  { path: '/calls/:callId/session', name: 'rtc-call', component: RtcCallPage, meta: { requiresAuth: true } },
  { path: '/purchase/:productId', name: 'purchase', component: PurchasePage },
  { path: '/purchase/:productId/success', name: 'purchase-success', component: PurchaseSuccessPage },
  { path: '/purchase/:productId/fail', name: 'purchase-fail', component: PurchaseFailPage },
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
  {
    path: '/seller/dashboard',
    name: 'seller-dashboard',
    component: SellerDashboardPage,
    meta: { requiresAuth: true, requiresRole: 'SELLER' },
  },
  {
    path: '/mypage/orders',
    name: 'my-orders',
    component: MyOrdersPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/seller/apply',
    name: 'seller-apply',
    component: SellerApplyPage,
    meta: { requiresAuth: true, sellerRegistrationOnly: true },
  },
  {
    path: '/seller/products',
    name: 'seller-products',
    component: ProductManagePage,
    meta: { requiresAuth: true, requiresRole: 'SELLER' },
  },
  {
    path: '/seller/products/new',
    name: 'seller-product-new',
    component: ProductRegisterPage,
    meta: { requiresAuth: true, requiresRole: 'SELLER' },
  },
  {
    path: '/seller/products/:productId/edit',
    name: 'seller-product-edit',
    component: ProductRegisterPage,
    meta: { requiresAuth: true, requiresRole: 'SELLER' },
  },
  {
    path: '/seller/products/:productId/device-check',
    name: 'seller-product-device-check',
    component: DeviceCheckPage,
    meta: { requiresAuth: true, requiresRole: 'SELLER' },
  },
  { path: '/admin', name: 'admin', component: AdminPage },
  { path: '/dev-tools', name: 'dev-tools', component: DevToolsPage },
  { path: '/coming-soon/:feature', name: 'coming-soon', component: ComingSoonPage },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage },

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
  if (to.meta.requiresAuth && !getAccessToken()) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.meta.requiresRole && !hasRole(to.meta.requiresRole)) {
    return { name: 'home', query: { notice: 'forbidden' } }
  }

  if (to.meta.sellerRegistrationOnly && hasRole('SELLER')) {
    return { name: 'seller-products' }
  }

  return true
})

export default router
