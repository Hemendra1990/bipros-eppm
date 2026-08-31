import { request, type FullConfig } from '@playwright/test';
import * as fs from 'fs/promises';
import * as path from 'path';
import { TEST_USERS_FILE, type ProvisionedFixture } from './fixtures/test-users';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Default behaviour: keep the e2e users provisioned (idempotent — re-running
 * the suite reuses them). Set DELETE_E2E_USERS=1 to disable them at the end
 * of the run so they don't appear in the admin user list during manual QA.
 *
 * Note: there is no DELETE /v1/users/{id}; we toggle enabled=false instead,
 * which is sufficient — a disabled account fails login + is hidden from
 * default user-listing queries that filter on enabled=true.
 */
export default async function globalTeardown(_config: FullConfig): Promise<void> {
  if (process.env.DELETE_E2E_USERS !== '1') {
    return;
  }

  const outPath = path.resolve(TEST_USERS_FILE);
  let raw: string;
  try {
    raw = await fs.readFile(outPath, 'utf-8');
  } catch {
    // No record from globalSetup — nothing to clean up.
    return;
  }
  const fixture = JSON.parse(raw) as ProvisionedFixture;
  const provisioned = fixture.users;

  const api = await request.newContext();
  try {
    const loginRes = await api.post(`${API_BASE}/v1/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
      headers: { 'Content-Type': 'application/json' },
    });
    if (!loginRes.ok()) {
      // eslint-disable-next-line no-console
      console.warn('[e2e globalTeardown] admin login failed; skipping disable.');
      return;
    }
    const body = (await loginRes.json()) as { data: { accessToken: string } };
    const token = body.data.accessToken;

    for (const user of provisioned) {
      if (!user.id) continue;
      const res = await api.put(`${API_BASE}/v1/users/${user.id}/status`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        data: { enabled: false },
      });
      if (res.ok()) {
        // eslint-disable-next-line no-console
        console.log(`[e2e globalTeardown] disabled ${user.username}`);
      } else {
        // eslint-disable-next-line no-console
        console.warn(
          `[e2e globalTeardown] could not disable ${user.username}: ${res.status()}`,
        );
      }
    }
  } finally {
    await api.dispose();
  }
}
