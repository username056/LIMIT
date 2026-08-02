<script setup>
// 상품을 줄지어 보여 주는 섹션. 인기 상품과 최근 등록 상품이 이 하나를 함께 씁니다.
// 다른 건 정렬 기준·개수·카드 모양뿐이라 props로 받습니다.
//
// 카드는 상품 목록·관심상품이 쓰는 ProductCard 그대로입니다. 홈 전용 카드를 따로
// 두었더니 제조사·모델과 상품명의 위아래가 뒤바뀌어, 같은 상품이 화면마다 다르게
// 보였습니다. 한 벌만 두면 한쪽을 고칠 때 다른 쪽이 뒤처지는 일도 없습니다.
//
// 여기는 처음부터 실제 /products 데이터를 씁니다. 예전에 이 자리에 있던 목업 카드가
// 눌러도 없는 상품으로 이어져서 통째로 내려간 적이 있습니다(HomePage 옛 주석 참고).
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProducts } from '../../api/products'
import { useFavoriteIds } from '../../composables/useFavoriteIds'
import { vReveal } from '../../composables/useReveal'
import ProductCard from '../ProductCard.vue'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  // 백엔드가 허용하는 공개 정렬은 createdAt·price·viewCount 셋뿐입니다.
  sort: { type: String, default: 'createdAt,desc' },
  size: { type: Number, default: 3 },
  variant: { type: String, default: 'popular' },
  columns: { type: Number, default: 3 },
})

// 서버 정렬이 막혔을 때 대신 받아 오는 개수입니다. 이 안에서 앞쪽 몇 개를 고릅니다.
const FALLBACK_POOL_SIZE = 20

// props.sort('viewCount,desc')와 같은 기준으로 앞에서 다시 세웁니다.
function sortLocally(items) {
  const [field, direction = 'asc'] = props.sort.split(',')
  const sign = direction === 'desc' ? -1 : 1
  return [...items].sort((a, b) => {
    const left = Number(a?.[field] ?? 0)
    const right = Number(b?.[field] ?? 0)
    return (left - right) * sign
  })
}

const route = useRoute()
const router = useRouter()
const products = ref([])
const isLoading = ref(true)
const errorMessage = ref('')

const { favoriteIds, pendingIds, loadFavoriteIds, toggleFavorite } = useFavoriteIds()

async function onToggleFavorite(product) {
  const handled = await toggleFavorite(product.productId)
  // 로그인해야 담을 수 있습니다. 돌아올 자리를 남겨 두고 보냅니다.
  if (!handled) await router.push({ name: 'login', query: { redirect: route.fullPath } })
}

onMounted(async () => {
  try {
    const response = await getProducts({ page: 0, size: props.size, sort: props.sort })
    products.value = response?.data || []
  } catch (error) {
    console.warn(`[home] ${props.sort} 정렬을 서버가 거부했습니다. 앞에서 직접 추립니다.`, error)
    // 서버가 이 정렬을 모르면(구버전 등) 홈이 통째로 빈 채로 남습니다. 그럴 바에는
    // 기본 정렬로 넉넉히 받아 와 여기서 추립니다. 서버가 갱신되면 위 경로로 돌아갑니다.
    try {
      const fallback = await getProducts({ page: 0, size: FALLBACK_POOL_SIZE })
      products.value = sortLocally(fallback?.data || []).slice(0, props.size)
    } catch (fallbackError) {
      products.value = []
      errorMessage.value = fallbackError?.message?.trim() || '상품을 불러오지 못했습니다.'
      console.warn('[home] 기본 정렬로도 불러오지 못했습니다.', fallbackError)
    }
  } finally {
    isLoading.value = false
  }

  // 두 섹션 모두 하트가 있으므로 담긴 목록을 함께 받아 둡니다.
  await loadFavoriteIds()
})
</script>

<template>
  <!--
    등록된 상품이 없어도 섹션은 남깁니다. 통째로 사라지면 화면 구성이 그날그날
    달라져서, 무엇이 비었는지 보는 사람도 만드는 사람도 알 수 없습니다.
  -->
  <section class="home-section">
    <div class="home-shell">
      <div
        v-reveal
        class="products__head"
      >
        <div>
          <h2 class="home-section__title">
            {{ title }}
          </h2>
          <p
            v-if="subtitle"
            class="home-section__sub"
          >
            {{ subtitle }}
          </p>
        </div>
        <RouterLink
          :to="{ name: 'products' }"
          class="products__more"
        >
          전체 상품 보기 →
        </RouterLink>
      </div>

      <div
        v-if="isLoading || products.length"
        class="products__grid"
        :style="{ '--products-columns': columns }"
      >
        <template v-if="isLoading">
          <span
            v-for="index in size"
            :key="`skeleton-${index}`"
            class="products__skeleton"
          />
        </template>
        <ProductCard
          v-for="(product, index) in products"
          v-else
          :key="product.productId"
          v-reveal="index * 80"
          :product="product"
          :to="{ name: 'product-detail', params: { productId: product.productId } }"
        >
          <!-- 인기 섹션에서만 왜 이 카드가 여기 있는지 사진 위에 적어 둡니다. -->
          <template
            v-if="variant === 'popular'"
            #image-overlay
          >
            <span class="products__badge">인기</span>
          </template>

          <!--
            가격 줄 오른쪽 하트. 목록 화면과 같은 모양·같은 클래스를 씁니다.
            카드 전체가 상세로 가는 링크라 기본 동작을 막아야 담기만 됩니다.
          -->
          <template #body-action>
            <button
              type="button"
              class="favorite-button flex h-9 w-9 shrink-0 items-center justify-center rounded-md border text-lg leading-none transition disabled:opacity-60"
              :class="favoriteIds.has(product.productId)
                ? 'favorite-button--on border-primary bg-accent text-primary'
                : 'border-border bg-surface text-text-sub hover:border-primary hover:text-primary'"
              :aria-label="favoriteIds.has(product.productId)
                ? `${product.name} 좋아요 해제`
                : `${product.name} 좋아요`"
              :disabled="pendingIds.has(product.productId)"
              @click.prevent.stop="onToggleFavorite(product)"
            >
              {{ favoriteIds.has(product.productId) ? '♥' : '♡' }}
            </button>
          </template>
        </ProductCard>
      </div>

      <p
        v-else
        class="products__empty"
        :class="{ 'products__empty--error': errorMessage }"
      >
        {{ errorMessage || '아직 보여 드릴 상품이 없습니다.' }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.products__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 36px;
}

.products__more {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-sub);
  transition: color 0.25s var(--home-ease);
}

.products__more:hover {
  color: var(--color-gradient-start);
}

.products__grid {
  display: grid;
  grid-template-columns: repeat(var(--products-columns, 3), minmax(0, 1fr));
  gap: 28px;
}

/* 사진 왼쪽 위에 얹는 '인기' 표시. 사진을 가리지 않게 작고 반투명하게 둡니다. */
.products__badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 90%);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-gradient-start);
  backdrop-filter: blur(6px);
}

.products__empty {
  padding: 56px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  font-size: 14px;
  text-align: center;
  color: var(--color-text-sub);
}

.products__empty--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

/* 자리만 잡아 두는 회색 판입니다. 카드가 들어오며 화면이 튀지 않게 합니다. */
.products__skeleton {
  height: 360px;
  border-radius: var(--radius-lg);
  background: #f1f5f9;
}

@media (max-width: 1024px) {
  .products__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
  }
}

@media (max-width: 600px) {
  .products__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .products__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
