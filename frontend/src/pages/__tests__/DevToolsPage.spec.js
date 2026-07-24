import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import DevToolsPage from '../DevToolsPage.vue'

describe('DevToolsPage', () => {
  it('Swagger, Grafana와 SonarCloud 바로가기를 표시한다', () => {
    const wrapper = mount(DevToolsPage)
    const expectedSonarUrl = import.meta.env.VITE_SONAR_URL || 'https://sonarcloud.io/project/overview?id=limit'

    expect(wrapper.findAll('a')).toHaveLength(3)
    expect(wrapper.get('[data-testid="swagger-link"]').attributes()).toMatchObject({
      href: 'http://localhost:18080/swagger-ui.html',
      rel: 'noopener noreferrer',
      target: '_blank',
    })
    expect(wrapper.get('[data-testid="grafana-link"]').attributes('href'))
      .toBe('http://localhost:3000')
    expect(wrapper.get('[data-testid="sonar-link"]').attributes()).toMatchObject({
      href: expectedSonarUrl,
      rel: 'noopener noreferrer',
      target: '_blank',
    })
  })
})
