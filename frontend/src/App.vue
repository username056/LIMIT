<script setup>
import { resolveDemoUrl } from './demoLinks'

const swaggerUrl = resolveDemoUrl(
  'docs',
  'http://localhost:18080/swagger-ui.html',
  import.meta.env.VITE_SWAGGER_URL,
)
const grafanaUrl = resolveDemoUrl(
  'grafana',
  'http://localhost:3000',
  import.meta.env.VITE_GRAFANA_URL,
)
</script>

<template>
  <main class="demo-page">
    <nav
      class="demo-links"
      aria-label="데모 바로가기"
    >
      <a
        class="demo-link swagger"
        data-testid="swagger-link"
        :href="swaggerUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="demo-link-title">Swagger</span>
        <span class="demo-link-description">API 명세 보기 <span aria-hidden="true">↗</span></span>
      </a>
      <a
        class="demo-link grafana"
        data-testid="grafana-link"
        :href="grafanaUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="demo-link-title">Grafana</span>
        <span class="demo-link-description">모니터링 보기 <span aria-hidden="true">↗</span></span>
      </a>
    </nav>
  </main>
</template>
