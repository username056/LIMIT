# 테스트 계정 BaseInit 가이드

`global.bootstrap.BaseInitData`는 애플리케이션 시작 시 테스트용 일반 회원과 `OPERATOR` 관리자 계정을 한 번 생성한다. 실제 생성 규칙은 각각 auth와 admin 도메인 서비스에 있으며 `global`은 실행 순서만 조정한다.

## 안전 규칙

- 기본값은 `BASE_INIT_ENABLED=false`다.
- 이메일과 비밀번호에는 코드 기본값이 없으며 실제 값은 Git에 포함되지 않는 환경 파일이나 Secret에만 저장한다.
- 테스트 관리자는 `SUPER_ADMIN`이 아니라 `OPERATOR`로 고정한다.
- 같은 이메일이 이미 있으면 비밀번호나 개인정보를 덮어쓰지 않는다.
- 일반 회원은 이메일 인증 완료 상태로 만들고 필수 약관 동의 이력을 저장한다. 마케팅 동의는 저장하지 않는다.
- 비밀번호는 영문과 숫자를 포함한 12자 이상이어야 한다.

## 로컬 사용

`infra/.env.local`에 다음 값을 추가한다.

```env
BASE_INIT_ENABLED=true

BASE_INIT_MEMBER_ENABLED=true
BASE_INIT_MEMBER_EMAIL=
BASE_INIT_MEMBER_PASSWORD=
BASE_INIT_MEMBER_NICKNAME=
BASE_INIT_MEMBER_PHONE=

BASE_INIT_ADMIN_ENABLED=true
BASE_INIT_ADMIN_EMAIL=
BASE_INIT_ADMIN_PASSWORD=
BASE_INIT_ADMIN_NAME=
```

필요하지 않은 계정은 해당 `*_ENABLED=false`로 제외할 수 있다. 백엔드 컨테이너를 다시 생성한 뒤 로그인 성공을 확인하고 `BASE_INIT_ENABLED=false`로 되돌린다.

## 배포 환경 사용

운영 `infra/.env` 또는 CI Secret에도 동일한 변수명을 사용한다. 운영에서 계속 활성화하지 않고 다음 순서를 따른다.

1. 운영 DB를 백업한다.
2. 계정별로 겹치지 않는 이메일과 닉네임, 임시 비밀번호를 설정한다.
3. `BASE_INIT_ENABLED=true`로 백엔드를 한 번 배포한다.
4. 회원 로그인과 관리자 로그인을 확인한다.
5. 즉시 `BASE_INIT_ENABLED=false`로 되돌려 다시 배포한다.
6. 공유가 끝난 임시 비밀번호는 노출된 것으로 간주하고 계정을 삭제하거나 비밀번호 변경 API로 교체한다.

BaseInit은 스키마를 만들지 않는다. Flyway migration과 `ddl-auto=validate`가 모두 성공한 뒤에만 실행된다.
