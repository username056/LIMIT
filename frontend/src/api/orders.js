import { apiClient } from './client'

export function listOrders() {
  return apiClient.get('/orders')
}
