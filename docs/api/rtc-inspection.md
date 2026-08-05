# 선택적 1:1 실시간 확인

## 범위

- 채팅방의 구매자·판매자만 영상 확인을 요청하고 수락·거절할 수 있다.
- 제안자는 상대가 응답하기 전에 약속 시간·메모를 변경하거나 약속을 취소할 수 있다.
- 수락 시 상품과 연결된 WebRTC 세션을 만들고 판매자가 offer를 생성한다.
- SDP, ICE candidate, 특정 부위 확인 요청은 WebSocket으로 상대 참가자에게만 중계한다.
- 통화 종료 시 확인한 체크리스트 항목과 항목별 메모, 전체 메모를 저장한다.
- 전체 통화 녹화, 미디어 파일 저장, 서버 미디어 중계는 MVP 범위에서 제외한다.

## API

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/v1/chat-rooms/{roomId}/calls` | 영상 확인 요청 |
| GET | `/api/v1/calls` | 내 요청 목록 |
| GET | `/api/v1/calls/{callId}` | 요청 상세 |
| POST | `/api/v1/calls/{callId}/response` | 상대방의 수락·거절 |
| PATCH | `/api/v1/calls/{callId}` | 제안자의 약속 시간·메모 변경 |
| DELETE | `/api/v1/calls/{callId}?reason={reason}` | 제안자의 약속 취소. 사유는 선택 |
| GET | `/api/v1/rtc-sessions/{sessionId}` | 세션·상품 체크리스트 조회 |
| POST | `/api/v1/rtc-sessions/{sessionId}/join` | 최초 입장·재입장용 2분 단기 토큰 발급 |
| POST | `/api/v1/rtc-sessions/{sessionId}/connected` | P2P 또는 TURN 연결 성공 기록 |
| POST | `/api/v1/rtc-sessions/{sessionId}/end` | 체크리스트·메모 저장 후 현재 연결 종료. 기존 세션 만료 시각은 유지 |
| WS | `/ws/rtc?token=...` | offer, answer, ICE, 재협상, 부위 요청, 종료 중계 |

모든 REST 성공 응답은 `{ "data": ..., "meta": null }` 형식이다. WebSocket 입장 토큰은 REST 인증 후 발급되며 한 번 사용하면 즉시 폐기된다.

`GET /api/v1/calls` 응답은 일정 시각 `scheduledAt`, 상대 닉네임 `counterpartName`,
세션 만료 시각 `sessionExpiresAt`을 포함한다. 같은 채팅방에 `PROPOSED` 또는 `ACCEPTED`
일정이 있으면 새 일정 생성은 `RTC006`으로 거절하고 기존 일정이 있음을 안내한다.

## 연결 실패와 재입장

1. 브라우저의 ICE 연결이 `failed`가 되면 `restartIce()`와 새 offer를 시도한다.
2. 시그널링 연결이 끊기거나 복구되지 않으면 사용자가 재입장 버튼으로 새 단기 토큰을 발급받는다.
3. 같은 회원이 재입장하면 서버는 해당 회원의 이전 WebSocket을 닫고 최신 연결로 교체한다.
4. 상대가 나가면 `peer-left`, 특정 확인 요청은 `inspection-request` 이벤트로 전달한다.
5. 한 참가자가 종료하면 현재 P2P 연결만 끊는다. 재입장은 최초 약속에 설정된 세션 만료 시각까지만 허용한다.
6. 검수 결과를 제출하고 연결을 종료한 시각은 `inspectionSubmittedAt`으로 별도 보존한다. 이후 세션이 만료돼도 이 값은 유지되며 화면에서는 `검수 완료`로 표시한다.
   유예 시간이 지나면 분 단위 만료 스캔 또는 다음 입장 시점에 `EXPIRED`로 전환한다.

## 환경 계약

- `RTC_ALLOWED_ORIGIN_PATTERNS`: 허용 프론트 Origin 목록
- `RTC_STUN_URL`: 기본 STUN URL
- `RTC_TURN_URL`, `RTC_TURN_USERNAME`, `RTC_TURN_CREDENTIAL`: 선택 TURN 설정
- `RTC_TURN_REALM`: Coturn 장기 자격증명 realm. 운영 기본값은 `l1mit.shop`
- `VITE_WS_BASE_URL`: 프론트 시그널링 서버 주소. 미설정 시 API 또는 현재 Origin 사용

운영 Nginx는 `/ws/rtc`의 Upgrade/Connection 헤더를 전달하고 읽기 제한 시간을 1시간으로 설정한다.

## 수치와 트러블슈팅 기록

- 백엔드 단위·컨트롤러 테스트: 133건에서 139건으로 6건 증가
- 프론트 테스트: 38건에서 40건으로 2건 증가
- MySQL 통합 테스트: 13건 통과, 신규 테이블·컬럼·인덱스 검증 포함
- 시그널 메시지 최대 크기: 64 KiB
- 입장 토큰 유효시간: 2분, 단일 사용
- 세션 기본 입장 가능시간: 수락 후 2시간
- Coturn 릴레이 UDP 포트: `49160-49200` 41개로 제한
- Grafana Canvas 노드: 16개에서 18개로 2개 증가(Coturn 본체·상태)
- Prometheus Canvas 상태 대상: 6개에서 7개로 1개 증가
- 운영 Coturn 최초 기동 부하: CPU `0.04%`, 메모리 `7.211 MiB / 256 MiB`
- 운영 Prometheus 수집 상태: `up{job="coturn"}=1`

발생한 문제와 조치:

1. Spring WebSocket 현재 버전에 대용량 메시지 종료 상수가 없어 컴파일이 실패했다. 표준 종료 코드 1009를 명시해 해결했다.
2. 데이터소스를 제외하는 기존 컨텍스트 테스트가 신규 RTC 서비스 저장소 빈을 찾지 못했다. 해당 테스트 컨텍스트에서는 RTC 서비스를 mock으로 격리했다.
3. Flyway 통합 테스트의 최신 버전 기대값이 이전 날짜로 남아 실패했다. 신규 버전 `20260728`로 갱신했다.
4. 단독 Nginx 문법 검증 컨테이너는 운영 upstream DNS와 TLS 인증서가 없어 전체 `nginx -t`를 완료하지 못했다. WebSocket location은 기존 운영 upstream·TLS 계약을 그대로 사용하며 실제 배포 전 서버에서 `nginx -t`가 필요하다.
5. 샌드박스에서는 Gradle 배포본 다운로드가 소켓 권한으로 차단됐다. 네트워크가 허용된 검증 환경에서 동일 명령을 재실행해 전체 빌드가 성공했다.
6. Windows PowerShell은 `npm.ps1` 실행과 따옴표 없는 `-Dopenapi.output=...` 인자를 각각 차단·오해석했다. `npm.cmd`와 따옴표로 묶은 Gradle 시스템 속성으로 재실행해 프론트 검증 및 OpenAPI 내보내기를 완료했다.
7. 공식 Coturn 이미지에는 `wget`가 없어 컨테이너 내부 헬스체크가 실행되지 않았다. Prometheus 컨테이너가 Coturn의 `9641` 엔드포인트를 확인하는 방식으로 변경했다.
8. 메트릭 점검 시 PowerShell 파이프가 앞부분만 읽고 닫혀 명령 종료 코드는 1이었지만, `turn_traffic_*` 지표 출력은 정상 확인됐다.
9. Windows에서 `scp -r`로 운영 모니터링 폴더를 동기화하자 디렉터리 권한이 `700`이 되어 Prometheus가 설정을 읽지 못했다. 디렉터리 `755`, 파일 `644`로 복구했고 배포 스크립트가 매번 이를 표준화하도록 보강했다.
10. 운영 Coturn·Prometheus·Grafana 기동과 Canvas 반영은 완료했지만 UFW TURN 규칙 변경은 별도 위험 승인 없이 수행하지 않았다. 현재 외부 TURN 포트는 차단 상태다.

## 남은 운영 위험

- 현재 Coturn은 MVP용 고정 장기 자격증명을 사용한다. 외부 노출 전 보안 그룹·UFW를 지정 포트로만 제한하고, 후속 단계에서 TURN REST API 기반 단기 credential로 교체해야 한다.
- 시그널링 참가자 맵과 입장 토큰은 현재 단일 애플리케이션 인스턴스 메모리에 있다. 동시에 여러 백엔드 인스턴스가 트래픽을 받게 되면 sticky routing 또는 Redis Pub/Sub 기반 시그널링으로 확장해야 한다.
