import { afterEach, describe, expect, it, vi } from 'vitest'
import * as Sentry from '@sentry/vue'
import { initializeSentry } from '../sentry'

vi.mock('@sentry/vue', () => ({
  init: vi.fn(),
  consoleLoggingIntegration: vi.fn(() => ({ name: 'ConsoleLogs' })),
}))

describe('initializeSentry', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('DSN이 없으면 Sentry를 비활성 상태로 둔다', () => {
    expect(initializeSentry({}, { MODE: 'test' })).toBe(false)
    expect(Sentry.init).not.toHaveBeenCalled()
  })

  it('운영 환경과 release를 포함해 Sentry를 초기화한다', () => {
    const app = {}

    expect(
      initializeSentry(app, {
        MODE: 'production',
        VITE_SENTRY_DSN: ' https://public@example.ingest.sentry.io/1 ',
        VITE_SENTRY_ENVIRONMENT: ' production ',
        VITE_SENTRY_RELEASE: ' commit-sha ',
      }),
    ).toBe(true)

    expect(Sentry.init).toHaveBeenCalledWith(
      expect.objectContaining({
        app,
        dsn: 'https://public@example.ingest.sentry.io/1',
        environment: 'production',
        release: 'commit-sha',
        sendDefaultPii: false,
        enableLogs: true,
      }),
    )
    expect(Sentry.consoleLoggingIntegration).toHaveBeenCalledWith({
      levels: ['log', 'info', 'warn', 'error'],
    })
  })

  it('전송 전에 URL의 query와 hash 및 요청 쿠키를 제거한다', () => {
    initializeSentry({}, { VITE_SENTRY_DSN: 'https://public@example.ingest.sentry.io/1' })
    const options = Sentry.init.mock.calls[0][0]
    const event = {
      request: {
        url: 'https://l1mit.shop/listings?email=user@example.com#detail',
        query_string: 'email=user@example.com',
        cookies: { session: 'secret' },
      },
      breadcrumbs: [
        {
          data: {
            from: '/search?keyword=private',
            to: '/listings/1#detail',
            url: 'https://api.l1mit.shop/api/v1/listings?owner=1',
          },
        },
      ],
    }

    const sanitizedEvent = options.beforeSend(event)

    expect(sanitizedEvent).toEqual({
      request: {
        url: 'https://l1mit.shop/listings',
      },
      breadcrumbs: [
        {
          data: {
            from: '/search',
            to: '/listings/1',
            url: 'https://api.l1mit.shop/api/v1/listings',
          },
        },
      ],
    })
    expect(event.request.url).toContain('?email=')
    expect(event.request.cookies).toEqual({ session: 'secret' })
    expect(event.breadcrumbs[0].data.from).toBe('/search?keyword=private')
  })

  it('개인정보 정제에 실패한 이벤트는 전송하지 않는다', () => {
    initializeSentry({}, { VITE_SENTRY_DSN: 'https://public@example.ingest.sentry.io/1' })
    const options = Sentry.init.mock.calls[0][0]
    const invalidRequest = new Proxy(
      {},
      {
        ownKeys() {
          throw new Error('cannot enumerate request')
        },
      },
    )

    expect(options.beforeSend({ request: invalidRequest })).toBeNull()
  })

  it('콘솔 로그의 인증정보, 개인정보와 URL query/hash를 제거한다', () => {
    initializeSentry({}, { VITE_SENTRY_DSN: 'https://public@example.ingest.sentry.io/1' })
    const options = Sentry.init.mock.calls[0][0]
    const log = {
      message:
        'request https://l1mit.shop/listings?email=user@example.com#detail authorization=Bearer secret-token',
      attributes: {
        email: 'user@example.com',
        accessToken: 'secret-token',
        'refresh-token': 'secret-refresh-token',
        api_key: 'secret-api-key',
        accessibility: 'enabled',
        path: '/listings?owner=private#detail',
        nested: {
          phone: '010-1234-5678',
          detail: 'contact user@example.com',
        },
      },
    }

    expect(options.beforeSendLog(log)).toEqual({
      message: 'request https://l1mit.shop/listings authorization=[Filtered]',
      attributes: {
        email: '[Filtered]',
        accessToken: '[Filtered]',
        'refresh-token': '[Filtered]',
        api_key: '[Filtered]',
        accessibility: 'enabled',
        path: '/listings',
        nested: {
          phone: '[Filtered]',
          detail: 'contact [Filtered email]',
        },
      },
    })
    expect(log.attributes.email).toBe('user@example.com')
  })

  it('로그 개인정보 정제에 실패하면 로그를 전송하지 않는다', () => {
    initializeSentry({}, { VITE_SENTRY_DSN: 'https://public@example.ingest.sentry.io/1' })
    const options = Sentry.init.mock.calls[0][0]
    const invalidAttributes = new Proxy(
      {},
      {
        ownKeys() {
          throw new Error('cannot enumerate attributes')
        },
      },
    )

    expect(options.beforeSendLog({ message: 'safe', attributes: invalidAttributes })).toBeNull()
  })
})
