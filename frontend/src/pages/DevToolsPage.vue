<script setup>
import { resolveDemoUrl } from '../utils/demoLinks'

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
const sonarUrl = import.meta.env.VITE_SONAR_URL || 'https://sonarcloud.io/project/overview?id=limit'
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
      <a
        class="demo-link sonar"
        data-testid="sonar-link"
        :href="sonarUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="demo-link-title">SonarCloud</span>
        <span class="demo-link-description">코드 품질 보기 <span aria-hidden="true">↗</span></span>
      </a>
    </nav>
  </main>
</template>

<style scoped>
.demo-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
}

.demo-links {
  display: grid;
  width: min(100%, 346px);
  gap: 12px;
}

.demo-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  color: #172033;
  background: #f8fafc;
  text-decoration: none;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.demo-link:hover {
  border-color: #93c5fd;
  box-shadow: 0 10px 24px rgb(37 99 235 / 10%);
  transform: translateY(-2px);
}

.demo-link:focus-visible {
  outline: 3px solid rgb(37 99 235 / 28%);
  outline-offset: 2px;
}

.demo-link-title {
  font-weight: 800;
}

.demo-link-description {
  color: #64748b;
  font-size: 0.875rem;
  white-space: nowrap;
}

.demo-link.swagger .demo-link-title {
  color: #047857;
}

.demo-link.grafana .demo-link-title {
  color: #c2410c;
}

.demo-link.sonar .demo-link-title {
  color: #7c3aed;
}
</style>
