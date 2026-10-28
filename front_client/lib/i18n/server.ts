import { cookies } from 'next/headers';
import { LOCALE_COOKIE, parseLocale, translate, type Locale, type MessageKey } from './index';

export async function getServerLocale(): Promise<Locale> {
  return parseLocale((await cookies()).get(LOCALE_COOKIE)?.value);
}

/** t() for server components, bound to the request's locale cookie. */
export async function getServerT() {
  const locale = await getServerLocale();
  return (key: MessageKey, ...args: (string | number)[]) => translate(locale, key, ...args);
}
