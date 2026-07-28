import { apiClient } from './client'

export function registerSeller(payload) {
  return apiClient.post('/sellers', payload)
}

export function getMySellerProfile() {
  return apiClient.get('/sellers/me')
}
