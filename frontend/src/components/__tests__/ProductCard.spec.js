import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductCard from '../ProductCard.vue'

const routerLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :data-to="JSON.stringify(to)"><slot /></a>',
}

function mountCard(props) {
  return mount(ProductCard, {
    props,
    global: { stubs: { RouterLink: routerLinkStub } },
  })
}

const product = {
  productId: 1001,
  name: 'Galaxy Book4 Pro 팝니다',
  manufacturerName: 'Samsung',
  modelName: 'Galaxy Book4 Pro',
  price: 1890000,
  status: 'ON_SALE',
  thumbnailUrl: 'https://cdn.example.com/1001.jpg',
}

describe('ProductCard', () => {
  it('대표 이미지와 이름·가격을 보여준다', () => {
    const wrapper = mountCard({ product })

    expect(wrapper.get('img').attributes('src')).toBe('https://cdn.example.com/1001.jpg')
    expect(wrapper.get('img').attributes('alt')).toBe('Galaxy Book4 Pro 팝니다')
    expect(wrapper.text()).toContain('Galaxy Book4 Pro 팝니다')
    expect(wrapper.text()).toContain('1,890,000원')
    expect(wrapper.text()).toContain('Samsung · Galaxy Book4 Pro')
  })

  it('대표 이미지가 없으면 자리표시자를 보여준다', () => {
    const wrapper = mountCard({ product: { ...product, thumbnailUrl: null } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('등록된 이미지 없음')
  })

  it('판매 완료 상품은 이미지 위에 안내를 덮는다', () => {
    const wrapper = mountCard({ product: { ...product, status: 'SOLD' } })

    expect(wrapper.text()).toContain('판매 완료')
  })

  it('to를 주면 카드 전체가 링크가 된다', () => {
    const wrapper = mountCard({
      product,
      to: { name: 'product-detail', params: { productId: 1001 } },
    })

    expect(JSON.parse(wrapper.get('[data-to]').attributes('data-to'))).toEqual({
      name: 'product-detail',
      params: { productId: 1001 },
    })
  })

  // 판매자 화면은 카드 안에 관리 버튼이 있어 전체를 링크로 감쌀 수 없습니다.
  it('titleTo를 주면 제목만 링크가 된다', () => {
    const wrapper = mountCard({
      product,
      titleTo: { name: 'product-detail', params: { productId: 1001 } },
    })

    const links = wrapper.findAll('[data-to]')
    expect(links).toHaveLength(1)
    expect(links[0].text()).toBe('Galaxy Book4 Pro 팝니다')
  })

  it('링크가 없으면 카드를 감싸는 앵커도 없다', () => {
    const wrapper = mountCard({ product })

    expect(wrapper.find('[data-to]').exists()).toBe(false)
  })
})
