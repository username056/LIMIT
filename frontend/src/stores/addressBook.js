import { ref } from 'vue'

// 배송지 API가 아직 없어 브라우저 세션 동안만 메모리에 유지되는 임시 저장소입니다.
// 개인정보라 localStorage 등에는 저장하지 않으며, 새로고침하면 초기화됩니다.
export const addressBook = ref([
  {
    id: 1,
    label: '우리집',
    isDefault: true,
    zonecode: '',
    addressLine: '서울시 강남구 테헤란로 123',
    addressDetail: '마크타워 5층',
    address: '서울시 강남구 테헤란로 123 마크타워 5층',
    receiverName: '홍길동',
    receiverPhone: '010-1234-5678',
  },
])

export function getDefaultAddress() {
  return addressBook.value.find((item) => item.isDefault) || addressBook.value[0] || null
}
