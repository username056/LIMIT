import { ref } from 'vue'
import { addFavorite, getMyFavorites, removeFavorite } from '../api/favorites'
import { getAccessToken } from '../auth/session'

/*
  좋아요한 상품 목록을 한 번만 받아 두고, 카드마다 담김/안 담김을 판단합니다.
  카드마다 상태를 물어보면 상품 수만큼 요청이 나갑니다.
  상품 목록 화면이 쓰는 방식과 같습니다.
*/
export function useFavoriteIds() {
  const favoriteIds = ref(new Set())
  const pendingIds = ref(new Set())

  async function loadFavoriteIds() {
    if (!getAccessToken()) {
      favoriteIds.value = new Set()
      return
    }
    try {
      const response = await getMyFavorites({ page: 0, size: 100 })
      favoriteIds.value = new Set((response?.data || []).map((item) => item.productId))
    } catch {
      // 담긴 목록을 못 받아도 상품은 보여야 합니다. 하트만 빈 상태로 둡니다.
      favoriteIds.value = new Set()
    }
  }

  // 로그인이 안 되어 있으면 false를 돌려줍니다. 부른 쪽에서 로그인으로 보냅니다.
  async function toggleFavorite(productId) {
    if (!getAccessToken()) return false
    const id = Number(productId)
    if (pendingIds.value.has(id)) return true

    pendingIds.value = new Set(pendingIds.value).add(id)
    const wasFavorite = favoriteIds.value.has(id)
    try {
      if (wasFavorite) await removeFavorite(id)
      else await addFavorite(id)
      const next = new Set(favoriteIds.value)
      if (wasFavorite) next.delete(id)
      else next.add(id)
      favoriteIds.value = next
    } catch {
      // 실패하면 원래 상태 그대로 둡니다. 홈에서 오류 문구까지 띄우지는 않습니다.
    } finally {
      const next = new Set(pendingIds.value)
      next.delete(id)
      pendingIds.value = next
    }
    return true
  }

  return { favoriteIds, pendingIds, loadFavoriteIds, toggleFavorite }
}
