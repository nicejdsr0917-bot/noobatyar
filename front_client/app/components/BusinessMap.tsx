'use client';

import { useEffect, useRef } from 'react';
import 'leaflet/dist/leaflet.css';
import { MAP_ATTRIBUTION, MAP_MAX_ZOOM, MAP_TILE_URL, type LatLng } from '@/lib/maps';
import { t } from '@/lib/i18n';

// Tabler's map-pin, filled with the page's primary colour (the business's own
// colour on a .themed page). A divIcon rather than Leaflet's default marker:
// the default's PNGs are resolved by URL at runtime and break under a bundler.
const PIN_HTML = `
<svg viewBox="0 0 24 24" width="40" height="40" aria-hidden="true">
  <path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7z"
        fill="var(--color-primary)" stroke="#fff" stroke-width="1.5"/>
  <circle cx="12" cy="9" r="2.6" fill="#fff"/>
</svg>`;

interface Props {
  location: LatLng;
  /** Business name, for the map's accessible label and the pin's tooltip. */
  title: string;
}

/**
 * A small map with the business's pin. Leaflet is loaded only here, after
 * hydration — it touches `window` at import time, and a visitor who never
 * scrolls to the map should not pay for it on first paint either.
 *
 * On touch screens one finger keeps scrolling the page (the map would
 * otherwise swallow every swipe that starts on it) and two fingers zoom;
 * the mouse wheel likewise scrolls the page, with +/- for zoom.
 */
export default function BusinessMap({ location, title }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const { lat, lng } = location;

  useEffect(() => {
    let cancelled = false;
    let map: import('leaflet').Map | undefined;

    import('leaflet').then((mod) => {
      const L = (mod as unknown as { default?: typeof mod }).default ?? mod;
      if (cancelled || !containerRef.current) return;

      map = L.map(containerRef.current, {
        center: [lat, lng],
        zoom: 16,
        maxZoom: MAP_MAX_ZOOM,
        scrollWheelZoom: false,
        dragging: !L.Browser.mobile,
        attributionControl: false,
      });
      L.tileLayer(MAP_TILE_URL, { maxZoom: MAP_MAX_ZOOM }).addTo(map);
      // prefix: false drops Leaflet's own "Leaflet" credit; the OSM one is
      // the credit the tile licence requires.
      L.control.attribution({ prefix: false }).addAttribution(MAP_ATTRIBUTION).addTo(map);
      L.marker([lat, lng], {
        title,
        keyboard: false,
        icon: L.divIcon({
          className: 'biz-map-pin',
          html: PIN_HTML,
          iconSize: [40, 40],
          iconAnchor: [20, 37],
        }),
      }).addTo(map);
    });

    return () => {
      cancelled = true;
      map?.remove();
    };
  }, [lat, lng, title]);

  // dir="ltr": the map is laid out the same way in every language — Leaflet
  // positions its panes and controls assuming a left-to-right container.
  return (
    <div
      ref={containerRef}
      className="biz-map"
      dir="ltr"
      role="region"
      aria-label={t('locationOnMap', title)}
    />
  );
}
