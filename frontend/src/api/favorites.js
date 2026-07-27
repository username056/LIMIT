import { apiClient } from './client'

/**
 * @typedef {Object} FavoriteProductResponse
 * @property {number} favoriteId
 * @property {number} productId
 * @property {string} name
 * @property {string|null} manufacturerName
 * @property {string} modelName
 * @property {number} price
 * @property {string} status
 * @property {string} favoritedAt
 */

export function getMyFavorites({ page = 0, size = 20 } = {}) {
  return apiClient.getEnvelope(`/members/me/favorites?page=${page}&size=${size}`)
}

export function getFavoriteStatus(productId) {
  return apiClient.get(`/products/${productId}/favorites/me`)
}

export function addFavorite(productId) {
  return apiClient.post(`/products/${productId}/favorites`)
}

export function removeFavorite(productId) {
  return apiClient.delete(`/products/${productId}/favorites`)
}
