/**
 * The business map: tile source and the hand-off links to Iranian routing
 * apps.
 *
 * Raster tiles on purpose: street names are drawn on the tile server and
 * arrive as pictures, so Persian is joined and right-to-left correctly in
 * every browser — no client-side text shaping to get wrong. OpenStreetMap's
 * standard tiles are free and keyless and show local (Persian) names inside
 * Iran; their usage policy asks for the "© OpenStreetMap" credit on the map.
 * The owner app (mobile_owner, core/ui/components/map/TileMap.kt) draws the
 * same tiles, so owner and customer see the same map. To move to another
 * source, change both.
 */
export const MAP_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
export const MAP_ATTRIBUTION =
  '<a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">© OpenStreetMap</a>';
export const MAP_MAX_ZOOM = 19;

export interface LatLng {
  lat: number;
  lng: number;
}

/** The business's pin, or null when the owner hasn't set one (or the API
 *  withheld it — it is masked along with the address for guest viewers). */
export function businessLocation(biz: {
  latitude?: number | null;
  longitude?: number | null;
}): LatLng | null {
  const { latitude, longitude } = biz;
  if (typeof latitude !== 'number' || typeof longitude !== 'number') return null;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  return { lat: latitude, lng: longitude };
}

/** Neshan's short share link. Opens the point in the Neshan app when it is
 *  installed, otherwise on neshan.org, with routing one tap away. */
export function neshanUrl({ lat, lng }: LatLng): string {
  return `https://nshn.ir/?lat=${lat}&lng=${lng}`;
}

/** Balad's location page, centred on the point. */
export function baladUrl({ lat, lng }: LatLng): string {
  return `https://balad.ir/location?latitude=${lat}&longitude=${lng}&zoom=16`;
}
