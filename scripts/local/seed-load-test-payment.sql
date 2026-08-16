-- Local-only payment concurrency load-test fixture.
--
-- Login password for all load-test accounts: DemoPayment123!
-- The value stored below is the same BCrypt hash used by seed-payment-demo.sql,
-- never a plaintext password.
--
-- This script only touches loadtest.*@limit.local accounts and [LOAD] listings.
-- Safe to re-run: every insert is guarded by NOT EXISTS.

START TRANSACTION;

-- 1 seller + 20 buyers (buyer01..buyer20) for the ownership test,
-- buyer01 is reused for the idempotency and key-misuse tests.
INSERT INTO user_account (
    email,
    password,
    nickname,
    phone,
    status,
    marketing_opt_in,
    email_verified_at,
    password_changed_at,
    created_at,
    updated_at
)
SELECT
    seed.email,
    '$2b$10$XgEoR6khRByPXmWd/kBD8uckEhpKnuJq44CebKhyxxxqBw8yFZnpi',
    seed.nickname,
    seed.phone,
    'ACTIVE',
    FALSE,
    NOW(6),
    NOW(6),
    NOW(6),
    NOW(6)
FROM (
    SELECT 'loadtest.seller@limit.local' AS email, '부하테스트판매자' AS nickname, '01090000000' AS phone
    UNION ALL SELECT 'loadtest.buyer01@limit.local', '부하테스트구매자01', '01090000001'
    UNION ALL SELECT 'loadtest.buyer02@limit.local', '부하테스트구매자02', '01090000002'
    UNION ALL SELECT 'loadtest.buyer03@limit.local', '부하테스트구매자03', '01090000003'
    UNION ALL SELECT 'loadtest.buyer04@limit.local', '부하테스트구매자04', '01090000004'
    UNION ALL SELECT 'loadtest.buyer05@limit.local', '부하테스트구매자05', '01090000005'
    UNION ALL SELECT 'loadtest.buyer06@limit.local', '부하테스트구매자06', '01090000006'
    UNION ALL SELECT 'loadtest.buyer07@limit.local', '부하테스트구매자07', '01090000007'
    UNION ALL SELECT 'loadtest.buyer08@limit.local', '부하테스트구매자08', '01090000008'
    UNION ALL SELECT 'loadtest.buyer09@limit.local', '부하테스트구매자09', '01090000009'
    UNION ALL SELECT 'loadtest.buyer10@limit.local', '부하테스트구매자10', '01090000010'
    UNION ALL SELECT 'loadtest.buyer11@limit.local', '부하테스트구매자11', '01090000011'
    UNION ALL SELECT 'loadtest.buyer12@limit.local', '부하테스트구매자12', '01090000012'
    UNION ALL SELECT 'loadtest.buyer13@limit.local', '부하테스트구매자13', '01090000013'
    UNION ALL SELECT 'loadtest.buyer14@limit.local', '부하테스트구매자14', '01090000014'
    UNION ALL SELECT 'loadtest.buyer15@limit.local', '부하테스트구매자15', '01090000015'
    UNION ALL SELECT 'loadtest.buyer16@limit.local', '부하테스트구매자16', '01090000016'
    UNION ALL SELECT 'loadtest.buyer17@limit.local', '부하테스트구매자17', '01090000017'
    UNION ALL SELECT 'loadtest.buyer18@limit.local', '부하테스트구매자18', '01090000018'
    UNION ALL SELECT 'loadtest.buyer19@limit.local', '부하테스트구매자19', '01090000019'
    UNION ALL SELECT 'loadtest.buyer20@limit.local', '부하테스트구매자20', '01090000020'
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM user_account existing
    WHERE existing.email = seed.email
       OR existing.nickname = seed.nickname
);

UPDATE user_account
SET status = 'ACTIVE',
    email_verified_at = COALESCE(email_verified_at, NOW(6)),
    updated_at = NOW(6)
WHERE email LIKE 'loadtest.%@limit.local';

INSERT INTO member_terms_agreement (
    user_id,
    terms_code,
    terms_version,
    is_agreed,
    agreed_at,
    created_at
)
SELECT
    member.user_id,
    terms.terms_code,
    '2026-07-22',
    terms.is_agreed,
    CASE WHEN terms.is_agreed = TRUE THEN NOW(6) ELSE NULL END,
    NOW(6)
FROM user_account member
CROSS JOIN (
    SELECT 'SERVICE' AS terms_code, TRUE AS is_agreed
    UNION ALL SELECT 'PRIVACY', TRUE
    UNION ALL SELECT 'AGE_14', TRUE
    UNION ALL SELECT 'MARKETING', FALSE
) terms
WHERE member.email LIKE 'loadtest.%@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM member_terms_agreement existing
      WHERE existing.user_id = member.user_id
        AND existing.terms_code = terms.terms_code
        AND existing.terms_version = '2026-07-22'
  );

INSERT INTO seller (
    user_id,
    seller_type,
    status,
    country_code,
    business_name,
    settlement_bank_name,
    settlement_account_holder,
    settlement_account_last4,
    created_at,
    updated_at
)
SELECT
    member.user_id,
    'INDIVIDUAL',
    'ACTIVE',
    'KR',
    NULL,
    'Load Test Bank',
    'Payment Load Test Seller',
    '9000',
    NOW(6),
    NOW(6)
FROM user_account member
WHERE member.email = 'loadtest.seller@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM seller existing
      WHERE existing.user_id = member.user_id
  );

-- Test 1: ownership 전용 매물 1건 (buyer01~20 전체가 경합)
INSERT INTO listing (
    seller_id, category_id, title, description, price, color, storage_gb,
    trade_region, checklist_template_id, precheck_completed, status, version,
    created_at, updated_at
)
SELECT
    member.user_id, 8,
    '[LOAD] Ownership Test Listing',
    'Local k6 ownership concurrency test listing',
    500000, 'Onyx Black', 256, 'Seoul Gangnam', 1, TRUE, 'ON_SALE', 0,
    NOW(6), NOW(6)
FROM user_account member
WHERE member.email = 'loadtest.seller@limit.local'
  AND NOT EXISTS (
      SELECT 1 FROM listing existing WHERE existing.title = '[LOAD] Ownership Test Listing'
  );

-- Test 2: idempotency 전용 매물 1건 (buyer01 단독)
INSERT INTO listing (
    seller_id, category_id, title, description, price, color, storage_gb,
    trade_region, checklist_template_id, precheck_completed, status, version,
    created_at, updated_at
)
SELECT
    member.user_id, 8,
    '[LOAD] Idempotency Test Listing',
    'Local k6 idempotency concurrency test listing',
    500000, 'Onyx Black', 256, 'Seoul Gangnam', 1, TRUE, 'ON_SALE', 0,
    NOW(6), NOW(6)
FROM user_account member
WHERE member.email = 'loadtest.seller@limit.local'
  AND NOT EXISTS (
      SELECT 1 FROM listing existing WHERE existing.title = '[LOAD] Idempotency Test Listing'
  );

-- Test 3: 멱등키 오용 계약 테스트용 매물 2건 (buyer01 단독, 순차 실행)
INSERT INTO listing (
    seller_id, category_id, title, description, price, color, storage_gb,
    trade_region, checklist_template_id, precheck_completed, status, version,
    created_at, updated_at
)
SELECT
    member.user_id, 8,
    '[LOAD] Key Misuse Test Listing A',
    'Local k6 idempotency key misuse contract test listing A',
    500000, 'Onyx Black', 256, 'Seoul Gangnam', 1, TRUE, 'ON_SALE', 0,
    NOW(6), NOW(6)
FROM user_account member
WHERE member.email = 'loadtest.seller@limit.local'
  AND NOT EXISTS (
      SELECT 1 FROM listing existing WHERE existing.title = '[LOAD] Key Misuse Test Listing A'
  );

INSERT INTO listing (
    seller_id, category_id, title, description, price, color, storage_gb,
    trade_region, checklist_template_id, precheck_completed, status, version,
    created_at, updated_at
)
SELECT
    member.user_id, 8,
    '[LOAD] Key Misuse Test Listing B',
    'Local k6 idempotency key misuse contract test listing B',
    500000, 'Onyx Black', 256, 'Seoul Gangnam', 1, TRUE, 'ON_SALE', 0,
    NOW(6), NOW(6)
FROM user_account member
WHERE member.email = 'loadtest.seller@limit.local'
  AND NOT EXISTS (
      SELECT 1 FROM listing existing WHERE existing.title = '[LOAD] Key Misuse Test Listing B'
  );

COMMIT;

-- After running, look up the generated ids for the k6 env vars:
-- SELECT id, title FROM listing WHERE title LIKE '[LOAD]%';
