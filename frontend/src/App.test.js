import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('App', () => {
  it('Swagger와 Grafana 바로가기만 표시한다', () => {
    const wrapper = mount(App)

    expect(wrapper.findAll('a')).toHaveLength(2)
    expect(wrapper.text()).toBe('SwaggerAPI 명세 보기 ↗Grafana모니터링 보기 ↗')
    expect(wrapper.get('[data-testid="swagger-link"]').attributes()).toMatchObject({
      href: 'http://localhost:18080/swagger-ui.html',
      rel: 'noopener noreferrer',
      target: '_blank',
    })
    expect(wrapper.get('[data-testid="grafana-link"]').attributes('href'))
      .toBe('http://localhost:3000')
  })
})
