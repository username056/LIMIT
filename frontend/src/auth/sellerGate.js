import { ref } from 'vue'
import { getAccessToken, hasRole } from './session'

// 상품 등록은 판매자만 할 수 있습니다. 헤더의 '판매하기'와 목록의 '내 상품 등록하기'가
// 같은 안내를 쓰도록 판정과 이동 로직을 한곳에 모았습니다.
// 라우터 가드에 그냥 맡기면 홈으로 튕기면서 '권한이 없습니다' 팝업만 떠서, 무엇을 해야 하는지 알 수 없습니다.
export const SELL_ENTRY_PATH = '/seller/products/new'

export function useSellerGate(router) {
  const isSellerNoticeOpen = ref(false)

  async function goToSell() {
    if (!getAccessToken()) {
      await router.push({ name: 'login', query: { redirect: SELL_ENTRY_PATH } })
      return
    }
    if (hasRole('SELLER')) {
      await router.push({ name: 'seller-product-new' })
      return
    }
    isSellerNoticeOpen.value = true
  }

  async function goToSellerApply() {
    isSellerNoticeOpen.value = false
    await router.push({ name: 'seller-apply' })
  }

  function closeSellerNotice() {
    isSellerNoticeOpen.value = false
  }

  return { isSellerNoticeOpen, goToSell, goToSellerApply, closeSellerNotice }
}
