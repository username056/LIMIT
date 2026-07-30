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

// 판매자가 직접 '판매 완료'로 닫을 수 있는 상태입니다. 서버 Listing.markSoldBySeller와 같은 조건입니다.
// 예약 이후는 구매자가 결제·검수에 들어가 있어 판매자가 임의로 닫지 못합니다.
const SELLER_CLOSABLE_STATUSES = ['ON_SALE', 'HIDDEN']

export function canSellerMarkSold(status) {
  return SELLER_CLOSABLE_STATUSES.includes(status)
}

// 판매자가 상품 정보를 고칠 수 있는 상태입니다. 서버 Listing.EDITABLE_STATUSES와 같은 목록을 씁니다.
// 등록을 끝낸 뒤에야 가격 오타 같은 실수를 알아차리는 경우가 있어 판매 중·숨김도 수정을 허용하고,
// 구매자가 조건을 보고 결제·검수에 들어간 예약 이후 상태만 막습니다.
const EDITABLE_PRODUCT_STATUSES = ['DRAFT', 'ON_SALE', 'HIDDEN']

export function isProductEditable(status) {
  return EDITABLE_PRODUCT_STATUSES.includes(status)
}

// 판매가 끝나 더 이상 구매할 수 없는 상태입니다. 목록·상세에서 사진 위에 안내를 덮어 표시합니다.
export function isSoldOut(status) {
  return status === 'SOLD'
}
