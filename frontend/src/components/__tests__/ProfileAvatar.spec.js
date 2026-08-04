import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProfileAvatar from '../ProfileAvatar.vue'

describe('ProfileAvatar', () => {
  it('사진이 있으면 사진을 그린다', () => {
    const wrapper = mount(ProfileAvatar, {
      props: { src: 'https://cdn/profile.jpg', name: '한정판' },
    })

    const image = wrapper.find('img')
    expect(image.attributes('src')).toBe('https://cdn/profile.jpg')
    expect(image.attributes('alt')).toBe('한정판 프로필 사진')
    expect(wrapper.find('span').exists()).toBe(false)
  })

  it('사진이 없으면 닉네임 첫 글자로 대신한다', () => {
    const wrapper = mount(ProfileAvatar, { props: { name: '한정판' } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('span').text()).toBe('한')
  })

  // 닉네임까지 비면 빈 원만 남아 무엇이 빠진 자리인지 알 수 없습니다.
  it('닉네임도 없으면 빈 원을 남기지 않는다', () => {
    expect(mount(ProfileAvatar).find('span').text()).toBe('회')
    expect(mount(ProfileAvatar, { props: { name: '   ' } }).find('span').text()).toBe('회')
  })

  it('크기와 모서리는 화면이 넘겨준 클래스를 그대로 쓴다', () => {
    const wrapper = mount(ProfileAvatar, {
      props: { name: '한정판', sizeClass: 'h-16 w-16', rounded: 'rounded-lg' },
    })

    expect(wrapper.find('span').classes()).toEqual(
      expect.arrayContaining(['h-16', 'w-16', 'rounded-lg']),
    )
  })

  /*
    서버가 주는 주소는 시한이 있는 presigned URL일 수 있어 화면을 오래 열어 두면 만료됩니다.
    상품 썸네일이 cdn_url 탓에 몇 달간 안 보인 적이 있어, 사진 쪽 실패는 화면에서도 막습니다.
  */
  it('사진을 못 불러오면 첫 글자로 물러난다', async () => {
    const wrapper = mount(ProfileAvatar, {
      props: { src: 'https://cdn/expired.jpg', name: '한정판' },
    })

    await wrapper.find('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('span').text()).toBe('한')
  })

  it('사진 주소가 새로 오면 다시 사진을 시도한다', async () => {
    const wrapper = mount(ProfileAvatar, {
      props: { src: 'https://cdn/expired.jpg', name: '한정판' },
    })
    await wrapper.find('img').trigger('error')

    await wrapper.setProps({ src: 'https://cdn/fresh.jpg' })

    expect(wrapper.find('img').attributes('src')).toBe('https://cdn/fresh.jpg')
  })
})

