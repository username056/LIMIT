import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SocialProviderButton from '../SocialProviderButton.vue'

/*
  버튼과 이미지가 서로 바뀌면 카카오 버튼을 눌렀는데 네이버로 넘어갑니다. 눌러 보기
  전까지는 아무도 모르는 종류의 실수라, 어느 회사가 어느 파일을 쓰는지 고정해 둡니다.
*/
describe('SocialProviderButton', () => {
  it.each([
    ['google', 'google_login', 'Google 계정으로 로그인'],
    ['naver', 'naver_login', '네이버 로그인'],
    ['kakao', 'kakao_login', '카카오 로그인'],
  ])('%s는 %s 이미지와 규정 문구를 쓴다', (provider, fileName, label) => {
    const wrapper = mount(SocialProviderButton, { props: { provider } })

    const image = wrapper.get('img')
    expect(image.attributes('src')).toContain(fileName)
    // 글자가 이미지 안에 있어 alt가 유일한 읽을 거리입니다.
    expect(image.attributes('alt')).toBe(label)
  })

  // 원본 크기가 저마다 달라(180x40, 366x90, 840x192) 높이만 맞추고 폭은 비율대로 둡니다.
  // 폭을 억지로 맞추면 로고가 늘어나 각 회사 규정을 벗어납니다.
  it('높이만 맞추고 폭은 비율대로 둔다', () => {
    const wrapper = mount(SocialProviderButton, { props: { provider: 'kakao' } })

    expect(wrapper.get('img').classes()).toEqual(expect.arrayContaining(['h-11', 'w-auto']))
  })

  it('연결 중에는 다시 누를 수 없고 상태를 읽어 준다', () => {
    const wrapper = mount(SocialProviderButton, {
      props: { provider: 'naver', loading: true },
    })

    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.get('img').attributes('alt')).toBe('네이버 로그인 연결 중')
  })

  it('누르면 click을 올린다', async () => {
    const wrapper = mount(SocialProviderButton, { props: { provider: 'google' } })

    await wrapper.trigger('click')

    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
