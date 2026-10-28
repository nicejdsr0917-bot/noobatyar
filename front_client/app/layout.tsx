import type { Metadata, Viewport } from 'next';
import './globals.css';
import ThemeToggle from './components/ThemeToggle';
import ServiceWorkerRegistration from './components/ServiceWorkerRegistration';
import PushPermissionPrompt from './components/PushPermissionPrompt';
import LanguageToggle from './components/LanguageToggle';
import localFont from 'next/font/local';
import { SITE_URL } from '@/lib/site';
import { dirOf } from '@/lib/i18n';
import { getServerLocale, getServerT } from '@/lib/i18n/server';
import { I18nProvider } from '@/lib/i18n/client';

/**
 * English UI font: Nunito (Google Fonts, OFL), the same three weights the site
 * uses of Yekan Bakh. Its fully rounded stroke ends match Yekan's soft shapes
 * and its strokes are lighter than the first pick (Rubik), which read heavy
 * next to Yekan. Bundled with next/font/local instead of next/font/google:
 * the production build runs on a server in Iran, where fetching from Google
 * at build time can't be relied on. Latin-only subset — Persian names in an
 * English page fall back to Yekan through --font-base (globals.css).
 */
const nunito = localFont({
  src: [
    { path: './fonts/Nunito-Regular.ttf', weight: '400', style: 'normal' },
    { path: './fonts/Nunito-SemiBold.ttf', weight: '600', style: 'normal' },
    { path: './fonts/Nunito-Bold.ttf', weight: '700', style: 'normal' },
  ],
  variable: '--font-latin',
  display: 'swap',
});
export async function generateMetadata(): Promise<Metadata> {
  const locale = await getServerLocale();
  const t = await getServerT();
  return {
    // Without metadataBase, every relative URL a page puts in `alternates` or
    // `openGraph` is emitted relative to whatever host rendered it, and Next
    // warns at build time. Crawlers and chat previews need absolute ones.
    metadataBase: new URL(SITE_URL),
    title: t('noobatyarOnlineBooking'),
    description: t('noobatyarOnlineBookingBookAnytime'),
    openGraph: {
      title: t('noobatyarOnlineBooking'),
      description: t('noobatyarOnlineBookingBookAnytime'),
      siteName: t('noobatyar'),
      locale: locale === 'en' ? 'en_US' : 'fa_IR',
      type: 'website',
    },
  };
}

// Icons and the web manifest are wired up by file convention, not from here:
// app/icon.svg, app/apple-icon.png, app/favicon.ico and app/manifest.ts each
// get their own <link> tag emitted by Next. Re-declaring `manifest` in the
// metadata above is what previously pointed the browser at a non-existent
// /manifest.json.
export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#7c3aed' },
    { media: '(prefers-color-scheme: dark)', color: '#0f0f0f' },
  ],
};

// Applied before paint to prevent a flash of the wrong theme.
const themeScript = `
(function () {
  try {
    var t = localStorage.getItem('theme');
    if (t === 'dark' || t === 'light') {
      document.documentElement.setAttribute('data-theme', t);
    }
  } catch (e) {}
})();
`;

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Language comes from the `lang` cookie (set by LanguageToggle); Persian by
  // default. It drives direction and font for the whole document.
  const locale = await getServerLocale();
  return (
    // themeScript below stamps data-theme onto this element while the browser
    // is still parsing HTML — i.e. before React hydrates — so the client tree
    // legitimately carries an attribute the server never rendered. This is the
    // documented escape hatch for that pattern (Next.js "Preventing a flash
    // before hydration"); without it every page load logs a hydration error.
    <html lang={locale} dir={dirOf(locale)} className={nunito.variable} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <I18nProvider locale={locale}>
          <div id="app-shell">
            <ThemeToggle />
            <LanguageToggle />
            <ServiceWorkerRegistration />
            <PushPermissionPrompt />
            {children}
          </div>
        </I18nProvider>
      </body>
    </html>
  );
}
