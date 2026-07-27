// WIREFRAME MOCK: 상품 목록/상세 API가 연결되기 전까지 화면 흐름을 확인하기 위한 데이터 생성기입니다.
// 실제 연동 시 이 파일 대신 src/api/product.js를 거친 백엔드 응답을 사용하세요.

export const VERIFICATION_TIERS = [
  { value: '0-5', label: '0-5', badge: 'bg-red-50 text-red-600' },
  { value: '5-7', label: '5-7', badge: 'bg-yellow-50 text-yellow-600' },
  { value: '8-10', label: '8-10', badge: 'bg-green-50 text-green-600' },
  { value: '10+', label: '10개 이상', badge: 'bg-blue-50 text-blue-600' },
]

const MOCK_TEMPLATES = [
  { brand: 'Samsung', name: 'Galaxy Tab S9 Ultra', deviceType: 'tablet', price: 990000, required: 14 },
  { brand: 'Apple', name: 'iPad Pro 12.9 M2', deviceType: 'tablet', price: 1450000, required: 14 },
  { brand: 'Samsung', name: 'Galaxy Book4 Pro', deviceType: 'laptop', price: 1680000, required: 12 },
  { brand: 'Apple', name: 'MacBook Air 13 M2', deviceType: 'laptop', price: 1240000, required: 14 },
  { brand: 'Apple', name: 'iPhone 15 Pro Max', deviceType: 'smartphone', price: 1280000, required: 14 },
  { brand: 'LG', name: '그램 16인치 2023', deviceType: 'laptop', price: 1150000, required: 12 },
  { brand: 'Samsung', name: 'Galaxy S24 Ultra', deviceType: 'smartphone', price: 1050000, required: 14 },
  { brand: 'Lenovo', name: 'ThinkPad X1 Carbon', deviceType: 'laptop', price: 1390000, required: 14 },
  { brand: 'Dell', name: 'XPS 13 Plus', deviceType: 'laptop', price: 1290000, required: 12 },
  { brand: 'Apple', name: 'iPhone 14 Pro', deviceType: 'smartphone', price: 890000, required: 14 },
]

export const PAGE_SIZE = 6

export function buildProduct(seed) {
  const template = MOCK_TEMPLATES[((seed % MOCK_TEMPLATES.length) + MOCK_TEMPLATES.length) % MOCK_TEMPLATES.length]
  const required = template.required
  return {
    id: seed + 1,
    brand: template.brand,
    name: template.name,
    deviceType: template.deviceType,
    price: template.price + (seed % 5) * 10000,
    required,
    verified: (seed * 7 + 3) % (required + 1),
    liked: false,
    createdAt: 100000 - seed,
  }
}

export function buildPage(pageIndex) {
  return Array.from({ length: PAGE_SIZE }, (_, i) => buildProduct(pageIndex * PAGE_SIZE + i))
}

export function buildProductById(productId) {
  return buildProduct(Number(productId) - 1)
}

export function verificationTierOf(product) {
  if (product.verified <= 5) return VERIFICATION_TIERS[0]
  if (product.verified <= 7) return VERIFICATION_TIERS[1]
  if (product.verified <= 10) return VERIFICATION_TIERS[2]
  return VERIFICATION_TIERS[3]
}
