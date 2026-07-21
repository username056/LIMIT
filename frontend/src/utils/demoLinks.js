export function resolveDemoUrl(
  subdomain,
  localUrl,
  configuredUrl,
  currentLocation = window.location,
) {
  if (configuredUrl) return configuredUrl

  const { hostname, protocol } = currentLocation
  if (hostname === 'localhost' || hostname === '127.0.0.1') return localUrl

  const rootDomain = hostname.split('.').slice(-2).join('.')
  return `${protocol}//${subdomain}.${rootDomain}`
}
