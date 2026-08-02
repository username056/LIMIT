/*
  스크롤로 내려오면 한 번 나타나는 효과.
  ---------------------------------------------------------------------------
  화면 밖 요소까지 처음부터 애니메이션을 걸면 사용자가 그 자리에 왔을 때는 이미
  끝나 있어서 아무 일도 안 일어난 것처럼 보입니다. 그래서 보일 때 시작합니다.

  라이브러리는 쓰지 않고 브라우저 기본 기능(IntersectionObserver)만 씁니다.
  한 번 나타난 뒤에는 관찰을 끊습니다. 오르내릴 때마다 다시 뜨면 산만합니다.

  쓰는 쪽:
    import { vReveal } from '../../composables/useReveal'
    <section v-reveal>...</section>
    <div v-reveal="120">...</div>   // 120ms 늦게 (여러 개를 차례로 띄울 때)
*/

const REVEALED = 'is-revealed'

// 관찰자는 하나만 만들어 모든 요소가 나눠 씁니다. 요소마다 만들면 낭비입니다.
let observer = null

function ensureObserver() {
  if (observer) return observer
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add(REVEALED)
        observer.unobserve(entry.target)
      })
    },
    // 아래에서 조금 올라온 시점에 시작해야 눈에 걸립니다.
    { rootMargin: '0px 0px -12% 0px', threshold: 0.05 },
  )
  return observer
}

export const vReveal = {
  mounted(el, binding) {
    // 움직임을 줄여 달라고 한 사용자에게는 그냥 처음부터 보여 줍니다.
    const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    // 테스트 환경(jsdom)에는 IntersectionObserver가 없습니다. 없으면 그냥 보여 줍니다.
    if (prefersReduced || typeof IntersectionObserver === 'undefined') {
      el.classList.add(REVEALED)
      return
    }

    el.classList.add('reveal')
    const delay = Number(binding.value)
    if (Number.isFinite(delay) && delay > 0) el.style.transitionDelay = `${delay}ms`
    ensureObserver().observe(el)
  },

  unmounted(el) {
    observer?.unobserve(el)
  },
}
