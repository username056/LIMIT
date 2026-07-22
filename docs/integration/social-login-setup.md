# 소셜 로그인 설정

Google·Kakao·Naver 모두 Authorization Code 흐름을 사용한다. Client Secret과 공급자 토큰 교환은 백엔드에서만 수행한다. 프론트에는 OAuth Client ID나 Secret을 넣지 않는다.

## Callback 등록값

| 공급자 | 로컬 | 운영 |
| --- | --- | --- |
| Google | `http://localhost:5173/auth/callback/google` | `https://l1mit.shop/auth/callback/google` |
| Kakao | `http://localhost:5173/auth/callback/kakao` | `https://l1mit.shop/auth/callback/kakao` |
| Naver | `http://localhost:5173/auth/callback/naver` | `https://l1mit.shop/auth/callback/naver` |

공급자 콘솔과 백엔드 `*_OAUTH_REDIRECT_URIS`는 스킴, 호스트, 포트, 경로, 마지막 슬래시까지 정확히 일치해야 한다. 여러 URI를 허용할 때만 쉼표로 구분한다.

## 로컬 설정

`infra/.env.local`에는 실제 백엔드 키를 넣고 Git에 커밋하지 않는다.

```env
GOOGLE_OAUTH_ENABLED=true
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/google

KAKAO_OAUTH_ENABLED=true
KAKAO_OAUTH_CLIENT_ID=
KAKAO_OAUTH_CLIENT_SECRET=
KAKAO_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/kakao

NAVER_OAUTH_ENABLED=true
NAVER_OAUTH_CLIENT_ID=
NAVER_OAUTH_CLIENT_SECRET=
NAVER_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/naver

OAUTH_ATTEMPT_STORE=redis
SOCIAL_SIGNUP_STORE=redis
AUTH_COOKIE_SECURE=false
```

변수명은 `*_OAUTH_REDIRECT_URI`가 아니라 복수형 `*_OAUTH_REDIRECT_URIS`다. `*_OAUTH_ENABLED=true`가 없으면 해당 공급자는 `AUTH006`으로 거절된다. 값을 바꾼 뒤에는 백엔드 컨테이너를 다시 생성해야 한다.

프론트 `frontend/.env.local`에는 공개 URL만 둔다.

```env
VITE_API_BASE_URL=/api/v1
VITE_OAUTH_REDIRECT_BASE_URL=http://localhost:5173
VITE_TERMS_OF_SERVICE_URL=/terms/service
VITE_PRIVACY_POLICY_URL=/terms/privacy
```

로컬 Vite는 `/api`를 `localhost:18080`으로 프록시한다.

## 운영 설정

운영 백엔드 환경변수는 EC2의 권한 제한 파일 또는 CI Secret으로 관리한다. 다음 값은 로컬 값과 달라야 한다.

```env
FRONTEND_EMAIL_VERIFICATION_URL=https://l1mit.shop/verify-email
GOOGLE_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/google
KAKAO_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/kakao
NAVER_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/naver
AUTH_COOKIE_SECURE=true
```

운영 프론트 빌드에는 다음 공개값을 주입한다.

```env
VITE_API_BASE_URL=https://api.l1mit.shop/api/v1
VITE_OAUTH_REDIRECT_BASE_URL=https://l1mit.shop
VITE_TERMS_OF_SERVICE_URL=https://l1mit.shop/terms/service
VITE_PRIVACY_POLICY_URL=https://l1mit.shop/terms/privacy
```

API가 `api.l1mit.shop`처럼 별도 오리진이면 브라우저 요청에 credentials가 포함되어야 하고 Nginx는 허용된 프론트 오리진에만 `Access-Control-Allow-Credentials: true`를 반환해야 한다.

## 공급자별 확인

- Google: Web Application OAuth Client, `openid email profile`, 운영·로컬 Authorized redirect URI 등록
- Kakao: REST API 키를 Client ID로 사용, Client Secret 사용 설정과 카카오계정 이메일·프로필 동의항목 확인
- Naver: 로그인 오픈API 서비스 URL·Callback URL·이메일 제공 항목과 개발 상태 테스터 등록 확인

서버는 공급자 오류 본문과 토큰을 로그나 API 응답에 노출하지 않고 `AUTH012`로 변환한다.
