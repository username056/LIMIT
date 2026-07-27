# ADR-0002: 단일 EC2 Docker Compose와 Blue-Green 배포

- 상태: Accepted
- 결정일: 2026-07-18

## 배경

Limit은 4 vCPU, 15 GiB RAM의 단일 EC2에서 기존 Apache/Gerrit을 보존하면서 Spring Boot와 데이터 계층을 운영해야 한다. Kubernetes와 Swarm은 이 규모에서 운영 비용과 자원 사용이 더 크다.

## 결정

- GitLab CI가 테스트·품질·이미지 빌드·배포를 조정하고 실제 로직은 `scripts/`에 둔다.
- Vue 정적 산출물 `frontend/dist`는 S3 + CloudFront에 배포한다.
- Spring Boot는 Docker Compose의 Blue(`127.0.0.1:8081`)와 Green(`127.0.0.1:8082`) 중 하나만 프록시 upstream으로 사용한다.
- MySQL, MongoDB, Redis는 `internal` Compose 네트워크에 두며 Blue-Green으로 복제하지 않는다. 벡터 데이터는 별도 Qdrant 없이 MongoDB에 저장한다.
- 운영 443 진입점은 Nginx를 우선안으로 한다. 기존 Apache/Gerrit 8989는 변경하지 않는다. 실제 적용 전 443 listener와 Apache include 구성을 서버에서 다시 확인한다.
- `dev`, `main` 브랜치 병합 시 검증된 registry digest를 자동 배포하며 readiness 또는 smoke test 실패 시 기존 색상을 유지하거나 복구한다. `main`은 프로젝트 종료 시점의 최종 병합에만 사용한다.

## 결과와 제한

- readiness 또는 smoke 실패 시 기존 색상은 계속 요청을 처리한다.
- 단일 EC2 장애, 호스트 Nginx 장애, 데이터 볼륨 장애는 Blue-Green으로 복구되지 않는다.
- Nginx 설치·인증서·UFW·Docker·Swap·재부팅 및 실제 배포는 별도 승인이 필요하다.
