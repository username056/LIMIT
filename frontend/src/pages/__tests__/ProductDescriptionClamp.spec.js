import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

/*
  상품 설명의 '더 보기'가 나오는 조건만 떼어내 확인합니다.
  ---------------------------------------------------------------------------
  전에는 글자 수(180자)로 판단했는데 자르는 쪽은 CSS가 네 줄로 봅니다. 두 기준이 어긋나
  짧지만 줄바꿈이 많은 글(80자·5줄)은 잘리는데도 버튼이 나오지 않아, 뒷부분을 볼 방법이
  없었습니다. 실제로 잘렸는지 재서 판단하도록 바꿨습니다.

  ProductDetailPage 전체를 띄우면 상품·이미지·판매자·진단 조회를 모두 흉내 내야 해서,
  같은 판단 로직만 작은 컴포넌트로 옮겨 확인합니다.
*/
const DescriptionBlock = defineComponent({
  props: { text: { type: String, default: '' } },
  setup(props) {
    const descriptionEl = ref(null)
    const isDescriptionExpanded = ref(false)
    const isDescriptionClamped = ref(false)

    function measureDescription() {
      const element = descriptionEl.value
      if (!element || isDescriptionExpanded.value) return
      isDescriptionClamped.value = element.scrollHeight > element.clientHeight + 1
    }

    onMounted(() => window.addEventListener('resize', measureDescription))
    onBeforeUnmount(() => window.removeEventListener('resize', measureDescription))

    return { props, descriptionEl, isDescriptionExpanded, isDescriptionClamped, measureDescription }
  },
  template: `
    <div>
      <p ref="descriptionEl" :class="isDescriptionExpanded ? '' : 'line-clamp-4'">{{ props.text }}</p>
      <button
        v-if="isDescriptionClamped || isDescriptionExpanded"
        type="button"
        @click="isDescriptionExpanded = !isDescriptionExpanded"
      >{{ isDescriptionExpanded ? '접기' : '더 보기' }}</button>
    </div>
  `,
})

// jsdom은 실제 높이를 재지 않아 늘 0입니다. 잘린 상황과 아닌 상황을 직접 만들어 줍니다.
function setHeights(element, { scrollHeight, clientHeight }) {
  Object.defineProperty(element, 'scrollHeight', { value: scrollHeight, configurable: true })
  Object.defineProperty(element, 'clientHeight', { value: clientHeight, configurable: true })
}

describe('상품 설명 더 보기', () => {
  it('짧아도 네 줄을 넘겨 잘렸으면 더 보기를 띄운다', async () => {
    const wrapper = mount(DescriptionBlock, {
      props: { text: '갤럭시Z 플립 3 라벤더입니다\n색상이 이뻐요\n폴드가 생겨서 판매합니다\n초기화 3번\n직거래 가능' },
    })

    setHeights(wrapper.get('p').element, { scrollHeight: 200, clientHeight: 112 })
    wrapper.vm.measureDescription()
    await nextTick()

    expect(wrapper.text()).toContain('더 보기')
  })

  it('잘리지 않았으면 버튼을 두지 않는다', async () => {
    const wrapper = mount(DescriptionBlock, { props: { text: '한 줄짜리 설명입니다' } })

    setHeights(wrapper.get('p').element, { scrollHeight: 28, clientHeight: 28 })
    wrapper.vm.measureDescription()
    await nextTick()

    expect(wrapper.text()).not.toContain('더 보기')
  })

  // 펼친 뒤에는 잘린 곳이 없어 다시 재면 false가 됩니다. 그때 '접기'가 사라지면 안 됩니다.
  it('펼친 뒤에도 접기가 남아 있다', async () => {
    const wrapper = mount(DescriptionBlock, { props: { text: '긴 설명' } })
    setHeights(wrapper.get('p').element, { scrollHeight: 200, clientHeight: 112 })
    wrapper.vm.measureDescription()
    await nextTick()

    await wrapper.get('button').trigger('click')
    setHeights(wrapper.get('p').element, { scrollHeight: 200, clientHeight: 200 })
    wrapper.vm.measureDescription()
    await nextTick()

    expect(wrapper.text()).toContain('접기')
  })
})
