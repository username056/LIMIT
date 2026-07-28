import { apiClient } from './client'

export function searchPlaces(query, size = 8) {
  const search = new URLSearchParams({ query, size })
  return apiClient.get(`/places/search?${search.toString()}`)
}
