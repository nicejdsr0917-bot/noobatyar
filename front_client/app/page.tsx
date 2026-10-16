import HomeClient from './HomeClient';
import { getServerT } from '@/lib/i18n/server';

export async function generateMetadata() {
  const t = await getServerT();
  return {
    title: t('noobatyarOnlineBooking'),
    description: t('bookYourFavoriteBusinessesOnline'),
  };
}

/**
 * "/" is the customer's home: their own upcoming appointments, under the app
 * banner. It is not a directory — booking always starts at /b/<code>, and this
 * route must never leak an unauthenticated list of every registered business.
 *
 * The page itself stays a Server Component so the metadata is emitted for
 * crawlers; the signed-in/signed-out split lives in HomeClient, which reads the
 * visitor token from localStorage.
 */
export default function Home() {
  return <HomeClient />;
}
