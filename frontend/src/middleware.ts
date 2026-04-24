import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Skip static assets in /public — they have a file extension and are served
  // from the project root (e.g. /bipros-logo.png). Without this, the unauth
  // redirect below turns every image/font request into an HTML redirect.
  if (/\.[a-zA-Z0-9]+$/.test(pathname)) {
    return NextResponse.next();
  }

  // Next.js metadata routes (`app/icon.png`, `app/apple-icon.png`, etc.) are
  // served as extension-less paths like `/icon` with a hash query string.
  // Without this bypass, browser favicon requests get redirected to /auth/login.
  if (/^\/(icon|apple-icon|opengraph-image|twitter-image)(\b|\d|\/)/.test(pathname)) {
    return NextResponse.next();
  }

  const token = request.cookies.get('access_token')?.value;
  const isAuthPage = pathname.startsWith('/auth');

  if (!token && !isAuthPage) {
    return NextResponse.redirect(new URL('/auth/login', request.url));
  }

  if (token && isAuthPage) {
    return NextResponse.redirect(new URL('/', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|icon|apple-icon|opengraph-image|twitter-image|api).*)'],
};
