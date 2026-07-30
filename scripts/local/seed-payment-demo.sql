-- Local-only payment demo fixture.
--
-- Login password for all demo accounts: DemoPayment123!
-- The value stored below is a BCrypt hash, never a plaintext password.
--
-- This script only touches the three demo accounts and [DEMO] listings.

START TRANSACTION;

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
    SELECT
        'seller.demo@limit.local' AS email,
        '결제데모판매자' AS nickname,
        '01010001000' AS phone
    UNION ALL
    SELECT
        'buyer1.demo@limit.local',
        '결제데모구매자1',
        '01020002000'
    UNION ALL
    SELECT
        'buyer2.demo@limit.local',
        '결제데모구매자2',
        '01030003000'
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
WHERE email IN (
    'seller.demo@limit.local',
    'buyer1.demo@limit.local',
    'buyer2.demo@limit.local'
);

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
WHERE member.email IN (
    'seller.demo@limit.local',
    'buyer1.demo@limit.local',
    'buyer2.demo@limit.local'
)
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
    'Demo Bank',
    'Payment Demo Seller',
    '1000',
    NOW(6),
    NOW(6)
FROM user_account member
WHERE member.email = 'seller.demo@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM seller existing
      WHERE existing.user_id = member.user_id
  );

INSERT INTO listing (
    seller_id,
    category_id,
    title,
    description,
    price,
    color,
    storage_gb,
    trade_region,
    checklist_template_id,
    precheck_completed,
    status,
    version,
    created_at,
    updated_at
)
SELECT
    member.user_id,
    8,
    '[DEMO] Galaxy S24 Payment Test',
    'Local Toss payment demo listing',
    650000,
    'Onyx Black',
    256,
    'Seoul Gangnam',
    1,
    TRUE,
    'ON_SALE',
    0,
    NOW(6),
    NOW(6)
FROM user_account member
WHERE member.email = 'seller.demo@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM listing existing
      WHERE existing.title = '[DEMO] Galaxy S24 Payment Test'
  );

INSERT INTO listing (
    seller_id,
    category_id,
    title,
    description,
    price,
    color,
    storage_gb,
    trade_region,
    checklist_template_id,
    precheck_completed,
    status,
    version,
    created_at,
    updated_at
)
SELECT
    member.user_id,
    11,
    '[DEMO] Galaxy Tab S9 Payment Test',
    'Local Toss payment demo listing',
    720000,
    'Graphite',
    256,
    'Seoul Songpa',
    4,
    TRUE,
    'ON_SALE',
    0,
    NOW(6),
    NOW(6)
FROM user_account member
WHERE member.email = 'seller.demo@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM listing existing
      WHERE existing.title = '[DEMO] Galaxy Tab S9 Payment Test'
  );

INSERT INTO listing (
    seller_id,
    category_id,
    title,
    description,
    price,
    color,
    storage_gb,
    trade_region,
    checklist_template_id,
    precheck_completed,
    status,
    version,
    created_at,
    updated_at
)
SELECT
    member.user_id,
    14,
    '[DEMO] LG gram 16 Payment Test',
    'Local Toss payment demo listing',
    1250000,
    'White',
    512,
    'Seoul Mapo',
    7,
    TRUE,
    'ON_SALE',
    0,
    NOW(6),
    NOW(6)
FROM user_account member
WHERE member.email = 'seller.demo@limit.local'
  AND NOT EXISTS (
      SELECT 1
      FROM listing existing
      WHERE existing.title = '[DEMO] LG gram 16 Payment Test'
  );

COMMIT;
