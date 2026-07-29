CREATE TABLE `call_appointment` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`appointment_key`	CHAR(36)	NOT NULL	COMMENT '외부 노출용 키',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '소속 채팅방 ID',
	`proposer_id`	BIGINT	NOT NULL	COMMENT '제안 회원 ID',
	`respondent_id`	BIGINT	NOT NULL	COMMENT '응답 회원 ID',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'PROPOSED'	COMMENT '약속 상태 (ENUM: PROPOSED|ACCEPTED|REJECTED|CANCELED|COMPLETED)',
	`scheduled_at`	DATETIME(3)	NOT NULL	COMMENT '예정 시각',
	`memo`	VARCHAR(500)	NULL	COMMENT '메모',
	`cancel_reason`	VARCHAR(500)	NULL	COMMENT '취소 사유',
	`version`	BIGINT	NOT NULL	DEFAULT 0	COMMENT '낙관적 락',
	`responded_at`	DATETIME(3)	NULL	COMMENT '응답 시각',
	`canceled_at`	DATETIME(3)	NULL	COMMENT '취소 시각',
	`completed_at`	DATETIME(3)	NULL	COMMENT '완료 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각'
);

CREATE TABLE `withdrawal_request` (
	`withdrawal_request_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '탈퇴 요청 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '요청 회원 ID',
	`withdrawal_reason`	VARCHAR(500)	NOT NULL	COMMENT '탈퇴 사유',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'REQUESTED'	COMMENT '탈퇴 요청 상태',
	`blocked_reason`	VARCHAR(500)	NULL	COMMENT '탈퇴 보류 사유',
	`requested_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '요청 시각',
	`processed_at`	DATETIME(6)	NULL	COMMENT '처리 완료 시각',
	`processed_admin_id`	BIGINT	NULL	COMMENT '처리 관리자 ID'
);

CREATE TABLE `chat_room` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '연결된 매물 ID (원본 product_id)',
	`transaction_id`	BIGINT	NULL	COMMENT '연결된 결제 ID (원본 transaction_id → payment.id)',
	`buyer_id`	BIGINT	NOT NULL	COMMENT '구매자 회원 ID',
	`seller_id`	BIGINT	NOT NULL	COMMENT '판매자 회원 ID',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '채팅방 상태 (ENUM: ACTIVE|CLOSED)',
	`last_message_id`	BIGINT	NULL	COMMENT '마지막 메시지 ID (순환 참조, ALTER TABLE로 추가)',
	`last_message_seq`	BIGINT	NOT NULL	DEFAULT 0	COMMENT '마지막 메시지 시퀀스',
	`last_message_at`	DATETIME(3)	NULL	COMMENT '마지막 메시지 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각',
	`closed_at`	DATETIME(3)	NULL	COMMENT '종료 시각',
	CONSTRAINT `PK_CHAT_ROOM` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CHAT_ROOM_LISTING_USERS` UNIQUE (`listing_id`, `buyer_id`, `seller_id`)
);

CREATE TABLE `rtc_session_summary` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`rtc_session_id`	BIGINT	NOT NULL	COMMENT '대상 세션 ID',
	`connection_setup_ms`	BIGINT	NULL	COMMENT '연결 성립까지 걸린 시간(ms)',
	`used_turn`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT 'TURN 경유 여부',
	`avg_rtt_ms`	DECIMAL(10, 3)	NULL	COMMENT '평균 RTT(ms)',
	`max_rtt_ms`	DECIMAL(10, 3)	NULL	COMMENT '최대 RTT(ms)',
	`avg_packet_loss_rate`	DECIMAL(8, 6)	NULL	COMMENT '평균 패킷 손실률',
	`avg_jitter_ms`	DECIMAL(10, 3)	NULL	COMMENT '평균 지터(ms)',
	`avg_inbound_bitrate_kbps`	DECIMAL(12, 3)	NULL	COMMENT '평균 수신 비트레이트(kbps)',
	`avg_fps`	DECIMAL(6, 2)	NULL	COMMENT '평균 FPS',
	`ice_restart_count`	INT	NOT NULL	DEFAULT 0	COMMENT 'ICE 재시작 횟수',
	`reconnect_count`	INT	NOT NULL	DEFAULT 0	COMMENT '재연결 횟수',
	`recovery_succeeded`	BOOLEAN	NULL	COMMENT '복구 성공 여부',
	`calculated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '집계 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각'
);

CREATE TABLE `checklist_template_item` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '템플릿 항목 고유 식별자',
	`checklist_template_id`	BIGINT	NOT NULL	COMMENT '소속 템플릿 ID',
	`item_code`	VARCHAR(30)	NOT NULL	COMMENT '항목 코드(예: SP-EXT-001)',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '항목명',
	`purpose`	VARCHAR(200)	NOT NULL	COMMENT '확인 목적',
	`capture_guide`	TEXT	NOT NULL	COMMENT '판매자 촬영 안내',
	`evidence_type`	VARCHAR(30)	NOT NULL	COMMENT '증거 유형 (ENUM: PHOTO|VIDEO|DIAGNOSTIC_FILE|SELLER_CONFIRMATION)',
	`automation_type`	VARCHAR(30)	NOT NULL	DEFAULT 'NONE'	COMMENT '검수 자동화 유형 (ENUM: NONE|FILE_PARSE|OCR)',
	`parser_type`	VARCHAR(50)	NULL	COMMENT 'battery_report, dxdiag 등. 없으면 NULL',
	`is_required`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '필수 여부. 필수 미완료 시 판매 게시 불가',
	`allowed_formats`	VARCHAR(100)	NULL	COMMENT '허용 파일 형식(콤마 구분)',
	`min_count`	INT	NULL	COMMENT '사진 최소 개수',
	`max_count`	INT	NULL	COMMENT '사진 최대 개수',
	`min_duration_sec`	INT	NULL	COMMENT '영상 최소 길이(초)',
	`max_duration_sec`	INT	NULL	COMMENT '영상 최대 길이(초)',
	`max_file_size_mb`	INT	NULL	COMMENT '파일 최대 용량(MB)',
	`visible_to_buyer`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '구매자 공개 여부',
	`privacy_masking_required`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '개인정보 마스킹 필요 여부',
	`display_order`	INT	NOT NULL	DEFAULT 0	COMMENT '정렬 순서'
);

CREATE TABLE `listing` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '매물 고유 식별자',
	`seller_id`	BIGINT	NOT NULL	COMMENT '판매자 회원 ID',
	`category_id`	BIGINT	NOT NULL	COMMENT '매물이 속한 리프(모델) 카테고리',
	`title`	VARCHAR(200)	NOT NULL	COMMENT '매물 제목',
	`description`	TEXT	NULL	COMMENT '매물 상세 설명',
	`price`	INT	NOT NULL	COMMENT '판매 가격',
	`checklist_template_id`	BIGINT	NOT NULL	COMMENT '등록 시점 적용 템플릿(스냅샷 고정)',
	`precheck_completed`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '판매 전 계정 제거 안내 자가 체크(경고성)',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'DRAFT'	COMMENT '매물 라이프사이클 상태 (ENUM: DRAFT|ON_SALE|RESERVED|PAID|INSPECTING|CONFIRMED|SETTLED|CANCELLED|HIDDEN|SUSPENDED)',
	`suspended_reason`	VARCHAR(200)	NULL	COMMENT '관리자 제재 사유',
	`buyer_id`	BIGINT	NULL	COMMENT '거래 중 구매자',
	`reserved_at`	DATETIME	NULL	COMMENT '예약 시각',
	`reserved_until`	DATETIME	NULL	COMMENT '예약 만료 판정 기준 시각. 결제 유예 적용 시 이 값만 연장된다',
	`paid_at`	DATETIME	NULL	COMMENT '결제 승인 시각(에스크로)',
	`confirmed_at`	DATETIME	NULL	COMMENT '구매확정 시각',
	`settled_at`	DATETIME	NULL	COMMENT '정산 완료 시각',
	`version`	BIGINT	NOT NULL	DEFAULT 0	COMMENT '낙관적 잠금(동시 예약 방지)',
	`deleted_at`	DATETIME	NULL	COMMENT '논리 삭제 시각. SETTLED 매물은 삭제 금지',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '등록 시각',
	`updated_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '마지막 수정 시각'
);

CREATE TABLE `listing_account_removal_check` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '대상 매물 ID',
	`guide_id`	BIGINT	NOT NULL	COMMENT '등록 시점 가이드 버전 고정',
	`seller_confirmed`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '판매자 자가체크 완료 여부',
	`confirmed_at`	DATETIME	NULL	COMMENT '체크 완료 시각',
	`screenshot_url`	VARCHAR(500)	NULL	COMMENT '선택 업로드 스크린샷'
);

CREATE TABLE `dxdiag_result` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`evidence_id`	BIGINT	NOT NULL	COMMENT '대상 증거 ID (evidence_type=DIAGNOSTIC_FILE)',
	`cpu`	VARCHAR(100)	NULL	COMMENT 'CPU 정보',
	`memory`	VARCHAR(50)	NULL	COMMENT '메모리 정보',
	`gpu`	VARCHAR(100)	NULL	COMMENT 'GPU 정보',
	`gpu_memory`	VARCHAR(30)	NULL	COMMENT 'GPU 메모리',
	`driver_version`	VARCHAR(50)	NULL	COMMENT '드라이버 버전',
	`sound_device`	VARCHAR(100)	NULL	COMMENT '사운드 장치',
	`parser_version`	VARCHAR(20)	NOT NULL	COMMENT '파서 버전',
	`parse_status`	VARCHAR(30)	NOT NULL	COMMENT '파싱 결과 상태 (ENUM: SUCCESS|FAILED|PARTIAL)',
	`parsed_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '파싱 시각'
);

CREATE TABLE `reinspection_request_item` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '내부 식별자',
	`reinspection_request_id`	BIGINT	NOT NULL	COMMENT '재검수 요청 ID',
	`listing_checklist_item_id`	BIGINT	NOT NULL	COMMENT '구매자가 선택한 체크리스트 항목 ID',
	`item_name_snapshot`	VARCHAR(100)	NOT NULL	COMMENT '채팅 이력 보존용 항목명 스냅샷',
	`request_content`	VARCHAR(1000)	NOT NULL	COMMENT '해당 항목에 대한 구매자 요청 내용',
	`display_order`	INT	NOT NULL	DEFAULT 0	COMMENT '채팅 표시 순서',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE `rtc_session` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`session_key`	CHAR(36)	NOT NULL	COMMENT '외부 노출용 키',
	`call_appointment_id`	BIGINT	NULL	COMMENT '연결된 약속 ID',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '소속 채팅방 ID',
	`listing_id`	BIGINT	NOT NULL	COMMENT '대상 매물 ID (원본 product_id)',
	`transaction_id`	BIGINT	NULL	COMMENT '연결된 결제 ID (원본 transaction_id → payment.id)',
	`seller_id`	BIGINT	NOT NULL	COMMENT '판매자 회원 ID(송출자)',
	`buyer_id`	BIGINT	NOT NULL	COMMENT '구매자 회원 ID(수신자)',
	`media_direction`	VARCHAR(30)	NOT NULL	DEFAULT 'SELLER_TO_BUYER'	COMMENT '미디어 송출 방향(판매자 카메라 전용) (ENUM: SELLER_TO_BUYER)',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'CREATED'	COMMENT '세션 상태 (ENUM: CREATED|WAITING|CONNECTING|CONNECTED|ENDED|FAILED|EXPIRED)',
	`connection_type`	VARCHAR(30)	NOT NULL	DEFAULT 'UNKNOWN'	COMMENT '연결 경로 (ENUM: UNKNOWN|P2P|TURN)',
	`end_reason`	VARCHAR(30)	NULL	COMMENT '종료 사유 (ENUM: NORMAL|SELLER_LEFT|BUYER_LEFT|TIMEOUT|CONNECTION_FAILED|CANCELED|SYSTEM_ERROR)',
	`expires_at`	DATETIME(3)	NOT NULL	COMMENT '만료 시각',
	`connected_at`	DATETIME(3)	NULL	COMMENT '연결 성립 시각',
	`ended_at`	DATETIME(3)	NULL	COMMENT '종료 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각'
);

CREATE TABLE `reinspection_request_message` (
	`event_type`	VARCHAR(30)	NOT NULL	COMMENT '채팅 알림 종류 (ENUM: REQUESTED|COMPLETED|CANCELED)',
	`reinspection_request_id`	BIGINT	NOT NULL	COMMENT '재검수 요청 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '자동 생성된 SYSTEM 채팅 메시지 ID',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE `chat_message_media` (
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '대상 메시지 ID',
	`chat_media_id`	BIGINT	NOT NULL	COMMENT '대상 미디어 ID',
	`display_order`	TINYINT	NOT NULL	DEFAULT 1	COMMENT '노출 순서',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각'
);

CREATE TABLE `chat_media` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`media_key`	CHAR(36)	NOT NULL	COMMENT '외부 노출용 키',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '소속 채팅방 ID',
	`uploader_id`	BIGINT	NOT NULL	COMMENT '업로드 회원 ID',
	`media_type`	VARCHAR(30)	NOT NULL	COMMENT '미디어 유형 (ENUM: IMAGE|VIDEO)',
	`upload_status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '업로드 상태 (ENUM: PENDING|UPLOADED|VERIFIED|FAILED)',
	`bucket_name`	VARCHAR(100)	NOT NULL	COMMENT '버킷명',
	`object_key`	VARCHAR(1024)	NOT NULL	COMMENT '오브젝트 키',
	`original_filename`	VARCHAR(255)	NOT NULL	COMMENT '원본 파일명',
	`mime_type`	VARCHAR(100)	NOT NULL	COMMENT 'MIME 타입',
	`file_size_bytes`	BIGINT	NOT NULL	COMMENT '파일 크기(byte)',
	`width_px`	INT	NULL	COMMENT '가로 픽셀',
	`height_px`	INT	NULL	COMMENT '세로 픽셀',
	`duration_ms`	BIGINT	NULL	COMMENT '재생 시간(ms)',
	`upload_expires_at`	DATETIME(3)	NOT NULL	COMMENT '업로드 URL 만료 시각',
	`verified_at`	DATETIME(3)	NULL	COMMENT '검증 완료 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각'
);

CREATE TABLE `chat_room_participant` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '소속 채팅방 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '참여 회원 ID',
	`participant_role`	VARCHAR(30)	NOT NULL	COMMENT '참여 역할 (ENUM: BUYER|SELLER)',
	`last_read_seq`	BIGINT	NOT NULL	DEFAULT 0	COMMENT '마지막으로 읽은 시퀀스',
	`joined_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '참여 시각',
	`left_at`	DATETIME(3)	NULL	COMMENT '퇴장 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각',
	CONSTRAINT `PK_CHAT_ROOM_PARTICIPANT` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CHAT_ROOM_PARTICIPANT_USER` UNIQUE (`chat_room_id`, `user_id`),
	CONSTRAINT `UK_CHAT_ROOM_PARTICIPANT_ROLE` UNIQUE (`chat_room_id`, `participant_role`),
	CONSTRAINT `FK_CHAT_ROOM_PARTICIPANT_ROOM` FOREIGN KEY (`chat_room_id`)
		REFERENCES `chat_room` (`id`)
);

CREATE TABLE `payment` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '대상 매물 ID',
	`buyer_id`	BIGINT	NOT NULL	COMMENT '결제 회원(구매자) ID',
	`payment_version`	INT	NOT NULL	DEFAULT 1	COMMENT '낙관적 락(동시 결제 방지)',
	`idempotency_key`	VARCHAR(100)	NOT NULL	COMMENT '멱등키',
	`attempt_no`	INT	NOT NULL	DEFAULT 1	COMMENT '동일 거래 재시도 횟수',
	`pg_provider`	VARCHAR(20)	NOT NULL	DEFAULT 'TOSS'	COMMENT 'PG사',
	`method`	VARCHAR(20)	NULL	COMMENT '결제수단',
	`provider_transaction_id`	VARCHAR(200)	NULL	COMMENT 'PG사 거래 ID',
	`webhook_verified`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT 'PG 웹훅 서명 검증 여부',
	`requested_amount`	DECIMAL(12, 2)	NOT NULL	COMMENT '요청 금액',
	`approved_amount`	DECIMAL(12, 2)	NULL	COMMENT '승인 금액',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'REQUESTED'	COMMENT '결제 상태 (ENUM: REQUESTED|APPROVED|FAILED|EXPIRED)',
	`failed_reason`	VARCHAR(255)	NULL	COMMENT '실패 사유',
	`requested_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '요청 시각',
	`approved_at`	DATETIME	NULL	COMMENT '승인 시각',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '생성 시각',
	`updated_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '수정 시각'
);

CREATE TABLE `login_attempt_log` (
	`log_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '로그 고유 ID',
	`account_type`	VARCHAR(20)	NOT NULL	COMMENT '로그인 대상 계정 유형',
	`user_id`	BIGINT	NULL	COMMENT '회원 로그인 시도 대상 ID',
	`admin_id`	BIGINT	NULL	COMMENT '관리자 로그인 시도 대상 ID',
	`ip_address`	VARCHAR(45)	NOT NULL	COMMENT '요청 IP',
	`success`	BOOLEAN	NOT NULL	COMMENT '성공 여부',
	`failure_reason`	VARCHAR(50)	NULL	COMMENT '실패 사유 코드',
	`attempted_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '시도 시각'
);

CREATE TABLE `ocr_result` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`evidence_id`	BIGINT	NOT NULL	COMMENT '대상 증거 ID (evidence_type=VIDEO, 종합진단 화면)',
	`field_type`	VARCHAR(30)	NOT NULL	COMMENT '인식 필드 종류 (ENUM: CPU|MODEL_NAME|RAM|GPU|OS_VERSION|OTHER)',
	`raw_text`	TEXT	NULL	COMMENT '원본 인식 텍스트',
	`parsed_value`	VARCHAR(200)	NULL	COMMENT '정규화된 값',
	`confidence`	DECIMAL(4, 3)	NULL	COMMENT '확신도 (0.000~1.000)',
	`ocr_model_version`	VARCHAR(30)	NOT NULL	COMMENT 'OCR 모델/엔진 버전',
	`detected_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '인식 시각'
);

CREATE TABLE `listing_checklist_item` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '스냅샷 항목 고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '소속 매물 ID',
	`template_item_id`	BIGINT	NOT NULL	COMMENT '원본 템플릿 항목 ID',
	`item_code`	VARCHAR(30)	NOT NULL	COMMENT '항목 코드(스냅샷 복사)',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '항목명(스냅샷 복사)',
	`capture_guide`	TEXT	NOT NULL	COMMENT '촬영 안내(스냅샷 복사)',
	`evidence_type`	VARCHAR(30)	NOT NULL	COMMENT '증거 유형 (ENUM: PHOTO|VIDEO|DIAGNOSTIC_FILE|SELLER_CONFIRMATION)',
	`automation_type`	VARCHAR(30)	NOT NULL	DEFAULT 'NONE'	COMMENT '검수 자동화 유형(스냅샷 복사) (ENUM: NONE|FILE_PARSE|OCR)',
	`parser_type`	VARCHAR(50)	NULL	COMMENT 'battery_report, dxdiag 등(스냅샷 복사)',
	`is_required`	BOOLEAN	NOT NULL	COMMENT '필수 여부',
	`allowed_formats`	VARCHAR(100)	NULL	COMMENT '허용 파일 형식',
	`min_count`	INT	NULL	COMMENT '사진 최소 개수',
	`max_count`	INT	NULL	COMMENT '사진 최대 개수',
	`min_duration_sec`	INT	NULL	COMMENT '영상 최소 길이(초)',
	`max_duration_sec`	INT	NULL	COMMENT '영상 최대 길이(초)',
	`max_file_size_mb`	INT	NULL	COMMENT '파일 최대 용량(MB)',
	`visible_to_buyer`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '구매자 공개 여부',
	`privacy_masking_required`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '개인정보 마스킹 필요 여부',
	`completion_status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '항목 완료 상태 (ENUM: PENDING|SUBMITTED|COMPLETED)',
	`display_order`	INT	NOT NULL	DEFAULT 0	COMMENT '정렬 순서'
);

CREATE TABLE `user_address` (
	`address_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '배송지 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '배송지 소유 회원 ID',
	`recipient_name`	VARCHAR(50)	NOT NULL	COMMENT '수령인 이름',
	`phone`	VARCHAR(20)	NOT NULL	COMMENT '수령인 연락처',
	`country_code`	CHAR(2)	NOT NULL	DEFAULT 'KR'	COMMENT 'ISO 3166-1 alpha-2 국가 코드',
	`address_line`	VARCHAR(255)	NOT NULL	COMMENT '상세 주소',
	`postal_code`	VARCHAR(20)	NOT NULL	COMMENT '우편번호',
	`is_default`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '기본 배송지 여부',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '등록 시각',
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '마지막 수정 시각'
);

CREATE TABLE `account_removal_guide` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`device_type`	VARCHAR(30)	NOT NULL	COMMENT '적용 기기 대분류 (category.device_type 재사용) (ENUM: SMARTPHONE|FOLDABLE|TABLET|LAPTOP)',
	`manufacturer`	VARCHAR(50)	NOT NULL	COMMENT '제조사',
	`template_version`	INT	NOT NULL	DEFAULT 1	COMMENT '가이드 버전',
	`steps`	JSON	NOT NULL	COMMENT '단계별 가이드 콘텐츠',
	`disclaimer_text`	TEXT	NOT NULL	COMMENT '고지 문구',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '생성일시'
);

CREATE TABLE `member_sanction` (
	`sanction_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '제재 이력 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '제재 대상 회원 ID',
	`restriction_type`	VARCHAR(30)	NOT NULL	COMMENT '제한 유형',
	`reason_code`	VARCHAR(50)	NOT NULL	COMMENT '제재 사유 코드',
	`reason_detail`	VARCHAR(500)	NOT NULL	COMMENT '제재 상세 사유',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '제재 상태',
	`start_at`	DATETIME(6)	NOT NULL	COMMENT '제재 시작 시각',
	`end_at`	DATETIME(6)	NOT NULL	COMMENT '제재 종료 예정 시각',
	`admin_id`	BIGINT	NOT NULL	COMMENT '제재 등록 관리자 ID',
	`released_at`	DATETIME(6)	NULL	COMMENT '수동 해제 시각',
	`release_admin_id`	BIGINT	NULL	COMMENT '해제 관리자 ID',
	`release_reason`	VARCHAR(500)	NULL	COMMENT '해제 사유',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '등록 시각',
	`extra_field`	VARCHAR(255)	NULL	COMMENT '원본 DDL에 있던 미상 컬럼 — 용도 확인 후 제거/활용 결정 필요'
);

CREATE TABLE `inquiry_answer` (
	`answer_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '답변 버전 고유 ID',
	`inquiry_id`	BIGINT	NOT NULL	COMMENT '대상 문의 ID',
	`version`	INT	NOT NULL	COMMENT '답변 버전(1부터 증가)',
	`content`	TEXT	NOT NULL	COMMENT '답변 내용',
	`is_current`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '최신 답변 여부',
	`admin_id`	BIGINT	NOT NULL	COMMENT '답변 등록 관리자 ID',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '등록 시각'
);

CREATE TABLE `admin_action_log` (
	`log_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '감사 로그 고유 ID',
	`admin_id`	BIGINT	NOT NULL	COMMENT '작업 수행 관리자 ID',
	`action_type`	VARCHAR(50)	NOT NULL	COMMENT '작업 유형',
	`target_type`	VARCHAR(50)	NOT NULL	COMMENT '작업 대상 자원 유형',
	`target_id`	BIGINT	NOT NULL	COMMENT '대상 자원 ID (target_type에 따른 다형 참조, FK 없음)',
	`reason`	VARCHAR(500)	NULL	COMMENT '작업 사유',
	`before_data`	JSON	NULL	COMMENT '변경 전 데이터',
	`after_data`	JSON	NULL	COMMENT '변경 후 데이터',
	`ip_address`	VARCHAR(45)	NULL	COMMENT '요청 IP',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '수행 시각'
);

CREATE TABLE `user_account` (
	`user_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '회원 고유 ID',
	`email`	VARCHAR(255)	NOT NULL	COMMENT '로그인 이메일, 중복 불가',
	`password`	VARCHAR(255)	NULL	COMMENT 'BCrypt 암호화 비밀번호, 소셜 전용 가입자는 NULL',
	`nickname`	VARCHAR(50)	NOT NULL	COMMENT '회원 닉네임, 중복 불가',
	`phone`	VARCHAR(20)	NULL	COMMENT '회원 연락처',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '회원 상태',
	`marketing_opt_in`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '마케팅 정보 수신 동의 여부',
	`email_verified_at`	DATETIME(6)	NULL	COMMENT '이메일 인증 완료 시각',
	`last_login_at`	DATETIME(6)	NULL	COMMENT '마지막 로그인 성공 시각',
	`password_changed_at`	DATETIME(6)	NULL	COMMENT '비밀번호 마지막 변경 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '가입 시각',
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '정보 마지막 수정 시각',
	`withdrawn_at`	DATETIME(6)	NULL	COMMENT '탈퇴 처리 완료 시각'
);

CREATE TABLE `reinspection_request` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '내부 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '재검수 대상 매물 ID',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '알림을 전송할 채팅방 ID',
	`request_key`	CHAR(36)	NOT NULL	COMMENT '외부 노출용 UUID',
	`reason`	VARCHAR(1000)	NOT NULL	COMMENT '구매자가 작성한 전체 요청 사유',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'REQUESTED'	COMMENT '재검수 진행 상태 (ENUM: REQUESTED|COMPLETED|CANCELED)',
	`version`	BIGINT	NOT NULL	DEFAULT 0	COMMENT '완료·취소 동시 요청 방지용 낙관적 잠금 버전',
	`requested_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '요청 시각',
	`completed_at`	DATETIME(3)	NULL	COMMENT '판매자 완료 시각',
	`canceled_at`	DATETIME(3)	NULL	COMMENT '취소 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`buyer_id`	BIGINT	NOT NULL	COMMENT '재검수를 요청한 구매자 ID',
	`seller_id`	BIGINT	NOT NULL	COMMENT '재검수를 수행할 판매자 ID'
);

CREATE TABLE `battery_report_result` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`evidence_id`	BIGINT	NOT NULL	COMMENT '대상 증거 ID (evidence_type=DIAGNOSTIC_FILE)',
	`design_capacity`	VARCHAR(30)	NULL	COMMENT '설계 용량',
	`full_charge_capacity`	VARCHAR(30)	NULL	COMMENT '완전충전 용량',
	`cycle_count`	INT	NULL	COMMENT '충전 사이클 수',
	`battery_manufacturer`	VARCHAR(50)	NULL	COMMENT '배터리 제조사',
	`capacity_ratio`	DECIMAL(5, 2)	NULL	COMMENT '완전충전용량/설계용량 * 100',
	`parser_version`	VARCHAR(20)	NOT NULL	COMMENT '파서 버전',
	`parse_status`	VARCHAR(30)	NOT NULL	COMMENT '파싱 결과 상태 (ENUM: SUCCESS|FAILED|PARTIAL)',
	`parsed_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '파싱 시각'
);

CREATE TABLE `seller` (
	`seller_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '판매자 고유 ID',
	`seller_name`	VARCHAR(100)	NOT NULL	COMMENT '판매자(스토어) 이름',
	`seller_category`	VARCHAR(30)	NOT NULL	COMMENT '셀러 구분(개인/기업) (ENUM: INDIVIDUAL|BUSINESS)',
	`country`	VARCHAR(50)	NOT NULL	COMMENT '셀러 소재 국가',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '판매자 상태 (ENUM: ACTIVE|RESTRICTED|SUSPENDED|BANNED)',
	`product_limit`	INT	NOT NULL	DEFAULT 0	COMMENT '등록 가능 상품 수 한도(신규 셀러 단계적 완화 대상)',
	`sales_amount_limit`	DECIMAL(14, 2)	NOT NULL	DEFAULT 0	COMMENT '판매 금액 한도',
	`approved_at`	DATETIME	NULL	COMMENT '판매자 승인일시',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '생성일시'
);

CREATE TABLE `wishlist` (
	`wishlist_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '관심상품 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '등록 회원 ID',
	`listing_id`	BIGINT	NOT NULL	COMMENT '관심 매물 ID (원본은 product 참조였으나 listing으로 수정)',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '등록일시'
);

CREATE TABLE `listing_image` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '이미지 고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '소속 매물 ID',
	`image_type`	VARCHAR(30)	NOT NULL	COMMENT '대표/상세 이미지 구분 (ENUM: THUMBNAIL|DETAIL)',
	`s3_key`	VARCHAR(500)	NOT NULL	COMMENT 'S3 원본 경로(내부용)',
	`cdn_url`	VARCHAR(500)	NOT NULL	COMMENT 'CDN 접근 URL',
	`mime_type`	VARCHAR(50)	NOT NULL	COMMENT 'MIME 타입',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '등록일시'
);

CREATE TABLE `admin_account` (
	`admin_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '관리자 고유 ID',
	`email`	VARCHAR(255)	NOT NULL	COMMENT '관리자 로그인 이메일',
	`password`	VARCHAR(255)	NOT NULL	COMMENT 'BCrypt 암호화 관리자 비밀번호',
	`name`	VARCHAR(50)	NOT NULL	COMMENT '관리자 이름',
	`role`	VARCHAR(30)	NOT NULL	DEFAULT 'OPERATOR'	COMMENT '관리자 권한',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '관리자 계정 상태',
	`last_login_at`	DATETIME(6)	NULL	COMMENT '마지막 로그인 성공 시각',
	`password_changed_at`	DATETIME(6)	NULL	COMMENT '비밀번호 마지막 변경 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '계정 생성 시각',
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '마지막 수정 시각'
);

CREATE TABLE `chat_outbox_event` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`event_id`	CHAR(36)	NOT NULL	COMMENT '이벤트 고유 키',
	`aggregate_type`	VARCHAR(50)	NOT NULL	COMMENT '애그리거트 유형',
	`aggregate_id`	BIGINT	NOT NULL	COMMENT '애그리거트 ID (다형 참조, FK 없음)',
	`event_type`	VARCHAR(100)	NOT NULL	COMMENT '이벤트 유형',
	`payload`	JSON	NOT NULL	COMMENT '이벤트 페이로드',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '발행 상태 (ENUM: PENDING|PROCESSING|PUBLISHED|FAILED)',
	`retry_count`	INT	NOT NULL	DEFAULT 0	COMMENT '재시도 횟수',
	`next_retry_at`	DATETIME(3)	NULL	COMMENT '다음 재시도 시각',
	`published_at`	DATETIME(3)	NULL	COMMENT '발행 완료 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각',
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '수정 시각'
);

CREATE TABLE `inquiry` (
	`inquiry_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '문의 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '작성 회원 ID',
	`inquiry_type`	VARCHAR(50)	NOT NULL	COMMENT '문의 유형',
	`title`	VARCHAR(200)	NOT NULL	COMMENT '문의 제목',
	`content`	TEXT	NOT NULL	COMMENT '문의 내용',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'OPEN'	COMMENT '문의 처리 상태',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '접수 시각',
	`closed_at`	DATETIME(6)	NULL	COMMENT '종료 시각'
);

CREATE TABLE `evidence` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '증거 고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '소속 매물 ID',
	`listing_checklist_item_id`	BIGINT	NOT NULL	COMMENT '대상 스냅샷 항목 ID',
	`evidence_type`	VARCHAR(30)	NOT NULL	COMMENT '증거 유형 (ENUM: PHOTO|VIDEO|DIAGNOSTIC_FILE|SELLER_CONFIRMATION)',
	`s3_key`	VARCHAR(500)	NOT NULL	COMMENT 'S3 원본 경로',
	`cdn_url`	VARCHAR(500)	NULL	COMMENT 'CDN 접근 URL(처리 완료 후)',
	`mime_type`	VARCHAR(50)	NOT NULL	COMMENT 'MIME 타입',
	`captured_at`	DATETIME	NULL	COMMENT '촬영 시각',
	`uploaded_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '업로드 완료 시각',
	`processing_status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '미디어 처리 상태 (ENUM: PENDING|PROCESSING|READY|FAILED|INFECTED)'
);

CREATE TABLE `seller_application` (
	`application_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '판매자 신청 고유 ID',
	`seller_category`	VARCHAR(30)	NOT NULL	COMMENT '신청 구분(개인/기업 셀러) (ENUM: INDIVIDUAL|BUSINESS)',
	`applicant_name`	VARCHAR(100)	NOT NULL	COMMENT '신청자 이름',
	`applicant_email`	VARCHAR(255)	NOT NULL	COMMENT '신청자 이메일',
	`applicant_phone`	VARCHAR(20)	NOT NULL	COMMENT '신청자 연락처',
	`identity_doc_s3_key`	VARCHAR(500)	NOT NULL	COMMENT '여권/신분증 파일 경로(S3, 암호화 저장 권장)',
	`business_reg_s3_key`	VARCHAR(500)	NULL	COMMENT '현지 사업자등록증 파일 경로(기업 셀러만 해당)',
	`business_address`	VARCHAR(255)	NULL	COMMENT '사업장 주소',
	`settlement_bank_name`	VARCHAR(100)	NOT NULL	COMMENT '정산 은행 (원본 컬럼명 Field2 → 의미에 맞게 수정)',
	`settlement_account`	VARCHAR(255)	NOT NULL	COMMENT '정산 계좌 정보(암호화 저장 권장)',
	`account_holder_name`	VARCHAR(100)	NOT NULL	COMMENT '예금주 (원본 컬럼명 Field → 의미에 맞게 수정)',
	`planned_categories`	VARCHAR(255)	NULL	COMMENT '판매 예정 카테고리',
	`terms_agreed_at`	DATETIME	NOT NULL	COMMENT '약관 동의일시',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '심사 상태 (ENUM: PENDING|APPROVED|REJECTED)',
	`reject_reason`	VARCHAR(500)	NULL	COMMENT '거절 사유(거절 시 필수)',
	`applied_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '신청일시',
	`processed_at`	DATETIME	NULL	COMMENT '심사 처리일시'
);

CREATE TABLE `settlement` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '대상 매물 ID',
	`seller_id`	BIGINT	NOT NULL	COMMENT '정산 대상 판매자 ID',
	`amount`	INT	NOT NULL	COMMENT '정산 금액',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'PENDING'	COMMENT '정산 상태 (ENUM: PENDING|SETTLED)',
	`pending_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '보류 시작 시각',
	`settled_at`	DATETIME	NULL	COMMENT '정산 완료 시각'
);

CREATE TABLE `checklist_template` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '템플릿 고유 식별자',
	`category_id`	BIGINT	NOT NULL	COMMENT '리프(모델) 카테고리 ID',
	`version`	INT	NOT NULL	COMMENT '템플릿 버전. 수정 = 새 버전 발행',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'DRAFT'	COMMENT 'PUBLISHED 후 항목 수정 금지 (ENUM: DRAFT|PUBLISHED|DEPRECATED)',
	`published_at`	DATETIME	NULL	COMMENT '발행 시각',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '생성일시'
);

CREATE TABLE `call_appointment_message` (
	`call_appointment_id`	BIGINT	NOT NULL	COMMENT '대상 약속 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '대상 메시지 ID',
	`appointment_event`	VARCHAR(30)	NOT NULL	COMMENT '약속 이벤트 유형 (ENUM: PROPOSED|ACCEPTED|REJECTED|RESCHEDULED|CANCELED|COMPLETED)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각'
);

CREATE TABLE `listing_status_history` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '이력 고유 식별자',
	`listing_id`	BIGINT	NOT NULL	COMMENT '대상 매물 ID',
	`from_status`	VARCHAR(20)	NOT NULL	COMMENT '이전 상태',
	`to_status`	VARCHAR(20)	NOT NULL	COMMENT '변경 상태',
	`reason`	VARCHAR(200)	NULL	COMMENT '사유',
	`actor_id`	BIGINT	NULL	COMMENT '수행자 ID (회원 또는 관리자, 다형 참조라 FK 없음. 시스템 처리 시 NULL)',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '변경 시각'
);

CREATE TABLE `social_account` (
	`social_account_id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '소셜 계정 연동 고유 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '연동된 회원 ID',
	`provider`	VARCHAR(20)	NOT NULL	COMMENT '소셜 로그인 공급자',
	`provider_user_id`	VARCHAR(255)	NOT NULL	COMMENT '공급자 발급 고유 식별자',
	`provider_email`	VARCHAR(255)	NULL	COMMENT '공급자가 반환한 이메일',
	`linked_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '최초 연동 시각'
);

CREATE TABLE `category` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '카테고리 고유 식별자',
	`parent_id`	BIGINT	NULL	COMMENT '상위 카테고리 ID(자기참조). 최상위는 NULL',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '화면 노출명',
	`device_type`	VARCHAR(30)	NOT NULL	COMMENT '기기 대분류 (ENUM: SMARTPHONE|FOLDABLE|TABLET|LAPTOP)',
	`manufacturer`	VARCHAR(50)	NULL	COMMENT '제조사명, 대분류는 NULL',
	`os_family`	VARCHAR(30)	NULL	COMMENT '운영체제 계열 (ENUM: IOS|ANDROID|WINDOWS|MACOS)',
	`model_code`	VARCHAR(50)	NULL	COMMENT '모델코드, 리프(모델)에만 채움',
	`display_order`	INT	NOT NULL	DEFAULT 0	COMMENT '정렬 순서',
	`is_active`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '등록 폼 노출 여부'
);

CREATE TABLE `chat_message` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '고유 식별자',
	`chat_room_id`	BIGINT	NOT NULL	COMMENT '소속 채팅방 ID',
	`room_sequence`	BIGINT	NOT NULL	COMMENT '방 내 시퀀스',
	`sender_id`	BIGINT	NULL	COMMENT '발신 회원 ID (시스템 메시지는 NULL)',
	`client_message_id`	CHAR(36)	NULL	COMMENT '클라이언트 멱등키',
	`message_type`	VARCHAR(30)	NOT NULL	COMMENT '메시지 유형 (ENUM: TEXT|IMAGE|VIDEO|APPOINTMENT|CALL|SYSTEM)',
	`content`	TEXT	NULL	COMMENT '텍스트 내용',
	`status`	VARCHAR(30)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '메시지 상태 (ENUM: ACTIVE|DELETED)',
	`sent_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '전송 시각',
	`deleted_at`	DATETIME(3)	NULL	COMMENT '삭제 시각',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3)	COMMENT '생성 시각'
);

ALTER TABLE `call_appointment` ADD CONSTRAINT `PK_CALL_APPOINTMENT` PRIMARY KEY (
	`id`
);

ALTER TABLE `withdrawal_request` ADD CONSTRAINT `PK_WITHDRAWAL_REQUEST` PRIMARY KEY (
	`withdrawal_request_id`
);

ALTER TABLE `rtc_session_summary` ADD CONSTRAINT `PK_RTC_SESSION_SUMMARY` PRIMARY KEY (
	`id`
);

ALTER TABLE `checklist_template_item` ADD CONSTRAINT `PK_CHECKLIST_TEMPLATE_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `listing` ADD CONSTRAINT `PK_LISTING` PRIMARY KEY (
	`id`
);

ALTER TABLE `listing_account_removal_check` ADD CONSTRAINT `PK_LISTING_ACCOUNT_REMOVAL_CHECK` PRIMARY KEY (
	`id`
);

ALTER TABLE `dxdiag_result` ADD CONSTRAINT `PK_DXDIAG_RESULT` PRIMARY KEY (
	`id`
);

ALTER TABLE `reinspection_request_item` ADD CONSTRAINT `PK_REINSPECTION_REQUEST_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `rtc_session` ADD CONSTRAINT `PK_RTC_SESSION` PRIMARY KEY (
	`id`
);

ALTER TABLE `reinspection_request_message` ADD CONSTRAINT `PK_REINSPECTION_REQUEST_MESSAGE` PRIMARY KEY (
	`event_type`,
	`reinspection_request_id`
);

ALTER TABLE `chat_message_media` ADD CONSTRAINT `PK_CHAT_MESSAGE_MEDIA` PRIMARY KEY (
	`chat_message_id`,
	`chat_media_id`
);

ALTER TABLE `chat_media` ADD CONSTRAINT `PK_CHAT_MEDIA` PRIMARY KEY (
	`id`
);

ALTER TABLE `payment` ADD CONSTRAINT `PK_PAYMENT` PRIMARY KEY (
	`id`
);

ALTER TABLE `login_attempt_log` ADD CONSTRAINT `PK_LOGIN_ATTEMPT_LOG` PRIMARY KEY (
	`log_id`
);

ALTER TABLE `ocr_result` ADD CONSTRAINT `PK_OCR_RESULT` PRIMARY KEY (
	`id`
);

ALTER TABLE `listing_checklist_item` ADD CONSTRAINT `PK_LISTING_CHECKLIST_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `user_address` ADD CONSTRAINT `PK_USER_ADDRESS` PRIMARY KEY (
	`address_id`
);

ALTER TABLE `account_removal_guide` ADD CONSTRAINT `PK_ACCOUNT_REMOVAL_GUIDE` PRIMARY KEY (
	`id`
);

ALTER TABLE `member_sanction` ADD CONSTRAINT `PK_MEMBER_SANCTION` PRIMARY KEY (
	`sanction_id`
);

ALTER TABLE `inquiry_answer` ADD CONSTRAINT `PK_INQUIRY_ANSWER` PRIMARY KEY (
	`answer_id`
);

ALTER TABLE `admin_action_log` ADD CONSTRAINT `PK_ADMIN_ACTION_LOG` PRIMARY KEY (
	`log_id`
);

ALTER TABLE `user_account` ADD CONSTRAINT `PK_USER_ACCOUNT` PRIMARY KEY (
	`user_id`
);

ALTER TABLE `reinspection_request` ADD CONSTRAINT `PK_REINSPECTION_REQUEST` PRIMARY KEY (
	`id`
);

ALTER TABLE `battery_report_result` ADD CONSTRAINT `PK_BATTERY_REPORT_RESULT` PRIMARY KEY (
	`id`
);

ALTER TABLE `seller` ADD CONSTRAINT `PK_SELLER` PRIMARY KEY (
	`seller_id`
);

ALTER TABLE `wishlist` ADD CONSTRAINT `PK_WISHLIST` PRIMARY KEY (
	`wishlist_id`
);

ALTER TABLE `listing_image` ADD CONSTRAINT `PK_LISTING_IMAGE` PRIMARY KEY (
	`id`
);

ALTER TABLE `admin_account` ADD CONSTRAINT `PK_ADMIN_ACCOUNT` PRIMARY KEY (
	`admin_id`
);

ALTER TABLE `chat_outbox_event` ADD CONSTRAINT `PK_CHAT_OUTBOX_EVENT` PRIMARY KEY (
	`id`
);

ALTER TABLE `inquiry` ADD CONSTRAINT `PK_INQUIRY` PRIMARY KEY (
	`inquiry_id`
);

ALTER TABLE `evidence` ADD CONSTRAINT `PK_EVIDENCE` PRIMARY KEY (
	`id`
);

ALTER TABLE `seller_application` ADD CONSTRAINT `PK_SELLER_APPLICATION` PRIMARY KEY (
	`application_id`
);

ALTER TABLE `settlement` ADD CONSTRAINT `PK_SETTLEMENT` PRIMARY KEY (
	`id`
);

ALTER TABLE `checklist_template` ADD CONSTRAINT `PK_CHECKLIST_TEMPLATE` PRIMARY KEY (
	`id`
);

ALTER TABLE `call_appointment_message` ADD CONSTRAINT `PK_CALL_APPOINTMENT_MESSAGE` PRIMARY KEY (
	`call_appointment_id`,
	`chat_message_id`
);

ALTER TABLE `listing_status_history` ADD CONSTRAINT `PK_LISTING_STATUS_HISTORY` PRIMARY KEY (
	`id`
);

ALTER TABLE `social_account` ADD CONSTRAINT `PK_SOCIAL_ACCOUNT` PRIMARY KEY (
	`social_account_id`
);

ALTER TABLE `category` ADD CONSTRAINT `PK_CATEGORY` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_message` ADD CONSTRAINT `PK_CHAT_MESSAGE` PRIMARY KEY (
	`id`
);

ALTER TABLE `reinspection_request_message` ADD CONSTRAINT `FK_reinspection_request_TO_reinspection_request_message_1` FOREIGN KEY (
	`reinspection_request_id`
)
REFERENCES `reinspection_request` (
	`id`
);

ALTER TABLE `chat_message_media` ADD CONSTRAINT `FK_chat_message_TO_chat_message_media_1` FOREIGN KEY (
	`chat_message_id`
)
REFERENCES `chat_message` (
	`id`
);

ALTER TABLE `chat_message_media` ADD CONSTRAINT `FK_chat_media_TO_chat_message_media_1` FOREIGN KEY (
	`chat_media_id`
)
REFERENCES `chat_media` (
	`id`
);

ALTER TABLE `call_appointment_message` ADD CONSTRAINT `FK_call_appointment_TO_call_appointment_message_1` FOREIGN KEY (
	`call_appointment_id`
)
REFERENCES `call_appointment` (
	`id`
);

ALTER TABLE `call_appointment_message` ADD CONSTRAINT `FK_chat_message_TO_call_appointment_message_1` FOREIGN KEY (
	`chat_message_id`
)
REFERENCES `chat_message` (
	`id`
);



