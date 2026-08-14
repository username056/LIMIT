import http from 'k6/http';
import { check } from 'k6';
import { login, authParams, requireEnv } from './lib/auth.js';

// Test 3 - Idempotency key misuse contract: same buyer + same key reused
// across a different listing and then a different method must both be
// rejected with PAY004. Sequential, not a concurrency test.
// See docs/testing/payment-concurrency-key-misuse.md.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';

export const options = {
  scenarios: {
    keyMisuse: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export function setup() {
  const listingIdA = requireEnv('LOAD_TEST_MISUSE_LISTING_ID_A');
  const listingIdB = requireEnv('LOAD_TEST_MISUSE_LISTING_ID_B');
  const password = requireEnv('LOAD_TEST_PASSWORD');
  const token = login('loadtest.buyer01@limit.local', password);
  const idempotencyKey = `load-misuse-${Date.now()}`;
  return {
    listingIdA: Number(listingIdA),
    listingIdB: Number(listingIdB),
    token,
    idempotencyKey,
  };
}

function createPayment(baseUrl, params, listingId, method, idempotencyKey) {
  return http.post(
    `${baseUrl}/api/v1/payments`,
    JSON.stringify({ listingId, method, idempotencyKey }),
    params,
  );
}

export default function (data) {
  const params = authParams(data.token);

  const first = createPayment(BASE_URL, params, data.listingIdA, 'CARD', data.idempotencyKey);
  check(first, { 'request 1 (listing A, CARD): 201': (r) => r.status === 201 });
  console.log(`request 1: status=${first.status} body=${first.body}`);

  const second = createPayment(BASE_URL, params, data.listingIdB, 'CARD', data.idempotencyKey);
  check(second, {
    'request 2 (listing B, same key): 409 PAY004': (r) =>
      r.status === 409 && r.json('error.code') === 'PAY004',
  });
  console.log(`request 2: status=${second.status} body=${second.body}`);

  const third = createPayment(
    BASE_URL,
    params,
    data.listingIdA,
    'ACCOUNT_TRANSFER',
    data.idempotencyKey,
  );
  check(third, {
    'request 3 (listing A, different method, same key): 409 PAY004': (r) =>
      r.status === 409 && r.json('error.code') === 'PAY004',
  });
  console.log(`request 3: status=${third.status} body=${third.body}`);
}
