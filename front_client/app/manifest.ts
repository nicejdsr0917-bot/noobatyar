import type { MetadataRoute } from 'next';
import { t } from '@/lib/i18n';

// Served at /manifest.webmanifest, and Next emits the <link rel="manifest">
// tag for it automatically — layout.tsx must not also declare `manifest`, or
// the two disagree on the URL.
//
// This replaces a dangling `manifest: '/manifest.json'` in the old layout
// metadata: no such file ever existed in app/ or public/, so the browser was
// fetching a 404 and the site was not installable.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: t('noobatyarOnlineBooking'),
    short_name: t('noobatyar'),
    description: t('noobatyarOnlineBookingBookAnytime'),
    start_url: '/',
    display: 'standalone',
    dir: 'rtl',
    lang: 'fa',
    background_color: '#f9fafb',
    theme_color: '#7c3aed',
    icons: [
      {
        src: '/icons/icon-192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        // Full-bleed square: Android crops installed-app icons to the launcher's
        // own shape, and would slice into a rounded icon's artwork.
        src: '/icons/icon-maskable-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}
