# Limit 프론트엔드 스캐폴딩

한정 상품도 취급하는 전자기기 전문 중고 판매 서비스, **Limit**의 프론트엔드 초기 세팅입니다.
헤더/푸터/버튼/카드 등 공통 컴포넌트와 색상·폰트 토큰이 이미 잡혀 있어서, 각자 담당 페이지 만들 때 이걸 가져다 쓰면 스타일이 자동으로 통일됩니다.

## 디자인이 실제로 어떻게 보이는지 먼저 확인하세요

코드(`.vue` 파일)만 보면 실제로 어떤 색으로 나오는지 감이 안 올 수 있어요.
**`preview.html`을 더블클릭해서 브라우저로 열어보시면** 헤더/푸터/버튼/색상/폰트가 실제로 렌더링된 화면을 바로 볼 수 있습니다. (설치 필요 없음, 그냥 파일 더블클릭)

### 자주 쓰는 색상 클래스 (hex 몰라도 이거만 쓰면 됨)

| 클래스 | 실제 색 | 언제 쓰나 |
|---|---|---|
| `text-primary` | 밝은 블루 | 강조 텍스트, 링크 |
| `bg-primary-gradient` | 인디고→하늘색 그라데이션 | 메인 버튼 배경 |
| `bg-accent` | 아주 연한 하늘색 | 뱃지/알림바 배경 |
| `bg-surface` | 흰색 | 카드/버튼 등 기본 배경 |
| `bg-bg` | 아주 연한 회색 | 페이지 전체 배경 |
| `border-border` | 연한 회색 테두리 | 카드/인풋 테두리 |
| `text-text-main` | 짙은 남색 (검정 대신) | 본문 텍스트 |
| `text-text-sub` | 회색 | 보조 설명 텍스트 |

**직접 hex 코드(`#6C8DFF` 같은 거) 쓰지 말고 위 클래스 이름으로 쓰세요.** 그러면 나중에 색 하나 바꿀 때 파일 하나(`tokens.css`)만 고치면 전체가 다 바뀝니다.

## 폴더 구조

```
├── index.html               # 진입 HTML (Pretendard 폰트 로딩)
├── package.json
├── vite.config.js           # 빌드/개발 서버 설정, @ alias, /api 프록시(→ localhost:18080)
├── vitest.config.js         # 테스트 설정
├── eslint.config.js         # lint 설정
├── tailwind.config.js
├── postcss.config.js
├── .env.example             # 환경변수 예시 (복사해서 .env로 사용)
├── preview.html             # 디자인(색상/폰트/그라데이션) 눈으로 확인하는 정적 미리보기, 실제 프로젝트엔 안 쓰임
src/
├── main.js                  # 앱 시작점
├── App.vue                  # 루트 컴포넌트 (라우터 뷰만 포함)
├── index.css                # tailwind + 토큰 CSS 불러오는 곳
├── api/
│   ├── client.js             # 공통 API 클라이언트 (백엔드 응답 계약에 맞춰 파싱)
│   └── health.js             # 사용 예시 (GET /health)
├── router/
│   └── index.js              # 경로 목록 - 새 페이지는 여기에 등록
├── styles/
│   └── tokens.css            # 색상/radius/spacing 값
├── utils/
│   └── demoLinks.js          # 환경별 운영 도구(Swagger/Grafana/Sonar) URL 계산
├── components/
│   ├── AppHeader.vue          # 공통 헤더 (로고 LIMIT + 네비 + 검색/장바구니/유저)
│   ├── AppFooter.vue          # 공통 푸터
│   ├── AppSidebar.vue         # 사이드바 네비 (대시보드/마이페이지에서 사용)
│   ├── BaseButton.vue         # 버튼 (primary/outline/ghost)
│   ├── BaseInput.vue          # 인풋 (라벨 포함)
│   ├── BaseCard.vue           # 흰 배경 + 얇은 테두리 카드
│   ├── BaseBadge.vue          # 상태 뱃지 (정산완료/보류 등)
│   ├── BaseTable.vue          # 테이블 (헤더는 columns prop, 바디는 slot)
│   ├── BasePagination.vue     # 페이지네이션
│   ├── BaseTabs.vue           # 탭 전환
│   ├── BaseStepper.vue        # 단계 표시 (셀러 신청 등)
│   ├── BaseSelect.vue         # 드롭다운
│   ├── BaseToggle.vue         # 토글 스위치 (노출 여부 on/off 등)
│   ├── BaseDateRange.vue      # 기간 선택 (시작일~종료일)
│   ├── FileUploadBox.vue      # 파일 업로드 드롭존
│   ├── StatCard.vue           # 숫자 강조 카드 (정산 요약 등)
│   ├── charts/
│   │   ├── BarChart.vue        # 막대그래프 (Chart.js)
│   │   └── DonutChart.vue      # 도넛/게이지 (Chart.js, 중앙 % 표시)
│   └── __tests__/
│       └── BaseButton.spec.js # 렌더링 테스트 예시
├── layouts/
│   ├── DefaultLayout.vue      # 헤더+푸터 (쇼핑몰 화면용)
│   └── SidebarLayout.vue      # 헤더+사이드바+푸터 (대시보드/마이페이지용)
└── pages/
    ├── HomePage.vue            # 메인페이지 (히어로 + 상품 카드 + 인기 랭킹)
    ├── LoginPage.vue           # 로그인 페이지
    ├── SellerDashboardPage.vue # 판매자 정산/통계 대시보드
    ├── MyOrdersPage.vue        # 마이페이지 주문내역
    ├── SellerApplyPage.vue     # 셀러 신청하기
    ├── ProductManagePage.vue   # 상품 등록/관리 (토글, 기간선택, 실시간 로그 포함)
    └── AdminPage.vue           # 운영 도구(Swagger/Grafana/Sonar) 바로가기 (`/admin`)
```

## 실행 방법

```bash
cp .env.example .env
npm install
npm run dev
```

브라우저에서 `http://localhost:5173` 열면 메인페이지(`/`)와 로그인 페이지(`/login`)가 바로 보입니다.
백엔드(Spring Boot, `localhost:18080`)를 같이 띄워두면 `/api` 요청이 자동으로 프록시됩니다.

## ⚠️ 참고: 사이드바/네비바 항목은 아직 확정 아님

지금 코드에 들어간 네비바 항목(휴대폰/태블릿/노트북/카메라)이나 사이드바 항목(대시보드/상품 등록/판매 내역/정산/설정)은 **예시 데이터**입니다. 실제 카테고리/메뉴 이름이 바뀔 수 있어요.

**다행히 레이아웃 구조는 그대로 유지되니, 이름이 바뀌어도 컴포넌트를 다시 만들 필요 없습니다.** 아래처럼 배열의 텍스트만 바꾸면 끝나요:

```js
// AppHeader.vue의 navItems, 또는 각 페이지의 sidebarItems
const navItems = ['카테고리명1', '카테고리명2']  // 여기 텍스트만 교체
```

## 새 페이지(자기 담당 MVP 화면) 추가하는 법

1. `src/pages/`에 새 파일 만들기 (예: `CheckoutPage.vue`) — `LoginPage.vue` 구조 참고
2. `src/router/index.js`에 경로 한 줄 추가:
   ```js
   { path: '/checkout', name: 'checkout', component: () => import('../pages/CheckoutPage.vue') },
   ```
3. `DefaultLayout`으로 감싸고 안에 `BaseCard`/`BaseButton`/`BaseInput` 조합해서 내용 채우기
4. 색상은 직접 hex 쓰지 말고 tailwind 클래스 사용: `text-primary`, `bg-accent`, `border-border` 등
5. 컴포넌트 안에서 파일 import할 땐 `@/components/...` 형태로 쓸 수 있음 (`@`는 `src/`를 가리킴)

## API 연동하는 법

컴포넌트에서 `fetch`나 `axios`를 직접 호출하지 말고, `src/api/`를 거칩니다.

```js
// src/api/order.js (예시 - 실제 백엔드 스펙 나오면 이런 식으로 추가)
import { apiClient } from './client'

export function getOrder(orderId) {
  return apiClient.get(`/orders/${orderId}`)
}
```

`apiClient`는 백엔드 응답 계약(성공 `{ data, meta }`, 실패 `{ error: {code, message, fieldErrors}, traceId }`)을 이미 파싱해서 돌려주므로, 컴포넌트에서는 `data`만 받아 쓰면 됩니다. 실패 시 `ApiError`가 던져지니 `try/catch`로 처리하세요.

## 코드 검사 / 테스트 / 빌드

기능 변경 후 아래 두 개는 꼭 실행합니다 (팀 컨벤션):

```bash
npm run lint
npm run build
```

테스트는 작성한 테스트가 있을 때만:

```bash
npm run test
```

새 컴포넌트 만들 때는 `src/components/__tests__/BaseButton.spec.js`처럼 간단한 렌더링 테스트를 하나씩 추가하는 걸 권장합니다.

## 운영 도구(Swagger/Grafana/Sonar) 바로가기 - `/admin`

Swagger, Grafana, SonarCloud로 이동하는 링크는 `/admin` 페이지(`src/pages/AdminPage.vue`)에 모여 있습니다.
화면에서는 **footer 오른쪽 하단의 "관리자 페이지" 링크**로 들어갈 수 있습니다 (`src/components/AppFooter.vue`).

> ⚠️ 지금은 별도 인증 없이 누구나 `/admin`에 접근할 수 있습니다. 여유가 되면 관리자로 로그인해야만 접속할 수 있도록 막을 예정입니다.

## GitLab 저장소에 반영하는 법

이 폴더 내용을 저장소의 `frontend/` 폴더에 그대로 덮어쓴 뒤, MR을 올리면 됩니다.

## 사이드바가 있는 페이지 만들 때

`DefaultLayout` 대신 `SidebarLayout`을 쓰고, `sidebarItems` 배열만 넘기면 됩니다.

```vue
<script setup>
import SidebarLayout from '@/layouts/SidebarLayout.vue'

const sidebarItems = [
  { label: '대시보드', icon: '', active: true },
  { label: '설정', icon: '', active: false },
]
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    여기에 내용
  </SidebarLayout>
</template>
```

## 다음에 만들면 좋은 것

- [ ] 실제 백엔드 API 스펙 나오면 `src/api/`에 도메인별 함수 추가 (주문, 대기열, 정산 등)
- [ ] 각자 담당 MVP 화면 만들 때 이 컴포넌트들 적용
- [ ] 로딩/빈 결과/오류 상태 UI (API 연동 시작하면 필요, 팀 컨벤션에 명시된 항목)
- [ ] `/admin` 관리자 로그인 인증 적용 (현재는 누구나 접근 가능)
