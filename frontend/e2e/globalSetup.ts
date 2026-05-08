import { request, type APIRequestContext, type FullConfig } from '@playwright/test';
import * as fs from 'fs/promises';
import * as path from 'path';
import {
  E2E_TEST_USERS,
  TEST_USERS_FILE,
  type ProvisionedFixture,
  type ProvisionedUser,
} from './fixtures/test-users';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function getAdminToken(api: APIRequestContext): Promise<string> {
  const res = await api.post(`${API_BASE}/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) {
    throw new Error(
      `[e2e globalSetup] admin login failed: ${res.status()} ${await res.text()}\n` +
        `Is the backend running at ${API_BASE}? See CLAUDE.md for startup steps.`,
    );
  }
  const body = (await res.json()) as { data: { accessToken: string } };
  return body.data.accessToken;
}

async function fetchProfileIdsByCode(
  api: APIRequestContext,
  token: string,
): Promise<Record<string, string>> {
  const res = await api.get(`${API_BASE}/v1/profiles`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`[e2e globalSetup] /v1/profiles failed: ${res.status()}`);
  }
  const body = (await res.json()) as {
    data: Array<{ id: string; code: string }>;
  };
  return Object.fromEntries(body.data.map((p) => [p.code, p.id]));
}

async function findUserIdByUsername(
  api: APIRequestContext,
  token: string,
  username: string,
): Promise<string | null> {
  // The user-list endpoint pages; usernames are unique so a simple
  // page-walk with size=200 is enough for any realistic dev DB.
  const res = await api.get(`${API_BASE}/v1/users`, {
    params: { page: 0, size: 200 },
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) return null;
  const body = (await res.json()) as {
    data: { content: Array<{ id: string; username: string }> };
  };
  return body.data.content.find((u) => u.username === username)?.id ?? null;
}

async function provisionUser(
  api: APIRequestContext,
  token: string,
  profileIdsByCode: Record<string, string>,
  user: (typeof E2E_TEST_USERS)[number],
): Promise<ProvisionedUser> {
  const profileId = profileIdsByCode[user.profileCode];
  if (!profileId) {
    throw new Error(
      `[e2e globalSetup] No profile with code "${user.profileCode}" exists. ` +
        `Backend ProfileSeeder may not have run; restart the backend.`,
    );
  }

  const createRes = await api.post(`${API_BASE}/v1/users`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      username: user.username,
      email: user.email,
      password: user.password,
      firstName: user.firstName,
      lastName: user.lastName,
      profileId,
      enabled: true,
    },
  });

  if (createRes.ok()) {
    const body = (await createRes.json()) as { data: { id: string } };
    return { ...user, id: body.data.id };
  }

  // Idempotent path: if the user exists from a previous run, look it up.
  // 409 Conflict is the documented duplicate response; some validation
  // failures also surface as 400 with a USER_ALREADY_EXISTS code, so we
  // fall through to the lookup either way.
  if (createRes.status() === 409 || createRes.status() === 400) {
    const existingId = await findUserIdByUsername(api, token, user.username);
    if (existingId) {
      return { ...user, id: existingId };
    }
  }

  throw new Error(
    `[e2e globalSetup] Failed to provision ${user.username}: ` +
      `${createRes.status()} ${await createRes.text()}`,
  );
}

async function pickFirstProjectId(
  api: APIRequestContext,
  token: string,
): Promise<string | null> {
  const res = await api.get(`${API_BASE}/v1/projects`, {
    params: { page: 0, size: 1 },
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) return null;
  const body = (await res.json()) as { data: { content: Array<{ id: string }> } };
  return body.data.content[0]?.id ?? null;
}

async function ensureMembership(
  api: APIRequestContext,
  token: string,
  projectId: string,
  user: ProvisionedUser,
): Promise<void> {
  if (!user.id) return;
  const res = await api.post(`${API_BASE}/v1/projects/${projectId}/members`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: { userId: user.id, role: 'TEAM_MEMBER' },
  });
  // 201 = created, 409 = already a member with that role; both are OK.
  if (!res.ok() && res.status() !== 409) {
    throw new Error(
      `[e2e globalSetup] Failed to add ${user.username} to project ${projectId}: ` +
        `${res.status()} ${await res.text()}`,
    );
  }
}

export default async function globalSetup(_config: FullConfig): Promise<void> {
  const api = await request.newContext();
  try {
    const token = await getAdminToken(api);
    const profileIdsByCode = await fetchProfileIdsByCode(api, token);

    const users: ProvisionedUser[] = [];
    for (const user of E2E_TEST_USERS) {
      const result = await provisionUser(api, token, profileIdsByCode, user);
      users.push(result);
      // eslint-disable-next-line no-console
      console.log(
        `[e2e globalSetup] ✓ user ${user.username} (${user.profileCode}) -> ${result.id}`,
      );
    }

    // Each e2e user must be a member of at least one project for the
    // ChatController's @aiAccess.canChat guard to pass — otherwise every
    // chat call returns 403. Pick the first project (admin sees all) and
    // enroll every test user as a read-only TEAM_MEMBER there. Also enroll
    // the seeded `pmanager` user so the PROJECT_MANAGER smoke test can run.
    const projectId = await pickFirstProjectId(api, token);
    if (projectId) {
      for (const user of users) {
        await ensureMembership(api, token, projectId, user);
        // eslint-disable-next-line no-console
        console.log(
          `[e2e globalSetup] ✓ ${user.username} added to project ${projectId} (TEAM_MEMBER)`,
        );
      }
      const pmanagerId = await findUserIdByUsername(api, token, 'pmanager');
      if (pmanagerId) {
        const res = await api.post(`${API_BASE}/v1/projects/${projectId}/members`, {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: { userId: pmanagerId, role: 'PROJECT_MANAGER' },
        });
        if (res.ok() || res.status() === 409) {
          // eslint-disable-next-line no-console
          console.log(
            `[e2e globalSetup] ✓ pmanager added to project ${projectId} (PROJECT_MANAGER)`,
          );
        }
      }
    } else {
      // eslint-disable-next-line no-console
      console.warn(
        `[e2e globalSetup] No project found — chat tests will be skipped at runtime.`,
      );
    }

    const fixture: ProvisionedFixture = { projectId, users };
    const outPath = path.resolve(TEST_USERS_FILE);
    await fs.mkdir(path.dirname(outPath), { recursive: true });
    await fs.writeFile(outPath, JSON.stringify(fixture, null, 2), 'utf-8');
    // eslint-disable-next-line no-console
    console.log(`[e2e globalSetup] wrote ${TEST_USERS_FILE}`);
  } finally {
    await api.dispose();
  }
}
