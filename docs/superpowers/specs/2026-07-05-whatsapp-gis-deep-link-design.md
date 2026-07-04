# WhatsApp → GIS Deep Link Design

**Date:** 2026-07-05
**Status:** Approved

## Problem

EPPM users need to access the GIS module via WhatsApp. Clicking a link in WhatsApp should open Chrome with the user automatically authenticated and land on the GIS viewer for a specific project.

## Constraints

- Same parent domain for API and frontend (e.g., `api.bipros.com` + `app.bipros.com`)
- Reuse existing JWT access token (1-hour expiry, HS256)
- Stateless backend (no server-side sessions)
- Minimal frontend changes

## Flow

```
WhatsApp: https://api.<domain>.com/v1/public/whatsapp/gis?projectId=X&token=Y
                    │
                    ▼
         GET /v1/public/whatsapp/gis
         Backend: validate JWT, verify project exists
         ↓ HTTP 302 Found
         Set-Cookie: access_token=<token>; Domain=.<domain>.com; Path=/; Secure; SameSite=None
         Location: https://app.<domain>.com/projects/<projectId>/gis-viewer
                    │
                    ▼
         Browser stores cookie, follows redirect
         Frontend middleware (proxy.ts) reads cookie → allows access
         GIS page loads
```

## Components

### Backend

**Module:** `bipros-api` (no new Maven module needed — single controller + service)

| File | Purpose |
|------|---------|
| `WhatsAppController.java` | `GET /v1/public/whatsapp/gis` — accepts `projectId` + `token` query params, delegates to service, returns 302 |
| `WhatsAppService.java` | Validates JWT via `JwtTokenProvider`, checks project exists via `ProjectRepository`, builds redirect response |
| `WhatsAppProperties.java` | `@ConfigurationProperties("bipros.whatsapp")` — `frontendUrl`, `cookieDomain`, `cookieSecure` |

**Config (application.yml):**
```yaml
bipros:
  whatsapp:
    frontend-url: ${WHATSAPP_FRONTEND_URL:http://localhost:3000}
    cookie-domain: ${WHATSAPP_COOKIE_DOMAIN:.localhost}
    cookie-secure: ${WHATSAPP_COOKIE_SECURE:false}
```

**Response — success:**
```
HTTP 302 Found
Set-Cookie: access_token=<token>; Domain=.localhost; Path=/; Secure=false; SameSite=None
Location: http://localhost:3000/projects/<projectId>/gis-viewer
```

**Response — invalid token:**
```
HTTP 302 Found
Location: http://localhost:3000/auth/login?error=invalid_token
```

**Cookie notes:**
- `SameSite=None` required for cross-subdomain cookie from redirect (browser treats WhatsApp → API redirect as cross-site)
- Non-HttpOnly: frontend JS needs to read it for localStorage sync + axios interceptor
- `Secure=false` in dev (localhost), `true` in prod

### Frontend

**Changes — minimal:**

1. **`useEffect` token sync** in GIS page or a wrapper component:
   - On mount, check if `access_token` cookie exists but Zustand auth store has no token
   - If so, call `/v1/auth/me` to fetch user details, hydrate auth store
   - This ensures axios interceptor finds token in localStorage on subsequent API calls

**No middleware changes needed.** `proxy.ts` already reads `access_token` cookie for route gating.

### Security

| Concern | Mitigation |
|---------|------------|
| Token leakage via URL | Token is only in browser memory after redirect (cookie), not in URL after landing |
| Replay attacks | Standard JWT expiry (1h) limits window |
| CSRF from other tab | SameSite=Lax (prod) limits cross-origin requests |
| Missing project | Frontend shows project-not-found, no crash |

## Error Handling

| Scenario | HTTP | Behavior |
|----------|------|----------|
| Missing `projectId` | 400 | JSON error body |
| Missing `token` | 400 | JSON error body |
| Token expired/invalid | 302 | Redirect to `/auth/login?error=invalid_token` |
| Project not found | 302 | Redirect to GIS page (frontend shows 404 state) |

## Testing

### Backend
- Unit: `WhatsAppService` — mock `JwtTokenProvider`, `ProjectRepository`
- Integration: `WhatsAppApiIntegrationTest` — Testcontainers PG, real JWT generation, verify 302 + cookie headers

### Frontend
- Unit: `useEffect` token sync hook
- E2E: Playwright — navigate to WhatsApp deep-link URL, verify redirect to GIS page authenticated

## Dependencies

- `bipros-security` module (`JwtTokenProvider`, already a dependency of `bipros-api`)
- `bipros-project` module (`ProjectRepository`, already a dependency)
- No new libraries needed
