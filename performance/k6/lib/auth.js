import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';

// Logs in with a local load-test account and returns the access token.
// Credentials come only from __ENV. Do not hardcode credentials here and do
// not reference any real environment file path from this script.
export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/sessions`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200) {
    throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  }
  const token = res.json('data.accessToken');
  if (!token) {
    throw new Error(`login response missing data.accessToken for ${email}`);
  }
  return token;
}

export function authParams(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  };
}

export function requireEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`missing required env var: ${name}`);
  }
  return value;
}
