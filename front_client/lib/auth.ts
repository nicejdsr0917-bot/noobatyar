// Single owner of the visitor token's storage key. Everything else (pages,
// api.ts, the push prompt) goes through here instead of touching localStorage.
const KEY = 'visitor_token';

export function getVisitorToken(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setVisitorToken(token: string): void {
  localStorage.setItem(KEY, token);
}

export function clearVisitorToken(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(KEY);
}

// The React hook lives in lib/visitorToken.ts; it is not re-exported here so
// that server modules importing api.ts never pull a hook into their graph.
