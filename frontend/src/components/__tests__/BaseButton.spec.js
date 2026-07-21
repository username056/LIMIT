import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from '../BaseButton.vue'

describe('BaseButton', () => {
  it('슬롯 텍스트를 렌더링한다', () => {
    const wrapper = mount(BaseButton, {
      slots: { default: '로그인' },
    })
    expect(wrapper.text()).toContain('로그인')
  })

  it('variant="outline"이면 outline 스타일 클래스를 갖는다', () => {
    const wrapper = mount(BaseButton, {
      props: { variant: 'outline' },
      slots: { default: '취소' },
    })
    expect(wrapper.classes().join(' ')).toContain('border')
  })
})
