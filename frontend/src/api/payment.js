import { apiClient } from './client'

export function createPayment(payload) {
  return apiClient.post('/payments', payload)
}

export function confirmPayment(paymentId, payload) {
  return apiClient.post(`/payments/${paymentId}/confirm`, payload)
}

export function getPayment(paymentId) {
  return apiClient.get(`/payments/${paymentId}`)
}

export function cancelPayment(paymentId) {
  return apiClient.post(`/payments/${paymentId}/cancel`)
}

export function retryPayment(paymentId, method) {
  return apiClient.post(`/payments/${paymentId}/retry`, { method })
}
