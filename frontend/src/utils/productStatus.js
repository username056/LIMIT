// 상품 상태를 판매자·구매자 화면에서 같은 말로 보여주기 위한 공용 라벨입니다.
// 서버는 DRAFT / ON_SALE 을 구분하지만 화면에서는 '초안'이나 '판매 등록' 같은 절차 용어를 쓰지 않고,
// 아직 공개되지 않은 상태를 '임시 저장 중'으로만 안내합니다.
const PRODUCT_STATUS_LABELS = {
  DRAFT: '임시 저장 중',
  ON_SALE: '판매 중',
  RESERVED: '예약 중',
  SOLD: '판매 완료',
  HIDDEN: '숨김',
}

export function productStatusLabel(status) {
  return PRODUCT_STATUS_LABELS[status] || '상태 확인 중'
}

// 판매가 끝나 더 이상 구매할 수 없는 상태입니다. 목록·상세에서 사진 위에 안내를 덮어 표시합니다.
export function isSoldOut(status) {
  return status === 'SOLD'
}
