import { useSyncExternalStore } from 'react';
import { getVisitorToken } from '@/lib/auth';

/**
 * The token only changes through login or logout, both of which navigate away
 * and remount the screen — so there is nothing to subscribe to for the life
 * of one mount.
 */
const NEVER_CHANGES = () => () => {};
const readToken = () => getVisitorToken();
/** `undefined` = not known yet (server render + the hydration pass). */
const noTokenOnServer = () => undefined;

/**
 * Read the stored visitor token without a setState-in-effect cascade.
 *
 * localStorage is an external store, so this is what useSyncExternalStore is
 * for: the server snapshot keeps SSR and the hydration render agreeing on
 * "unknown", and the real value arrives on the first post-hydration render.
 */
export function useVisitorToken(): string | null | undefined {
  return useSyncExternalStore(NEVER_CHANGES, readToken, noTokenOnServer);
}
