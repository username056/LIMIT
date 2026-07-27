import * as Sentry from '@sentry/vue'

const FILTERED_VALUE = '[Filtered]'
const SENSITIVE_LOG_KEY =
  /^(?:authorization|cookies?|password|passwd|secret|(?:access|refresh|id)[_\s-]?token|api[_\s-]?key|(?:user[_\s-]?)?(?:email|phone))$/i

function stripQueryAndHash(value) {
  if (typeof value !== 'string') {
    return value
  }

  const queryIndex = value.indexOf('?')
  const hashIndex = value.indexOf('#')
  const indexes = [queryIndex, hashIndex].filter((index) => index >= 0)

  return indexes.length === 0 ? value : value.slice(0, Math.min(...indexes))
}

function sanitizeLogText(value) {
  return value
    .replace(
      /((?:["']?(?:authorization|cookie|password|passwd|secret|access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|email|phone)["']?)\s*[:=]\s*)(?:Bearer\s+[^\s,;}]+|"[^"]*"|'[^']*'|[^\s,;}]+)/gi,
      `$1${FILTERED_VALUE}`,
    )
    .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, `Bearer ${FILTERED_VALUE}`)
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, FILTERED_VALUE)
    .replace(/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g, '[Filtered email]')
    .replace(/https?:\/\/[^\s"'<>]+/gi, (url) => stripQueryAndHash(url))
    .replace(
      /(^|[\s("'`])((?:\/|\.\.?\/)[^\s"'<>?#]+)[?#][^\s"'<>)]*/g,
      (_match, prefix, path) => `${prefix}${path}`,
    )
}

function sanitizeLogValue(value, key = '') {
  if (SENSITIVE_LOG_KEY.test(key)) {
    return FILTERED_VALUE
  }

  if (typeof value === 'string') {
    return sanitizeLogText(value)
  }

  if (Array.isArray(value)) {
    return value.map((item) => sanitizeLogValue(item))
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([entryKey, entryValue]) => [
        entryKey,
        sanitizeLogValue(entryValue, entryKey),
      ]),
    )
  }

  return value
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

export function sanitizeSentryLog(log) {
  return {
    ...log,
    message: sanitizeLogValue(log.message),
    attributes: sanitizeLogValue(log.attributes),
  }
}

export function sanitizeSentryLogSafely(log) {
  try {
    return sanitizeSentryLog(log)
  } catch {
    // 개인정보 정제에 실패한 로그는 원본 상태로 전송하지 않는다.
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
    enableLogs: true,
    integrations: [
      Sentry.consoleLoggingIntegration({
        levels: ['log', 'info', 'warn', 'error'],
      }),
    ],
    beforeSend: sanitizeSentryEventSafely,
    beforeSendLog: sanitizeSentryLogSafely,
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
