import http from 'k6/http';
import { check } from 'k6';
import { login, authParams, requireEnv } from './lib/auth.js';

// Test 1 - Listing ownership: 20 different buyers race for the same
// [LOAD] Ownership Test Listing. Exactly one must win the reservation.
// See docs/testing/payment-concurrency-ownership.md for the full plan.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const BUYER_COUNT = 20;

export const options = {
  scenarios: {
    ownership: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export function setup() {
  const listingId = requireEnv('LOAD_TEST_OWNERSHIP_LISTING_ID');
  const password = requireEnv('LOAD_TEST_PASSWORD');

  const tokens = [];
  for (let i = 1; i <= BUYER_COUNT; i++) {
    const email = `loadtest.buyer${String(i).padStart(2, '0')}@limit.local`;
    tokens.push(login(email, password));
  }
  return { listingId: Number(listingId), tokens };
}

export default function (data) {
  const requests = data.tokens.map((token, i) => ({
    method: 'POST',
    url: `${BASE_URL}/api/v1/payments`,
    body: JSON.stringify({
      listingId: data.listingId,
      method: 'CARD',
      idempotencyKey: `load-ownership-${i + 1}-${Date.now()}`,
    }),
    params: authParams(token),
  }));

  const responses = http.batch(requests);

  let created = 0;
  let conflict = 0;
  let serverError = 0;
  let winnerBuyerIndex = null;
  const conflictCodes = {};

  responses.forEach((res, i) => {
    if (res.status === 201) {
      created += 1;
      winnerBuyerIndex = i + 1;
    } else if (res.status === 409) {
      conflict += 1;
      const code = res.json('error.code');
      conflictCodes[code] = (conflictCodes[code] || 0) + 1;
    } else if (res.status >= 500) {
      serverError += 1;
      console.error(`unexpected 5xx from buyer index ${i + 1}: ${res.status} ${res.body}`);
    }
  });

  check(null, {
    'exactly one 201': () => created === 1,
    'exactly nineteen 409': () => conflict === 19,
    'zero 5xx': () => serverError === 0,
    'conflicts are only PRD001 or PAY005': () =>
      Object.keys(conflictCodes).every((code) => code === 'PRD001' || code === 'PAY005'),
  });

  console.log(
    `ownership summary: created=${created} conflict=${conflict} serverError=${serverError} ` +
      `winnerBuyerIndex=${winnerBuyerIndex} conflictCodes=${JSON.stringify(conflictCodes)}`,
  );
}
