import http from 'k6/http';
import { check } from 'k6';
import { login, authParams, requireEnv } from './lib/auth.js';

// Test 2 - Idempotency: one buyer sends 20 concurrent requests with the
// same listing, method, and idempotencyKey. All must converge on the same
// Payment. See docs/testing/payment-concurrency-idempotency.md.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const REQUEST_COUNT = 20;

export const options = {
  scenarios: {
    idempotency: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export function setup() {
  const listingId = requireEnv('LOAD_TEST_IDEMPOTENCY_LISTING_ID');
  const password = requireEnv('LOAD_TEST_PASSWORD');
  const token = login('loadtest.buyer01@limit.local', password);
  const idempotencyKey = `load-idempotency-${Date.now()}`;
  return { listingId: Number(listingId), token, idempotencyKey };
}

export default function (data) {
  const body = JSON.stringify({
    listingId: data.listingId,
    method: 'CARD',
    idempotencyKey: data.idempotencyKey,
  });
  const params = authParams(data.token);
  const requests = Array.from({ length: REQUEST_COUNT }, () => ({
    method: 'POST',
    url: `${BASE_URL}/api/v1/payments`,
    body,
    params,
  }));

  const responses = http.batch(requests);

  let success = 0;
  let serverError = 0;
  let pay004 = 0;
  let pay005 = 0;
  let other4xx = 0;
  const paymentIds = new Set();
  const otherCodes = {};

  responses.forEach((res, i) => {
    if (res.status === 201) {
      success += 1;
      paymentIds.add(res.json('data.paymentId'));
    } else if (res.status >= 500) {
      serverError += 1;
      console.error(`unexpected 5xx on request ${i + 1}: ${res.status} ${res.body}`);
    } else {
      const code = res.json('error.code');
      if (code === 'PAY004') {
        pay004 += 1;
      } else if (code === 'PAY005') {
        pay005 += 1;
      } else {
        other4xx += 1;
        otherCodes[code] = (otherCodes[code] || 0) + 1;
      }
    }
  });

  check(null, {
    'all 20 responses are 201': () => success === REQUEST_COUNT,
    'single distinct paymentId': () => paymentIds.size === 1,
    'zero PAY004': () => pay004 === 0,
    'zero 5xx': () => serverError === 0,
  });

  console.log(
    `idempotency summary: success=${success} distinctPaymentIds=${paymentIds.size} ` +
      `pay004=${pay004} pay005=${pay005} other4xx=${other4xx} ` +
      `otherCodes=${JSON.stringify(otherCodes)} serverError=${serverError}`,
  );
}
