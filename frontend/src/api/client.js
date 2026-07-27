// 공통 API 클라이언트
// - API 주소는 여기서만 관리 (컴포넌트에 하드코딩하지 않기)
// - 백엔드 응답 계약을 그대로 반영:
//   성공: { data, meta }
//   실패: { error: { code, message, fieldErrors }, traceId }

import { getAccessToken } from '../auth/session'
import { captureApiException } from '../monitoring/sentry'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export class ApiError extends Error {
  constructor({ code, message, fieldErrors, traceId, status }) {
    super(message)
    this.code = code
    this.fieldErrors = fieldErrors
    this.traceId = traceId
    this.status = status
  }
}

async function request(path, options = {}) {
  const { unwrapResponse = true, ...fetchOptions } = options
  const accessToken = getAccessToken()
  const method = fetchOptions.method || 'GET'
  let res

  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...fetchOptions,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(fetchOptions.headers || {}),
      },
    })
  } catch (error) {
    captureApiException(error, { method, path })
    throw error
  }

  const body = await res.json().catch(() => null)

  if (!res.ok) {
    const err = body?.error || {}
    const apiError = new ApiError({
      code: err.code || 'UNKNOWN_ERROR',
      message: err.message || '요청 처리 중 오류가 발생했습니다.',
      fieldErrors: err.fieldErrors || null,
      traceId: body?.traceId || res.headers.get('X-Trace-Id') || null,
      status: res.status,
    })

    if (res.status >= 500) {
      captureApiException(apiError, {
        method,
        path,
        status: apiError.status,
        code: apiError.code,
        traceId: apiError.traceId,
      })
    }

    throw apiError
  }

  // 성공 응답은 항상 { data, meta } 형태 → data만 꺼내서 반환
  return unwrapResponse ? body?.data : body
}

export const apiClient = {
  get: (path) => request(path, { method: 'GET' }),
  getEnvelope: (path) => request(path, { method: 'GET', unwrapResponse: false }),
  post: (path, payload) => request(path, { method: 'POST', body: JSON.stringify(payload) }),
  put: (path, payload) => request(path, { method: 'PUT', body: JSON.stringify(payload) }),
  patch: (path, payload) => request(path, { method: 'PATCH', body: JSON.stringify(payload) }),
  delete: (path) => request(path, { method: 'DELETE' }),
}
