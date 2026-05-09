import type { APIRequestContext } from '@playwright/test';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Adds the user identified by `username` as a TEAM_MEMBER on the given
 * project, using admin credentials. Idempotent: 201 (created) and 409
 * (already a member) both succeed silently.
 *
 * Used by spec 32 because the e2e fixture project is ROAD-001 (sparse
 * data); for AI evaluation we want the e2e PROJECT_ENGINEER user on the
 * data-rich 6155 Barka-Nakhal project too.
 */
export async function ensureUserOnProject(
  api: APIRequestContext,
  username: string,
  projectId: string,
  role: 'TEAM_MEMBER' | 'PROJECT_MANAGER' = 'TEAM_MEMBER',
): Promise<void> {
  const adminLogin = await api.post(`${API_BASE}/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!adminLogin.ok()) {
    throw new Error(`admin login failed: ${adminLogin.status()} ${await adminLogin.text()}`);
  }
  const adminToken = ((await adminLogin.json()) as { data: { accessToken: string } }).data
    .accessToken;

  const usersRes = await api.get(`${API_BASE}/v1/users`, {
    params: { page: 0, size: 200 },
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!usersRes.ok()) {
    throw new Error(`/v1/users failed: ${usersRes.status()}`);
  }
  const usersBody = (await usersRes.json()) as {
    data: { content: Array<{ id: string; username: string }> };
  };
  const user = usersBody.data.content.find((u) => u.username === username);
  if (!user) {
    throw new Error(`user "${username}" not found — did globalSetup run?`);
  }

  const memberRes = await api.post(`${API_BASE}/v1/projects/${projectId}/members`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
      'Content-Type': 'application/json',
    },
    data: { userId: user.id, role },
  });
  if (!memberRes.ok() && memberRes.status() !== 409) {
    throw new Error(
      `failed to add ${username} to ${projectId}: ${memberRes.status()} ${await memberRes.text()}`,
    );
  }
}
