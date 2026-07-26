import * as Sentry from '@sentry/vue'

function stripQueryAndHash(value) {
  if (typeof value !== 'string') {
    return value
  }

  const queryIndex = value.indexOf('?')
  const hashIndex = value.indexOf('#')
  const indexes = [queryIndex, hashIndex].filter((index) => index >= 0)

  return indexes.length === 0 ? value : value.slice(0, Math.min(...indexes))
}

export function sanitizeSentryEvent(event) {
  const sanitizedEvent = { ...event }

  if (event.request) {
    const { query_string: _queryString, cookies: _cookies, ...request } = event.request
    sanitizedEvent.request = {
      ...request,
      url: stripQueryAndHash(request.url),
    }
  }

  if (Array.isArray(event.breadcrumbs)) {
    sanitizedEvent.breadcrumbs = event.breadcrumbs.map((breadcrumb) => {
      if (!breadcrumb?.data) {
        return breadcrumb
      }

      const data = { ...breadcrumb.data }
      for (const key of ['url', 'from', 'to']) {
        data[key] = stripQueryAndHash(data[key])
      }

      return { ...breadcrumb, data }
    })
  }

  return sanitizedEvent
}

export function sanitizeSentryEventSafely(event) {
  try {
    return sanitizeSentryEvent(event)
  } catch {
    // 개인정보 정제에 실패한 이벤트는 원본 상태로 전송하지 않는다.
    return null
  }
}

export function initializeSentry(app, env = import.meta.env) {
  const dsn = env.VITE_SENTRY_DSN?.trim()
  if (!dsn) {
    return false
  }

  Sentry.init({
    app,
    dsn,
    environment: env.VITE_SENTRY_ENVIRONMENT?.trim() || env.MODE || 'unknown',
    release: env.VITE_SENTRY_RELEASE?.trim() || undefined,
    sendDefaultPii: false,
    beforeSend: sanitizeSentryEventSafely,
  })

  return true
}

export function captureApiException(error, { method, path, status, code, traceId } = {}) {
  Sentry.withScope((scope) => {
    scope.setContext('api', {
      method,
      path: stripQueryAndHash(path),
      status,
      code,
      traceId,
    })
    Sentry.captureException(error)
  })
}
