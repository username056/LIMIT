// 가격 입력은 증감 화살표가 붙는 type="number" 대신 문자 입력으로 다룹니다.
// 값은 숫자만 담고 화면에는 천 단위 쉼표를 붙여 보여주기 위해, 변환을 한곳에 모아 둡니다.
export const MAX_PRICE_DIGITS = 12

export function toPriceDigits(value, maxDigits = MAX_PRICE_DIGITS) {
  return String(value ?? '').replace(/[^0-9]/g, '').slice(0, maxDigits)
}

export function formatPriceDigits(digits) {
  if (digits === '' || digits === null || digits === undefined) return ''
  return Number(digits).toLocaleString('ko-KR')
}
