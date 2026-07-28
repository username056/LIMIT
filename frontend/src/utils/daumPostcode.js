const SCRIPT_SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

let scriptLoadPromise = null

function loadScript() {
  if (window.daum?.Postcode) return Promise.resolve()
  if (scriptLoadPromise) return scriptLoadPromise

  scriptLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_SRC
    script.onload = () => resolve()
    script.onerror = () => {
      scriptLoadPromise = null
      reject(new Error('주소 검색 서비스를 불러오지 못했습니다.'))
    }
    document.head.appendChild(script)
  })
  return scriptLoadPromise
}

/**
 * @returns {Promise<{ zonecode: string, address: string }>}
 */
export async function openDaumPostcode() {
  await loadScript()
  return new Promise((resolve) => {
    new window.daum.Postcode({
      oncomplete(data) {
        resolve({
          zonecode: data.zonecode,
          address: data.roadAddress || data.jibunAddress,
        })
      },
    }).open()
  })
}

export function formatAddress({ zonecode, address, addressDetail } = {}) {
  const parts = []
  if (zonecode) parts.push(`[${zonecode}]`)
  if (address) parts.push(address)
  if (addressDetail) parts.push(addressDetail)
  return parts.join(' ')
}
