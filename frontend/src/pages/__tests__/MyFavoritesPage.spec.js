import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MyFavoritesPage from '../MyFavoritesPage.vue'

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
}

describe('MyFavoritesPage', () => {
  it('목업 관심 상품을 해제하면 목록에서 제거한다', async () => {
    const wrapper = mount(MyFavoritesPage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
        },
      },
    })

    expect(wrapper.findAll('h2')).toHaveLength(3)
    const removeButtons = wrapper.findAll('button').filter((button) => button.text() === '해제')
    await removeButtons[0].trigger('click')

    expect(wrapper.findAll('h2')).toHaveLength(2)
    expect(wrapper.text()).not.toContain("iPhone 14 Pro 'Space Black'")
  })
})
