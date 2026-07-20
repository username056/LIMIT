const domainParts = window.location.hostname.split('.')
const rootDomain = domainParts.slice(-2).join('.') || 'l1mit.shop'
const apiOrigin = `https://api.${rootDomain}`

document.querySelector('#api-link').href = `${apiOrigin}/api/v1/`
document.querySelector('#docs-link').href = `https://docs.${rootDomain}/`
document.querySelector('#grafana-link').href = `https://grafana.${rootDomain}/`

const statusElement = document.querySelector('#api-status')
const refreshButton = document.querySelector('#refresh-status')

async function refreshStatus() {
  statusElement.className = 'status pending'
  statusElement.textContent = '확인 중'
  refreshButton.disabled = true

  try {
    const response = await fetch(`${apiOrigin}/health`, { cache: 'no-store' })
    if (!response.ok) throw new Error('Readiness request failed')

    const payload = await response.json()
    if (payload.status !== 'UP') throw new Error('Backend is not ready')

    statusElement.className = 'status up'
    statusElement.textContent = '정상'
  } catch {
    statusElement.className = 'status down'
    statusElement.textContent = '연결 실패'
  } finally {
    refreshButton.disabled = false
  }
}

refreshButton.addEventListener('click', refreshStatus)
refreshStatus()
