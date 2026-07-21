import { apiClient } from './client'

// README에 명시된 기본 확인 엔드포인트 예시
export function getHealth() {
  return apiClient.get('/health')
}
