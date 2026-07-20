import { describe, expect, it } from 'vitest'

import { resolveDemoUrl } from './demoLinks'

describe('resolveDemoUrl', () => {
  it('로컬에서는 로컬 운영 도구 주소를 사용한다', () => {
    expect(resolveDemoUrl(
      'docs',
      'http://localhost:18080/swagger-ui.html',
      undefined,
      { hostname: 'localhost', protocol: 'http:' },
    )).toBe('http://localhost:18080/swagger-ui.html')
  })

  it('배포 환경에서는 현재 루트 도메인의 서브도메인을 사용한다', () => {
    expect(resolveDemoUrl(
      'docs',
      'http://localhost:18080/swagger-ui.html',
      undefined,
      { hostname: 'www.l1mit.shop', protocol: 'https:' },
    )).toBe('https://docs.l1mit.shop')
  })

  it('환경변수 주소가 있으면 가장 먼저 사용한다', () => {
    expect(resolveDemoUrl(
      'docs',
      'http://localhost:18080/swagger-ui.html',
      'https://demo.example.com/swagger',
      { hostname: 'l1mit.shop', protocol: 'https:' },
    )).toBe('https://demo.example.com/swagger')
  })
})
